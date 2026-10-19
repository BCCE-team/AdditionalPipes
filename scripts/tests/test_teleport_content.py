import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from materialize_sources import materialize

TARGETS = (
    '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge', '1.21.11-neoforge',
    '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
)
PIPES = {
    'pipe_items_teleport': '0',
    'pipe_fluids_teleport': '2',
    'pipe_power_teleport': '3',
    'pipe_fe_teleport': '43',
}


class TeleportContentTests(unittest.TestCase):
    def test_every_teleport_target(self):
        with tempfile.TemporaryDirectory(prefix='ap-teleport-resources-') as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materialize(target, effective)
                    src = effective / 'src/main/java/buildcraft/additionalpipes/pipes'
                    definition = (src / 'APTeleportPipes.java').read_text()
                    component = (src / 'TeleportPipeComponent.java').read_text()
                    self.assertIn('PipeApi.flowForgeEnergy', definition)
                    self.assertIn('transferExternalEnergy(', component)
                    self.assertIn('transferFluid(', component)
                    self.assertIn('transferMj(', component)
                    self.assertIn('context.transfer(', component)
                    self.assertIn('CODEC', component)
                    self.assertIn('PipeComponentLifecycle', component)
                    self.assertIn('TeleportUiServer.open(player, this)', component)
                    self.assertNotIn('Look at the pipe and use /apteleport', component)
                    self.assertIn('PipeActivationResult.SUCCESS', component)
                    gui = effective / 'src/main/java/buildcraft/additionalpipes/client/TeleportConfigScreen.java'
                    self.assertTrue(gui.is_file())
                    source = gui.read_text()
                    for label in ('Frequency:', 'Send Only', 'Receive Only', 'Send & Receive', 'Public', 'Private', 'Connected Pipes', 'Ownership'):
                        self.assertIn(label, source)
                    self.assertIn('PlatformNetwork.sendToServer(new APPacket.TeleportUpdate', source)
                    self.assertIn('new APPacket.TeleportViewRequest', source)
                    self.assertIn('Ledger_Neptune.SPRITE_SPLIT_POS', source)
                    self.assertIn('GUI_WIDTH = 228', source)
                    self.assertIn('GUI_HEIGHT = 117', source)
                    self.assertTrue((effective / 'src/main/resources/assets/additionalpipes/textures/gui/background_generic.png').is_file())
                    self.assertFalse((src / 'APTeleportCommands.java').exists())
                    modern = target in ('1.21.11-neoforge', '26.1.2-neoforge',
                                        '26.2-neoforge', '26.3-neoforge')
                    symbol = 'Identifier' if modern else 'ResourceLocation'
                    self.assertIn(f'import net.minecraft.resources.{symbol};', definition)
                    self.assertIn(f'public static {symbol} id(', definition)

                    res = effective / 'src/main/resources'
                    lang = json.loads((res / 'assets/additionalpipes/lang/en_us.json').read_text())
                    recipe_dir = 'recipe' if modern or target == '1.21.1-neoforge' else 'recipes'
                    for name, texture in PIPES.items():
                        self.assertIn(f'pipe.additionalpipes.{name}', lang)
                        self.assertTrue((res / f'assets/additionalpipes/models/item/{name}.json').is_file())
                        recipe_name = 'assembly/pipe_items_teleport' if name == 'pipe_items_teleport' else name
                        self.assertTrue((res / f'data/additionalpipes/{recipe_dir}/{recipe_name}.json').is_file())
                        self.assertIn('"' + texture + '"', definition)
                        if modern:
                            self.assertTrue((res / f'assets/additionalpipes/items/{name}.json').is_file())
                    fe_model = json.loads((res / 'assets/additionalpipes/models/item/pipe_fe_teleport.json').read_text())
                    self.assertEqual(fe_model['textures']['pipe'], 'additionalpipes:block/43')
                    self.assertEqual(fe_model['elements'][0]['from'], [4, 0, 4])
                    self.assertEqual(fe_model['elements'][0]['to'], [12, 16, 12])
                    self.assertTrue((res / 'assets/additionalpipes/textures/block/43.png').exists())
                    fe_recipe = json.loads((res / f'data/additionalpipes/{recipe_dir}/pipe_fe_teleport.json').read_text())
                    expected = ['additionalpipes:pipe_power_teleport', 'buildcrafttransport:gold_fe']
                    ingredients = [part if isinstance(part, str) else part['item'] for part in fe_recipe['ingredients']]
                    self.assertEqual(ingredients, expected)
                    self.assertEqual(fe_recipe['result'].get('id', fe_recipe['result'].get('item')),
                                     'additionalpipes:pipe_fe_teleport')


if __name__ == '__main__':
    unittest.main()
