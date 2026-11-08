"""4.7.7 Jeweled Transport Pipe compatibility and every target's effective sources."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location('materialize_sources', ROOT / 'scripts/materialize_sources.py')
materializer = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(materializer)


class JeweledPipeTests(unittest.TestCase):
    def test_all_seven_targets_materialize_real_pipe_and_gui(self):
        with tempfile.TemporaryDirectory() as tmp:
            for target in materializer.config()['targets'].split(','):
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materializer.materialize(target, effective)
                    base = effective / 'src/main'
                    java = base / 'java/buildcraft/additionalpipes'
                    resources = base / 'resources/assets/additionalpipes'
                    pipe = (java / 'pipes/APJeweledPipes.java').read_text()
                    logic = (java / 'pipes/JeweledPipeComponent.java').read_text()
                    matcher = (java / 'pipes/APJeweledItemMatcher.java').read_text()
                    menu = (java / 'pipes/JeweledMenu.java').read_text()
                    screen = (java / 'client/JeweledScreen.java').read_text()
                    registry = (java / 'platform/APContentRegistry.java').read_text()
                    entry = (java / 'AdditionalPipes.java').read_text()
                    self.assertIn('JEWELED = "pipe_items_jeweled"', pipe)
                    if target == '1.21.11-neoforge' or target.startswith('26.'):
                        self.assertIn('import net.minecraft.resources.Identifier;', pipe)
                        self.assertIn('import net.minecraft.resources.Identifier;', logic)
                        self.assertIn('Identifier typeId()', logic)
                        self.assertNotIn('ResourceLocation', pipe + logic)
                    else:
                        self.assertIn('import net.minecraft.resources.ResourceLocation;', pipe)
                        self.assertIn('ResourceLocation typeId()', logic)
                    self.assertIn('PipeApi.flowItems', pipe)
                    self.assertIn('JeweledPipeComponent::new', pipe)
                    self.assertIn('JeweledRouting.select(', logic)
                    self.assertIn('PersistentType.<JeweledPipeComponent, OpaqueData>builder', pipe)
                    self.assertIn('void addDrops(List<ItemStack> drops, int fortune)', logic)
                    self.assertIn('APAdvancedWoodStackIO.write', logic)
                    self.assertIn('APAdvancedWoodStackIO.read', logic)
                    self.assertIn('MAX_PERSISTED_BYTES = 64 * 1024', logic)
                    self.assertIn('setOptions(int side, SideFilterData.Options options)', logic)
                    self.assertIn('new Slot(inventory, row * 9 + col', menu)
                    self.assertIn('clickMenuButton(Player player, int button)', menu)
                    self.assertIn('component.setOptions(side, after)', menu)
                    self.assertIn('JEWELED_MENU', registry)
                    self.assertIn('APJeweledPipes.isJeweled(id)', registry)
                    self.assertIn('APJeweledPipes.register();', entry)
                    self.assertIn('APJeweledPipes.createItem(path)', entry)
                    self.assertIn('class APJeweledMenuAccess', (java / 'platform/APJeweledMenuAccess.java').read_text())
                    self.assertIn('icon("jeweled_gui", 202, 211)', screen)
                    self.assertIn('JeweledGuiLayout.tabEnds(', screen)
                    self.assertIn('JeweledGuiLayout.tabAt(', screen)
                    self.assertIn('JeweledGuiLayout.toggleAt(', screen)
                    self.assertIn('drawOriginalTabs(graphics)', screen)
                    self.assertIn('drawOriginalToggle(graphics', screen)
                    self.assertIn('OUTLINES[', screen)
                    self.assertNotIn('Button.builder(', screen)
                    self.assertTrue((resources / 'textures/gui/jeweled_gui.png').exists())
                    for side in range(1, 7):
                        with self.subTest(target=target, outline=side):
                            image = resources / f'textures/gui/jeweled_outline_{side}.png'
                            self.assertTrue(image.exists(), image)
                            self.assertEqual(image.read_bytes()[:8], b'\x89PNG\r\n\x1a\n')
                    names = json.loads((resources / 'lang/en_us.json').read_text())
                    self.assertEqual(names['pipe.additionalpipes.pipe_items_jeweled'], 'Jeweled Transport Pipe')
                    self.assertEqual(names['gui.additionalpipes.jeweled'], 'Jeweled Pipe')
                    self.assertEqual(names['gui.additionalpipes.jeweled.unsorted'], 'Accept Unsorted')
                    self.assertEqual(names['gui.additionalpipes.jeweled.match'], 'Match:')
                    self.assertEqual(names['gui.additionalpipes.jeweled.nbt'], 'Tag')
                    self.assertEqual(names['gui.additionalpipes.jeweled.metadata'], 'Meta')
                    self.assertIn('isSameItemSameTags', matcher) if target in ('1.19.2-forge', '1.20.1-forge') else self.assertIn('isSameItemSameComponents', matcher)
                    if target.startswith('26.'):
                        self.assertIn('GuiGraphicsExtractor', screen)
                    elif target == '1.19.2-forge':
                        self.assertIn('PoseStack', screen)
                    elif target == '1.20.1-forge':
                        self.assertIn('GuiGraphics', screen)
                        # 1.20.1 Screen.renderBackground accepts only GuiGraphics.
                        self.assertIn('renderBackground(graphics);', screen)
                        self.assertNotIn('renderBackground(graphics, mouseX, mouseY, partialTick);', screen)
                    else:
                        self.assertIn('GuiGraphics', screen)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_original_477_tab_positions_and_click_regions(self):
        with tempfile.TemporaryDirectory() as tmp:
            sources = [
                ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/client/JeweledGuiLayout.java',
                ROOT / 'scripts/tests/java/JeweledGuiLayoutRegression.java',
            ]
            subprocess.run(['javac', '--release', '17', '-d', tmp, *map(str, sources)],
                           check=True, text=True, capture_output=True)
            completed = subprocess.run(['java', '-cp', tmp, 'JeweledGuiLayoutRegression'],
                                       check=True, text=True, capture_output=True)
            self.assertIn('PASS JeweledGuiLayoutRegression', completed.stdout)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_original_routing_and_162_filter_slots(self):
        with tempfile.TemporaryDirectory() as tmp:
            sources = [
                ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/filter/SideFilterData.java',
                ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/pipes/JeweledRouting.java',
                ROOT / 'scripts/tests/java/JeweledPipeRegression.java',
            ]
            subprocess.run(['javac', '--release', '17', '-d', tmp, *map(str, sources)],
                           check=True, text=True, capture_output=True)
            done = subprocess.run(['java', '-cp', tmp, 'JeweledPipeRegression'],
                                  check=True, text=True, capture_output=True)
            self.assertIn('PASS JeweledPipeRegression', done.stdout)


if __name__ == '__main__':
    unittest.main()
