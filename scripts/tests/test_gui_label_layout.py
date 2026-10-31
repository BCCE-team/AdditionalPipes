"""Prevent overlapping/duplicate-shadow labels across AP's seven GUI targets."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import sys

ROOT = Path(__file__).resolve().parents[2]
TARGETS = (
    '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
    '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
)
NAMES = (
    'InsertionConfigScreen', 'DistributionConfigScreen',
    'TeleportConfigScreen', 'ClosedPipeScreen', 'AdvancedWoodScreen',
)

sys.path.insert(0, str(ROOT / 'scripts'))
from materialize_sources import materialize  # noqa: E402


class GuiLabelLayoutTests(unittest.TestCase):
    def test_bounded_labels_with_actual_java_execution(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Java toolchain missing')
        sources = [
            ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/client/GuiTextLayout.java',
            ROOT / 'scripts/tests/java/GuiTextLayoutRegression.java',
        ]
        with tempfile.TemporaryDirectory(prefix='ap-gui-label-') as temp:
            subprocess.run(['javac', '--release', '17', '-d', temp, *map(str, sources)],
                           cwd=ROOT, capture_output=True, text=True, check=True)
            result = subprocess.run(['java', '-cp', temp, 'GuiTextLayoutRegression'],
                                    cwd=ROOT, capture_output=True, text=True, check=True)
            self.assertIn('PASS GuiTextLayoutRegression', result.stdout)
            print(result.stdout.strip())

    def test_teleport_title_is_declared_only_once_in_every_effective_target(self):
        # Source layering can conceal errors that only surface in a particular Minecraft target.
        # BCCE 1.21.11 previously compiled a shared screen containing both
        # `String heading = heading;` and a second `String heading = ...` declaration.
        with tempfile.TemporaryDirectory(prefix='ap-teleport-compile-sanity-') as temp:
            for target in TARGETS:
                with self.subTest(target=target):
                    dest = Path(temp) / target
                    materialize(target, dest)
                    code = (dest / 'src/main/java/buildcraft/additionalpipes/client/TeleportConfigScreen.java').read_text(encoding='utf-8')
                    self.assertEqual(len(re.findall(r'\bString\s+heading\s*=', code)), 1,
                                     f'{target}: duplicate local heading declaration')
                    self.assertNotRegex(code, r'\bString\s+(\w+)\s*=\s*\1\s*;',
                                        f'{target}: self-initialized local string')
                    self.assertIn('String heading = GuiTextLayout.fit(screenTitle(), GUI_WIDTH - 82, font::width);', code)

    def test_distribution_does_not_reference_removed_direction_labels(self):
        # 26.X used graphics.text rather than the drawString call removed from
        # other screen versions. A leftover FACE reference broke all 26.X builds.
        with tempfile.TemporaryDirectory(prefix='ap-distribution-face-sanity-') as temp:
            for target in TARGETS:
                with self.subTest(target=target):
                    dest = Path(temp) / target
                    materialize(target, dest)
                    code = (dest / 'src/main/java/buildcraft/additionalpipes/client/DistributionConfigScreen.java').read_text(encoding='utf-8')
                    self.assertNotRegex(code, r'\bFACE\b',
                                        f'{target}: obsolete face label referenced in Distribution GUI')
                    self.assertIn('for (int i = 0; i < quotaButtons.length; i++)', code)
                    self.assertIn('String heading = GuiTextLayout.fit(', code)

    def test_every_target_uses_single_shadow_free_label_pass(self):
        with tempfile.TemporaryDirectory(prefix='ap-gui-targets-') as temp:
            for target in TARGETS:
                with self.subTest(target=target):
                    dest = Path(temp) / target
                    materialize(target, dest)
                    src = dest / 'src/main/java/buildcraft/additionalpipes/client'
                    self.assertTrue((src / 'GuiTextLayout.java').is_file())
                    for name in NAMES:
                        code = (src / (name + '.java')).read_text(encoding='utf-8')
                        self.assertIn('GuiTextLayout.fit(', code, (target, name))
                        self.assertNotIn('drawShadow(', code, (target, name))
                        self.assertNotIn('drawCenteredString(', code, (target, name))
                        if target == '1.19.2-forge':
                            self.assertNotIn('drawString(graphics, font, ', code, (target, name))
                            self.assertIn('font.draw(graphics,', code, (target, name))
                        elif target.startswith('26.'):
                            self.assertIn('GuiGraphicsExtractor', code, (target, name))
                            self.assertIn('graphics.text(font,', code, (target, name))
                            self.assertIn(', false);', code, (target, name))
                        else:
                            self.assertIn('graphics.drawString(font,', code, (target, name))
                            self.assertIn(', false);', code, (target, name))
                    teleport=(src / 'TeleportConfigScreen.java').read_text(encoding='utf-8')
                    for text in ('Pipes link by frequency', 'Select frequency with buttons',
                                 'Mode sets transfer direction'):
                        self.assertIn(f'cut("{text}", displayW - 16)', teleport)


if __name__ == '__main__':
    unittest.main()
