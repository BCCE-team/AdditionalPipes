"""Obsidian Fluid Pipe parity, MJ-only energy policy, build layering and conservative-transfer tests."""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from materialize_sources import materialize

TARGETS = (
    "1.19.2-forge", "1.20.1-forge", "1.21.1-neoforge", "1.21.11-neoforge",
    "26.1.2-neoforge", "26.2-neoforge", "26.3-neoforge",
)
PKG = "src/main/java/buildcraft/additionalpipes"


class ObsidianFluidTests(unittest.TestCase):
    def test_each_target_uses_real_fluid_pipe_and_native_item_containers(self):
        with tempfile.TemporaryDirectory(prefix="ap-obsidian-") as tmp:
            for target in TARGETS:
                with self.subTest(target=target):
                    effective = Path(tmp) / target
                    materialize(target, effective)
                    pkg = effective / PKG
                    utility = (pkg / "pipes/APUtilityPipes.java").read_text()
                    component = (pkg / "pipes/ObsidianFluidComponent.java").read_text()
                    behaviour = (pkg / "pipes/APObsidianFluidBehaviour.java").read_text()
                    containers = (pkg / "pipes/APFluidContainers.java").read_text()
                    injection = (pkg / "pipes/APObsidianFluidInjection.java").read_text()
                    registry = (pkg / "platform/APContentRegistry.java").read_text()
                    common = (pkg / "AdditionalPipes.java").read_text()
                    self.assertIn('OBSIDIAN = "pipe_fluids_obsidian"', utility)
                    self.assertIn('OBSIDIAN_COMPONENT = id("obsidian_fluid_drain")', utility)
                    self.assertIn('OBSIDIAN.equals(path)', utility)
                    self.assertIn('ObsidianFluidComponent::new', utility)
                    self.assertIn('ObsidianFluidComponent.CODEC', utility)
                    self.assertIn('PipeType.builder(id(OBSIDIAN))', utility)
                    self.assertIn('fluidProfile(goldFluid.fluidProfile().orElseThrow())', utility)
                    self.assertIn('component(OBSIDIAN_COMPONENT).build()', utility)
                    self.assertIn('.texSuffixes("31")', utility)
                    self.assertIn('.flow(PipeApi.flowFluids)', utility)
                    self.assertIn('.logic(APObsidianFluidBehaviour::new, APObsidianFluidBehaviour::new)', utility)
                    self.assertIn('APUtilityPipes.isUtility(id)', registry)
                    self.assertIn('APUtilityPipes.createItem(path)', common)
                    self.assertIn('implements PipeRuntimeComponent', component)
                    self.assertIn('public void entityCollided(', component)
                    self.assertIn('MJ_CAPACITY_MICRO = 256L * MjAmount.MICRO_MJ_PER_MJ', component)
                    self.assertNotIn('IDLE_MJ_PER_TICK_MICRO', component)
                    self.assertNotIn('storedMicroMj = Math.max(0, storedMicroMj -', component)
                    self.assertIn('MAX_MJ_RECEIVE_MICRO = 64L * MjAmount.MICRO_MJ_PER_MJ', component)
                    self.assertIn('MjTransferResult insert(MjAmount offered, OperationMode mode)', component)
                    self.assertIn('Optional<MjPort> mjPort(Direction direction)', component)
                    self.assertIn('storedMicroMj', component)
                    self.assertIn('output.writeByte(2)', component)
                    self.assertIn('version != 1 && version != 2', component)
                    self.assertNotIn('ExternalEnergyPort', component)
                    self.assertNotIn('storedFe', component)
                    self.assertIn('SUCTION_DISTANCE = 4', component)
                    self.assertIn('new AABB(pos).inflate(SUCTION_DISTANCE)', component)
                    self.assertIn('nearby.sort(java.util.Comparator.comparingDouble', component)
                    self.assertNotIn('suctionSide != null', component)
                    self.assertNotIn('BlockPos target = context.position().relative(suctionSide, distance);', component)
                    self.assertNotIn('MAX_FLUID_PER_TICK', component)
                    self.assertIn('Math.min(Integer.MAX_VALUE, remaining)', component)
                    self.assertIn('FluidComponentPayload', component)
                    self.assertIn('APAdvancedWoodStackIO.write', component)
                    self.assertIn('APAdvancedWoodStackIO.read', component)
                    self.assertIn('level.addFreshEntity(dropped)', component)
                    self.assertIn('APObsidianFluidInjection.insert(context,', component)
                    self.assertNotIn('context.fluidPort(side)', component)
                    self.assertIn('flow.insertFluidsForce(stack, null, FluidAction.EXECUTE)', injection)
                    self.assertIn('FuelApiBridge.stackOf(offered)', injection)
                    self.assertNotIn('context.fluidPort(', injection)
                    if target == '26.3-neoforge':
                        self.assertIn('import buildcraft.transport.internal.pipe.FluidAction;', injection)
                    elif target.endswith('neoforge'):
                        self.assertIn('import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;', injection)
                    else:
                        self.assertIn('import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;', injection)
                    self.assertIn('class APObsidianFluidBehaviour extends APObsidianFluidBehaviourBase', behaviour)
                    self.assertIn('ApiPortCapabilities.external(mjEndpoint, MjCapabilities.CAP_RECEIVER)', behaviour)
                    self.assertIn('new MjCapabilityHelper(receiver).getCapability(capability, facing)', behaviour)
                    self.assertNotIn('feEndpoint', behaviour)
                    self.assertNotIn('ForgeCapabilities.ENERGY', behaviour)
                    self.assertNotIn('Capabilities.EnergyStorage', behaviour)
                    self.assertIn('FuelApiBridge.volumeOf(drained)', containers)
                    self.assertIn('FluidUtil.getFluidHandler(unit).orElse(null)', containers)
                    self.assertIn('unit.setCount(1)', containers)
                    self.assertIn('IFluidHandler.FluidAction.SIMULATE', containers)
                    self.assertIn('IFluidHandler.FluidAction.EXECUTE', containers)
                    self.assertIn('handler.getContainer().copy()', containers)
                    if target == "26.3-neoforge":
                        self.assertIn('buildcraft.lib.compat.neoforge263.fluids.FluidUtil', containers)
                        self.assertIn('buildcraft.lib.compat.neoforge263.fluids.IFluidHandlerItem', containers)
                    elif target.endswith("neoforge"):
                        self.assertIn('net.neoforged.neoforge.fluids.FluidUtil', containers)
                    else:
                        self.assertIn('net.minecraftforge.fluids.FluidUtil', containers)
                    modern = target == "1.21.11-neoforge" or target.startswith("26.")
                    self.assertIn('public ' + ("Identifier" if modern else "ResourceLocation") + ' typeId()', component)
                    self.assertTrue((effective / 'src/main/resources/assets/additionalpipes/textures/block/31.png').exists())
                    self.assertTrue((effective / 'src/main/resources/assets/additionalpipes/models/item/pipe_fluids_obsidian.json').exists())
                    self.assertTrue((effective / 'src/main/resources/assets/additionalpipes/blockstates/pipe_fluids_obsidian.json').exists())
                    lang = json.loads((effective / 'src/main/resources/assets/additionalpipes/lang/en_us.json').read_text())
                    self.assertIn('block.additionalpipes.pipe_fluids_obsidian', lang)
                    self.assertEqual(lang['pipe.additionalpipes.pipe_fluids_obsidian'], 'Obsidian Fluid Pipe')

    def test_addon_exposes_mj_and_uses_buildcraft_conversion_policy(self):
        root = ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/pipes'
        base = (root / 'APObsidianFluidBehaviourBase.java').read_text()
        self.assertIn('Optional<MjPort> mjPort()', base)
        self.assertNotIn('ExternalEnergyPort', base)
        for loader in ('forge', 'neoforge'):
            behaviour = (ROOT / f'source-platforms/{loader}/src/main/java/buildcraft/additionalpipes/pipes/APObsidianFluidBehaviour.java').read_text()
            self.assertIn('MjCapabilityHelper', behaviour)
            self.assertIn('MjCapabilities.CAP_RECEIVER', behaviour)
            self.assertNotIn('feEndpoint', behaviour)
            self.assertNotIn('ExternalEnergyPort', behaviour)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_platform_neutral_injection_compiles_on_neoforge_and_263(self):
        # Compile the exact loader adapters against small stubs of their native
        # fluid types: the 26.3 transport FluidAction is NOT the pre-26.3 API.
        adapters = (
            ('neoforge', 'source-platforms/neoforge',
                'net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction'),
            ('26.3', 'version-src/26.3-neoforge',
                'buildcraft.transport.internal.pipe.FluidAction'),
        )
        for name, layer, action in adapters:
            with self.subTest(loader=name), tempfile.TemporaryDirectory(prefix='ap-neutral-injection-') as tmp:
                temp = Path(tmp)
                def stub(path, contents):
                    dest = temp / path
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    dest.write_text(contents)
                stub('buildcraft/api/v2/fluid/FluidVolume.java', '''package buildcraft.api.v2.fluid;
                    public record FluidVolume(long quantity) {
                      public boolean isEmpty() {return quantity<=0;}
                      public FluidAmount amount(){return new FluidAmount(quantity);}
                    }''')
                stub('buildcraft/api/v2/fluid/FluidAmount.java', '''package buildcraft.api.v2.fluid;
                    public record FluidAmount(long milliBuckets){}''')
                stub('buildcraft/api/v2/pipe/PipeMutationContext.java',
                    'package buildcraft.api.v2.pipe; public interface PipeMutationContext{}')
                stub('buildcraft/transport/internal/pipe/IPipe.java', '''package buildcraft.transport.internal.pipe;
                    public interface IPipe { Object getFlow(); }''')
                stub('net/minecraft/core/Direction.java',
                    'package net.minecraft.core; public enum Direction {NORTH;}')
                stub('net/neoforged/neoforge/fluids/FluidStack.java', '''package net.neoforged.neoforge.fluids;
                    public record FluidStack(int getAmount) {
                      public boolean isEmpty(){return getAmount<=0;}
                    }''')
                if name == '26.3':
                    stub('buildcraft/transport/internal/pipe/FluidAction.java',
                        'package buildcraft.transport.internal.pipe; public enum FluidAction{SIMULATE,EXECUTE}')
                else:
                    stub('net/neoforged/neoforge/fluids/capability/IFluidHandler.java',
                        'package net.neoforged.neoforge.fluids.capability; public interface IFluidHandler{enum FluidAction{SIMULATE,EXECUTE}}')
                stub('buildcraft/transport/internal/pipe/IFlowFluid.java', f'''package buildcraft.transport.internal.pipe;
                    import net.minecraft.core.Direction;
                    import net.neoforged.neoforge.fluids.FluidStack;
                    import {action};
                    public interface IFlowFluid {{
                      int insertFluidsForce(FluidStack stack, Direction from, FluidAction mode);
                    }}''')
                stub('buildcraft/lib/fluid/FuelApiBridge.java', '''package buildcraft.lib.fluid;
                    import buildcraft.api.v2.fluid.FluidVolume;
                    import net.neoforged.neoforge.fluids.FluidStack;
                    public class FuelApiBridge {
                      public static FluidStack stackOf(FluidVolume offered) {
                        return new FluidStack((int)offered.amount().milliBuckets());
                      }
                    }''')
                stub('TestInjection.java', f'''import buildcraft.additionalpipes.pipes.APObsidianFluidInjection;
                    import buildcraft.api.v2.fluid.FluidVolume;
                    import buildcraft.api.v2.pipe.PipeMutationContext;
                    import buildcraft.transport.internal.pipe.*;
                    import net.neoforged.neoforge.fluids.FluidStack;
                    import net.minecraft.core.Direction;
                    import {action};
                    public class TestInjection {{
                      static class Host implements PipeMutationContext, IPipe {{
                        public Object getFlow() {{return new IFlowFluid() {{
                          public int insertFluidsForce(FluidStack stack,Direction from,FluidAction mode) {{
                            if (from!=null || mode!=FluidAction.EXECUTE) throw new AssertionError("wrong fluid direction");
                            return Math.min(160, stack.getAmount());
                          }}
                        }};}}
                      }}
                      public static void main(String[] unused) {{
                        if (APObsidianFluidInjection.insert(new Host(),new FluidVolume(1000))!=160)
                          throw new AssertionError("should transfer partial 160 mB through neutral input");
                        if (APObsidianFluidInjection.insert(new Host(),new FluidVolume(0))!=0)
                          throw new AssertionError("empty volume must not transfer");
                        System.out.println("PASS native neutral port");
                      }}
                    }}''')
                adapter = ROOT / layer / 'src/main/java/buildcraft/additionalpipes/pipes/APObsidianFluidInjection.java'
                sources = [*temp.rglob('*.java'), adapter]
                result = subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8',
                    '-d', str(temp/'classes'), *map(str, sources)], capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                run = subprocess.run(['java', '-cp', str(temp/'classes'), 'TestInjection'],
                    capture_output=True, text=True)
                self.assertEqual(run.returncode, 0, run.stderr)
                self.assertIn('PASS native neutral port', run.stdout)

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_compiles_and_executes_suction_transfer_blocking_energy_and_persistence(self):
        # The exact BCCE APIs are verified separately against 8.0.24; this offline
        # fixture probes the Java component state machine without downloading Minecraft.
        stubs = {
            'net/minecraft/resources/ResourceLocation.java': '''package net.minecraft.resources;
                public record ResourceLocation(String value) {
                  public static ResourceLocation tryParse(String s){return s==null||!s.contains(":")?null:new ResourceLocation(s);}
                  public String toString(){return value;}
                }''',
            'net/minecraft/core/Direction.java': '''package net.minecraft.core;
                public enum Direction {DOWN(0,-1,0),UP(0,1,0),NORTH(0,0,-1),SOUTH(0,0,1),WEST(-1,0,0),EAST(1,0,0);
                  final int x,y,z; Direction(int a,int b,int c){x=a;y=b;z=c;}
                  public int getStepX(){return x;} public int getStepY(){return y;} public int getStepZ(){return z;}
                  public Direction getOpposite(){return values()[ordinal()^1];}
                }''',
            'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;
                public record BlockPos(int x,int y,int z){
                  public BlockPos relative(Direction d,int n){return new BlockPos(x+d.getStepX()*n,y+d.getStepY()*n,z+d.getStepZ()*n);}
                  public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
                }''',
            'net/minecraft/world/phys/AABB.java': '''package net.minecraft.world.phys;
                import net.minecraft.core.BlockPos;
                public record AABB(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
                  public AABB(BlockPos p){this(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY()+1,p.getZ()+1);}
                  public AABB inflate(double a){return new AABB(minX-a,minY-a,minZ-a,maxX+a,maxY+a,maxZ+a);}
                  public boolean contains(double x,double y,double z){return x>=minX && x<=maxX && y>=minY && y<=maxY && z>=minZ && z<=maxZ;}
                }''',
            'net/minecraft/sounds/SoundEvents.java': 'package net.minecraft.sounds;public final class SoundEvents{public static final Object ITEM_PICKUP=new Object();}',
            'net/minecraft/sounds/SoundSource.java': 'package net.minecraft.sounds;public enum SoundSource {BLOCKS}',
            'net/minecraft/world/item/ItemStack.java': '''package net.minecraft.world.item;
                public class ItemStack { public static final ItemStack EMPTY=new ItemStack("",0);
                  public final String item; public int count;
                  public ItemStack(String i,int n){item=i;count=n;} public boolean isEmpty(){return count<=0;}
                  public int getCount(){return count;}public void setCount(int n){count=n;}
                  public ItemStack copy(){return new ItemStack(item,count);} public void shrink(int n){count-=n;}
                }''',
            'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity;
                import net.minecraft.core.BlockPos;
                public class Entity { protected boolean removed; protected int id;protected BlockPos pos;
                  public Entity(int id,BlockPos pos){this.id=id;this.pos=pos;}
                  public int getId(){return id;}public boolean isRemoved(){return removed;}
                  public BlockPos blockPosition(){return pos;}public void discard(){removed=true;}
                }''',
            'net/minecraft/world/entity/item/ItemEntity.java': '''package net.minecraft.world.entity.item;
                import net.minecraft.world.entity.Entity;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.core.BlockPos;
                import net.minecraft.server.level.ServerLevel;
                public class ItemEntity extends Entity{
                  private ItemStack item;
                  public final double x,y,z;
                  public ItemEntity(ServerLevel level,double x,double y,double z,ItemStack stack){
                    super(++level.nextId,new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z)));
                    this.x=x;this.y=y;this.z=z;item=stack;
                  }
                  public double distanceToSqr(double x,double y,double z){
                    return (this.x-x)*(this.x-x)+(this.y-y)*(this.y-y)+(this.z-z)*(this.z-z);
                  }
                  public ItemStack getItem(){return item;}public void setItem(ItemStack stack){item=stack;}
                  public void setDeltaMovement(double x,double y,double z){}
                }''',
            'net/minecraft/world/level/Level.java': 'package net.minecraft.world.level; public class Level {}',
            'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
                import java.util.*;
                import net.minecraft.core.BlockPos;
                import net.minecraft.world.phys.AABB;
                import net.minecraft.world.entity.item.ItemEntity;
                import net.minecraft.sounds.SoundSource;
                public class ServerLevel extends net.minecraft.world.level.Level {
                  public int nextId=0;public boolean spawnAllowed=true;public final List<ItemEntity> items=new ArrayList<>();
                  public boolean hasChunkAt(BlockPos p){return true;}
                  public List<ItemEntity> getEntitiesOfClass(Class<ItemEntity> type,AABB box){
                    List<ItemEntity> found=new ArrayList<>();for(ItemEntity e:items)
                    if(!e.isRemoved()&&box.contains(e.x,e.y,e.z))found.add(e);return found;
                  }
                  public boolean addFreshEntity(ItemEntity e){if(!spawnAllowed)return false;items.add(e);return true;}
                  public void playSound(Object player,BlockPos p,Object event,SoundSource source,float volume,float pitch){}
                }''',
            'buildcraft/api/v2/OperationMode.java': 'package buildcraft.api.v2;public enum OperationMode{SIMULATE,EXECUTE}',
            'buildcraft/api/v2/energy/MjAmount.java': '''package buildcraft.api.v2.energy;
                public record MjAmount(long microMj){
                  public static final long MICRO_MJ_PER_MJ=1000000L;
                  public static MjAmount ofMicro(long n){return new MjAmount(n);}
                  public static MjAmount ofMj(long n){return new MjAmount(n*MICRO_MJ_PER_MJ);}
                }''',
            'buildcraft/api/v2/energy/MjTransferResult.java': '''package buildcraft.api.v2.energy;
                public record MjTransferResult(MjAmount requested,MjAmount transferred,MjAmount remainder){
                  public static MjTransferResult of(MjAmount requested,MjAmount moved){
                    return new MjTransferResult(requested,moved,MjAmount.ofMicro(requested.microMj()-moved.microMj()));
                  }
                  public static MjTransferResult none(MjAmount requested){return of(requested,MjAmount.ofMicro(0));}
                }''',
            'buildcraft/api/v2/energy/MjPort.java': '''package buildcraft.api.v2.energy;
                import buildcraft.api.v2.OperationMode;
                public interface MjPort{
                  MjTransferResult insert(MjAmount offered,OperationMode mode);
                  MjTransferResult extract(MjAmount requested,OperationMode mode);
                  MjAmount stored();MjAmount capacity();default boolean canExtract(){return true;}
                }''',
            'buildcraft/api/v2/BuildCraftServices.java': '''package buildcraft.api.v2;
                public enum BuildCraftServices { ENERGY }''',
            'buildcraft/api/v2/BuildCraftApi.java': '''package buildcraft.api.v2;
                import buildcraft.api.v2.energy.EnergyService;
                public class BuildCraftApi{
                  public static EnergyService service(BuildCraftServices key){return new EnergyService();}
                }''',
            'buildcraft/api/v2/energy/EnergyService.java': '''package buildcraft.api.v2.energy;
                public class EnergyService{
                  public EnergyConversion conversion(){return new EnergyConversion(100000L);}
                }''',
            'buildcraft/api/v2/energy/EnergyConversion.java': '''package buildcraft.api.v2.energy;
                public record EnergyConversion(long microMjPerFe){
                  public long feToMicroMjClamped(long fe){return fe*microMjPerFe;}
                }''',
            'buildcraft/api/v2/fluid/FluidComponentPayload.java': '''package buildcraft.api.v2.fluid;
                import net.minecraft.resources.ResourceLocation;import java.util.Optional;
                public record FluidComponentPayload(ResourceLocation format,byte[] contents){
                  public static FluidComponentPayload empty(){return new FluidComponentPayload(null,new byte[0]);}
                  public static FluidComponentPayload of(ResourceLocation id,byte[] bytes){return new FluidComponentPayload(id,bytes);}
                  public boolean isEmpty(){return format==null;}
                  public Optional<ResourceLocation> formatId(){return Optional.ofNullable(format);}
                  public byte[] copyCanonicalBytes(){return contents.clone();}
                }''',
            'buildcraft/api/v2/fluid/FluidVariant.java': '''package buildcraft.api.v2.fluid;
                import net.minecraft.resources.ResourceLocation;
                public record FluidVariant(ResourceLocation fluidId,FluidComponentPayload components){
                  public static FluidVariant of(ResourceLocation id){return new FluidVariant(id,FluidComponentPayload.empty());}
                  public static FluidVariant of(ResourceLocation id,FluidComponentPayload p){return new FluidVariant(id,p);}
                }''',
            'buildcraft/api/v2/fluid/FluidAmount.java': '''package buildcraft.api.v2.fluid;
                public record FluidAmount(long milliBuckets) {}''',
            'buildcraft/api/v2/fluid/FluidVolume.java': '''package buildcraft.api.v2.fluid;
                import java.util.Optional;
                public record FluidVolume(FluidVariant fluid,long mb){
                  public static FluidVolume empty(){return new FluidVolume(null,0);}
                  public static FluidVolume of(FluidVariant f,long n){return n==0?empty():new FluidVolume(f,n);}
                  public boolean isEmpty(){return mb==0;}
                  public FluidAmount amount(){return new FluidAmount(mb);}
                  public FluidVariant requireVariant(){return fluid;}
                }''',
            'buildcraft/api/v2/fluid/FluidTransferResult.java': '''package buildcraft.api.v2.fluid;
                public record FluidTransferResult(long moved){public FluidAmount transferredAmount(){return new FluidAmount(moved);}}''',
            'buildcraft/api/v2/fluid/FluidPort.java': '''package buildcraft.api.v2.fluid;
                import buildcraft.api.v2.OperationMode;
                public interface FluidPort{FluidTransferResult insert(FluidVolume fluid,OperationMode mode);}
            ''',
            'buildcraft/api/v2/persistence/OpaqueData.java': '''package buildcraft.api.v2.persistence;
                import net.minecraft.resources.ResourceLocation;
                public record OpaqueData(ResourceLocation format,byte[] bytes){public int size(){return bytes.length;}}''',
            'buildcraft/api/v2/persistence/ApiCodec.java': '''package buildcraft.api.v2.persistence;
                public interface ApiCodec<T,P>{CodecResult<P> encode(T value); CodecResult<T> decode(P data);}''',
            'buildcraft/api/v2/persistence/CodecResult.java': '''package buildcraft.api.v2.persistence;
                public record CodecResult<T>(T value,String error){
                  public static <T> CodecResult<T> success(T v){return new CodecResult<>(v,null);}
                  public static <T> CodecResult<T> failure(String e){return new CodecResult<>(null,e);}
                  public T valueOrThrow(){if(error!=null)throw new AssertionError(error);return value;}
                }''',
            'buildcraft/api/v2/pipe/PipeView.java': '''package buildcraft.api.v2.pipe;
                import java.util.*;import net.minecraft.core.*;
                public interface PipeView{
                  BlockPos position();Set<Direction> connectedSides();
                  default boolean isClientSide(){return false;}
                }''',
            'buildcraft/api/v2/pipe/PipeMutationContext.java': '''package buildcraft.api.v2.pipe;
                import net.minecraft.core.Direction;import buildcraft.api.v2.fluid.FluidPort;import java.util.Optional;
                public interface PipeMutationContext extends PipeView{
                  void markChanged(); Optional<FluidPort> fluidPort(Direction d);
                }''',
            'buildcraft/api/v2/pipe/PipeExecutionContext.java': '''package buildcraft.api.v2.pipe;
                public interface PipeExecutionContext extends PipeMutationContext{
                  boolean isServerSide();boolean isActive();
                }''',
            'buildcraft/api/v2/pipe/PipeRuntimeComponent.java': '''package buildcraft.api.v2.pipe;
                import java.util.*;import net.minecraft.world.item.ItemStack;import net.minecraft.core.Direction;
                import net.minecraft.world.entity.Entity;import buildcraft.api.v2.energy.MjPort;
                import net.minecraft.resources.ResourceLocation;
                public interface PipeRuntimeComponent{
                  ResourceLocation typeId();default void attached(PipeView p){}
                  default void suspended(){}default void invalidated(){}
                  default Optional<MjPort> mjPort(Direction d){return Optional.empty();}
                  default void addDrops(List<ItemStack> d,int fortune){}
                  default void tick(PipeMutationContext c){}
                  default void entityCollided(PipeView p,Entity e){}
                }''',
            'net/minecraftforge/fluids/FluidStack.java': '''package net.minecraftforge.fluids;
                public class FluidStack {
                  private final int amount;
                  public FluidStack(int amount){this.amount=amount;}
                  public int getAmount(){return amount;}
                  public boolean isEmpty(){return amount<=0;}
                }''',
            'net/minecraftforge/fluids/capability/IFluidHandler.java': '''package net.minecraftforge.fluids.capability;
                public interface IFluidHandler { enum FluidAction {SIMULATE,EXECUTE} }''',
            'buildcraft/transport/internal/pipe/IFlowFluid.java': '''package buildcraft.transport.internal.pipe;
                import net.minecraft.core.Direction;
                import net.minecraftforge.fluids.FluidStack;
                import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
                public interface IFlowFluid {int insertFluidsForce(FluidStack stack,Direction from,FluidAction action);}
                ''',
            'buildcraft/lib/fluid/FuelApiBridge.java': '''package buildcraft.lib.fluid;
                import buildcraft.api.v2.fluid.FluidVolume;
                import net.minecraftforge.fluids.FluidStack;
                public final class FuelApiBridge {
                  public static FluidStack stackOf(FluidVolume fluid){
                    return new FluidStack((int)fluid.amount().milliBuckets());
                  }
                }''',
            'buildcraft/transport/internal/pipe/IPipe.java': '''package buildcraft.transport.internal.pipe;
                import net.minecraft.server.level.ServerLevel;
                public interface IPipe{Holder getHolder();Object getFlow();
                  record Holder(ServerLevel level){public net.minecraft.world.level.Level getPipeWorld(){return level;}}
                }''',
            'buildcraft/additionalpipes/pipes/APUtilityPipes.java': '''package buildcraft.additionalpipes.pipes;
                import net.minecraft.resources.ResourceLocation;
                public final class APUtilityPipes{public static final ResourceLocation OBSIDIAN_COMPONENT=ResourceLocation.tryParse("additionalpipes:obsidian_fluid_drain");}''',
            'buildcraft/additionalpipes/pipes/APFluidContainers.java': '''package buildcraft.additionalpipes.pipes;
                import buildcraft.api.v2.fluid.*;
            import buildcraft.api.v2.energy.*;import net.minecraft.world.item.ItemStack;
                import net.minecraft.resources.ResourceLocation;
                public final class APFluidContainers{
                  public record Drained(FluidVolume fluid,ItemStack container){}
                  public static Drained drainOne(ItemStack s){
                    if(s==null||s.isEmpty()||!s.item.equals("water_bucket"))return null;
                    return new Drained(FluidVolume.of(FluidVariant.of(ResourceLocation.tryParse("minecraft:water")),1000),new ItemStack("bucket",1));
                  }
                }''',
            'buildcraft/additionalpipes/pipes/APAdvancedWoodStackIO.java': '''package buildcraft.additionalpipes.pipes;
                import java.io.*;import net.minecraft.world.item.ItemStack;
                public class APAdvancedWoodStackIO{
                  public static void write(DataOutputStream o,ItemStack s)throws IOException{o.writeUTF(s.item);o.writeInt(s.count);}
                  public static ItemStack read(DataInputStream i)throws IOException{return new ItemStack(i.readUTF(),i.readInt());}
                }''',
        }
        harness = '''import buildcraft.additionalpipes.pipes.*;
            import buildcraft.api.v2.OperationMode;
            import buildcraft.api.v2.fluid.*;
            import buildcraft.api.v2.energy.*;
            import buildcraft.api.v2.pipe.*;
            import net.minecraft.core.*;
            import net.minecraft.server.level.ServerLevel;
            import net.minecraft.world.entity.item.ItemEntity;
            import net.minecraft.world.item.ItemStack;
            import java.util.*;
            public class TestObsidian {
              static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
              static class Runtime implements PipeExecutionContext,buildcraft.transport.internal.pipe.IPipe{
                final ServerLevel level;int marks;long accepted;int wrongSidedInjections;
                final Set<Direction> connections=EnumSet.of(Direction.EAST);
                final FluidFlow flow = new FluidFlow(this);
                Runtime(ServerLevel world){level=world;}
                public buildcraft.transport.internal.pipe.IPipe.Holder getHolder(){return new buildcraft.transport.internal.pipe.IPipe.Holder(level);}
                public buildcraft.transport.internal.pipe.IFlowFluid getFlow(){return flow;}
                public boolean isServerSide(){return true;}public boolean isActive(){return true;}
                public void markChanged(){marks++;}
                public BlockPos position(){return new BlockPos(0,0,0);}
                public Set<Direction> connectedSides(){return connections;}
                public Optional<FluidPort> fluidPort(Direction side){
                  throw new AssertionError("Sided injection locks outgoing fluid faces");
                }
              }
              // Minimal BCCE-style center buffer and 160 mB/t carrier. An input-side
              // cooldown would prevent this outgoing face from draining indefinitely.
              static class FluidFlow implements buildcraft.transport.internal.pipe.IFlowFluid {
                final Runtime pipe; int center,delivered;
                FluidFlow(Runtime p){pipe=p;}
                public int insertFluidsForce(net.minecraftforge.fluids.FluidStack stack,Direction from,
                        net.minecraftforge.fluids.capability.IFluidHandler.FluidAction mode){
                  if(from!=null)pipe.wrongSidedInjections++;
                  int accepted=(int)Math.max(0,Math.min(pipe.accepted,
                    Math.min(stack.getAmount(),160-center)));
                  if(mode==net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE)
                    center+=accepted;
                  return accepted;
                }
                void tickNetwork(){
                  if(pipe.wrongSidedInjections>0)return;
                  int out=Math.min(center,160);
                  center-=out;delivered+=out;
                }
              }
              public static void main(String[] args) throws Exception {
                ServerLevel world=new ServerLevel();Runtime pipe=new Runtime(world);
                ObsidianFluidComponent c=new ObsidianFluidComponent();c.attached(pipe);c.tick(pipe);
                ItemEntity entity=new ItemEntity(world,0.5,0.5,0.5,new ItemStack("water_bucket",3));
                world.items.add(entity);
                c.entityCollided(pipe,entity);
                check(entity.getItem().count==2,"intake of one item only");
                check(c.pendingFluid().amount().milliBuckets()==1000,"buffer retains full bucket");
                check(c.pendingContainer().item.equals("bucket"),"empty bucket retained");
                pipe.accepted=0;for(int i=0;i<3;i++)c.tick(pipe);
                check(c.pendingFluid().amount().milliBuckets()==1000,"blocked pipe must not drain buffer");
                check(world.items.size()==1,"do not eject before fluid transfer");
                var snapshot=ObsidianFluidComponent.CODEC.encode(c).valueOrThrow();
                ObsidianFluidComponent reloaded=ObsidianFluidComponent.CODEC.decode(snapshot).valueOrThrow();
                check(reloaded.pendingFluid().amount().milliBuckets()==1000,"saved fluid amount");
                check(reloaded.pendingContainer().item.equals("bucket"),"saved empty item");
                pipe.accepted=160;
                for(int i=0;i<12;i++){pipe.flow.tickNetwork();reloaded.tick(pipe);}
                check(reloaded.pendingFluid().isEmpty(),"eventually transfers all fluid");
                check(pipe.wrongSidedInjections==0,"fluid injection must never mark an output as input");
                check(pipe.flow.delivered+pipe.flow.center==1000,"all 1000 mB retained in physical fluid carrier");
                check(world.items.size()==2 && world.items.get(1).getItem().item.equals("bucket"),"one empty bucket returned");
                // A fully connected pipe must also have no artificial intake face.
                ServerLevel sixWorld=new ServerLevel();Runtime six=new Runtime(sixWorld);
                six.connections.addAll(EnumSet.allOf(Direction.class));six.accepted=160;
                ObsidianFluidComponent sixComponent=new ObsidianFluidComponent();
                sixComponent.attached(six);sixComponent.tick(six);
                ItemEntity sixBucket=new ItemEntity(sixWorld,0.5,0.5,0.5,new ItemStack("water_bucket",1));
                sixWorld.items.add(sixBucket);sixComponent.entityCollided(six,sixBucket);
                for(int i=0;i<12;i++){six.flow.tickNetwork();sixComponent.tick(six);}
                check(sixComponent.pendingFluid().isEmpty(),"six connected faces must not block output");
                check(six.flow.delivered+six.flow.center==1000,"six-face flow must conserve the bucket");
                check(six.wrongSidedInjections==0,"six-face flow must use a neutral intake");
                check(sixWorld.items.size()==2,"drained bucket must return exactly once");
                entity.discard(); // Remaining buckets from the initial stack must not mask distant candidates.
                ObsidianFluidComponent fresh=new ObsidianFluidComponent();fresh.attached(pipe);
                long simulated=fresh.powerPort().insert(MjAmount.ofMj(90),OperationMode.SIMULATE)
                    .transferred().microMj();
                check(simulated==MjAmount.ofMj(64).microMj() && fresh.storedMj().microMj()==0,
                    "MJ simulation and max receive");
                fresh.powerPort().insert(MjAmount.ofMj(90),OperationMode.EXECUTE);
                check(fresh.storedMj().microMj()==MjAmount.ofMj(64).microMj(),"MJ received");
                // Two connected faces previously disabled the distance scan entirely.
                pipe.connections.add(Direction.NORTH);
                ItemEntity distant=new ItemEntity(world,-1.5,0.5,0.5,new ItemStack("water_bucket",1));
                world.items.add(distant);
                fresh.tick(pipe);
                check(distant.isRemoved(),"powered suction works with multiple connected fluid faces");
                check(fresh.storedMj().microMj()==MjAmount.ofMj(62).microMj(),"MJ cost based on distance");
                check(fresh.pendingFluid().amount().milliBuckets()==1000,"distant pickup holds fluid");
                fresh.powerPort().insert(MjAmount.ofMj(400),OperationMode.EXECUTE);
                check(fresh.storedMj().microMj()==MjAmount.ofMj(126).microMj(),"MJ receive clamp");
                var mjSnapshot=ObsidianFluidComponent.CODEC.encode(fresh).valueOrThrow();
                var restored=ObsidianFluidComponent.CODEC.decode(mjSnapshot).valueOrThrow();
                check(restored.storedMj().microMj()==MjAmount.ofMj(126).microMj(),"MJ v2 persistence");
                // Nearest matching bucket wins even when the entity list is in far-to-near order.
                ObsidianFluidComponent around=new ObsidianFluidComponent();around.attached(pipe);
                around.powerPort().insert(MjAmount.ofMj(64),OperationMode.EXECUTE);
                ItemEntity farther=new ItemEntity(world,-3.5,0.5,0.5,new ItemStack("water_bucket",1));
                ItemEntity offset=new ItemEntity(world,1.5,0.5,1.5,new ItemStack("water_bucket",1));
                world.items.add(farther);world.items.add(offset);
                around.tick(pipe);
                check(offset.isRemoved(),"diagonal adjacent bucket found, not only a one-wide line");
                check(!farther.isRemoved(),"nearest bucket prioritized ahead of more distant bucket");
                check(around.storedMj().microMj()==MjAmount.ofMj(62).microMj(),"MJ charged for actual radial distance");
                // No MJ means collision pickup only, not remote suction.
                ObsidianFluidComponent noPower=new ObsidianFluidComponent();noPower.attached(pipe);
                ItemEntity contact=new ItemEntity(world,2.5,0.5,0.5,new ItemStack("water_bucket",1));
                world.items.add(contact);noPower.tick(pipe);
                check(!contact.isRemoved(),"zero-MJ pipe cannot suck nearby bucket");
                noPower.entityCollided(pipe,contact);
                check(contact.isRemoved(),"collision pickup needs no MJ");
                var legacy=new java.io.ByteArrayOutputStream();
                var stream=new java.io.DataOutputStream(legacy);
                stream.writeByte(1);stream.writeShort(150);stream.writeBoolean(false);
                APAdvancedWoodStackIO.write(stream,ItemStack.EMPTY);stream.flush();
                var previous=ObsidianFluidComponent.CODEC.decode(new buildcraft.api.v2.persistence.OpaqueData(
                    APUtilityPipes.OBSIDIAN_COMPONENT, legacy.toByteArray())).valueOrThrow();
                check(previous.storedMj().microMj()==MjAmount.ofMj(15).microMj(),
                    "legacy FE persisted state migrated into MJ");
                // Regression: BCCE's Redstone Engine produces 0.05 MJ/t. Previously
                // the pipe burned 0.5 MJ/t while idle and could never build up
                // the 1 MJ needed to suction a container one block away.
                ServerLevel redstoneWorld=new ServerLevel();Runtime redstonePipe=new Runtime(redstoneWorld);
                ObsidianFluidComponent redstone=new ObsidianFluidComponent();redstone.attached(redstonePipe);
                ItemEntity adjacent=new ItemEntity(redstoneWorld,1.5,0.5,0.5,new ItemStack("water_bucket",1));
                redstoneWorld.items.add(adjacent);
                long redstonePulse=MjAmount.MICRO_MJ_PER_MJ/20;
                for(int tick=1;tick<=19;tick++){
                  var inserted=redstone.powerPort().insert(MjAmount.ofMicro(redstonePulse),OperationMode.EXECUTE);
                  check(inserted.transferred().microMj()==redstonePulse,"redstone MJ port must accept output");
                  redstone.tick(redstonePipe);
                  check(redstone.storedMj().microMj()==tick*redstonePulse,
                    "idle tick must not consume redstone engine MJ; tick="+tick);
                  check(!adjacent.isRemoved(),"redstone engine accumulates before suction");
                }
                // Reopening the world mid-charge must preserve accumulated MJ.
                var halfway=ObsidianFluidComponent.CODEC.encode(redstone).valueOrThrow();
                redstone=ObsidianFluidComponent.CODEC.decode(halfway).valueOrThrow();
                redstone.attached(redstonePipe);
                redstone.powerPort().insert(MjAmount.ofMicro(redstonePulse),OperationMode.EXECUTE);
                redstone.tick(redstonePipe);
                check(adjacent.isRemoved(),"one Redstone Engine must power distance=1 suction after 20 ticks");
                check(redstone.storedMj().microMj()==0,"distance=1 consumes exactly 1 MJ");
                check(redstone.pendingFluid().amount().milliBuckets()==1000,"redstone engine powers real fluid pickup");
                System.out.println("PASS obsidian component transfer, redstone MJ accumulation and persistence");
              }
            }'''
        with tempfile.TemporaryDirectory(prefix='ap-obsidian-java-') as tmp:
            temp = Path(tmp)
            for path, src in stubs.items():
                output = temp / path
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_text(src)
            (temp / 'TestObsidian.java').write_text(harness)
            java_files = [*temp.rglob('*.java'),
                          ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/pipes/ObsidianFluidComponent.java',
                          ROOT / 'source-platforms/forge/src/main/java/buildcraft/additionalpipes/pipes/APObsidianFluidInjection.java']
            result = subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(temp/'classes'), *map(str, java_files)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            run = subprocess.run(['java', '-cp', str(temp/'classes'), 'TestObsidian'], capture_output=True, text=True)
            self.assertEqual(run.returncode, 0, run.stderr)
            self.assertIn('PASS obsidian component transfer, redstone MJ accumulation and persistence', run.stdout)


if __name__ == '__main__':
    unittest.main()
