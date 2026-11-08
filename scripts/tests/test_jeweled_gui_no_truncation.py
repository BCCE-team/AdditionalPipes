"""Regression: all seven Minecraft targets render complete Jeweled GUI text."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location('materialize_sources', ROOT / 'scripts/materialize_sources.py')
materializer = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(materializer)


class JeweledGuiNoTruncationTests(unittest.TestCase):
    def test_all_targets_have_full_text_and_correct_matrix_api(self):
        with tempfile.TemporaryDirectory() as tmp:
            for target in materializer.config()['targets'].split(','):
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materializer.materialize(target, effective)
                    java = effective / 'src/main/java/buildcraft/additionalpipes/client'
                    screen = (java / 'JeweledScreen.java').read_text()
                    helper = (java / 'GuiTextLayout.java').read_text()
                    self.assertIn('horizontalScale(text, maxWidth, font::width)', screen)
                    self.assertIn('drawUnclippedText(graphics, tabNames[side]', screen)
                    self.assertIn('drawUnclippedText(graphics, text, x + 3', screen)
                    self.assertIn('drawUnclippedText(graphics, heading', screen)
                    self.assertNotIn('GuiTextLayout.fit(', screen)
                    self.assertNotIn('String fitted =', screen)
                    self.assertIn('public static float horizontalScale(', helper)
                    if target == '1.19.2-forge':
                        self.assertIn('graphics.pushPose();', screen)
                        self.assertIn('font.draw(graphics, text, 0, 0, colour);', screen)
                    elif target == '1.20.1-forge' or target == '1.21.1-neoforge':
                        self.assertIn('graphics.pose().pushPose();', screen)
                        self.assertIn('graphics.drawString(font, text, 0, 0, colour, false);', screen)
                    elif target == '1.21.11-neoforge':
                        self.assertIn('graphics.pose().pushMatrix();', screen)
                        self.assertIn('graphics.drawString(font, text, 0, 0, colour, false);', screen)
                    else:
                        self.assertIn('graphics.pose().pushMatrix();', screen)
                        self.assertIn('graphics.text(font, text, 0, 0, colour, false);', screen)
                    self.assertIn('graphics.pose().scale(factor, 1.0F);', screen) if target.startswith(('26.', '1.21.11')) else None
                    self.assertNotIn('README.md', screen)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_full_labels_fit_even_wide_translations_and_fonts(self):
        sources = [
            ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/client/GuiTextLayout.java',
            ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/client/JeweledGuiLayout.java',
            ROOT / 'scripts/tests/java/JeweledFullLabelsRegression.java',
        ]
        with tempfile.TemporaryDirectory() as tmp:
            subprocess.run(['javac', '--release', '17', '-d', tmp, *map(str, sources)],
                           check=True, text=True, capture_output=True)
            result = subprocess.run(['java', '-cp', tmp, 'JeweledFullLabelsRegression'],
                                    check=True, text=True, capture_output=True)
            self.assertIn('PASS JeweledFullLabelsRegression', result.stdout)


if __name__ == '__main__':
    unittest.main()
