"""Full Addition Transport Pipe registration, routing parity and source-layer smoke tests."""
from pathlib import Path
import json
import shutil
import subprocess
import tempfile
import unittest
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from materialize_sources import materialize

TARGETS = (
    "1.19.2-forge", "1.20.1-forge", "1.21.1-neoforge", "1.21.11-neoforge",
    "26.1.2-neoforge", "26.2-neoforge", "26.3-neoforge",
)


class AdditionPipeTests(unittest.TestCase):
    def test_materialized_targets_use_a_real_item_pipe(self):
        with tempfile.TemporaryDirectory(prefix="ap-addition-") as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    dest = Path(tmp) / target
                    materialize(target, dest)
                    pkg = dest / "src/main/java/buildcraft/additionalpipes"
                    reg = (pkg / "platform/APContentRegistry.java").read_text()
                    bootstrap = (pkg / "AdditionalPipes.java").read_text()
                    addition = (pkg / "pipes/APAdditionPipes.java").read_text()
                    component = (pkg / "pipes/AdditionPipeComponent.java").read_text()
                    policy = (pkg / "pipes/AdditionRouting.java").read_text()
                    ports = (pkg / "pipes/APItemPorts.java").read_text()

                    self.assertIn("APAdditionPipes.isAddition(id)", reg)
                    if target == "1.19.2-forge":
                        self.assertIn("createTabbedPipeItem(id)", reg)
                    else:
                        self.assertIn("AdditionalPipes.createPipeItem(id)", reg)
                    self.assertIn("APAdditionPipes.register();", bootstrap)
                    self.assertIn("APAdditionPipes.createItem(path)", bootstrap)
                    self.assertIn('ADDITION = "pipe_items_addition"', addition)
                    self.assertIn('.itemProfile(new ItemTransportProfile(64, 1))', addition)
                    self.assertIn('.component(COMPONENT).build()', addition)
                    self.assertIn('new PipeComponentType<>(COMPONENT, AdditionPipeComponent::new, null)', addition)
                    self.assertIn('.texSuffixes("4")', addition)
                    self.assertIn('.flow(PipeApi.flowItems)', addition)
                    self.assertIn('implements ItemRouteComponent, PipeComponentLifecycle', component)
                    self.assertIn('AdditionRouting.select(candidates, inventories, preferred)', component)
                    self.assertIn('context.pipe().endpoint(side) != PipeEndpointKind.BLOCK', component)
                    self.assertIn('APItemPorts.containsMatchingItem(', component)
                    self.assertIn('APItemPorts.canInsert(', component)
                    self.assertIn('containsMatchingItem(Level level', ports)
                    self.assertIn('getDamageValue() == desired.getDamageValue()', ports)
                    self.assertIn('items.insert(stack.copy(), false, true)', ports)
                    self.assertNotIn('items.insert(stack.copy(), false, false)', ports)
                    self.assertIn('return preferred != 0 ? preferred : candidates & ~inventoryDestinations;', policy)
                    modern = target == "1.21.11-neoforge" or target.startswith("26.")
                    symbol = "Identifier" if modern else "ResourceLocation"
                    self.assertIn(f"public static final {symbol} COMPONENT", addition)
                    self.assertIn(f"public {symbol} typeId()", component)
                    self.assertTrue((dest / "src/main/resources/assets/additionalpipes/textures/block/4.png").is_file())
                    assets = dest / "src/main/resources/assets/additionalpipes"
                    self.assertTrue((assets / "models/item/pipe_items_addition.json").exists())
                    self.assertTrue((assets / "blockstates/pipe_items_addition.json").exists())
                    self.assertIn("pipe.additionalpipes.pipe_items_addition", json.loads((assets / "lang/en_us.json").read_text()))
                    if modern:
                        self.assertIn('import net.minecraft.world.level.block.entity.BlockEntity;', ports)
                        self.assertIn('ItemStorage handler = PlatformStorage.items(level, pos, side);', ports)
                        self.assertIn('private static boolean contains(ItemStorage handler, ItemStack stack)', ports)
                        self.assertNotIn('Capabilities.ItemHandler.BLOCK', ports)
                    elif target.endswith("neoforge"):
                        self.assertIn('IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)', ports)
                    else:
                        self.assertIn('block.getCapability(ForgeCapabilities.ITEM_HANDLER, side)', ports)

    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required")
    def test_java_routing_policy_with_matching_full_and_empty_inventories(self):
        source = ROOT / "source-shared/src/main/java/buildcraft/additionalpipes/pipes/AdditionRouting.java"
        with tempfile.TemporaryDirectory(prefix="ap-addition-java-") as tmp:
            root = Path(tmp)
            (root / "TestAddition.java").write_text('''import buildcraft.additionalpipes.pipes.AdditionRouting;
public class TestAddition {
    static void check(int expected, int candidates, int inventory, int matching) {
        int result = AdditionRouting.select(candidates, inventory, matching);
        if (result != expected) throw new AssertionError("Expected " + expected + " got " + result);
    }
    public static void main(String[] args) {
        // Four exits: matching chest 0, nonmatching chest 1, pipes 2 and 3.
        check(0b000001, 0b001111, 0b000011, 0b000001);
        // Both chests contain an item and can accept it: equal routing candidates.
        check(0b000011, 0b001111, 0b000011, 0b000011);
        // Matching chest is full: fall back to pipe exits, not an empty chest.
        check(0b001100, 0b001111, 0b000011, 0);
        // No matching chest: ordinary pipe continues forward.
        check(0b001100, 0b001111, 0b000011, 0);
        // No inventory destinations: vanilla pipe routing remains unchanged.
        check(0b001100, 0b001100, 0, 0);
        // Only a mismatching chest: no route; caller's normal blockage applies.
        check(0, 0b000010, 0b000010, 0);
        // Matching destination not in BCCE's candidate set must never be picked.
        check(0b001100, 0b001100, 0b000001, 0b000001);
        // No candidates.
        check(0, 0, 0, 0);
        System.out.println("PASS");
    }
}''')
            subprocess.run(["javac", "-d", str(root), str(source), str(root / "TestAddition.java")], check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", str(root), "TestAddition"], check=True, capture_output=True, text=True)
            self.assertEqual(result.stdout.strip(), "PASS")


if __name__ == "__main__":
    unittest.main()
