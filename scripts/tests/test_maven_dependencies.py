"""Regression tests for the BCCE remote Maven dependency contract (offline)."""
from pathlib import Path
import importlib.util
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/materialize_sources.py"
SPEC = importlib.util.spec_from_file_location("materialize_sources", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class BcceMavenDependencyTests(unittest.TestCase):
    def test_remote_coordinates_match_each_target(self):
        values = MODULE.config()
        self.assertEqual(values["common.bcce.repository"], "https://api.modrinth.com/maven")
        self.assertEqual(values["common.bcce.group"], "maven.modrinth")
        self.assertEqual(values["common.bcce.module"], "z3Aj5eOl")
        version = values["common.bcce.version"]
        self.assertEqual(version, "8.0.24")
        for target in values["targets"].split(","):
            with self.subTest(target=target):
                minecraft, loader = target.rsplit("-", 1)
                self.assertEqual(minecraft, values[f"target.{target}.deps.minecraft"])
                self.assertEqual(loader, values[f"target.{target}.source.platform"])
                expected = f"maven.modrinth:z3Aj5eOl:{version}+{minecraft}+{loader}"
                self.assertEqual(expected.split(":"), [
                    values["common.bcce.group"],
                    values["common.bcce.module"],
                    f"{version}+{minecraft}+{loader}",
                ])

    def test_gradle_resolves_remote_and_never_embeds_bcce(self):
        common = (ROOT / "build-logic/common-target.gradle").read_text()
        self.assertIn('required(\'bcce.repository\')', common)
        self.assertIn('"${bcceVersion}+${minecraftVersion}+${loader}"', common)
        self.assertNotIn('verifyLocalBcceJar', common)
        self.assertNotIn('localMavenRoot', common)

        forge = (ROOT / "build-logic/loaders/forge-target.gradle").read_text()
        neo = (ROOT / "build-logic/loaders/neoforge-target.gradle").read_text()
        for contents in (forge, neo):
            self.assertIn("url = uri(ap.bcceRepositoryUrl)", contents)
            self.assertIn("includeGroup(ap.bcceGroup)", contents)
            self.assertIn("mavenCentral()", contents)
            self.assertNotIn("LocalBCCE", contents)
            self.assertNotIn("implementation ap.bcceCoordinates", contents)
        self.assertIn("compileOnly fg.deobf(ap.bcceCoordinates)", forge)
        self.assertIn("runtimeOnly fg.deobf(ap.bcceCoordinates)", forge)
        self.assertIn("compileOnly ap.bcceCoordinates", neo)
        self.assertIn("add(localBcceRuntime.name, ap.bcceCoordinates)", neo)

    def test_ci_requires_no_manual_bcce_import(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertNotIn("AP_BCCE_ARCHIVE_URL", workflow)
        self.assertNotIn("import_bcce.py", workflow)
        self.assertNotIn("/tmp/bcce-build.zip", workflow)
        self.assertIn("bash ./gradlew buildAndCollect", workflow)
        self.assertNotIn("run: python scripts/stub_compile_targets.py", workflow)
        self.assertFalse((ROOT / "scripts/import_bcce.py").exists())
        self.assertTrue((ROOT / "source-family-platforms/1.21.X/neoforge/.gitkeep").is_file())


if __name__ == "__main__":
    unittest.main()
