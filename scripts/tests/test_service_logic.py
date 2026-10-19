"""Compile and run the AP service layer under Java 17 without Minecraft dependencies.

Not a substitute for a Forge/NeoForge Gradle compile, but it catches Java errors,
serialization regressions and mismatched classic AP behaviour on every CI run.
"""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
SHARED = ROOT / 'source-shared/src/main/java'
EXCLUDE = {
    'AdditionalPipes.java', 'APServices.java', 'BCCEIntegration.java',
    'ChunkLoadingService.java', 'ChunkLoadingHandler.java',
    'ChunkloadRequestHandler.java', 'APDimensionIds.java', 'APPlayerLevel.java', 'APPlayerDrops.java', 'PlayerUtils.java',
    'APSwitchPipes.java', 'SwitchPipeBehaviour.java',
    'APTeleportPipes.java', 'TeleportPipeComponent.java',
    'TeleportUiServer.java', 'TeleportConfigScreen.java', 'APClientPackets.java', 'APClientScreenAccess.java',
    'InsertionConfigScreen.java', 'APInsertionPipes.java', 'PriorityInsertionComponent.java',
    'AdvancedWoodComponent.java', 'APInsertionUiServer.java', 'APPriorityBehaviour.java',
    'APAdvancedWoodBehaviourBase.java', 'AdvancedWoodMenu.java', 'AdvancedWoodScreen.java', 'APAdvancedWoodStackIO.java',
}


class ServiceRegressionTests(unittest.TestCase):
    def test_shared_service_algorithms(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Java compiler/runtime not installed')
        java = [p for p in SHARED.rglob('*.java') if p.name not in EXCLUDE]
        source = ROOT / 'scripts/tests/java/InfrastructureRegression.java'
        with tempfile.TemporaryDirectory(prefix='ap-services-') as tmp:
            command = ['javac', '--release', '17', '-encoding', 'UTF-8', '-d', tmp]
            command.extend(str(p) for p in java)
            command.append(str(source))
            subprocess.run(command, check=True, cwd=ROOT, capture_output=True, text=True)
            process = subprocess.run(['java', '-cp', tmp, 'InfrastructureRegression'],
                                     check=True, cwd=ROOT, capture_output=True, text=True)
            self.assertIn('PASS Additional Pipes service regression', process.stdout)
            print(process.stdout.strip())


if __name__ == '__main__':
    unittest.main()
