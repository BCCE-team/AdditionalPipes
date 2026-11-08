"""Reproduce AP/BCCE constructor ordering without a Minecraft runtime.

The old AdditionalPipes.initialize() threw during CONSTRUCT because the BCCE
transport pipe types were not registered yet. This harness models that order
and verifies that pipe registration happens only in deferred item suppliers.
"""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('materialize_sources', ROOT / 'scripts/materialize_sources.py')
MATERIALIZER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MATERIALIZER)
TARGETS = (
    '1.19.2-forge', '1.20.1-forge', '1.21.1-neoforge',
    '1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge',
)


class DeferredRegistrationTests(unittest.TestCase):
    def test_all_item_suppliers_defer_until_registry_event(self):
        with tempfile.TemporaryDirectory() as temporary:
            for target in TARGETS:
                with self.subTest(target=target):
                    selected = Path(temporary) / target
                    MATERIALIZER.materialize(target, selected)
                    package = selected / 'src/main/java/buildcraft/additionalpipes'
                    common = (package / 'AdditionalPipes.java').read_text()
                    bootstrap = (package / 'platform/AdditionalPipesForge.java' if target.rsplit('-', 1)[-1] == 'forge'
                                 else package / 'platform/AdditionalPipesNeoForge.java').read_text()
                    registry = (package / 'platform/APContentRegistry.java').read_text()
                    constructor_init = common.split('public static void initialize()', 1)[1].split('\n    }', 1)[0]
                    self.assertIn('APServices.initialize(', constructor_init)
                    self.assertNotIn('Pipes.register()', constructor_init)
                    self.assertIn('AdditionalPipes.initialize();', bootstrap)
                    self.assertIn('APContentRegistry.register(', bootstrap)
                    self.assertIn('ITEMS.register(id, () -> ', registry)
                    if target == '1.19.2-forge':
                        self.assertIn('createTabbedPipeItem(id)', registry)
                        self.assertIn('AdditionalPipes.ensurePipeTypesRegistered();', registry)
                        self.assertNotIn('AdditionalPipes.createPipeItem(id)', registry)
                    else:
                        self.assertIn('AdditionalPipes.createPipeItem(id)', registry)
                    self.assertNotIn('APSwitchPipes.createItem(id)', registry)
                    self.assertIn('APSwitchPipes.register();', common)
                    self.assertIn('APTeleportPipes.register();', common)
                    self.assertIn('APInsertionPipes.register();', common)
                    self.assertIn('APStoragePipes.register();', common)
                    self.assertIn('APUtilityPipes.register();', common)
                    self.assertIn('APAdditionPipes.register();', common)
                    self.assertIn('APJeweledPipes.register();', common)
                    self.assertIn('pipeTypesRegistered = true;', common)
                    if target.startswith('26.') or target == '1.21.11-neoforge':
                        self.assertIn('BCRegistrationScope.item(', registry)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required for runtime harness')
    def test_constructor_runs_before_bcce_without_touching_pipe_types(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            def write(path, content):
                file = root / path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(content)
                return file

            source = ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/AdditionalPipes.java'
            write('buildcraft/additionalpipes/AdditionalPipes.java', source.read_text())
            write('net/minecraft/world/item/Item.java', '''
                package net.minecraft.world.item;
                public class Item {
                    public final String name;
                    public Item(String name) { this.name = name; }
                }
            ''')
            write('buildcraft/additionalpipes/service/APServices.java', '''
                package buildcraft.additionalpipes.service;
                public final class APServices {
                    public static void initialize(java.nio.file.Path path) { }
                }
            ''')
            write('buildcraft/additionalpipes/pipes/Readiness.java', '''
                package buildcraft.additionalpipes.pipes;
                public final class Readiness {
                    public static boolean bcceReady;
                    public static int registrations;
                }
            ''')
            for group, path in (
                ('APSwitchPipes', 'pipe_items_switch'),
                ('APTeleportPipes', 'pipe_items_teleport'),
                ('APInsertionPipes', 'pipe_items_adv_wood'),
                ('APStoragePipes', 'pipe_items_closed'),
                ('APUtilityPipes', 'pipe_items_distribution'),
                ('APAdditionPipes', 'pipe_items_addition'),
                ('APJeweledPipes', 'pipe_items_jeweled'),
            ):
                write(f'buildcraft/additionalpipes/pipes/{group}.java', f'''
                    package buildcraft.additionalpipes.pipes;
                    public final class {group} {{
                        public static int calls;
                        public static boolean is{group[2:-5]}(String id) {{ return "{path}".equals(id); }}
                        public static void register() {{
                            if (!Readiness.bcceReady) throw new IllegalStateException("BCCE not ready");
                            calls++;
                            Readiness.registrations++;
                        }}
                        public static net.minecraft.world.item.Item createItem(String id) {{
                            if (calls != 1) throw new IllegalStateException("Pipe type not registered exactly once");
                            return new net.minecraft.world.item.Item(id);
                        }}
                    }}
                ''')
            write('Test.java', '''
                import buildcraft.additionalpipes.AdditionalPipes;
                import buildcraft.additionalpipes.pipes.Readiness;
                import buildcraft.additionalpipes.pipes.APSwitchPipes;
                import buildcraft.additionalpipes.pipes.APTeleportPipes;
                import buildcraft.additionalpipes.pipes.APInsertionPipes;
                import buildcraft.additionalpipes.pipes.APStoragePipes;
                import buildcraft.additionalpipes.pipes.APUtilityPipes;
                import buildcraft.additionalpipes.pipes.APAdditionPipes;
                import buildcraft.additionalpipes.pipes.APJeweledPipes;
                public class Test {
                    public static void main(String[] args) {
                        AdditionalPipes.initialize(); // BCCE transport is not initialized yet.
                        if (Readiness.registrations != 0) throw new AssertionError("Eager registration");
                        try {
                            AdditionalPipes.createPipeItem("unknown");
                            throw new AssertionError("Unknown pipe accepted");
                        } catch (IllegalArgumentException expected) { }
                        if (Readiness.registrations != 0) throw new AssertionError("Unknown pipe mutated registry");
                        Readiness.bcceReady = true; // BCCE mod constructor has finished.
                        for (String pipe : new String[] {"pipe_items_switch", "pipe_items_teleport",
                                "pipe_items_adv_wood", "pipe_items_closed",
                                "pipe_items_distribution", "pipe_items_addition", "pipe_items_jeweled"}) {
                            if (!pipe.equals(AdditionalPipes.createPipeItem(pipe).name))
                                throw new AssertionError("Wrong item: " + pipe);
                        }
                        AdditionalPipes.createPipeItem("pipe_items_switch"); // Reentrant event is safe.
                        if (Readiness.registrations != 7 || APSwitchPipes.calls != 1 ||
                                APTeleportPipes.calls != 1 || APInsertionPipes.calls != 1 ||
                                APStoragePipes.calls != 1 ||
                                APUtilityPipes.calls != 1 || APAdditionPipes.calls != 1
                                || APJeweledPipes.calls != 1) throw new AssertionError("Duplicate or missing registration");
                        System.out.println("PASS: AP registry initializes once after BCCE");
                    }
                }
            ''')
            sources = [str(p) for p in root.rglob('*.java')]
            subprocess.run(['javac', '-d', str(root / 'classes'), *sources], check=True, capture_output=True, text=True)
            outcome = subprocess.run(['java', '-cp', str(root / 'classes'), 'Test'], check=True, capture_output=True, text=True)
            self.assertIn('PASS: AP registry initializes once after BCCE', outcome.stdout)


if __name__ == '__main__':
    unittest.main()
