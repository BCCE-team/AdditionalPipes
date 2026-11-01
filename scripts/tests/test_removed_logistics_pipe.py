"""Logistics Teleport Pipe was a nonfunctional placeholder; prevent its return."""
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
from materialize_sources import materialize, config

REMOVED = 'pipe_logistics_teleport'
PRESERVED = (
    'pipe_items_teleport',
    'pipe_fluids_teleport',
    'pipe_power_teleport',
    'pipe_fe_teleport',
)


class RemovedLogisticsPipeTests(unittest.TestCase):
    def test_logistics_pipe_absent_from_all_targets(self):
        with tempfile.TemporaryDirectory(prefix='ap-no-logistics-') as tmp:
            for target in config()['targets'].split(','):
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materialize(target, effective)
                    src = effective / 'src/main'
                    assets = src / 'resources/assets/additionalpipes'
                    content = (src / 'java/buildcraft/additionalpipes/content/APContent.java').read_text()
                    lang = json.loads((assets / 'lang/en_us.json').read_text())
                    enum = (src / 'java/buildcraft/additionalpipes/api/PipeType.java').read_text()
                    command = (src / 'java/buildcraft/additionalpipes/service/APCommandService.java').read_text()

                    self.assertNotIn(REMOVED, content)
                    self.assertFalse(any(REMOVED in key for key in lang))
                    self.assertNotIn('LOGISTICS', enum)
                    self.assertNotIn('logistic', command.lower())
                    self.assertFalse((assets / 'textures/block/1.png').exists())
                    for folder in ('blockstates', 'models/block', 'models/item', 'items'):
                        self.assertFalse((assets / folder / (REMOVED + '.json')).exists(),
                                         f'{target}: leftover {folder}/{REMOVED}')
                    data = src / 'resources/data/additionalpipes'
                    for path in data.rglob('*.json'):
                        self.assertNotIn(REMOVED, path.name)
                        self.assertNotIn('additionalpipes:' + REMOVED, path.read_text())

                    # Functional teleport pipes remain registered through the BCCE API.
                    pipes = (src / 'java/buildcraft/additionalpipes/pipes/APTeleportPipes.java').read_text()
                    for kept in PRESERVED:
                        self.assertIn('new Definition("' + kept + '"', content)
                        self.assertIn('item.additionalpipes.' + kept, lang)
                        self.assertTrue((assets / 'models/item' / (kept + '.json')).exists())
                    for type_name in ('ITEMS', 'FLUIDS', 'POWER', 'FE'):
                        self.assertIn('types.register(id(' + type_name + ')', pipes)


if __name__ == '__main__':
    unittest.main()
