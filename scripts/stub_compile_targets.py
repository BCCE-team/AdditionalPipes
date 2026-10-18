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
'net.minecraft.resources.ResourceKey':'''public class ResourceKey<T>{public Object location(){return "minecraft:overworld";}public Object identifier(){return "minecraft:overworld";}public static <T> ResourceKey<T> create(ResourceKey<net.minecraft.core.Registry<T>> registry,ResourceLocation id){return new ResourceKey<>();}public static <T> ResourceKey<T> create(ResourceKey<net.minecraft.core.Registry<T>> registry,Identifier id){return new ResourceKey<>();}}''',
'net.minecraft.core.BlockPos':'''public class BlockPos{public int getX(){return 0;} public int getY(){return 0;} public int getZ(){return 0;} public BlockPos immutable(){return this;}}''',
'net.minecraft.world.level.ChunkPos':'''public class ChunkPos {public final int x,z;public ChunkPos(int x,int z){this.x=x;this.z=z;}public int x(){return x;}public int z(){return z;}}''',
'net.minecraft.world.entity.player.Player':'''public class Player{public net.minecraft.server.level.ServerLevel level(){return null;}public java.util.UUID getUUID(){return null;}public net.minecraft.core.BlockPos blockPosition(){return null;}}''',
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


STUBS.update({
'net.minecraft.resources.ResourceLocation':'''public class ResourceLocation{public final String id;public ResourceLocation(String id){this.id=id;}public ResourceLocation(String namespace,String path){this(namespace+":"+path);}public static ResourceLocation tryParse(String id){return new ResourceLocation(id);}public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n+":"+p);}public boolean equals(Object obj){return obj instanceof ResourceLocation r && id.equals(r.id);}public int hashCode(){return id.hashCode();}public String toString(){return id;}}''',
'net.minecraft.resources.Identifier':'''public class Identifier extends ResourceLocation{public Identifier(String id){super(id);}public static Identifier parse(String id){return new Identifier(id);}public static Identifier fromNamespaceAndPath(String n,String p){return new Identifier(n+":"+p);}}''',
'net.minecraft.core.Direction':'''public enum Direction{UP,DOWN,NORTH,SOUTH,EAST,WEST}''',
'net.minecraft.core.BlockPos':'''public class BlockPos{public int getX(){return 0;}public int getY(){return 0;}public int getZ(){return 0;}public BlockPos immutable(){return this;}public BlockPos relative(net.minecraft.core.Direction side){return this;}}''',
'net.minecraft.nbt.CompoundTag':'''public class CompoundTag{}''',
'net.minecraft.world.level.Level':'''public class Level{public boolean powered;public boolean hasNeighborSignal(net.minecraft.core.BlockPos pos){return powered;}}''',
'net.minecraft.world.level.block.state.BlockState':'''public class BlockState{}''',
'buildcraft.api.v2.registry.ApiRegistry':'''public class ApiRegistry<T>{public final java.util.Map<net.minecraft.resources.ResourceLocation,T> data=new java.util.HashMap<>();public T get(net.minecraft.resources.ResourceLocation id){return data.get(id);}public void register(net.minecraft.resources.ResourceLocation id,T object,java.util.function.Supplier<String> owner){data.put(id,object);}}''',
'buildcraft.api.v2.BuildCraftRegistries':'''public class BuildCraftRegistries{public static final buildcraft.api.v2.registry.ApiRegistry<buildcraft.api.v2.pipe.PipeType> PIPE_TYPES=new buildcraft.api.v2.registry.ApiRegistry<>();public static final buildcraft.api.v2.registry.ApiRegistry<buildcraft.api.v2.pipe.PipeComponentType<?>> PIPE_COMPONENT_TYPES=new buildcraft.api.v2.registry.ApiRegistry<>();}''',
'buildcraft.api.v2.BuildCraftServices':'''public class BuildCraftServices{public static final buildcraft.api.v2.pipe.PipeService PIPES=new buildcraft.api.v2.pipe.PipeService();}''',
'buildcraft.api.v2.BuildCraftApi':'''public final class BuildCraftApi{public static ApiRuntime runtime(){return null;}public static <T> buildcraft.api.v2.registry.ApiRegistry<T> registry(buildcraft.api.v2.registry.ApiRegistry<T> key){return key;}public static buildcraft.api.v2.pipe.PipeService service(buildcraft.api.v2.pipe.PipeService key){return key;}}''',
'buildcraft.api.v2.content.BuildCraftContentIds':'''public class BuildCraftContentIds {public static class Pipes {
 public static final net.minecraft.resources.Identifier GOLD_FLUID=net.minecraft.resources.Identifier.parse("buildcrafttransport:gold_fluid");
 public static final net.minecraft.resources.Identifier IRON_POWER=net.minecraft.resources.Identifier.parse("buildcrafttransport:iron_power");
 public static final net.minecraft.resources.Identifier GOLD_POWER=net.minecraft.resources.Identifier.parse("buildcrafttransport:gold_power");
 public static final net.minecraft.resources.Identifier IRON_FE=net.minecraft.resources.Identifier.parse("buildcrafttransport:iron_fe");
 public static final net.minecraft.resources.Identifier GOLD_FE=net.minecraft.resources.Identifier.parse("buildcrafttransport:gold_fe");
}}''',
'buildcraft.api.v2.energy.MjAmount':'''public record MjAmount(long microMj){public static MjAmount ofMicro(long amount){return new MjAmount(amount);}}''',
'buildcraft.api.v2.pipe.PipeComponent':'''public interface PipeComponent{net.minecraft.resources.ResourceLocation typeId();}''',
'buildcraft.api.v2.pipe.PipeView':'''public interface PipeView{net.minecraft.resources.ResourceLocation typeId();net.minecraft.core.BlockPos position();}''',
'buildcraft.api.v2.pipe.PipeConnectionComponent':'''public interface PipeConnectionComponent extends PipeComponent{PipeConnectionDecision connection(PipeConnectionContext ctx);}''',
'buildcraft.api.v2.pipe.PipeConnectionDecision':'''public enum PipeConnectionDecision{ALLOW,DENY,PASS}''',
'buildcraft.api.v2.pipe.PipeConnectionContext':'''public record PipeConnectionContext(net.minecraft.world.level.Level level,PipeView pipe,net.minecraft.core.Direction side,net.minecraft.world.level.block.state.BlockState neighbourState){}''',
'buildcraft.api.v2.pipe.PipeComponentType':'''public record PipeComponentType<C extends PipeComponent>(net.minecraft.resources.ResourceLocation id,java.util.function.Function<PipeView,C> factory,Object persistence){public C create(PipeView pipe){return factory.apply(pipe);}}''',
'buildcraft.api.v2.pipe.ItemTransportProfile':'''public record ItemTransportProfile(int maxItemsPerCycle,int routingWeight){}''',
'buildcraft.api.v2.pipe.FluidTransportProfile':'''public record FluidTransportProfile(long transferPerTick){}''',
'buildcraft.api.v2.pipe.ExternalEnergyTransportProfile':'''public record ExternalEnergyTransportProfile(long maxPerTick,boolean extractor){}''',
'buildcraft.api.v2.pipe.PowerTransportProfile':'''public record PowerTransportProfile(buildcraft.api.v2.energy.MjAmount maxPerTick,long resistancePerTick){public static PowerTransportProfile fromResistance(buildcraft.api.v2.energy.MjAmount max,long resistance,boolean extractor){return new PowerTransportProfile(max,resistance);}}''',
'buildcraft.api.v2.pipe.PipeType':'''public record PipeType(net.minecraft.resources.ResourceLocation id,ItemTransportProfile item,FluidTransportProfile fluid,PowerTransportProfile power,ExternalEnergyTransportProfile external,java.util.Set<net.minecraft.resources.ResourceLocation> components){
 public java.util.Optional<ItemTransportProfile> itemProfile(){return java.util.Optional.ofNullable(item);}
 public java.util.Optional<FluidTransportProfile> fluidProfile(){return java.util.Optional.ofNullable(fluid);}
 public java.util.Optional<PowerTransportProfile> mjProfile(){return java.util.Optional.ofNullable(power);}
 public java.util.Optional<ExternalEnergyTransportProfile> externalEnergyProfile(){return java.util.Optional.ofNullable(external);}
 public static Builder builder(net.minecraft.resources.ResourceLocation id){return new Builder(id);}
 public static class Builder{
  net.minecraft.resources.ResourceLocation id;
  ItemTransportProfile item;
  FluidTransportProfile fluid;
  PowerTransportProfile power;
  ExternalEnergyTransportProfile external;
  java.util.Set<net.minecraft.resources.ResourceLocation> components=new java.util.HashSet<>();
  public Builder(net.minecraft.resources.ResourceLocation id){this.id=id;}
  public Builder itemProfile(ItemTransportProfile v){item=v;return this;}
  public Builder fluidProfile(FluidTransportProfile v){fluid=v;return this;}
  public Builder mjProfile(PowerTransportProfile v){power=v;return this;}
  public Builder externalEnergyProfile(ExternalEnergyTransportProfile v){external=v;return this;}
  public Builder component(net.minecraft.resources.ResourceLocation id){components.add(id);return this;}
  public PipeType build(){return new PipeType(id,item,fluid,power,external,components);}
 }
}''',
'buildcraft.api.v2.pipe.PipeService':'''public class PipeService{public boolean allowPipe=true;public net.minecraft.world.item.Item createItem(net.minecraft.resources.ResourceLocation id){return new net.minecraft.world.item.Item(new net.minecraft.world.item.Item.Properties());}public java.util.Optional<PipeView> pipe(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos){return allowPipe?java.util.Optional.of(new PipeView(){public net.minecraft.resources.ResourceLocation typeId(){return null;}public net.minecraft.core.BlockPos position(){return pos;}}):java.util.Optional.empty();}}''',
'buildcraft.transport.internal.pipe.PipeFlowType':'''public class PipeFlowType{}''',
'buildcraft.transport.internal.pipe.PipeApi':'''public class PipeApi{
 public static final PipeFlowType flowItems=new PipeFlowType();
 public static final PipeFlowType flowFluids=new PipeFlowType();
 public static final PipeFlowType flowPower=new PipeFlowType();
 public static final PipeFlowType flowForgeEnergy=new PipeFlowType();
}''',
'buildcraft.transport.internal.pipe.IPipe':'''public interface IPipe{boolean isConnected(net.minecraft.core.Direction direction);}''',
'buildcraft.transport.internal.pipe.PipeBehaviour':'''public class PipeBehaviour{protected final IPipe pipe;public PipeBehaviour(IPipe pipe){this.pipe=pipe;}public PipeBehaviour(IPipe pipe,net.minecraft.nbt.CompoundTag tag){this.pipe=pipe;}public int getTextureIndex(net.minecraft.core.Direction side){return 0;}}''',
'buildcraft.transport.internal.pipe.PipeDefinition':'''public class PipeDefinition{public static final java.util.Map<String,PipeDefinitionBuilder> PIPE_DEFINITIONS=new java.util.HashMap<>();public static class PipeDefinitionBuilder{public net.minecraft.resources.ResourceLocation identifier;public PipeFlowType flow;public String[] textures;public PipeDefinitionBuilder texPrefixDirect(String texturePrefix){return this;}public PipeDefinitionBuilder texSuffixes(String... textures){this.textures=textures;return this;}public PipeDefinitionBuilder logic(java.util.function.Function<IPipe,PipeBehaviour> ctor,java.util.function.BiFunction<IPipe,net.minecraft.nbt.CompoundTag,PipeBehaviour> loader){return this;}public PipeDefinitionBuilder flow(PipeFlowType flow){this.flow=flow;return this;}public PipeDefinitionBuilder disableColouring(){return this;}public PipeDefinition define(){PIPE_DEFINITIONS.put(identifier.id,this);return new PipeDefinition();}}}''',
'net.minecraft.world.inventory.InventoryMenu':'''public class InventoryMenu{public static final net.minecraft.resources.ResourceLocation BLOCK_ATLAS=new net.minecraft.resources.ResourceLocation("minecraft:blocks");}''',
'net.minecraftforge.api.distmarker.Dist':'''public enum Dist{CLIENT,DEDICATED_SERVER}''',
'net.minecraftforge.eventbus.api.SubscribeEvent':'''@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)public @interface SubscribeEvent{}''',
'net.minecraftforge.client.event.TextureStitchEvent':'''public class TextureStitchEvent{public static class Atlas{public net.minecraft.resources.ResourceLocation location(){return new net.minecraft.resources.ResourceLocation("minecraft:blocks");}}public static class Pre{public Atlas getAtlas(){return new Atlas();}public void addSprite(net.minecraft.resources.ResourceLocation loc){}}}''',
'net.minecraftforge.fml.common.Mod':'''@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface Mod{String value();@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)@java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE)public @interface EventBusSubscriber{String modid();Bus bus();net.minecraftforge.api.distmarker.Dist[] value();public enum Bus{MOD,FORGE}}}''',
'buildcraft.lib.compat.minecraft.registry.BCRegistrationScope':'''public final class BCRegistrationScope{public static <T extends net.minecraft.world.item.Item> T item(net.minecraft.resources.ResourceKey<net.minecraft.world.item.Item> id,java.util.function.Supplier<T> factory){return factory.get();}}''',
})

