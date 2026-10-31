import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('materialize_sources', ROOT / 'scripts/materialize_sources.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
TARGETS = ('1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge', '1.21.11-neoforge',
           '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge')

class ClosedGravityTests(unittest.TestCase):
    def test_each_target_is_registered_and_uses_correct_api(self):
        with tempfile.TemporaryDirectory() as temporary:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(temporary) / target
                    MODULE.materialize(target, effective)
                    root = effective / 'src/main'
                    java = root / 'java/buildcraft/additionalpipes'
                    pipe = (java / 'pipes/APStoragePipes.java').read_text()
                    closed = (java / 'pipes/ClosedPipeComponent.java').read_text()
                    gravity = (java / 'pipes/GravityFeedComponent.java').read_text()
                    drop = (java / 'pipes/ClosedPipeBehaviour.java').read_text()
                    screen = (java / 'client/ClosedPipeScreen.java').read_text()
                    menu = (java / 'pipes/ClosedPipeMenu.java').read_text()
                    registry = (java / 'platform/APContentRegistry.java').read_text()
                    registration = (java / 'platform/APAdvancedWoodScreens.java').read_text()
                    modern = target.startswith('26.') or target == '1.21.11-neoforge'
                    self.assertIn('import net.minecraft.resources.' + ('Identifier' if modern else 'ResourceLocation'), pipe)
                    self.assertIn('texSuffixes("18", "19")', pipe)
                    self.assertIn('texSuffixes("32", "33")', pipe)
                    self.assertIn('PipeApi.flowItems', pipe)
                    self.assertIn('ClosedPipeComponent::new', pipe)
                    self.assertIn('GravityFeedComponent::new', pipe)
                    self.assertIn('APStoragePipes.register();', (java / 'AdditionalPipes.java').read_text())
                    self.assertIn('APStoragePipes.isStorage(id)', registry)
                    if target == '1.19.2-forge':
                        self.assertIn('createTabbedPipeItem(id)', registry)
                        self.assertIn('AdditionalPipes.ensurePipeTypesRegistered();', registry)
                        self.assertNotIn('AdditionalPipes.createPipeItem(id)', registry)
                    else:
                        self.assertIn('AdditionalPipes.createPipeItem(id)', registry)
                    self.assertIn('APStoragePipes.createItem(path)', (java / 'AdditionalPipes.java').read_text())
                    self.assertIn('CLOSED_PIPE_MENU', registry)
                    self.assertIn('ClosedPipeScreen::new', registration)
                    self.assertIn('APClosedPipeMenuAccess.open(player, this', closed)
                    self.assertIn('APAdvancedWoodStackIO.read(in)', closed)
                    self.assertIn('APAdvancedWoodStackIO.write(out, stack)', closed)
                    self.assertIn('for (ItemStack stack : items)', closed)
                    self.assertIn('StackUtil.canMerge', closed)
                    self.assertIn('storage.collectDrops(drops)', drop)
                    self.assertIn('scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR)', closed)
                    self.assertIn('clientOccupied', drop)
                    self.assertIn('@PipeEventHandler', drop)
                    self.assertIn('storage.absorb(falling)', drop)
                    self.assertIn('gravityFeedPipeTicksPerPull', gravity)
                    self.assertIn('Direction.UP', gravity)
                    self.assertIn('Direction.DOWN', gravity)
                    self.assertIn('OperationMode.SIMULATE', gravity)
                    self.assertIn('OperationMode.EXECUTE', gravity)
                    self.assertIn('addSlot(new Slot(storage, col + row * 3, 62 + col * 18, 17 + row * 18))', menu)
                    self.assertIn('moveItemStackTo(', menu)
                    self.assertIn('dispenser.png', screen)
                    if target.startswith('26.'):
                        self.assertIn('GuiGraphicsExtractor', screen)
                    elif target == '1.19.2-forge':
                        self.assertIn('PoseStack', screen)
                    else:
                        self.assertIn('GuiGraphics', screen)
                    for suffix in ('18', '19', '32', '33'):
                        self.assertTrue((root / f'resources/assets/additionalpipes/textures/block/{suffix}.png').exists())
                    resources = root / 'resources'
                    lang = json.loads((resources/'assets/additionalpipes/lang/en_us.json').read_text())
                    self.assertEqual(lang['gui.additionalpipes.closed'], 'Closed Pipe')
                    for item in ('pipe_items_closed', 'pipe_items_gravity_feed'):
                        self.assertEqual(lang[f'pipe.additionalpipes.{item}'], lang[f'item.additionalpipes.{item}'])
                        self.assertTrue((resources / f'assets/additionalpipes/models/item/{item}.json').is_file())
                        self.assertTrue((resources / f'assets/additionalpipes/blockstates/{item}.json').is_file())
                        folder = 'recipe' if target.startswith(('26.', '1.21.')) else 'recipes'
                        self.assertTrue((resources / f'data/additionalpipes/{folder}/{item}.json').is_file())

if __name__ == '__main__':
    unittest.main()
