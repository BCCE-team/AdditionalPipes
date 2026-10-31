"""Guard distribution quota logic and Water Pump source behaviour with Java execution."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'source-shared/src/main/java'
PACK = 'buildcraft/additionalpipes/pipes/'

class DistributionAndWaterPumpRegressionTests(unittest.TestCase):
    def test_distribution_counter(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Java 17 compiler/runtime missing')
        with tempfile.TemporaryDirectory(prefix='ap-dist-') as directory:
            files = [SHARED / PACK / 'DistributionCycle.java',
                     ROOT / 'scripts/tests/java/DistributionCycleRegression.java']
            subprocess.run(['javac', '--release', '17', '-d', directory, *map(str, files)],
                           cwd=ROOT, capture_output=True, text=True, check=True)
            result = subprocess.run(['java', '-cp', directory, 'DistributionCycleRegression'],
                                    cwd=ROOT, capture_output=True, text=True, check=True)
            self.assertIn('PASS DistributionCycleRegression', result.stdout)
            print(result.stdout.strip())

    def test_water_pump_real_component_with_mocked_api(self):
        """Actually compile and execute WaterPumpComponent under a small API2 mock."""
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('Java runtime unavailable')
        stubs = {
            'net/minecraft/resources/ResourceLocation.java': r"""package net.minecraft.resources;
                public record ResourceLocation(String value) {
                    public static ResourceLocation tryParse(String v) { return new ResourceLocation(v); }
                }
            """,
            'net/minecraft/core/Direction.java': r"""package net.minecraft.core;
                public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
            """,
            'net/minecraft/world/level/block/Blocks.java': r"""package net.minecraft.world.level.block;
                public final class Blocks { public static final Object WATER=new Object(), AIR=new Object(); }
            """,
            'net/minecraft/world/level/block/state/BlockState.java': r"""package net.minecraft.world.level.block.state;
                public record BlockState(Object block, boolean source) {
                    public boolean is(Object other) { return block == other; }
                    public FluidState getFluidState(){return new FluidState(source);}
                    public record FluidState(boolean source){public boolean isSource(){return source;}}
                }
            """,
            'buildcraft/additionalpipes/APConfiguration.java': r"""package buildcraft.additionalpipes;
                public final class APConfiguration {
                    public int waterPumpWaterPerTick=90;
                    private static final APConfiguration INSTANCE=new APConfiguration();
                    public static APConfiguration get(){return INSTANCE;}
                }
            """,
            'buildcraft/additionalpipes/pipes/APUtilityPipes.java': r"""package buildcraft.additionalpipes.pipes;
                public final class APUtilityPipes {
                    public static final net.minecraft.resources.ResourceLocation WATER_PUMP_COMPONENT=
                        net.minecraft.resources.ResourceLocation.tryParse("additionalpipes:pump");
                }
            """,
            'buildcraft/api/v2/OperationMode.java': r"""package buildcraft.api.v2;
                public enum OperationMode { EXECUTE, SIMULATE }
            """,
            'buildcraft/api/v2/fluid/FluidVariant.java': r"""package buildcraft.api.v2.fluid;
                public record FluidVariant(net.minecraft.resources.ResourceLocation id) {
                    public static FluidVariant of(net.minecraft.resources.ResourceLocation id){return new FluidVariant(id);}
                }
            """,
            'buildcraft/api/v2/fluid/FluidVolume.java': r"""package buildcraft.api.v2.fluid;
                public record FluidVolume(FluidVariant variant, long amount) {
                    public static FluidVolume of(FluidVariant variant, long value){return new FluidVolume(variant,value);}
                }
            """,
            'buildcraft/api/v2/fluid/FluidAmount.java': r"""package buildcraft.api.v2.fluid;
                public record FluidAmount(long milliBuckets) {}
            """,
            'buildcraft/api/v2/fluid/FluidTransferResult.java': r"""package buildcraft.api.v2.fluid;
                public record FluidTransferResult(FluidAmount transferredAmount) {}
            """,
            'buildcraft/api/v2/fluid/FluidPort.java': r"""package buildcraft.api.v2.fluid;
                public interface FluidPort {
                    FluidTransferResult insert(FluidVolume volume, buildcraft.api.v2.OperationMode mode);
                }
            """,
            'buildcraft/api/v2/pipe/PipeComponent.java': r"""package buildcraft.api.v2.pipe;
                public interface PipeComponent {net.minecraft.resources.ResourceLocation typeId();}
            """,
            'buildcraft/api/v2/pipe/PipeTickComponent.java': r"""package buildcraft.api.v2.pipe;
                public interface PipeTickComponent extends PipeComponent {void tick(PipeMutationContext context);}
            """,
            'buildcraft/api/v2/pipe/PipeMutationContext.java': r"""package buildcraft.api.v2.pipe;
                public interface PipeMutationContext {
                    java.util.Optional<buildcraft.api.v2.fluid.FluidPort> fluidPort(net.minecraft.core.Direction side);
                    void markChanged();
                }
            """,
            'buildcraft/api/v2/pipe/PipeExecutionContext.java': r"""package buildcraft.api.v2.pipe;
                public interface PipeExecutionContext extends PipeMutationContext {
                    boolean isServerSide(); boolean isActive();
                    boolean isNeighbourLoaded(net.minecraft.core.Direction side);
                    java.util.Optional<net.minecraft.world.level.block.state.BlockState> neighbourState(net.minecraft.core.Direction side);
                }
            """,
            'buildcraft/api/v2/pipe/PipeConnectionComponent.java': r"""package buildcraft.api.v2.pipe;
                public interface PipeConnectionComponent extends PipeComponent {
                    PipeConnectionDecision connection(PipeConnectionContext context);
                }
            """,
            'buildcraft/api/v2/pipe/PipeConnectionDecision.java': r"""package buildcraft.api.v2.pipe;
                public enum PipeConnectionDecision {PASS,DENY}
            """,
            'buildcraft/api/v2/pipe/PipeConnectionContext.java': r"""package buildcraft.api.v2.pipe;
                public record PipeConnectionContext(net.minecraft.core.Direction side) {}
            """,
        }
        harness=r"""package buildcraft.additionalpipes.pipes;
            import buildcraft.additionalpipes.APConfiguration;
            import buildcraft.api.v2.*;
            import buildcraft.api.v2.pipe.*;
            import buildcraft.api.v2.fluid.*;
            import net.minecraft.core.Direction;
            import net.minecraft.world.level.block.Blocks;
            import net.minecraft.world.level.block.state.BlockState;
            import java.util.Optional;
            public class WaterPumpRegression {
                static int checks;
                static void yes(boolean ok,String message) {if(!ok)throw new AssertionError(message);checks++;}
                static class World implements PipeExecutionContext {
                    boolean server=true,active=true,loaded=true,source=true;
                    Object block=Blocks.WATER;
                    long accepted=90,offered,total;
                    int marks;
                    public boolean isServerSide(){return server;}
                    public boolean isActive(){return active;}
                    public boolean isNeighbourLoaded(Direction side){return loaded;}
                    public Optional<BlockState> neighbourState(Direction side){return Optional.of(new BlockState(block,source));}
                    public void markChanged(){marks++;}
                    public Optional<FluidPort> fluidPort(Direction side){
                        yes(side==Direction.DOWN,"water enters through underside");
                        return Optional.of((volume,mode)->{
                            yes(mode==OperationMode.EXECUTE,"execute transfer only");
                            yes(volume.variant().id().value().equals("minecraft:water"),"water identity");
                            offered=volume.amount();
                            long moved=Math.min(accepted,offered);
                            total+=moved;
                            return new FluidTransferResult(new FluidAmount(moved));
                        });
                    }
                }
                public static void main(String[] args) {
                    WaterPumpComponent pump=new WaterPumpComponent();
                    World w=new World();
                    yes(pump.connection(new PipeConnectionContext(Direction.DOWN))==PipeConnectionDecision.DENY,"intake not connected");
                    yes(pump.connection(new PipeConnectionContext(Direction.NORTH))==PipeConnectionDecision.PASS,"others allowed");
                    pump.tick(w);
                    yes(w.offered==90&&w.total==90&&w.marks==1,"90mB from source");
                    w.source=false; pump.tick(w);yes(w.marks==1,"flowing water rejected");
                    w.source=true; w.block=Blocks.AIR; pump.tick(w); yes(w.marks==1,"nonwater rejected");
                    w.block=Blocks.WATER;w.loaded=false;pump.tick(w);yes(w.marks==1,"unloaded chunk ignored");
                    w.loaded=true;w.server=false;pump.tick(w);yes(w.marks==1,"no client mutation");
                    w.server=true;w.active=false;pump.tick(w);yes(w.marks==1,"inactive pipe rejected");
                    w.active=true;w.accepted=0;pump.tick(w);yes(w.marks==1,"blocked transfer not marked changed");
                    APConfiguration.get().waterPumpWaterPerTick=200;
                    w.accepted=200;pump.tick(w);yes(w.offered==200&&w.total==290&&w.marks==2,"config controls rate");
                    APConfiguration.get().waterPumpWaterPerTick=0;pump.tick(w);yes(w.marks==2,"zero rate disables pump");
                    System.out.println("PASS WaterPumpRegression "+checks+" assertions");
                }
            }
        """
        with tempfile.TemporaryDirectory(prefix='ap-pump-') as folder:
            temp=Path(folder)
            for path, source in stubs.items():
                file=temp/path
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text(source)
            file=temp/'buildcraft/additionalpipes/pipes/WaterPumpRegression.java'
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(harness)
            java_files=list(temp.rglob('*.java'))+[SHARED/PACK/'WaterPumpComponent.java']
            compiled=temp/'compiled'
            result=subprocess.run(['javac','--release','17','-d',str(compiled),*map(str,java_files)],
                                  cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            result=subprocess.run(['java','-cp',str(compiled),
                'buildcraft.additionalpipes.pipes.WaterPumpRegression'],
                cwd=ROOT,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertIn('PASS WaterPumpRegression',result.stdout)
            print(result.stdout.strip())

    def test_water_pump_component_and_registration_wired(self):
        classes = ('APUtilityPipes.java','DistributionComponent.java','DistributionCycle.java',
                   'DistributionPipeBehaviour.java','WaterPumpComponent.java', 'APDistributionUiServer.java')
        for filename in classes:
            self.assertTrue((SHARED / PACK / filename).exists(), filename)
        component = (SHARED / PACK / 'WaterPumpComponent.java').read_text()
        for token in ('PipeTickComponent', 'PipeConnectionComponent',
                      'world.isServerSide()', 'isNeighbourLoaded(Direction.DOWN)',
                      'below.is(Blocks.WATER)', 'below.getFluidState().isSource()',
                      'FluidVolume.of(WATER, rate)', 'OperationMode.EXECUTE',
                      'transferredAmount()', 'PipeConnectionDecision.DENY'):
            self.assertIn(token, component)
        utility = (SHARED / PACK / 'APUtilityPipes.java').read_text()
        self.assertIn('DISTRIBUTION_COMPONENT', utility)
        self.assertIn('WATER_PUMP_COMPONENT', utility)
        self.assertIn('pipe -> new DistributionComponent()', utility)
        self.assertNotIn('DistributionComponent::new', utility)
        self.assertIn('new WaterPumpComponent()', utility)
        for path in ROOT.glob('**/APContentRegistry.java'):
            if 'src/main/java' not in str(path):
                continue
            self.assertIn('APUtilityPipes.isUtility(id)', path.read_text(), str(path))

if __name__ == '__main__': unittest.main()
