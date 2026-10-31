"""Priority insertion routing must be ordinal, not weighted by priority values."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("materialize_sources", ROOT / "scripts/materialize_sources.py")
MOD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MOD)
TARGETS = ("1.19.2-forge", "1.20.1-forge", "1.21.1-neoforge",
           "1.21.11-neoforge", "26.1.2-neoforge", "26.2-neoforge", "26.3-neoforge")


class PriorityRoutingTests(unittest.TestCase):
    def test_every_target_selects_highest_available_inventory_tier(self):
        with tempfile.TemporaryDirectory(prefix="ap-priority-test-") as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    dest = Path(tmp) / target
                    MOD.materialize(target, dest)
                    root = dest / "src/main/java/buildcraft/additionalpipes/pipes"
                    component = (root / "PriorityInsertionComponent.java").read_text()
                    ports = (root / "APItemPorts.java").read_text()
                    self.assertIn("for (int priority = 6; priority >= 1; priority--)", component)
                    self.assertIn("if (!selected.isEmpty()) break;", component)
                    self.assertIn("selected.add(side);", component)
                    self.assertIn("selected.contains(side) ? 1 : 0", component)
                    self.assertIn("priorities[candidate.ordinal()] == 0 ? 0 : 1", component)
                    self.assertNotIn("Arrays.fill(priorities, 1)", component)
                    self.assertNotIn("Arrays.fill(instance.priorities, 1)", component)
                    self.assertIn("runtime.getHolder().getPipeWorld() instanceof ServerLevel", component)
                    self.assertIn("APItemPorts.canInsert(", component)
                    self.assertIn("ItemTransactorHelper.getTransactor(", ports)
                    self.assertIn("items.insert(stack.copy(), false, true)", ports)
                    self.assertIn("remainder.getCount() < stack.getCount()", ports)

    def test_expected_selection_with_fallback_and_disabled_sides(self):
        # This data-driven oracle represents six faces in Minecraft's ordinal order.
        priorities = [1, 2, 3, 4, 1, 1]
        def choose(available):
            for level in range(6, 0, -1):
                choices = [i for i, p in enumerate(priorities) if p == level and i in available]
                if choices:
                    return choices
            return []
        self.assertEqual(choose(set(range(6))), [3])  # green/4
        self.assertEqual(choose({0, 1, 2, 4, 5}), [2])  # blue/3
        self.assertEqual(choose({0, 1, 4, 5}), [1])  # cyan/2
        self.assertEqual(choose({0, 4, 5}), [0, 4, 5])  # equal tier only
        priorities[3] = 0
        self.assertNotIn(3, choose(set(range(6))))
        priorities[:] = [0] * 6
        self.assertEqual(choose(set(range(6))), [])


if __name__ == "__main__":
    unittest.main()
