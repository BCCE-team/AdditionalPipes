"""Compile and exercise the real JeweledPipeComponent codec against API-v2 contract stubs.

Checks inventory persistence and drops, not a substitute for Minecraft/Gradle.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]


class JeweledComponentCodecTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_compiles_and_roundtrips_162_real_filter_slots(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            def stub(name, source):
                path = base / 'stubs' / (name.replace('.', '/') + '.java')
                path.parent.mkdir(parents=True, exist_ok=True)
                pkg = name.rpartition('.')[0]
                path.write_text(f'package {pkg};\n' + textwrap.dedent(source))

            stub('net.minecraft.resources.ResourceLocation', '''
                public record ResourceLocation(String name){}
            ''')
            stub('net.minecraft.core.BlockPos', '''
                public record BlockPos(int getX,int getY,int getZ){}
            ''')
            stub('net.minecraft.core.Direction', '''
                public enum Direction { DOWN, UP, NORTH, SOUTH, WEST, EAST }
            ''')
            stub('net.minecraft.world.item.Item', '''public class Item {}''')
            stub('net.minecraft.world.item.ItemStack', '''
                public class ItemStack {
                    public static final ItemStack EMPTY=new ItemStack("",0,0,"");
                    private final String name, tag; private int amount, damage;
                    public ItemStack(String name,int amount,int damage,String tag){
                        this.name=name;this.amount=amount;this.damage=damage;this.tag=tag;
                    }
                    public boolean isEmpty(){return name.isEmpty()||amount<=0;}
                    public ItemStack copy(){return new ItemStack(name,amount,damage,tag);}
                    public int getCount(){return amount;}
                    public void setCount(int size){amount=size;}
                    public int getMaxStackSize(){return 64;}
                    public int getDamageValue(){return damage;}
                    public boolean isDamageableItem(){return true;}
                    public void setDamageValue(int value){damage=value;}
                    public ItemStack split(int size){int remove=Math.min(amount,size);amount-=remove;return new ItemStack(name,remove,damage,tag);}
                    private static final java.util.Map<String,Item> ITEMS=new java.util.HashMap<>();
                    public Item getItem(){return ITEMS.computeIfAbsent(name,k->new Item());}
                    public String name(){return name;}
                    public String tag(){return tag;}
                    public static boolean isSameItemSameTags(ItemStack a,ItemStack b){return a.name.equals(b.name)&&a.tag.equals(b.tag);}
                }
            ''')
            stub('buildcraft.additionalpipes.APConfiguration', '''
                public class APConfiguration {public boolean filterRightclicks=false;
                    public static APConfiguration get(){return new APConfiguration();}}
            ''')
            stub('buildcraft.additionalpipes.api.PipeLocation', '''
                public record PipeLocation(String dimension,int x,int y,int z){}
            ''')
            stub('buildcraft.additionalpipes.platform.APDimensionIds', '''
                public class APDimensionIds {public static String of(Object world){return "overworld";}}
            ''')
            stub('buildcraft.additionalpipes.platform.APJeweledMenuAccess', '''
                public class APJeweledMenuAccess {
                    public static void open(net.minecraft.server.level.ServerPlayer player,
                        buildcraft.additionalpipes.pipes.JeweledPipeComponent component,
                        buildcraft.additionalpipes.api.PipeLocation pos){}
                }
            ''')
            stub('buildcraft.additionalpipes.platform.APPlayerLevel', '''
                public class APPlayerLevel {
                    public static net.minecraft.server.level.ServerLevel of(net.minecraft.server.level.ServerPlayer player)
                        {return new net.minecraft.server.level.ServerLevel();}}
            ''')
            stub('buildcraft.additionalpipes.service.APServices', '''
                public class APServices {
                    public static net.minecraft.server.MinecraftServer server(){return null;}}
            ''')
            stub('buildcraft.additionalpipes.pipes.APJeweledPipes', '''
                public class APJeweledPipes {
                    public static final net.minecraft.resources.ResourceLocation COMPONENT=
                        new net.minecraft.resources.ResourceLocation("additionalpipes:jeweled_side_filters");}
            ''')
            stub('buildcraft.additionalpipes.pipes.APJeweledItemMatcher', '''
                public class APJeweledItemMatcher {
                    public static boolean sameData(net.minecraft.world.item.ItemStack a,net.minecraft.world.item.ItemStack b){
                        return a.name().equals(b.name())&&a.tag().equals(b.tag());}}
            ''')
            stub('buildcraft.additionalpipes.pipes.APAdvancedWoodStackIO', '''
                public class APAdvancedWoodStackIO {
                    public static void write(java.io.DataOutputStream out,net.minecraft.world.item.ItemStack value)
                        throws java.io.IOException {
                        out.writeBoolean(!value.isEmpty());
                        if(!value.isEmpty()){out.writeUTF(value.name());out.writeInt(value.getCount());
                            out.writeInt(value.getDamageValue());out.writeUTF(value.tag());}}
                    public static net.minecraft.world.item.ItemStack read(java.io.DataInputStream in)
                        throws java.io.IOException {
                        if(!in.readBoolean())return net.minecraft.world.item.ItemStack.EMPTY;
                        return new net.minecraft.world.item.ItemStack(in.readUTF(),in.readInt(),in.readInt(),in.readUTF());}
                }
            ''')
            stub('buildcraft.api.v2.OperationMode', '''public enum OperationMode { SIMULATE, EXECUTE }''')
            stub('buildcraft.api.v2.persistence.ApiCodec', '''
                public interface ApiCodec<V,O> {
                    CodecResult<O> encode(V value);CodecResult<V> decode(O value);}
            ''')
            stub('buildcraft.api.v2.persistence.CodecResult', '''
                public record CodecResult<T>(T value,String error){
                    public static <T> CodecResult<T> success(T value){return new CodecResult<>(value,null);}
                    public static <T> CodecResult<T> failure(String why){return new CodecResult<>(null,why);}}
            ''')
            stub('buildcraft.api.v2.persistence.OpaqueData', '''
                public record OpaqueData(net.minecraft.resources.ResourceLocation format,byte[] bytes){
                    public int size(){return bytes.length;}}
            ''')
            stub('buildcraft.api.v2.pipe.PipeView', '''
                public interface PipeView {
                    net.minecraft.core.BlockPos position();boolean isClientSide();}
            ''')
            stub('buildcraft.api.v2.pipe.PipeMutationContext', '''
                public interface PipeMutationContext extends PipeView {void markChanged();}
            ''')
            stub('buildcraft.api.v2.pipe.PipeComponentLifecycle', '''
                public interface PipeComponentLifecycle {
                    net.minecraft.resources.ResourceLocation typeId();
                    default void attached(PipeView view){}default void invalidated(){}
                    default void addDrops(java.util.List<net.minecraft.world.item.ItemStack> drops,int fortune){}
                }
            ''')
            stub('buildcraft.api.v2.pipe.ItemRouteContext', '''
                public record ItemRouteContext(PipeView pipe,net.minecraft.core.Direction input,
                    net.minecraft.world.item.ItemStack stack,java.util.Set<net.minecraft.core.Direction> candidates){}
            ''')
            stub('buildcraft.api.v2.pipe.RouteDecision', '''
                public record RouteDecision(java.util.Map<net.minecraft.core.Direction,Integer> weights){}
            ''')
            stub('buildcraft.api.v2.pipe.ItemRouteComponent', '''
                public interface ItemRouteComponent {
                    net.minecraft.resources.ResourceLocation typeId();
                    RouteDecision route(ItemRouteContext context);}
            ''')
            stub('buildcraft.api.v2.pipe.PipeActivationComponent', '''
                public interface PipeActivationComponent {
                    PipeActivationResult activate(PipeActivationContext context);}
            ''')
            stub('buildcraft.api.v2.pipe.PipeActivationResult', '''
                public enum PipeActivationResult { PASS, SUCCESS }
            ''')
            stub('buildcraft.api.v2.pipe.PipeActivationContext', '''
                public class PipeActivationContext {
                    public net.minecraft.world.item.ItemStack heldStack(){return net.minecraft.world.item.ItemStack.EMPTY;}
                    public Actor actor(){return new Actor();}
                    public buildcraft.api.v2.OperationMode mode(){return buildcraft.api.v2.OperationMode.SIMULATE;}
                    public PipeView pipe(){return null;}
                    public static class Actor {public java.util.Optional<java.util.UUID> playerId(){return java.util.Optional.empty();}}
                }
            ''')
            stub('buildcraft.transport.internal.pipe.IItemPipe', '''public interface IItemPipe{}''')
            stub('net.minecraft.server.level.ServerLevel', '''
                public class ServerLevel {public boolean hasChunkAt(net.minecraft.core.BlockPos pos){return true;}}
            ''')
            stub('net.minecraft.server.level.ServerPlayer', '''
                public class ServerPlayer {public boolean isShiftKeyDown(){return false;}}
            ''')
            stub('net.minecraft.server.MinecraftServer', '''
                public class MinecraftServer {
                    public Players getPlayerList(){return new Players();}
                    public static class Players {public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id){return null;}}
                }
            ''')

            # Compile the actual Java logic: no replacement implementation of the component.
            sources = list((base/'stubs').rglob('*.java')) + [
                ROOT/'source-shared/src/main/java/buildcraft/additionalpipes/filter/SideFilterData.java',
                ROOT/'source-shared/src/main/java/buildcraft/additionalpipes/pipes/JeweledRouting.java',
                ROOT/'source-shared/src/main/java/buildcraft/additionalpipes/pipes/JeweledPipeComponent.java',
            ]
            (base/'Harness.java').write_text(textwrap.dedent('''
                import buildcraft.additionalpipes.pipes.JeweledPipeComponent;
                import buildcraft.additionalpipes.filter.SideFilterData;
                import buildcraft.api.v2.pipe.*;
                import net.minecraft.core.*;
                import net.minecraft.world.item.*;
                import java.util.*;
                public class Harness {
                    static int n;
                    static void require(boolean yes,String why){n++;if(!yes)throw new AssertionError(why);}
                    static class Pipe implements PipeMutationContext {
                        int changed;
                        public BlockPos position(){return new BlockPos(1,2,3);}
                        public boolean isClientSide(){return false;}
                        public void markChanged(){changed++;}
                    }
                    static ItemStack stack(String n,int c,int damage,String tag){return new ItemStack(n,c,damage,tag);}
                    public static void main(String[] ignored){
                        JeweledPipeComponent component=new JeweledPipeComponent();
                        Pipe view=new Pipe();component.attached(view);
                        component.setItem(0,26,stack("diamond",10,2,"enchant:A"));
                        component.setItem(1,0,stack("diamond",1,2,"enchant:A"));
                        component.setItem(5,26,stack("gold",2,0,""));
                        component.setOptions(2,new SideFilterData.Options(false,false,true));
                        component.setOptions(0,new SideFilterData.Options(true,true,false));
                        require(view.changed==5,"Mutations were not persisted");
                        var serialized=JeweledPipeComponent.CODEC.encode(component);
                        require(serialized.error()==null,"Codec failed");
                        require(serialized.value().size()>0 && serialized.value().size()<=65536,
                            "Full six-side state must fit BCCE's 64KiB persistence budget");
                        var decoded=JeweledPipeComponent.CODEC.decode(serialized.value());
                        require(decoded.error()==null,"Decode failed: "+decoded.error());
                        var loaded=decoded.value();
                        require(loaded.getItem(0,26).getCount()==10,"first side/last slot restored");
                        require(loaded.getItem(1,0).getCount()==1,"second side restored");
                        require(loaded.getItem(5,26).getCount()==2,"last side/last slot restored");
                        require(loaded.options(0).matchMetadata() && loaded.options(0).matchNBT(),"strict options restored");
                        require(loaded.options(2).acceptUnsortedItems(),"fallback option restored");
                        require(loaded.options(3).equals(new SideFilterData.Options(false,false,false)),"independent default side");
                        require(loaded.getItem(4,0).isEmpty(),"empty slots restored");
                        ItemStack detached=loaded.getItem(0,26);detached.setCount(1);
                        require(loaded.getItem(0,26).getCount()==10,"Cannot mutate component via returned copy");
                        var wanted=stack("diamond",1,2,"enchant:A");
                        var result=loaded.route(new ItemRouteContext(view,Direction.SOUTH,wanted,
                            EnumSet.allOf(Direction.class)));
                        require(result.weights().get(Direction.DOWN)==1 && result.weights().get(Direction.UP)==1,
                            "Two matching exits retained");
                        require(result.weights().get(Direction.NORTH)==0,"Matching exit suppresses fallback");
                        result=loaded.route(new ItemRouteContext(view,Direction.SOUTH,stack("copper",1,0,""),
                            EnumSet.allOf(Direction.class)));
                        require(result.weights().get(Direction.NORTH)==1,"Unmatched route uses unsorted");
                        loaded.setOptions(2,new SideFilterData.Options(false,false,false));
                        result=loaded.route(new ItemRouteContext(view,Direction.SOUTH,stack("copper",1,0,""),
                            EnumSet.allOf(Direction.class)));
                        require(result.weights().values().stream().noneMatch(v->v>0),"No implicit fallbacks");
                        var drops=new ArrayList<ItemStack>(); loaded.addDrops(drops,0);
                        require(drops.size()==3,"Real filter stacks must drop on break");
                        loaded.addDrops(new ArrayList<>(),0);
                        require(loaded.getItem(0,26).getCount()==10,"Loot evaluation must be read only");
                        require(loaded.takeItem(0,26,4).getCount()==4,"Player can retrieve real items");
                        require(loaded.getItem(0,26).getCount()==6,"Inventory debit persisted");
                        loaded.removeItem(1,0);
                        require(loaded.getItem(1,0).isEmpty(),"Slot removal works");
                        JeweledPipeComponent full=new JeweledPipeComponent();
                        for(int side=0;side<6;side++) for(int slot=0;slot<27;slot++)
                            full.setItem(side,slot,stack("item"+side+"_"+slot,64,slot,"custom data ".repeat(80)));
                        var fullData=JeweledPipeComponent.CODEC.encode(full);
                        require(fullData.error()==null && fullData.value().size()<=65536,
                            "Compression preserves a fully populated 162-slot inventory");
                        var restored=JeweledPipeComponent.CODEC.decode(fullData.value());
                        require(restored.error()==null,"Compressed full inventory decode");
                        require(restored.value().getItem(5,26).getCount()==64,
                            "Final slot of compressed inventory survives reload");
                        var corrupt=JeweledPipeComponent.CODEC.decode(
                            new buildcraft.api.v2.persistence.OpaqueData(
                                buildcraft.additionalpipes.pipes.APJeweledPipes.COMPONENT,new byte[]{1,2,3}));
                        require(corrupt.error()!=null,"Malformed compressed inventory rejected");
                        System.out.println("PASS JeweledComponentCodec "+n+" assertions");
                    }
                }
            '''))
            sources.append(base/'Harness.java')
            subprocess.run(['javac','--release','17','-encoding','UTF-8','-d',str(base/'classes'),
                            *map(str,sources)],check=True,text=True,capture_output=True)
            result=subprocess.run(['java','-cp',str(base/'classes'),'Harness'],check=False,
                                  text=True,capture_output=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertIn('PASS JeweledComponentCodec',result.stdout)


if __name__=='__main__':unittest.main()
