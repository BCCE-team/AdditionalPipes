"""API-independent regression checks for AP source layering."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SCRIPTS = Path(__file__).resolve().parents[1]
_spec=importlib.util.spec_from_file_location('materialize_sources',SCRIPTS/'materialize_sources.py')
module=importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(module)


class SourceLayerTests(unittest.TestCase):
    def test_layers_precedence_and_isolation(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            for subdir in ('source-shared', 'source-families/old', 'source-platforms/forge',
                           'source-family-platforms/old/forge', 'version-src/1.19.2-forge'):
                (root/subdir/'src/main/java/sample').mkdir(parents=True)
            config=(root/'build-config')
            config.mkdir()
            (config/'common.properties').write_text('''common.source.shared_root=source-shared
source.family.old.root=source-families/old
source.platform.forge.root=source-platforms/forge
source.family_platform.old.forge.root=source-family-platforms/old/forge
''')
            (config/'targets.properties').write_text('''targets=1.19.2-forge
target.1.19.2-forge.source.family=old
target.1.19.2-forge.source.platform=forge
target.1.19.2-forge.source.overlay_root=version-src/1.19.2-forge
''')
            for name, content in [('source-shared','base'),('source-families/old','family'),
                                  ('source-platforms/forge','platform'),
                                  ('source-family-platforms/old/forge','pair'),
                                  ('version-src/1.19.2-forge','target')]:
                (root/name/'src/main/java/sample/Demo.java').write_text(content)
            with patch.object(module, 'ROOT', root):
                report=module.materialize('1.19.2-forge', root/'build/selected')
            self.assertEqual((root/'build/selected/src/main/java/sample/Demo.java').read_text(),'target')
            self.assertEqual(len(report['overrides']),4)
            self.assertEqual((root/'source-shared/src/main/java/sample/Demo.java').read_text(),'base')

    def test_dimension_access_for_each_target(self):
        modern = {'1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge'}
        targets = (
            '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
            '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
        )
        with tempfile.TemporaryDirectory() as tmp:
            for target in targets:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    module.materialize(target, effective)
                    code = (effective / 'src/main/java/buildcraft/additionalpipes/platform/APDimensionIds.java').read_text()
                    accessor = 'identifier()' if target in modern else 'location()'
                    self.assertIn(f'level.dimension().{accessor}', code)
                    if target.startswith('26.'):
                        tickets = (effective / 'src/main/java/buildcraft/additionalpipes/platform/chunk/PlatformChunkTickets.java').read_text()
                        self.assertIn('chunk.x(), chunk.z()', tickets)
                        self.assertNotIn('chunk.x, chunk.z', tickets)
                    if target == '1.21.11-neoforge':
                        registry = (effective / 'src/main/java/buildcraft/additionalpipes/platform/APContentRegistry.java').read_text()
                        self.assertIn('import net.minecraft.resources.Identifier;', registry)
                        self.assertNotIn('ResourceLocation', registry)

    def test_26x_registration_ids_and_layer(self):
        for target in ('26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge'):
            with self.subTest(target=target), tempfile.TemporaryDirectory() as tmp:
                effective = Path(tmp) / target
                report = module.materialize(target, effective)
                path = 'src/main/java/buildcraft/additionalpipes/platform/APContentRegistry.java'
                file = next(entry for entry in report['files'] if entry['path'] == path)
                self.assertEqual(file['source_layer'], 'source-family-platforms/26.X/neoforge')
                registry = (effective / path).read_text()
                self.assertIn('BlockBehaviour.Properties.of().setId(', registry)
                self.assertIn('new Item.Properties().setId(', registry)
                self.assertIn('BCRegistrationScope.item(', registry)
                self.assertIn('BLOCKS.register(bus);', registry)
                self.assertIn('ITEMS.register(bus);', registry)

    def test_teleport_screen_background_signature_by_target(self):
        targets = (
            '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
            '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
        )
        screen_path = 'src/main/java/buildcraft/additionalpipes/client/TeleportConfigScreen.java'
        signatures = {
            '1.19.2-forge': 'renderBackground(graphics);',
            '1.20.1-forge': 'renderBackground(graphics);',
        }
        with tempfile.TemporaryDirectory() as temp:
            for target in targets:
                with self.subTest(target=target):
                    selected = Path(temp) / target
                    module.materialize(target, selected)
                    source = (selected / screen_path).read_text()
                    if target.startswith('26.'):
                        self.assertIn('import net.minecraft.client.gui.GuiGraphicsExtractor;', source)
                        self.assertIn('void extractBackground(GuiGraphicsExtractor graphics,', source)
                        self.assertIn('void extractRenderState(GuiGraphicsExtractor graphics,', source)
                        self.assertIn('super.extractBackground(graphics, mouseX, mouseY, partialTick);', source)
                        self.assertIn('super.extractRenderState(graphics, mouseX, mouseY, partialTick);', source)
                        self.assertIn('graphics.text(font, screenTitle(),', source)
                        self.assertNotIn('GuiGraphics;', source)
                        self.assertNotIn('renderBackground(', source)
                        manifest = json.loads((selected / 'source-manifest.json').read_text())
                        layer = next(entry['source_layer'] for entry in manifest['files']
                                     if entry['path'] == screen_path)
                        self.assertEqual(layer, 'source-family-platforms/26.X/neoforge')
                    else:
                        expected = signatures.get(target, 'renderBackground(graphics, mouseX, mouseY, partialTick);')
                        self.assertIn(expected, source)
                        self.assertEqual(source.count('renderBackground('), 1)

    def test_teleport_screen_mouse_click_api_by_target(self):
        targets = (
            '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
            '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
        )
        screen_path = 'src/main/java/buildcraft/additionalpipes/client/TeleportConfigScreen.java'
        buttons_path = 'src/main/java/buildcraft/additionalpipes/client/APMouseButtons.java'
        with tempfile.TemporaryDirectory() as temp:
            for target in targets:
                with self.subTest(target=target):
                    selected = Path(temp) / target
                    report = module.materialize(target, selected)
                    source = (selected / screen_path).read_text()
                    if target == '1.21.11-neoforge' or target.startswith('26.'):
                        self.assertIn('import net.minecraft.client.input.MouseButtonEvent;', source)
                        self.assertIn('public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)', source)
                        self.assertIn('return super.mouseClicked(event, doubleClick);', source)
                        self.assertNotIn('super.mouseClicked(mouseX, mouseY, button)', source)
                    else:
                        self.assertIn('public boolean mouseClicked(double mouseX, double mouseY, int button)', source)
                        self.assertIn('return super.mouseClicked(mouseX, mouseY, button);', source)
                    if target.startswith('26.'):
                        self.assertIn('APMouseButtons.legacy(event.button())', source)
                        buttons = (selected / buttons_path).read_text()
                        layer = next(entry['source_layer'] for entry in report['files']
                                     if entry['path'] == buttons_path)
                        if target == '26.3-neoforge':
                            self.assertIn('BCGuiInput.legacyButton(button)', buttons)
                            self.assertEqual(layer, 'version-src/26.3-neoforge')
                        else:
                            self.assertIn('return button;', buttons)
                            self.assertEqual(layer, 'source-family-platforms/26.X/neoforge')

    def test_1192_registry_access_for_insertion_pipes(self):
        files = (
            'src/main/java/buildcraft/additionalpipes/pipes/AdvancedWoodComponent.java',
            'src/main/java/buildcraft/additionalpipes/pipes/APInsertionUiServer.java',
            'src/main/java/buildcraft/additionalpipes/client/InsertionConfigScreen.java',
        )
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / '1.19.2-forge'
            manifest = module.materialize('1.19.2-forge', target)
            layers = {entry['path']: entry['source_layer'] for entry in manifest['files']}
            for filename in files:
                with self.subTest(file=filename):
                    code = (target / filename).read_text()
                    self.assertEqual(layers[filename], 'version-src/1.19.2-forge')
                    self.assertNotIn('BuiltInRegistries', code)
            self.assertIn('ForgeRegistries.ITEMS.getKey(', (target / files[0]).read_text())
            advanced = (target / files[0]).read_text()
            self.assertIn('ForgeRegistries.ITEMS.getValues()', advanced)
            self.assertIn('public MjPort energyPort()', advanced)
            for other in ('1.20.1-forge', '1.21.1-neoforge', '1.21.11-neoforge',
                          '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge'):
                with self.subTest(target=other):
                    result = Path(tmp) / other
                    module.materialize(other, result)
                    self.assertIn('BuiltInRegistries.ITEM.getKey(', (result / files[0]).read_text())

    def test_insertion_item_capabilities_by_target(self):
        path = 'src/main/java/buildcraft/additionalpipes/pipes/APItemPorts.java'
        targets = (
            '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
            '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
        )
        with tempfile.TemporaryDirectory() as tmp:
            for target in targets:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    manifest = module.materialize(target, effective)
                    sources = {entry['path']: entry['source_layer'] for entry in manifest['files']}
                    source = (effective / path).read_text()
                    if target == '1.21.11-neoforge' or target.startswith('26.'):
                        expected_layer = ('version-src/1.21.11-neoforge' if target == '1.21.11-neoforge'
                                          else 'source-family-platforms/26.X/neoforge')
                        self.assertEqual(sources[path], expected_layer)
                        self.assertIn('import buildcraft.lib.misc.CapUtil;', source)
                        self.assertIn('var items = CapUtil.getItemHandler(level, pos, side);', source)
                        self.assertIn('CapUtil.getItemHandler(level, pos, side) != null', source)
                        self.assertIn('items.insertItem(slot, stack.copy(), true)', source)
                        self.assertNotIn('Capabilities.ItemHandler.BLOCK', source)
                        self.assertNotIn('import net.neoforged.neoforge.items.IItemHandler;', source)
                    elif target == '1.21.1-neoforge':
                        self.assertEqual(sources[path], 'source-platforms/neoforge')
                        self.assertIn('Capabilities.ItemHandler.BLOCK', source)
                    else:
                        self.assertEqual(sources[path], 'source-platforms/forge')
                        self.assertIn('ForgeCapabilities.ITEM_HANDLER', source)

    def test_player_drop_prediction_by_target(self):
        targets = (
            '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
            '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
        )
        adapter = 'src/main/java/buildcraft/additionalpipes/platform/APPlayerDrops.java'
        server = 'src/main/java/buildcraft/additionalpipes/pipes/APInsertionUiServer.java'
        with tempfile.TemporaryDirectory() as tmp:
            for target in targets:
                with self.subTest(target=target):
                    output = Path(tmp) / target
                    report = module.materialize(target, output)
                    layers = {entry['path']: entry['source_layer'] for entry in report['files']}
                    adapter_source = (output / adapter).read_text()
                    server_source = (output / server).read_text()
                    if target == '26.3-neoforge':
                        self.assertEqual(layers[adapter], 'version-src/26.3-neoforge')
                        self.assertIn('import net.minecraft.util.Prediction;', adapter_source)
                        self.assertIn('player.drop(stack, false, Prediction.SERVER_ONLY);', adapter_source)
                    else:
                        self.assertEqual(layers[adapter], 'source-shared')
                        self.assertIn('player.drop(stack, false);', adapter_source)
                        self.assertNotIn('Prediction', adapter_source)
                    self.assertNotIn('APPlayerDrops.drop(player, returned);', server_source)
                    self.assertIn('APAdvancedWoodMenuAccess.open(', server_source)

    def test_reject_unknown_target(self):
        with self.assertRaises(ValueError):
            module.get_source_layers('missing',module.config())


if __name__=='__main__':
    unittest.main()