STUBS['net.minecraft.world.level.material.Material']='public class Material{public static final Material GLASS=new Material();}'

STUBS.update({
'net.minecraft.resources.ResourceLocation':'''public class ResourceLocation{public final String id;public ResourceLocation(String id){this.id=id;}public ResourceLocation(String a,String b){this(a+":"+b);}public static ResourceLocation tryParse(String s){return new ResourceLocation(s);}public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}public String getPath(){return id.contains(":")?id.substring(id.indexOf(':')+1):id;}public boolean equals(Object o){return o instanceof ResourceLocation r && id.equals(r.id);}public int hashCode(){return id.hashCode();}public String toString(){return id;}}''',
'net.minecraft.server.level.ServerLevel':'''public class ServerLevel extends net.minecraft.world.level.Level{public net.minecraft.resources.ResourceKey<?> dimension(){return null;}public long getGameTime(){return 0;}}''',
'net.minecraft.server.MinecraftServer':'''public class MinecraftServer{public java.nio.file.Path getWorldPath(net.minecraft.world.level.storage.LevelResource path){return null;}public boolean isSameThread(){return true;}public java.lang.Iterable<net.minecraft.server.level.ServerLevel> getAllLevels(){return java.util.List.of();}public PlayerList getPlayerList(){return new PlayerList();}public static class PlayerList{public net.minecraft.server.level.ServerPlayer getPlayer(java.util.UUID id){return null;}}}''',
'net.minecraft.network.chat.Component':'''public class Component{public static Component translatable(String key){return new Component();}public static Component literal(String str){return new Component();}public String getString(){return "Player";}}''',
'net.minecraft.world.entity.player.Player':'''public class Player{public net.minecraft.server.level.ServerLevel level(){return null;}public java.util.UUID getUUID(){return null;}public net.minecraft.core.BlockPos blockPosition(){return null;}public net.minecraft.world.phys.Vec3 getEyePosition(){return new net.minecraft.world.phys.Vec3();}public net.minecraft.world.phys.Vec3 getLookAngle(){return new net.minecraft.world.phys.Vec3();}public net.minecraft.network.chat.Component getName(){return new net.minecraft.network.chat.Component();}public void sendSystemMessage(net.minecraft.network.chat.Component message){}}''',
'net.minecraft.world.level.Level':'''public class Level{public boolean powered;public boolean hasNeighborSignal(net.minecraft.core.BlockPos pos){return powered;}public boolean hasChunkAt(net.minecraft.core.BlockPos pos){return true;}public net.minecraft.world.phys.BlockHitResult clip(net.minecraft.world.level.ClipContext ctx){return new net.minecraft.world.phys.BlockHitResult();}}''',
'net.minecraft.world.phys.Vec3':'''public class Vec3{public Vec3 add(Vec3 other){return this;}public Vec3 scale(double scale){return this;}}''',
'net.minecraft.world.phys.HitResult':'''public class HitResult{public enum Type{BLOCK,MISS}public Type getType(){return Type.BLOCK;}}''',
'net.minecraft.world.phys.BlockHitResult':'''public class BlockHitResult extends HitResult{public net.minecraft.core.BlockPos getBlockPos(){return new net.minecraft.core.BlockPos();}}''',
'net.minecraft.world.level.ClipContext':'''public class ClipContext{public enum Block{OUTLINE}public enum Fluid{NONE}public ClipContext(net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end,Block block,Fluid fluid,net.minecraft.world.entity.player.Player player){}}''',
'net.minecraftforge.event.RegisterCommandsEvent':'''public class RegisterCommandsEvent{public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher(){return null;}}''',
'net.neoforged.neoforge.event.RegisterCommandsEvent':'''public class RegisterCommandsEvent{public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher(){return null;}}''',
'net.minecraft.commands.CommandSourceStack':'''public class CommandSourceStack{public net.minecraft.server.level.ServerPlayer getPlayerOrException() throws com.mojang.brigadier.exceptions.CommandSyntaxException{return null;}public net.minecraft.server.level.ServerLevel getLevel(){return null;}}''',
'net.minecraft.commands.Commands':'''public class Commands{public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literal(String text){return new com.mojang.brigadier.builder.LiteralArgumentBuilder<>();}public static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack,Integer> argument(String name,com.mojang.brigadier.arguments.IntegerArgumentType type){return new com.mojang.brigadier.builder.RequiredArgumentBuilder<>();}}''',
'com.mojang.brigadier.CommandDispatcher':'''public class CommandDispatcher<S>{public void register(com.mojang.brigadier.builder.LiteralArgumentBuilder<S> builder){}}''',
'com.mojang.brigadier.Command':'''@FunctionalInterface public interface Command<S>{int run(com.mojang.brigadier.context.CommandContext<S> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException;}''',
'com.mojang.brigadier.builder.ArgumentBuilder':'''public class ArgumentBuilder<S,T extends ArgumentBuilder<S,T>>{public T then(ArgumentBuilder<S,?> next){return (T)this;}public T executes(com.mojang.brigadier.Command<S> command){return (T)this;}}''',
'com.mojang.brigadier.builder.LiteralArgumentBuilder':'''public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S,LiteralArgumentBuilder<S>>{}''',
'com.mojang.brigadier.builder.RequiredArgumentBuilder':'''public class RequiredArgumentBuilder<S,T> extends ArgumentBuilder<S,RequiredArgumentBuilder<S,T>>{}''',
'com.mojang.brigadier.context.CommandContext':'''public class CommandContext<S>{public S getSource(){return null;}}''',
'com.mojang.brigadier.exceptions.CommandSyntaxException':'''public class CommandSyntaxException extends Exception{}''',
'com.mojang.brigadier.arguments.IntegerArgumentType':'''public class IntegerArgumentType{public static IntegerArgumentType integer(int low,int high){return new IntegerArgumentType();}public static int getInteger(com.mojang.brigadier.context.CommandContext<?> ctx,String key){return 0;}}''',
'buildcraft.api.v2.OperationMode':'''public enum OperationMode{EXECUTE,SIMULATE}''',
'buildcraft.api.v2.pipe.PipeMedium':'''public enum PipeMedium{ITEM,FLUID,MJ,EXTERNAL_ENERGY}''',
'buildcraft.api.v2.permission.AutomationActor':'''public class AutomationActor{public static AutomationActor unknown(){return new AutomationActor();}public static AutomationActor machineOwner(java.util.UUID id,String name,net.minecraft.resources.ResourceLocation source){return new AutomationActor();}public java.util.Optional<java.util.UUID> playerId(){return java.util.Optional.empty();}public java.util.Optional<String> playerName(){return java.util.Optional.empty();}}''',
'buildcraft.api.v2.energy.MjAmount':'''public record MjAmount(long microMj){public static final long MICRO_MJ_PER_MJ=1000000L;public static MjAmount ofMicro(long amount){return new MjAmount(amount);}}''',
'buildcraft.api.v2.fluid.FluidAmount':'''public record FluidAmount(long milliBuckets){public static FluidAmount of(long amount){return new FluidAmount(amount);}}''',
'buildcraft.api.v2.fluid.FluidMatcher':'''public interface FluidMatcher{static FluidMatcher any(){return null;}}''',
'buildcraft.api.v2.fluid.FluidVolume':'''public record FluidVolume(FluidAmount amount){}''',
'buildcraft.api.v2.persistence.ApiCodec':'''public interface ApiCodec<T,P>{CodecResult<P> encode(T v);CodecResult<T> decode(P d);}''',
'buildcraft.api.v2.persistence.CodecResult':'''public class CodecResult<T>{private final T value;private final String error;private CodecResult(T value,String error){this.value=value;this.error=error;}public static <T> CodecResult<T> success(T v){return new CodecResult<>(v,null);}public static <T> CodecResult<T> failure(String reason){return new CodecResult<>(null,reason);}public T valueOrThrow(){if(error!=null)throw new IllegalStateException(error);return value;}}''',
'buildcraft.api.v2.persistence.OpaqueData':'''public record OpaqueData(net.minecraft.resources.ResourceLocation format,byte[] bytes){public int size(){return bytes.length;}}''',
'buildcraft.api.v2.persistence.PersistentType':'''public class PersistentType<T,P>{public static <T,P> Builder<T,P> builder(net.minecraft.resources.ResourceLocation id,int schema,ApiCodec<T,P> codec){return new Builder<>();}public static class Builder<T,P>{public PersistentType<T,P> build(){return new PersistentType<>();}}}''',
'buildcraft.api.v2.pipe.PipeComponentLifecycle':'''public interface PipeComponentLifecycle extends PipeComponent{default void attached(PipeView view){}default void activated(){}default void suspended(){}default void removed(){}default void invalidated(){}}''',
'buildcraft.api.v2.pipe.PipeTickComponent':'''public interface PipeTickComponent extends PipeComponent{void tick(PipeMutationContext ctx);}''',
'buildcraft.api.v2.pipe.PipeMutationContext':'''public interface PipeMutationContext extends PipeView{void markChanged();}''',
'buildcraft.api.v2.pipe.PipeActivationComponent':'''public interface PipeActivationComponent extends PipeComponent{PipeActivationResult activate(PipeActivationContext ctx);}''',
'buildcraft.api.v2.pipe.PipeActivationResult':'''public enum PipeActivationResult{PASS,SUCCESS,DENIED,FAILED}''',
'buildcraft.api.v2.pipe.PipeActivationContext':'''public record PipeActivationContext(PipeMutationContext pipe,net.minecraft.core.Direction side,net.minecraft.world.item.ItemStack heldStack,buildcraft.api.v2.permission.AutomationActor actor,buildcraft.api.v2.OperationMode mode){}''',
'buildcraft.api.v2.pipe.ItemTransitComponent':'''public interface ItemTransitComponent extends PipeComponent{void onTransit(ItemTransitContext ctx);}''',
'buildcraft.api.v2.pipe.ItemTransitPhase':'''public enum ItemTransitPhase{ENTER,CENTER,TRANSFERRED,ROUTING,EXIT}''',
'buildcraft.api.v2.pipe.ItemTransitPayload':'''public class ItemTransitPayload{public java.util.Optional<byte[]> get(String key){return java.util.Optional.empty();}public ItemTransitPayload with(String key,byte[] value){return this;}public ItemTransitPayload without(String key){return this;}}''',
'buildcraft.api.v2.pipe.ItemTransitTransfer':'''public record ItemTransitTransfer(int accepted){public boolean succeeded(){return accepted>0;}}''',
'buildcraft.api.v2.pipe.ItemTransitContext':'''public interface ItemTransitContext{ItemTransitPhase phase();ItemTransitPayload payload();boolean setPayload(ItemTransitPayload data);ItemTransitTransfer transfer(net.minecraft.world.level.Level world,net.minecraft.core.BlockPos pos,net.minecraft.core.Direction side,buildcraft.api.v2.OperationMode mode);}''',
'buildcraft.api.v2.pipe.PipeResourceTickComponent':'''public interface PipeResourceTickComponent extends PipeComponent{void onResourceTick(PipeResourceTickContext ctx);}''',
'buildcraft.api.v2.pipe.PipeResourceTickContext':'''public interface PipeResourceTickContext{PipeView pipe();net.minecraft.world.level.Level world();PipeMedium medium();PipeResourceService transfers();}''',
'buildcraft.api.v2.pipe.PipeFluidTransferResult':'''public record PipeFluidTransferResult(buildcraft.api.v2.fluid.FluidVolume transferred){}''',
'buildcraft.api.v2.pipe.PipeMjTransferResult':'''public record PipeMjTransferResult(buildcraft.api.v2.energy.MjAmount transferred){}''',
'buildcraft.api.v2.pipe.PipeFeTransferResult':'''public record PipeFeTransferResult(long transferred){}''',
'buildcraft.api.v2.pipe.PipeResourceService':'''public interface PipeResourceService{PipeFluidTransferResult transferFluid(net.minecraft.world.level.Level s,net.minecraft.core.BlockPos sp,net.minecraft.core.Direction a,net.minecraft.world.level.Level t,net.minecraft.core.BlockPos tp,net.minecraft.core.Direction b,buildcraft.api.v2.fluid.FluidMatcher match,buildcraft.api.v2.fluid.FluidAmount limit,buildcraft.api.v2.permission.AutomationActor actor,buildcraft.api.v2.OperationMode mode);PipeMjTransferResult transferMj(net.minecraft.world.level.Level s,net.minecraft.core.BlockPos sp,net.minecraft.core.Direction a,net.minecraft.world.level.Level t,net.minecraft.core.BlockPos tp,net.minecraft.core.Direction b,buildcraft.api.v2.energy.MjAmount limit,buildcraft.api.v2.permission.AutomationActor actor,buildcraft.api.v2.OperationMode mode);PipeFeTransferResult transferExternalEnergy(net.minecraft.world.level.Level s,net.minecraft.core.BlockPos sp,net.minecraft.core.Direction a,net.minecraft.world.level.Level t,net.minecraft.core.BlockPos tp,net.minecraft.core.Direction b,long limit,buildcraft.api.v2.permission.AutomationActor actor,buildcraft.api.v2.OperationMode mode);}''',
'buildcraft.transport.pipe.behaviour.PipeBehaviourStructure':'''public class PipeBehaviourStructure extends buildcraft.transport.internal.pipe.PipeBehaviour{public PipeBehaviourStructure(buildcraft.transport.internal.pipe.IPipe pipe){super(pipe);}public PipeBehaviourStructure(buildcraft.transport.internal.pipe.IPipe pipe,net.minecraft.nbt.CompoundTag tag){super(pipe,tag);}}''',
'net.minecraft.core.Direction':'''public enum Direction{UP,DOWN,NORTH,SOUTH,EAST,WEST;public Direction getOpposite(){return this;}}''',
'buildcraft.api.v2.pipe.PipeView':'''public interface PipeView{net.minecraft.resources.ResourceLocation typeId();net.minecraft.core.BlockPos position();default java.util.Set<net.minecraft.core.Direction> connectedSides(){return java.util.Set.of();}default boolean isClientSide(){return false;}default java.util.Optional<buildcraft.api.v2.pipe.PipeComponent> component(net.minecraft.resources.ResourceLocation id){return java.util.Optional.empty();}}''',
})

