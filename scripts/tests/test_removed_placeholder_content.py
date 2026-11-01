"""Removed decorative Teleport Tether and inert Dog Deaggravator stay absent on all targets.

Neither feature was a working BCCE pipe; the Item/Fluid/Power/FE teleport
pipes, their services, and Addition Transport Pipe must remain available.
"""
from pathlib import Path
import json
import sys
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from materialize_sources import config, materialize

REMOVED = ('teleport_tether', 'dog_deaggravator')
PRESERVED = (
    'pipe_items_teleport', 'pipe_fluids_teleport', 'pipe_power_teleport',
    'pipe_fe_teleport', 'pipe_items_addition',
)


class RemovedPlaceholdersTests(unittest.TestCase):
    def test_no_registration_translation_or_assets_in_any_target(self):
        with TemporaryDirectory(prefix='ap-removed-placeholders-') as tmp:
            for target in config()['targets'].split(','):
                with self.subTest(target=target):
                    result = Path(tmp) / target
                    materialize(target, result)
                    source = result / 'src/main'
                    assets = source / 'resources/assets/additionalpipes'
                    lang = json.loads((assets / 'lang/en_us.json').read_text())
                    catalog = (source / 'java/buildcraft/additionalpipes/content/APContent.java').read_text()
                    registry = (source / 'java/buildcraft/additionalpipes/platform/APContentRegistry.java').read_text()
                    self.assertEqual(len(tuple(catalog.split('new Definition('))) - 1, 17)
                    for retired in REMOVED:
                        self.assertNotIn(retired, catalog)
                        self.assertNotIn(retired.upper(), registry)
                        self.assertNotIn(retired, registry)
                        self.assertFalse(any(retired in key for key in lang))
                        for path in (source / 'resources').rglob('*'):
                            if path.is_file():
                                self.assertNotIn(retired, path.as_posix())
                                if path.suffix == '.json':
                                    self.assertNotIn('additionalpipes:' + retired, path.read_text())
                    self.assertFalse((assets / 'textures/block/tether.png').exists())
                    self.assertFalse((assets / 'textures/item/dog_deaggravator.png').exists())
                    # In contrast, live teleport pipes and the newly functional Addition Pipe survive.
                    for kept in PRESERVED:
                        self.assertIn('new Definition("' + kept + '"', catalog)
                        self.assertTrue((assets / 'models/item' / (kept + '.json')).is_file())
                        self.assertIn('item.additionalpipes.' + kept, lang)
                    self.assertTrue((source / 'java/buildcraft/additionalpipes/pipes/'
                                     'AdditionPipeComponent.java').is_file())
                    pipes = (source / 'java/buildcraft/additionalpipes/pipes/'
                             'APTeleportPipes.java').read_text()
                    for medium in ('ITEMS', 'FLUIDS', 'POWER', 'FE'):
                        self.assertIn('types.register(id(' + medium + ')', pipes)


if __name__ == '__main__':
    unittest.main()
