"""Static regression checks for Additional Pipes' multi-platform release workflow.

Python stdlib only: runs in the repository's existing CI test stage.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github' / 'workflows' / 'publish.yml'
TARGET_CONFIG = ROOT / 'build-config' / 'targets.properties'
MOD_CONFIG = ROOT / 'build-config' / 'common.properties'


def property_value(path: Path, key: str) -> str:
    for line in path.read_text(encoding='utf-8').splitlines():
        if line.startswith(key + '='):
            return line.partition('=')[2].strip()
    raise AssertionError(f'Missing {key} in {path}')


class PublishingWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding='utf-8')
        cls.targets = property_value(TARGET_CONFIG, 'targets').split(',')
        cls.archive_name = property_value(MOD_CONFIG, 'common.mod.archive_name')

    def test_trigger_and_isolation(self):
        self.assertRegex(self.workflow, r'(?m)^  release:\s*$')
        self.assertIn('types: [published]', self.workflow)
        self.assertNotIn('  push:', self.workflow)
        self.assertIn('cancel-in-progress: false', self.workflow)
        self.assertIn('needs: [metadata, build]', self.workflow)
        self.assertIn('contents: write', self.workflow)

    def test_correct_platform_destinations_and_dependencies(self):
        self.assertIn('${{ vars.MODRINTH_PROJECT_ID }}', self.workflow)
        self.assertIn('${{ vars.CURSEFORGE_PROJECT_ID }}', self.workflow)
        self.assertIn('${{ secrets.MODRINTH_TOKEN }}', self.workflow)
        self.assertIn('${{ secrets.CURSEFORGE_TOKEN }}', self.workflow)
        self.assertIn('z3Aj5eOl(required)', self.workflow)
        self.assertIn('1592400(required)', self.workflow)
        # BCCE identifiers are dependencies only, never publication destination IDs.
        self.assertNotRegex(self.workflow, r'MODRINTH_PROJECT_ID:\s*z3Aj5eOl')
        self.assertNotRegex(self.workflow, r"CURSEFORGE_PROJECT_ID:\s*['\"]?1592400")
        self.assertIn("MODRINTH_PROJECT_ID\" == 'z3Aj5eOl'", self.workflow)
        self.assertIn("CURSEFORGE_PROJECT_ID\" == '1592400'", self.workflow)

    def test_7_exact_targets_and_filenames(self):
        release_steps = re.findall(r'(?m)^      - name: Publish (\S+) (Forge|NeoForge)$', self.workflow)
        actual = [f"{mc}-{'forge' if loader == 'Forge' else 'neoforge'}" for mc, loader in release_steps]
        self.assertEqual(actual, self.targets)
        self.assertEqual(len(self.targets), 7)
        self.assertEqual(self.workflow.count('uses: Kira-NT/mc-publish@v3'), 7)
        self.assertEqual(self.workflow.count('run: sleep "$PUBLISH_DELAY_SECONDS"'), 6)
        self.assertIn('PUBLISH_DELAY_SECONDS: \'60\'', self.workflow)
        for mc, loader in ((v.rsplit('-', 1)[0], v.rsplit('-', 1)[1]) for v in self.targets):
            jar = f'{self.archive_name}-${{{{ github.event.release.tag_name }}}}+{mc}+{loader}.jar'
            self.assertIn('files: release/' + jar, self.workflow)
            self.assertIn('game-versions: ' + mc, self.workflow)
            self.assertIn('loaders: ' + loader, self.workflow)
        self.assertIn('python scripts/verify_ap_jar.py "$target" "$jar"', self.workflow)

    def test_version_and_changelog_sources(self):
        self.assertIn("Release tag '$RELEASE_TAG' must match common.mod.version", self.workflow)
        self.assertEqual(self.workflow.count('version-type: ${{ needs.metadata.outputs.version_type }}'), 7)
        self.assertEqual(self.workflow.count('modrinth-changelog: ${{ github.event.release.body }}'), 7)
        self.assertEqual(self.workflow.count('curseforge-changelog: ${{ github.event.release.body }}'), 7)


if __name__ == '__main__':
    unittest.main()
