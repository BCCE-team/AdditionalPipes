#!/usr/bin/env python3
"""Synthetic compilation of seven overlaid AP services with minimal MC/loader stubs.

This checks target-specific Java syntax and interlayer linkage, NOT loader binary
compatibility, a Gradle build, or a Minecraft runtime. No network access needed.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess, sys
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'scripts'))
from materialize_sources import materialize

STUBS={
'net.minecraft.resources.ResourceLocation':'''public record ResourceLocation(String id){public ResourceLocation(String namespace,String path){this(namespace+":"+path);}public static ResourceLocation fromNamespaceAndPath(String a,String b){return new ResourceLocation(a+":"+b);}}''',
'net.minecraft.resources.Identifier':'''public record Identifier(String id){public static Identifier fromNamespaceAndPath(String a,String b){return new Identifier(a+":"+b);}}''',
'net.minecraft.resources.ResourceKey':'''public class ResourceKey<T>{public Object location(){return "minecraft:overworld";}public static <T> ResourceKey<T> create(ResourceKey<net.minecraft.core.Registry<T>> registry,ResourceLocation id){return new ResourceKey<>();}public static <T> ResourceKey<T> create(ResourceKey<net.minecraft.core.Registry<T>> registry,Identifier id){return new ResourceKey<>();}}''',
'net.minecraft.core.BlockPos':'''public class BlockPos{public int getX(){return 0;} public int getY(){return 0;} public int getZ(){return 0;} public BlockPos immutable(){return this;}}''',
'net.minecraft.world.level.ChunkPos':'''public class ChunkPos {public final int x,z;public ChunkPos(int x,int z){this.x=x;this.z=z;}public int x(){return x;}public int z(){return z;}}''',
'net.minecraft.world.entity.player.Player':'''public class Player{public java.util.UUID getUUID(){return null;}public net.minecraft.core.BlockPos blockPosition(){return null;}}''',
'net.minecraft.server.level.ServerLevel':'''public class ServerLevel{public net.minecraft.resources.ResourceKey<?> dimension(){return null;}public long getGameTime(){return 0;}}''',
'net.minecraft.server.level.ServerPlayer':'''public class ServerPlayer extends net.minecraft.world.entity.player.Player{public ServerLevel serverLevel(){return null;}}''',
'net.minecraft.world.level.storage.LevelResource':'''public class LevelResource{public static final LevelResource ROOT=new LevelResource();}''',
'net.minecraft.server.MinecraftServer':'''public class MinecraftServer{public java.nio.file.Path getWorldPath(net.minecraft.world.level.storage.LevelResource path){return null;}}''',
'buildcraft.api.v2.ApiVersion':'''public record ApiVersion(int major,int minor,int patch){}''',
'buildcraft.api.v2.ApiRuntime':'''public class ApiRuntime{public ApiVersion version(){return null;}public <T> java.util.Optional<T> service(Object key){return java.util.Optional.empty();}}''',
'buildcraft.api.v2.BuildCraftApi':'''public final class BuildCraftApi{public static ApiRuntime runtime(){return null;}}''',
'buildcraft.api.v2.BuildCraftServices':'''public class BuildCraftServices{public static final Object PIPES=new Object();}''',
'net.minecraftforge.fml.common.Mod':'''@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Mod{String value();}''',
'net.minecraftforge.common.MinecraftForge':'''public class MinecraftForge{public static final Bus EVENT_BUS=new Bus();public static class Bus{public <T> void addListener(java.util.function.Consumer<T> f){}}}''',
'net.minecraftforge.event.server.ServerStartingEvent':'''public class ServerStartingEvent{public net.minecraft.server.MinecraftServer getServer(){return null;}}''',
'net.minecraftforge.event.server.ServerStoppingEvent':'''public class ServerStoppingEvent{public net.minecraft.server.MinecraftServer getServer(){return null;}}''',
'net.minecraftforge.common.world.ForgeChunkManager':'''public final class ForgeChunkManager{public static class TicketHelper{public java.util.Map<net.minecraft.core.BlockPos,Object> getBlockTickets(){return null;}public void removeAllTickets(net.minecraft.core.BlockPos p){}}public static void setForcedChunkLoadingCallback(String a,java.util.function.BiConsumer<net.minecraft.server.level.ServerLevel, TicketHelper>b){}public static boolean forceChunk(net.minecraft.server.level.ServerLevel l,String id,net.minecraft.core.BlockPos b,int x,int z,boolean add,boolean tick){return true;}}''',
'net.minecraftforge.network.NetworkDirection':'''public enum NetworkDirection{PLAY_TO_SERVER,PLAY_TO_CLIENT}''',
'net.minecraftforge.network.NetworkEvent':'''public class NetworkEvent{public static class Context{public net.minecraft.server.level.ServerPlayer getSender(){return null;}public void enqueueWork(Runnable r){}public void setPacketHandled(boolean b){}}}''',
'net.minecraftforge.network.PacketDistributor':'''public class PacketDistributor{public static final PlayerTarget PLAYER=new PlayerTarget();public static class PlayerTarget{public Object with(java.util.function.Supplier<net.minecraft.server.level.ServerPlayer> s){return null;}}}''',
'net.minecraftforge.network.NetworkRegistry':'''public class NetworkRegistry{public static net.minecraftforge.network.simple.SimpleChannel newSimpleChannel(net.minecraft.resources.ResourceLocation r,java.util.function.Supplier<String> s,java.util.function.Predicate<String> p,java.util.function.Predicate<String> q){return null;}}''',
'net.minecraftforge.network.simple.SimpleChannel':'''public class SimpleChannel{public static class Buf{public Buf writeByteArray(byte[] bytes){return this;}public byte[] readByteArray(int length){return new byte[0];}} public <T> void registerMessage(int id,Class<T> c,java.util.function.BiConsumer<T,Buf> enc,java.util.function.Function<Buf,T> dec,java.util.function.BiConsumer<T,java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>> handle,java.util.Optional<net.minecraftforge.network.NetworkDirection> direction){}public void sendToServer(Object m){} public void send(Object t,Object msg){}}''',
'net.neoforged.fml.common.Mod':'''@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Mod{String value();}''',
'net.neoforged.bus.api.IEventBus':'''public interface IEventBus{<T> void addListener(java.util.function.Consumer<T> listener);}''',
'net.neoforged.neoforge.common.NeoForge':'''public class NeoForge{public static final net.neoforged.bus.api.IEventBus EVENT_BUS=new net.neoforged.bus.api.IEventBus(){public <T> void addListener(java.util.function.Consumer<T> l){}};}''',
'net.neoforged.neoforge.event.server.ServerStartingEvent':'''public class ServerStartingEvent{public net.minecraft.server.MinecraftServer getServer(){return null;}}''',
'net.neoforged.neoforge.event.server.ServerStoppingEvent':'''public class ServerStoppingEvent{public net.minecraft.server.MinecraftServer getServer(){return null;}}''',
'net.neoforged.neoforge.common.world.chunk.TicketHelper':'''public class TicketHelper{public java.util.Map<net.minecraft.core.BlockPos,Object> getBlockTickets(){return null;}public void removeAllTickets(net.minecraft.core.BlockPos pos){}}''',
'net.neoforged.neoforge.common.world.chunk.TicketController':'''public class TicketController{public TicketController(Object id,java.util.function.BiConsumer<net.minecraft.server.level.ServerLevel,TicketHelper> validator){}public boolean forceChunk(net.minecraft.server.level.ServerLevel w,net.minecraft.core.BlockPos p,int x,int z,boolean add,boolean ticking){return true;}}''',
'net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent':'''public class RegisterTicketControllersEvent{public void register(TicketController t){}}''',
'net.minecraft.network.protocol.PacketFlow':'''public enum PacketFlow{SERVERBOUND,CLIENTBOUND}''',
'net.minecraft.network.RegistryFriendlyByteBuf':'''public class RegistryFriendlyByteBuf{public void writeByteArray(byte[] b){} public byte[] readByteArray(int max){return null;}}''',
'net.minecraft.network.codec.StreamCodec':'''public interface StreamCodec<B,T>{static <B,T> StreamCodec<B,T> of(java.util.function.BiConsumer<B,T> encoder,java.util.function.Function<B,T> decoder){return null;}}''',
'net.minecraft.network.protocol.common.custom.CustomPacketPayload':'''public interface CustomPacketPayload{Type<? extends CustomPacketPayload> type();public record Type<T extends CustomPacketPayload>(Object value){}}''',
'net.neoforged.neoforge.network.handling.IPayloadContext':'''public interface IPayloadContext{net.minecraft.network.protocol.PacketFlow flow();net.minecraft.world.entity.player.Player player();void enqueueWork(Runnable r);}''',
'net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent':'''public class RegisterPayloadHandlersEvent{public Registrar registrar(String protocol){return null;}public static class Registrar{public <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void playBidirectional(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> t,net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,T>codec,java.util.function.BiConsumer<T,net.neoforged.neoforge.network.handling.IPayloadContext> receive){}public <T extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> void playBidirectional(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<T> t,net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,T>codec,java.util.function.BiConsumer<T,net.neoforged.neoforge.network.handling.IPayloadContext> client,java.util.function.BiConsumer<T,net.neoforged.neoforge.network.handling.IPayloadContext>server){}}}''',
'net.neoforged.neoforge.network.PacketDistributor':'''public class PacketDistributor{public static void sendToServer(Object payload){}public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player,Object payload){}}''',
'net.neoforged.neoforge.client.network.ClientPacketDistributor':'''public class ClientPacketDistributor{public static void sendToServer(Object payload){}}''',
}

STUBS.update({
'net.minecraft.core.Registry':'''public class Registry<T>{}''',
'net.minecraft.core.registries.Registries':'''public class Registries{public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.level.block.Block>> BLOCK=new net.minecraft.resources.ResourceKey<>();public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.item.Item>> ITEM=new net.minecraft.resources.ResourceKey<>();public static final net.minecraft.resources.ResourceKey<net.minecraft.core.Registry<net.minecraft.world.item.CreativeModeTab>> CREATIVE_MODE_TAB=new net.minecraft.resources.ResourceKey<>();}''',
'net.minecraft.world.level.block.state.BlockBehaviour':'''public class BlockBehaviour{public static class Properties{public static Properties of(){return new Properties();}public Properties strength(float value){return this;}public Properties noOcclusion(){return this;}public Properties setId(net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> key){return this;}}}''',
'net.minecraft.world.level.block.Block':'''public class Block{public Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties){}}''',
'net.minecraft.world.item.Item':'''public class Item{public static class Properties{public Properties tab(CreativeModeTab tab){return this;}public Properties setId(net.minecraft.resources.ResourceKey<Item> id){return this;}}public Item(Properties p){}public ItemStack getDefaultInstance(){return new ItemStack(this);}}''',
'net.minecraft.world.item.BlockItem':'''public class BlockItem extends Item{public BlockItem(net.minecraft.world.level.block.Block block, Item.Properties props){super(props);}}''',
'net.minecraft.world.item.ItemStack':'''public class ItemStack{public ItemStack(Item item){}}''',
'net.minecraft.network.chat.Component':'''public class Component{public static Component translatable(String key){return new Component();}}''',
'net.minecraft.world.item.CreativeModeTab':'''public class CreativeModeTab{public CreativeModeTab(){}public CreativeModeTab(String title){}public ItemStack makeIcon(){return null;}public static Builder builder(){return new Builder();}public interface Output{void accept(Item item);}public static class Builder{public Builder title(net.minecraft.network.chat.Component t){return this;}public Builder icon(java.util.function.Supplier<ItemStack> t){return this;}public Builder displayItems(java.util.function.BiConsumer<Object,Output> t){return this;}public CreativeModeTab build(){return new CreativeModeTab();}}}''',
'net.minecraftforge.eventbus.api.IEventBus':'''public interface IEventBus{}''',
'net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext':'''public class FMLJavaModLoadingContext{public static FMLJavaModLoadingContext get(){return new FMLJavaModLoadingContext();}public net.minecraftforge.eventbus.api.IEventBus getModEventBus(){return null;}}''',
'net.minecraftforge.registries.ForgeRegistries':'''public final class ForgeRegistries{public static final Object BLOCKS=new Object();public static final Object ITEMS=new Object();}''',
'net.minecraftforge.registries.RegistryObject':'''public class RegistryObject<T> implements java.util.function.Supplier<T>{public T get(){return null;}}''',
'net.minecraftforge.registries.DeferredRegister':'''public class DeferredRegister<T>{public static <I> DeferredRegister<I> create(Object obj,String id){return new DeferredRegister<>();}public <I extends T> RegistryObject<I> register(String id,java.util.function.Supplier<? extends I> factory){return new RegistryObject<>();}public void register(net.minecraftforge.eventbus.api.IEventBus bus){}}''',
'net.neoforged.neoforge.registries.DeferredRegister':'''public class DeferredRegister<T>{public static <I> DeferredRegister<I> create(Object registry,String id){return new DeferredRegister<>();}public <I extends T> java.util.function.Supplier<I> register(String id,java.util.function.Supplier<? extends I> factory){return ()->null;}public void register(net.neoforged.bus.api.IEventBus bus){}}''',
})

with TemporaryDirectory(prefix='ap-all-source-stub-') as tmp:
  tmp=Path(tmp)
  stub=tmp/'stubs'
  for fqcn,source in STUBS.items():
    package,name=fqcn.rsplit('.',1)
    path=stub/(fqcn.replace('.','/')+'.java')
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text('package '+package+';\n'+source,encoding='utf-8')
  for target in ['1.19.2-forge','1.20.1-forge','1.21.1-neoforge','1.21.11-neoforge','26.1.2-neoforge','26.2-neoforge','26.3-neoforge']:
    dest=tmp/target
    materialize(target,dest)
    java=list((dest/'src/main/java').rglob('*.java'))
    out=tmp/('compiled-'+target)
    out.mkdir()
    cmd=['javac','--release','17' if 'forge' in target and 'neo' not in target else '21','-encoding','UTF-8','-d',str(out)] + [str(p) for p in stub.rglob('*.java')] + [str(p) for p in java]
    p=subprocess.run(cmd,capture_output=True,text=True)
    if p.returncode:
      print('FAIL',target,'\n',p.stdout,p.stderr)
      sys.exit(1)
    print('PASS',target, len(java),'materialized Java files compile (synthetic stub validation only)')
