"""API-independent regression checks for AP source layering."""
import importlib.util
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

    def test_reject_unknown_target(self):
        with self.assertRaises(ValueError):
            module.get_source_layers('missing',module.config())


if __name__=='__main__':
    unittest.main()
