"""Verify that all AP targets use BCCE's non-consuming phantom-filter contract."""
import importlib.util
import re
from pathlib import Path
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('materialize_sources', SCRIPTS / 'materialize_sources.py')
MATERIALIZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MATERIALIZER)
TARGETS = (
    '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
    '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
)

class PhantomFilterTests(unittest.TestCase):
    def test_phantom_slots_on_all_targets(self):
        with tempfile.TemporaryDirectory() as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    MATERIALIZER.materialize(target, effective)
                    src = effective / 'src/main/java/buildcraft/additionalpipes'
                    menu = (src / 'pipes/AdvancedWoodMenu.java').read_text()
                    component = (src / 'pipes/AdvancedWoodComponent.java').read_text()
                    handler = (src / 'pipes/APInsertionUiServer.java').read_text()
                    self.assertIn('new FilterSlot(filterInventory, slot, 8 + 18 * slot, 18)', menu)
                    self.assertIn('FilterSlot extends Slot implements IPhantomSlot', menu)
                    self.assertIn('BCMenuUtil.handleFakeSlotClick(this, slotId, dragType, clickType, player)', menu)
                    self.assertIn('canAdjustCount() { return false; }', menu)
                    self.assertIn('mayPickup(Player player) { return false; }', menu)
                    self.assertIn('mayPlace(ItemStack stack) { return false; }', menu)
                    self.assertIn('getMaxStackSize() { return 1; }', menu)
                    self.assertIn('if (index < FILTER_COUNT) return ItemStack.EMPTY;', menu)
                    self.assertNotIn('moveItemStackTo(original, 0, FILTER_COUNT', menu)
                    self.assertIn('removeItem(int slot, int amount) { return ItemStack.EMPTY; }', menu)
                    self.assertIn('removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }', menu)
                    self.assertIn('filterStacks[slot] = phantomCopy(stack);', component)
                    self.assertIn('ghost.setCount(1);', component)
                    self.assertIn('value.filterStacks[i] = phantomCopy(stored);', component)
                    self.assertIn('out.writeByte(5);', component)
                    self.assertIn('if (format == 4 && !stored.isEmpty()) value.pendingLegacyItems.add(stored);', component)
                    self.assertIn('for (ItemStack stack : pendingLegacyItems)', component)
                    self.assertNotIn('for (ItemStack stack : filterStacks) if (!stack.isEmpty()) drops.add', component)
                    self.assertIn('component.returnLegacyItems(player);', handler)
                    self.assertIn('APPlayerDrops.drop(player, returned)', component)
                    self.assertIn('writeLong(value.energyMicroMj)', component)
                    self.assertIn('public boolean permits(ItemStack stack)', component)
                    self.assertEqual(menu.count('addSlot(new Slot(playerInventory,'), 2)
                    self.assertTrue(re.search(r'for \(int slot = 0; slot < FILTER_COUNT; slot\+\+\)', menu))

if __name__ == '__main__':
    unittest.main()
