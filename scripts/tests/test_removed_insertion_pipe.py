"""Deprecated Insertion Transport Pipe must remain absent in every source target.

Priority Insertion and Advanced Wooden are independent, live BCCE pipe types.
"""
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('materialize_sources', ROOT / 'scripts/materialize_sources.py')
MATERIALIZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MATERIALIZER)

DEPRECATED_ID = 'pipe_items_adv_insertion'
PRESERVED_IDS = ('pipe_items_priority', 'pipe_items_adv_wood')


class RemovedInsertionPipeTests(unittest.TestCase):
    def test_all_targets_exclude_obsolete_block_but_keep_other_insertion_pipes(self):
        with TemporaryDirectory() as tmp:
            for target in MATERIALIZER.config()['targets'].split(','):
                with self.subTest(target=target):
                    selected = Path(tmp) / target
                    MATERIALIZER.materialize(target, selected)
                    src = selected / 'src/main'
                    assets = src / 'resources/assets/additionalpipes'
                    content = (src / 'java/buildcraft/additionalpipes/content/APContent.java').read_text()
                    lang = json.loads((assets / 'lang/en_us.json').read_text())

                    self.assertNotIn(DEPRECATED_ID, content)
                    self.assertFalse(any(DEPRECATED_ID in key for key in lang))
                    for folder in ('blockstates', 'models/block', 'models/item', 'items'):
                        self.assertFalse((assets / folder / (DEPRECATED_ID + '.json')).exists(),
                                         f'{target}: leftover {folder} asset')
                    self.assertFalse((assets / 'textures/block/8.png').exists())

                    data = src / 'resources/data/additionalpipes'
                    # Layout differs across Minecraft data-pack generations.
                    for path in data.rglob('*.json'):
                        self.assertNotIn(DEPRECATED_ID, path.as_posix())
                        self.assertNotIn('additionalpipes:' + DEPRECATED_ID, path.read_text())

                    for kept in PRESERVED_IDS:
                        self.assertIn('new Definition("' + kept + '"', content)
                        self.assertIn('item.additionalpipes.' + kept, lang)
                        self.assertTrue((assets / 'models/item' / (kept + '.json')).exists())
                    self.assertTrue((src / 'java/buildcraft/additionalpipes/pipes/'
                                     'PriorityInsertionComponent.java').exists())
                    self.assertTrue((src / 'java/buildcraft/additionalpipes/pipes/'
                                     'AdvancedWoodComponent.java').exists())


if __name__ == '__main__':
    unittest.main()