STUBS.update({
    'net.minecraft.client.gui.screens.Screen':'''public class Screen{protected int width=400,height=300;public Screen(net.minecraft.network.chat.Component title){}protected void init(){}protected <T> T addRenderableWidget(T widget){return widget;}public boolean isPauseScreen(){return false;}}''',
    'net.minecraft.client.gui.components.Button':'''public class Button{public boolean active=true,visible=true;public Button(int x,int y,int w,int h,net.minecraft.network.chat.Component message,OnPress press){}public void setMessage(net.minecraft.network.chat.Component text){}public static Builder builder(net.minecraft.network.chat.Component text,OnPress press){return new Builder();}public interface OnPress{void onPress(Button button);}public static class Builder{public Builder bounds(int x,int y,int w,int h){return this;}public Button build(){return new Button(0,0,0,0,null,null);}}}''',
    'buildcraft.transport.internal.pipe.IItemPipe':'''public interface IItemPipe{}''',
    'net.minecraft.world.item.ItemStack':'''public class ItemStack{public ItemStack(net.minecraft.world.item.Item item){}public boolean isEmpty(){return false;}public net.minecraft.world.item.Item getItem(){return null;}}''',
    'net.minecraft.client.Minecraft':'''public class Minecraft{public net.minecraft.client.gui.screens.Screen screen;public static Minecraft getInstance(){return new Minecraft();}public void setScreen(net.minecraft.client.gui.screens.Screen screen){this.screen=screen;}}''',
    'net.minecraft.client.gui.Gui':'''public class Gui{public void setScreen(net.minecraft.client.gui.screens.Screen screen){}public net.minecraft.client.gui.screens.Screen screen(){return null;}}''',
    'net.minecraft.core.BlockPos':'''public class BlockPos{public BlockPos(){}public BlockPos(int x,int y,int z){}public int getX(){return 0;}public int getY(){return 0;}public int getZ(){return 0;}public BlockPos immutable(){return this;}public BlockPos relative(net.minecraft.core.Direction side){return this;}}''',
    'net.minecraft.network.chat.Component':'''public class Component{public static Component translatable(String key){return new Component();}public static Component literal(String str){return new Component();}public static Component empty(){return new Component();}public String getString(){return "Player";}}''',
})

