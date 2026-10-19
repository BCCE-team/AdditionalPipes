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
PIPES = ('pipe_items_switch', 'pipe_fluids_switch', 'pipe_power_switch', 'pipe_fe_switch')
TEXTURES = ('16', '17', '20', '21', '22', '23', '41', '42')


class SwitchContentTests(unittest.TestCase):
    def test_all_targets(self):
        with tempfile.TemporaryDirectory(prefix='ap-switch-resources-') as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materialize(target, effective)
                    src = effective / 'src/main/java/buildcraft/additionalpipes/pipes/APSwitchPipes.java'
                    code = src.read_text(encoding='utf-8')
                    modern = target.startswith('26.') or target == '1.21.11-neoforge'
                    symbol = 'Identifier' if modern else 'ResourceLocation'
                    self.assertIn(f'import net.minecraft.resources.{symbol};', code)
                    self.assertIn(f'private static {symbol} id(', code)

                    res = effective / 'src/main/resources'
                    names = json.loads((res / 'assets/additionalpipes/lang/en_us.json').read_text())
                    for name in PIPES:
                        self.assertIn(f'pipe.additionalpipes.{name}', names)
                        self.assertTrue((res / f'assets/additionalpipes/models/item/{name}.json').is_file())
                        folder = 'recipe' if modern or target == '1.21.1-neoforge' else 'recipes'
                        recipe = res / f'data/additionalpipes/{folder}/{name}.json'
                        self.assertTrue(recipe.is_file(), str(recipe))
                        value = json.loads(recipe.read_text())
                        self.assertIn(f'additionalpipes:{name}', json.dumps(value))
                        if modern:
                            self.assertTrue((res / f'assets/additionalpipes/items/{name}.json').is_file())
                    for tex in TEXTURES:
                        self.assertTrue((res / f'assets/additionalpipes/textures/block/{tex}.png').is_file())
                    self.assertTrue((res / 'assets/minecraft/atlases/blocks.json').is_file())
                    block_models = res / 'assets/additionalpipes/models/block'
                    item_models = res / 'assets/additionalpipes/models/item'
                    pipes = sorted(block_models.glob('pipe_*.json'))
                    self.assertEqual(len(pipes), 19)
                    for block_path in pipes:
                        texture = json.loads(block_path.read_text())['textures']['all']
                        model = json.loads((item_models / block_path.name).read_text())
                        self.assertEqual(model['parent'], 'minecraft:block/block')
                        self.assertFalse(model['ambientocclusion'])
                        self.assertEqual(model['gui_light'], 'front')
                        self.assertEqual(model['textures'], {'pipe': texture, 'particle': texture})
                        self.assertNotIn('display', model)
                        self.assertEqual(len(model['elements']), 1)
                        element = model['elements'][0]
                        self.assertEqual(element['from'], [4, 0, 4])
                        self.assertEqual(element['to'], [12, 16, 12])
                        self.assertFalse(element['shade'])
                        for side in ('up', 'down', 'north', 'south', 'east', 'west'):
                            face = element['faces'][side]
                            self.assertEqual(face['texture'], '#pipe')
                            self.assertEqual(face['uv'], [4, 4, 12, 12] if side in ('up', 'down') else [4, 0, 12, 16])
                    if target == '1.19.2-forge':
                        self.assertTrue((effective / 'src/main/java/buildcraft/additionalpipes/platform/APSwitchSprites.java').is_file())


    def test_advanced_wood_and_priority_insertion(self):
        with tempfile.TemporaryDirectory(prefix='ap-insertion-resources-') as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materialize(target, effective)
                    src = effective / 'src/main/java/buildcraft/additionalpipes/pipes'
                    modern = target.startswith('26.') or target == '1.21.11-neoforge'
                    symbol = 'Identifier' if modern else 'ResourceLocation'
                    registration = (src / 'APInsertionPipes.java').read_text()
                    wood = (src / 'AdvancedWoodComponent.java').read_text()
                    priority = (src / 'PriorityInsertionComponent.java').read_text()
                    ui = (effective / 'src/main/java/buildcraft/additionalpipes/client/InsertionConfigScreen.java').read_text()
                    for feature in ('PipeApi.flowItems', 'PipeComponentType',
                                    'ADVANCED_WOOD', 'PRIORITY', 'PersistentType'):
                        self.assertIn(feature, registration)
                    self.assertIn(f'import net.minecraft.resources.{symbol};', registration)
                    self.assertIn(f'@Override public {symbol} typeId()', wood)
                    self.assertIn(f'@Override public {symbol} typeId()', priority)
                    for behaviour in ('CAPACITY = 64', 'ENERGY_PER_ITEM = 1',
                                      'SLOW_INTERVAL = 16', 'FILTER_SLOTS = 9',
                                      'itemPipePort(', 'OperationMode.SIMULATE', 'OperationMode.EXECUTE',
                                      'isDamageableItem()', 'addDrops(', 'MjDemandPort', 'MjAmount',
                                      'MjTransferResult', 'Optional<MjPort> mjPort(',
                                      'ENERGY_PER_ITEM_MICRO_MJ', 'MICRO_MJ_PER_RF'):
                        self.assertIn(behaviour, wood)
                    self.assertNotIn('ExternalEnergyPort', wood)
                    self.assertNotIn('externalEnergyPort(', wood)
                    self.assertIn('textures/gui/priority_gui.png', ui)
                    self.assertNotIn('640 FE', ui)
                    self.assertIn('writeLong(value.energyMicroMj)', wood)
                    self.assertIn('value.energyMicroMj = in.readUnsignedShort() * MICRO_MJ_PER_RF', wood)
                    self.assertIn('energyMicroMj -= Math.min(moved, removed.getCount()) * ENERGY_PER_ITEM_MICRO_MJ', wood)
                    self.assertIn('textures/gui/priority_gui.png', ui)
                    self.assertIn('energy < 0 || energy > 64', (effective / 'src/main/java/buildcraft/additionalpipes/network/APPacket.java').read_text())
                    power_base = (src / 'APAdvancedWoodBehaviourBase.java').read_text()
                    power_platform = (src / 'APAdvancedWoodBehaviour.java').read_text()
                    self.assertIn('PipeExternalEndpoint mjEndpoint', power_base)
                    self.assertIn('ApiPortCapabilities.external(mjEndpoint, capability)', power_platform)
                    self.assertNotIn('ForgeCapabilities.ENERGY', power_platform)
                    self.assertNotIn('CapUtil.CAP_FE', power_platform)
                    for behaviour in ('route(ItemRouteContext', 'for (int priority = 6; priority >= 1; priority--)',
                                      'APItemPorts.canInsert(', 'Arrays.fill(priorities, 1)'):
                        self.assertIn(behaviour, priority)
                    self.assertIn('extends AbstractContainerMenu', (src / 'AdvancedWoodMenu.java').read_text())
                    self.assertIn('new APPacket.ValueUpdate(', ui)
                    self.assertIn('new APPacket.InsertionViewRequest(', ui)
                    res = effective / 'src/main/resources'
                    for filename in ('advanced_wood_gui.png', 'priority_gui.png'):
                        self.assertTrue((res / f'assets/additionalpipes/textures/gui/{filename}').is_file())
                        gui_source = (effective / 'src/main/java/buildcraft/additionalpipes/client' / ('AdvancedWoodScreen.java' if filename.startswith('advanced') else 'InsertionConfigScreen.java')).read_text()
                        self.assertIn(f'textures/gui/{filename}', gui_source)
                    for asset in (res / 'assets/additionalpipes').rglob('*'):
                        if asset.is_file():
                            identifier = asset.relative_to(res / 'assets/additionalpipes').as_posix()
                            self.assertRegex(identifier, r'^[a-z0-9/._-]+$')
                    for pipe in ('pipe_items_adv_wood', 'pipe_items_priority'):
                        self.assertTrue((res / f'assets/additionalpipes/models/item/{pipe}.json').is_file())
                    if target.startswith('26.'):
                        self.assertIn('GuiGraphicsExtractor', ui)
                    elif target == '1.19.2-forge':
                        self.assertIn('PoseStack', ui)
                    else:
                        self.assertIn('GuiGraphics', ui)


if __name__ == '__main__':
    unittest.main()
