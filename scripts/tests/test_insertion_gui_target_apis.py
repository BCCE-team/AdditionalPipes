"""Ensure every AP target uses a real container for Advanced Wooden Pipe."""
import importlib.util
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

class AdvancedWoodContainerTargetTests(unittest.TestCase):
    def test_phantom_filters_and_player_inventory_for_all_targets(self):
        with tempfile.TemporaryDirectory() as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    MATERIALIZER.materialize(target, effective)
                    src = effective / 'src/main/java/buildcraft/additionalpipes'
                    menu = (src / 'pipes/AdvancedWoodMenu.java').read_text()
                    screen = (src / 'client/AdvancedWoodScreen.java').read_text()
                    priority = (src / 'client/InsertionConfigScreen.java').read_text()
                    component = (src / 'pipes/AdvancedWoodComponent.java').read_text()
                    registry = (src / 'platform/APContentRegistry.java').read_text()
                    access = (src / 'platform/APAdvancedWoodMenuAccess.java').read_text()
                    handler = (src / 'pipes/APInsertionUiServer.java').read_text()
                    services = (src / 'service/APServices.java').read_text()
                    self.assertIn('extends AbstractContainerMenu', menu)
                    self.assertIn('implements Container', menu)
                    self.assertIn('new FilterSlot(filterInventory, slot, 8 + 18 * slot, 18)', menu)
                    self.assertIn('new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 76 + row * 18)', menu)
                    self.assertIn('new Slot(playerInventory, col, 8 + col * 18, 134)', menu)
                    self.assertIn('quickMoveStack(', menu)
                    self.assertIn('BCMenuUtil.handleFakeSlotClick(', menu)
                    self.assertIn('moveItemStackTo(', menu)
                    self.assertIn('component.getFilterStack(', menu)
                    self.assertIn('component.setFilterStack(', menu)
                    self.assertIn('component.setExcluded(!component.excluded())', menu)
                    self.assertIn('extends AbstractContainerScreen<AdvancedWoodMenu>', screen)
                    self.assertIn('handleInventoryButtonClick(menu.containerId, 0)', screen)
                    self.assertIn('textures/gui/advanced_wood_gui.png', screen)
                    self.assertNotIn('AdvancedWoodFilterUpdate(', screen)
                    self.assertNotIn('selectedInventorySlot', screen)
                    self.assertIn('textures/gui/priority_gui.png', priority)
                    self.assertIn('new APPacket.InsertionViewRequest(', priority)
                    self.assertIn('if (!view.priority()) return;', (src / 'client/APClientPackets.java').read_text())
                    self.assertIn('new APPacket.ValueUpdate(', priority)
                    self.assertIn('MENUS.register(bus);', registry)
                    self.assertIn('ADVANCED_WOOD_MENU', registry)
                    self.assertIn('new AdvancedWoodMenu(', access)
                    self.assertIn('APAdvancedWoodMenuAccess.open(player, component, position)', handler)
                    self.assertNotIn('applyFilter(', handler)
                    self.assertIn('private final ItemStack[] filterStacks', component)
                    self.assertIn('APAdvancedWoodStackIO.read(in)', component)
                    self.assertIn('APAdvancedWoodStackIO.write(out, stack)', component)
                    self.assertIn('format == 2 || format == 3 || format == 4', component)
                    self.assertIn('for (ItemStack stack : pendingLegacyItems)', component)
                    self.assertNotIn('APMessageType.ADVANCED_WOOD_FILTER, APMessageType.INSERTION_VIEW_REQUEST', services)
                    if target.startswith(('1.19.', '1.20.')):
                        self.assertIn('NetworkHooks.openScreen', access)
                        if target == '1.19.2-forge':
                            self.assertIn('new Button(', screen)
                            self.assertIn('PoseStack', screen)
                    elif target.startswith('26.'):
                        self.assertIn('GuiGraphicsExtractor', screen)
                        self.assertIn('public void extractBackground(GuiGraphicsExtractor graphics,', screen)
                        self.assertIn('extractLabels(', screen)
                        self.assertIn('Identifier.fromNamespaceAndPath(', screen)
                    else:
                        self.assertIn('GuiGraphics', screen)
                        self.assertIn('player.openMenu(', access)
                    self.assertTrue((effective / 'src/main/resources/assets/additionalpipes/textures/gui/advanced_wood_gui.png').is_file())

if __name__ == '__main__':
    unittest.main()
