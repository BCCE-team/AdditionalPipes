"""Regression guards for Additional Pipes CI run 42433293781."""

import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/materialize_sources.py'
SPEC = importlib.util.spec_from_file_location('materialize_sources', SCRIPT)
MATERIALIZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MATERIALIZER)
SCREEN = 'src/main/java/buildcraft/additionalpipes/client/JeweledScreen.java'


class CiBuildFixes(unittest.TestCase):
    def test_jeweled_screen_uses_correct_mouse_api_per_version(self):
        with tempfile.TemporaryDirectory() as directory:
            for target in MATERIALIZER.config()['targets'].split(','):
                with self.subTest(target=target):
                    destination = Path(directory) / target
                    MATERIALIZER.materialize(target, destination)
                    source = (destination / SCREEN).read_text(encoding='utf-8')
                    if target == '1.21.11-neoforge':
                        self.assertIn('import net.minecraft.client.input.MouseButtonEvent;', source)
                        self.assertIn('boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)', source)
                        self.assertIn('clickOriginalPanel(event.x(), event.y(), event.button())', source)
                        self.assertIn('super.mouseClicked(event, doubleClick)', source)
                        self.assertNotIn('super.mouseClicked(mouseX, mouseY, button)', source)
                    elif target.startswith('26.'):
                        self.assertIn('mouseClicked(MouseButtonEvent event, boolean doubleClick)', source)
                    else:
                        self.assertIn('mouseClicked(double mouseX, double mouseY, int button)', source)

    def test_legacy_forgegradle_downloads_are_serialized(self):
        props = (ROOT / 'builds/old/gradle.properties').read_text(encoding='utf-8')
        properties = {
            line.partition('=')[0].strip(): line.partition('=')[2].strip()
            for line in props.splitlines() if '=' in line and not line.lstrip().startswith('#')
        }
        self.assertEqual(properties['org.gradle.parallel'], 'false')
        gradle = (ROOT / 'build-logic/loaders/forge-target.gradle').read_text(encoding='utf-8')
        self.assertIn('compileOnly fg.deobf(ap.bcceCoordinates)', gradle)
        self.assertIn('runtimeOnly fg.deobf(ap.bcceCoordinates)', gradle)
        self.assertEqual(MATERIALIZER.config()['targets'].split(',')[:2], ['1.19.2-forge', '1.20.1-forge'])


if __name__ == '__main__':
    unittest.main()