STUBS.update({
    'com.mojang.authlib.GameProfile':'''public class GameProfile{public GameProfile(java.util.UUID id,String name){}}''',
    'com.mojang.blaze3d.vertex.PoseStack':'''public class PoseStack{}''',
    'com.mojang.blaze3d.systems.RenderSystem':'''public class RenderSystem{public static void setShaderColor(float r,float g,float b,float a){}}''',
    'net.minecraft.client.gui.Font':'''public class Font{public int width(String value){return value.length()*6;}public String plainSubstrByWidth(String value,int width){return value;}}''',
    'buildcraft.lib.BCLibSprites':'''public final class BCLibSprites{public static final Object ENGINE_ACTIVE=new Object(),WARNING_MINOR=new Object();}''',
    'buildcraft.lib.compat.minecraft.gui.BCGuiInput':'''public final class BCGuiInput{public static int legacyButton(int button){return button;}}''',
    'buildcraft.lib.misc.SpriteUtil':'''public class SpriteUtil{public static Object getFaceSprite(com.mojang.authlib.GameProfile id){return null;}public static Object getFaceOverlaySprite(com.mojang.authlib.GameProfile id){return null;}}''',
})

with TemporaryDirectory(prefix='ap-all-source-stub-') as tmp:
  tmp=Path(tmp)
  stub=tmp/'stubs'
  for fqcn,source in STUBS.items():
    package,name=fqcn.rsplit('.',1)
    path=stub/(fqcn.replace('.','/')+'.java')
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text('package '+package+';\n'+source,encoding='utf-8')
  for target in (__import__('os').environ['AP_STUB_TARGETS'].split(',') if 'AP_STUB_TARGETS' in __import__('os').environ else ['1.19.2-forge','1.20.1-forge','1.21.1-neoforge','1.21.11-neoforge','26.1.2-neoforge','26.2-neoforge','26.3-neoforge']):
    dest=tmp/target
    materialize(target,dest)
    java=list((dest/'src/main/java').rglob('*.java'))
    out=tmp/('compiled-'+target)
    out.mkdir()
    stub_sources = list(stub.rglob('*.java'))
    if target in ('26.2-neoforge', '26.3-neoforge'):
      fqcn = 'net.minecraft.client.Minecraft'
      rel = Path(fqcn.replace('.', '/') + '.java')
      special = tmp / ('specific-' + target) / rel
      special.parent.mkdir(parents=True, exist_ok=True)
      special.write_text('package net.minecraft.client; public class Minecraft {public final net.minecraft.client.gui.Gui gui = new net.minecraft.client.gui.Gui(); public static Minecraft getInstance(){return new Minecraft();}}')
      stub_sources = [p for p in stub_sources if p != stub / rel]
      stub_sources.append(special)
    if target.startswith('26.'):
      fqcn = 'net.minecraft.world.level.ChunkPos'
      rel = Path(fqcn.replace('.', '/') + '.java')
      special = tmp / ('specific-' + target) / rel
      special.parent.mkdir(parents=True, exist_ok=True)
      special.write_text('package net.minecraft.world.level; public class ChunkPos {private final int x,z; public ChunkPos(int x,int z){this.x=x;this.z=z;}public int x(){return x;}public int z(){return z;}}')
      stub_sources = [p for p in stub_sources if p != stub / rel]
      stub_sources.append(special)
    if target in ('1.21.11-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.3-neoforge'):
      fqcn = 'net.minecraft.resources.ResourceKey'
      rel = Path(fqcn.replace('.', '/') + '.java')
      special = tmp / ('specific-' + target) / rel
      special.parent.mkdir(parents=True, exist_ok=True)
      special.write_text('package net.minecraft.resources; public class ResourceKey<T> {public Object identifier(){return "minecraft:overworld";} public static <T> ResourceKey<T> create(ResourceKey<net.minecraft.core.Registry<T>> registry,ResourceLocation id){return new ResourceKey<>();} public static <T> ResourceKey<T> create(ResourceKey<net.minecraft.core.Registry<T>> registry,Identifier id){return new ResourceKey<>();}}')
      stub_sources = [p for p in stub_sources if p != stub / rel]
      stub_sources.append(special)
    if target == '1.19.2-forge':
      replacement = {
        'net.minecraft.world.entity.player.Player': STUBS['net.minecraft.world.entity.player.Player'].replace('public net.minecraft.server.level.ServerLevel level(){return null;}', ''),
        'net.minecraft.server.level.ServerPlayer': 'public class ServerPlayer extends net.minecraft.world.entity.player.Player { public ServerLevel getLevel(){ return null; } }',
        'net.minecraft.world.level.block.state.BlockBehaviour': 'public class BlockBehaviour { public static class Properties { public static Properties of(net.minecraft.world.level.material.Material material){ return new Properties(); } public Properties strength(float v){ return this; } public Properties noOcclusion(){ return this; } } }'
      }
      special=tmp/'specific-1192'
      names=[]
      for fqcn,src in replacement.items():
        rel=Path(fqcn.replace('.','/')+'.java')
        path=special/rel
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text('package '+fqcn.rsplit('.',1)[0]+';'+src)
        names.append(str(rel))
        stub_sources.append(path)
      stub_sources=[p for p in stub_sources if p.is_relative_to(special) or str(p.relative_to(stub)) not in names]
    def gui_stub(fqcn, code):
      rel=Path(fqcn.replace('.', '/')+'.java')
      special=tmp/('specific-gui-'+target)/rel
      special.parent.mkdir(parents=True,exist_ok=True)
      special.write_text('package '+fqcn.rsplit('.',1)[0]+';\n'+code)
      stub_sources[:]=[p for p in stub_sources if p != stub / rel and not p.name == rel.name or p == special]
      stub_sources.append(special)

    if target.startswith('26.'):
      graphics_type='net.minecraft.client.gui.GuiGraphicsExtractor'
    elif target=='1.19.2-forge':
      graphics_type='com.mojang.blaze3d.vertex.PoseStack'
    else:
      graphics_type='net.minecraft.client.gui.GuiGraphics'
    if graphics_type.endswith('GuiGraphics'):
      gui_stub(graphics_type,'public class GuiGraphics{public void drawString(net.minecraft.client.gui.Font f,String s,int x,int y,int c,boolean shadow){}}')
    elif graphics_type.endswith('GuiGraphicsExtractor'):
      gui_stub(graphics_type,'public class GuiGraphicsExtractor{public void text(net.minecraft.client.gui.Font f,String s,int x,int y,int c,boolean shadow){}}')

    if target == '1.21.11-neoforge' or target.startswith('26.'):
      gui_stub('net.minecraft.client.input.MouseButtonEvent','public record MouseButtonEvent(double x,double y,int button){}')

    graphics_simple=graphics_type.rsplit('.',1)[1]
    gui_stub('buildcraft.lib.gui.GuiIcon',
      'public class GuiIcon{public GuiIcon(Object texture,int u,int v,int w,int h){}'
      'public void drawAt('+graphics_type+' graphics,double x,double y){}'
      'public static void draw('+graphics_type+' graphics,Object sprite,double x,double y,double xx,double yy){}}')
    amount=',int color' if target not in ('1.19.2-forge','1.20.1-forge','1.21.1-neoforge') else ''
    gui_stub('buildcraft.lib.gui.ledger.Ledger_Neptune',
      'public class Ledger_Neptune{public static final Sprite9 SPRITE_SPLIT_POS=new Sprite9(),SPRITE_SPLIT_NEG=new Sprite9();'
      'public static class Sprite9{public void draw('+graphics_type+' graphics,double x,double y,double w,double h'+amount+'){} }}')

    if target=='1.19.2-forge':
      render='public void render(com.mojang.blaze3d.vertex.PoseStack graphics,int x,int y,float t){}'
      bg='public void renderBackground(com.mojang.blaze3d.vertex.PoseStack graphics){}'
      text='public static void drawCenteredString(com.mojang.blaze3d.vertex.PoseStack g,net.minecraft.client.gui.Font f,String s,int x,int y,int c){}public static void drawString(com.mojang.blaze3d.vertex.PoseStack g,net.minecraft.client.gui.Font f,String s,int x,int y,int c){}'
      click='public boolean mouseClicked(double x,double y,int button){return false;}'
    elif target.startswith('26.'):
      render='public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}'
      bg='public void extractBackground(net.minecraft.client.gui.GuiGraphicsExtractor g,int x,int y,float t){}'
      text=''
      click='public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick){return false;}'
    else:
      render='public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float t){}'
      if target=='1.20.1-forge':
        bg='public void renderBackground(net.minecraft.client.gui.GuiGraphics g){}'
      else:
        bg='public void renderBackground(net.minecraft.client.gui.GuiGraphics g,int x,int y,float t){}'
      text=''
      click=('public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick){return false;}'
             if target=='1.21.11-neoforge' else 'public boolean mouseClicked(double x,double y,int button){return false;}')
    gui_stub('net.minecraft.client.gui.screens.Screen',
       'public class Screen{protected int width=400,height=300;protected net.minecraft.client.gui.Font font=new net.minecraft.client.gui.Font();'
       'public Screen(net.minecraft.network.chat.Component title){}'
       'protected void init(){} protected <T> T addRenderableWidget(T widget){return widget;}'
       'public boolean isPauseScreen(){return false;} public void tick(){}'
       +render+bg+text+click+'}')

    cmd=['javac','--release','17' if 'forge' in target and 'neo' not in target else '21','-encoding','UTF-8','-d',str(out)] + [str(p) for p in stub_sources] + [str(p) for p in java] + [str(root/'scripts/tests/java/SwitchPipeRegression.java'), str(root/'scripts/tests/java/TeleportPipeRegression.java')]
    p=subprocess.run(cmd,capture_output=True,text=True)
    if p.returncode:
      print('FAIL',target,'\n',p.stdout,p.stderr)
      sys.exit(1)
    run=subprocess.run(['java','-cp',str(out),'SwitchPipeRegression'],capture_output=True,text=True)
    if run.returncode:
      print('FAIL switch contract',target,run.stdout,run.stderr)
      sys.exit(1)
    teleport_run=subprocess.run(['java','-cp',str(out),'TeleportPipeRegression'],capture_output=True,text=True)
    if teleport_run.returncode:
      print('FAIL teleport contract',target,teleport_run.stdout,teleport_run.stderr)
      sys.exit(1)
    print('PASS',target,len(java),'materialized Java files compile; '+run.stdout.strip()+'; '+teleport_run.stdout.strip()+' (synthetic validation only)')
