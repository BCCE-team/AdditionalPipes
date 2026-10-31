"""1.19.2 Forge regression: tab ownership and no unregistered intrusive holders.

Compiles the *actual* 1.19.2 AP registry and item subclass against small
Minecraft/Forge/BCCE contract stubs. No game runtime or remote Maven needed.
"""
from pathlib import Path
import importlib.util
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]
_spec = importlib.util.spec_from_file_location('materialize_sources', ROOT / 'scripts/materialize_sources.py')
materializer = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(materializer)


class Forge1192CreativeTabTests(unittest.TestCase):
    def test_only_1192_overrides_pipe_creative_category(self):
        with tempfile.TemporaryDirectory() as folder:
            for target in materializer.config()['targets'].split(','):
                effective = Path(folder) / target
                report = materializer.materialize(target, effective)
                pkg = effective / 'src/main/java/buildcraft/additionalpipes/platform'
                src = pkg / 'APItemPipeHolder.java'
                if target == '1.19.2-forge':
                    self.assertTrue(src.is_file())
                    self.assertIn('extends ItemPipeHolder', src.read_text())
                    self.assertIn('return Collections.singletonList(APContentRegistry.creativeTab());', src.read_text())
                    self.assertIn('tab == APContentRegistry.creativeTab()', src.read_text())
                    self.assertIn('tab == CreativeModeTab.TAB_SEARCH', src.read_text())
                    registry_source = (pkg / 'APContentRegistry.java').read_text()
                    self.assertIn('PipeRegistry.INSTANCE.setItemForPipe(', registry_source)
                    self.assertIn('PipeRegistry.INSTANCE.ensureRuntimeDefinition(', registry_source)
                    self.assertIn('AdditionalPipes.ensurePipeTypesRegistered();', registry_source)
                    # Forge 1.19.2 tracks every new Item as an intrusive holder.
                    # Do not create a stock item just to discard it for a custom-tab one.
                    self.assertNotIn('AdditionalPipes.createPipeItem(id)', registry_source)
                    self.assertNotIn('new ItemPipeHolder(', registry_source)
                    self.assertEqual(next(entry['source_layer'] for entry in report['files']
                                          if entry['path'].endswith('/APItemPipeHolder.java')),
                                     'version-src/1.19.2-forge')
                else:
                    self.assertFalse(src.exists(), f'Creative tab override leaked to {target}')

    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_ap_registry_creates_correctly_tabbed_registered_pipe_items(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)

            def write(relative, content):
                f = root / relative
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_text(textwrap.dedent(content), encoding='utf-8')

            prefix = 'buildcraft/additionalpipes/'
            origin = ROOT / 'version-src/1.19.2-forge/src/main/java/buildcraft/additionalpipes/platform'
            for filename in ('APContentRegistry.java', 'APItemPipeHolder.java'):
                write(prefix + 'platform/' + filename, (origin / filename).read_text())
            write(prefix + 'content/APContent.java',
                  (ROOT / 'source-shared/src/main/java/buildcraft/additionalpipes/content/APContent.java').read_text())

            write('net/minecraft/world/item/CreativeModeTab.java', '''
                package net.minecraft.world.item;
                public class CreativeModeTab {
                    public static final CreativeModeTab TAB_SEARCH = new CreativeModeTab("search");
                    public final String id;
                    public CreativeModeTab(String id) { this.id=id; }
                    public ItemStack makeIcon() { return null; }
                }
            ''')
            write('net/minecraft/core/NonNullList.java', '''
                package net.minecraft.core;
                public class NonNullList<T> extends java.util.ArrayList<T> { }
            ''')
            write('net/minecraft/world/item/ItemStack.java', '''
                package net.minecraft.world.item;
                public class ItemStack {
                    public final Item item;
                    public ItemStack(Item item) { this.item=item; }
                }
            ''')
            write('net/minecraft/world/item/Item.java', '''
                package net.minecraft.world.item;
                public class Item {
                    // The Forge intrusive-holder invariant uses reference identity, not equals().
                    private static final java.util.Set<Item> CREATED =
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                    private static final java.util.Set<Item> REGISTERED =
                        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                    public static void registered(Item item) { REGISTERED.add(item); }
                    public static void assertNoUnregisteredHolders() {
                        var leaked = new java.util.HashSet<>(CREATED);
                        leaked.removeAll(REGISTERED);
                        if (!leaked.isEmpty())
                            throw new IllegalStateException("Some intrusive holders were not added to registry: "
                                + leaked.size());
                        if (CREATED.size() != REGISTERED.size())
                            throw new AssertionError("Missing registered items");
                    }
                    public static class Properties {
                        public CreativeModeTab tab;
                        public Properties tab(CreativeModeTab tab) { this.tab=tab; return this; }
                    }
                    protected final CreativeModeTab tab;
                    public Item(Properties properties) {
                        tab=properties.tab;
                        CREATED.add(this);
                    }
                    public ItemStack getDefaultInstance() { return new ItemStack(this); }
                    public java.util.Collection<CreativeModeTab> getCreativeTabs() {
                        return java.util.Collections.singletonList(tab);
                    }
                    public void fillItemCategory(CreativeModeTab group,
                            net.minecraft.core.NonNullList<ItemStack> output) {
                        if (getCreativeTabs().contains(group)) output.add(getDefaultInstance());
                    }
                }
            ''')
            write('net/minecraft/world/item/BlockItem.java', '''
                package net.minecraft.world.item;
                import net.minecraft.world.level.block.Block;
                public class BlockItem extends Item {
                    public BlockItem(Block block, Properties props) { super(props); }
                }
            ''')
            write('net/minecraft/world/level/material/Material.java', '''
                package net.minecraft.world.level.material;
                public class Material { public static final Material GLASS = new Material(); }
            ''')
            write('net/minecraft/world/level/block/state/BlockBehaviour.java', '''
                package net.minecraft.world.level.block.state;
                import net.minecraft.world.level.material.Material;
                public class BlockBehaviour {
                    public static class Properties {
                        public static Properties of(Material material) { return new Properties(); }
                        public Properties strength(float value) { return this; }
                        public Properties noOcclusion() { return this; }
                    }
                }
            ''')
            write('net/minecraft/world/level/block/Block.java', '''
                package net.minecraft.world.level.block;
                public class Block {
                    public Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties p) { }
                }
            ''')
            write('net/minecraft/world/inventory/MenuType.java', '''
                package net.minecraft.world.inventory;
                public class MenuType<T> {
                    public interface MenuSupplier<T> { T create(int id, Object inventory); }
                    public MenuType(MenuSupplier<T> supplier) { }
                }
            ''')
            write('net/minecraftforge/eventbus/api/IEventBus.java', '''
                package net.minecraftforge.eventbus.api;
                public interface IEventBus { }
            ''')
            write('net/minecraftforge/registries/ForgeRegistries.java', '''
                package net.minecraftforge.registries;
                public final class ForgeRegistries {
                    public static final Object BLOCKS=new Object(), ITEMS=new Object(), MENU_TYPES=new Object();
                }
            ''')
            write('net/minecraftforge/registries/RegistryObject.java', '''
                package net.minecraftforge.registries;
                public class RegistryObject<T> {
                    private final java.util.function.Supplier<? extends T> provider;
                    private T value;
                    public RegistryObject(java.util.function.Supplier<? extends T> p) { provider=p; }
                    public T get() {
                        if (value == null) {
                            value=provider.get();
                            if (value instanceof net.minecraft.world.item.Item item)
                                net.minecraft.world.item.Item.registered(item);
                        }
                        return value;
                    }
                }
            ''')
            write('net/minecraftforge/registries/DeferredRegister.java', '''
                package net.minecraftforge.registries;
                public class DeferredRegister<T> {
                    public static <T> DeferredRegister<T> create(Object registry, String name) {
                        return new DeferredRegister<>();
                    }
                    public <I extends T> RegistryObject<I> register(String id,
                            java.util.function.Supplier<? extends I> factory) {
                        return new RegistryObject<>(factory);
                    }
                    public void register(net.minecraftforge.eventbus.api.IEventBus bus) { }
                }
            ''')
            write('buildcraft/transport/internal/pipe/PipeDefinition.java', '''
                package buildcraft.transport.internal.pipe;
                public class PipeDefinition {
                    public final String id;
                    public PipeDefinition(String id) { this.id=id; }
                }
            ''')
            write('net/minecraft/resources/ResourceLocation.java', '''
                package net.minecraft.resources;
                public final class ResourceLocation {
                    private final String ns, path;
                    public ResourceLocation(String ns, String path) { this.ns=ns; this.path=path; }
                    public String toString() { return ns + ":" + path; }
                }
            ''')
            write('buildcraft/api/v2/pipe/PipeType.java', '''
                package buildcraft.api.v2.pipe;
                public final class PipeType {
                    public final net.minecraft.resources.ResourceLocation id;
                    public PipeType(net.minecraft.resources.ResourceLocation id) { this.id=id; }
                }
            ''')
            write('buildcraft/api/v2/BuildCraftRegistries.java', '''
                package buildcraft.api.v2;
                public final class BuildCraftRegistries {
                    public static final Object PIPE_TYPES = new Object();
                }
            ''')
            write('buildcraft/api/v2/BuildCraftApi.java', '''
                package buildcraft.api.v2;
                public final class BuildCraftApi {
                    private static final PipeRegistry REGISTRY = new PipeRegistry();
                    public static PipeRegistry registry(Object key) { return REGISTRY; }
                    public static final class PipeRegistry {
                        public buildcraft.api.v2.pipe.PipeType get(net.minecraft.resources.ResourceLocation id) {
                            if (!buildcraft.additionalpipes.AdditionalPipes.registered)
                                throw new AssertionError("BCCE pipe type lookup before registration");
                            return new buildcraft.api.v2.pipe.PipeType(id);
                        }
                    }
                }
            ''')
            write('buildcraft/transport/item/ItemPipeHolder.java', '''
                package buildcraft.transport.item;
                import buildcraft.transport.internal.pipe.PipeDefinition;
                import net.minecraft.world.item.*;
                public class ItemPipeHolder extends Item {
                    public static final CreativeModeTab BCCE_TAB=new CreativeModeTab("buildcraft.pipes");
                    private final PipeDefinition definition;
                    public ItemPipeHolder(PipeDefinition definition) {
                        super(new Item.Properties().tab(BCCE_TAB));
                        this.definition=definition;
                    }
                    public PipeDefinition getDefinition() { return definition; }
                    @Override public java.util.Collection<CreativeModeTab> getCreativeTabs() {
                        return java.util.Collections.singletonList(BCCE_TAB);
                    }
                    @Override public void fillItemCategory(CreativeModeTab group,
                            net.minecraft.core.NonNullList<ItemStack> output) {
                        if (getCreativeTabs().contains(group)) output.add(getDefaultInstance());
                    }
                }
            ''')
            write('buildcraft/transport/pipe/PipeRegistry.java', '''
                package buildcraft.transport.pipe;
                import buildcraft.transport.item.ItemPipeHolder;
                import buildcraft.transport.internal.pipe.PipeDefinition;
                public enum PipeRegistry {
                    INSTANCE;
                    private final java.util.Map<PipeDefinition,ItemPipeHolder> items = new java.util.HashMap<>();
                    private final java.util.Map<String,PipeDefinition> definitions = new java.util.HashMap<>();
                    public PipeDefinition ensureRuntimeDefinition(buildcraft.api.v2.pipe.PipeType type) {
                        return definitions.computeIfAbsent(type.id.toString(), PipeDefinition::new);
                    }
                    public void setItemForPipe(PipeDefinition definition, ItemPipeHolder item) {
                        items.put(definition,item);
                    }
                    public ItemPipeHolder getItemForPipe(PipeDefinition definition) { return items.get(definition); }
                }
            ''')
            write(prefix + 'AdditionalPipes.java', '''
                package buildcraft.additionalpipes;
                import buildcraft.transport.internal.pipe.PipeDefinition;
                import buildcraft.transport.item.ItemPipeHolder;
                import buildcraft.transport.pipe.PipeRegistry;
                import net.minecraft.world.item.Item;
                public final class AdditionalPipes {
                    public static final String MOD_ID="additionalpipes";
                    public static boolean registered;
                    public static void ensurePipeTypesRegistered() { registered=true; }
                    // Old method intentionally allocates a second intrusive holder.
                    // The 1.19.2 registration path must never call it.
                    public static Item createPipeItem(String path) {
                        ensurePipeTypesRegistered();
                        PipeDefinition def = PipeRegistry.INSTANCE.ensureRuntimeDefinition(
                            new buildcraft.api.v2.pipe.PipeType(
                                new net.minecraft.resources.ResourceLocation(MOD_ID, path)));
                        ItemPipeHolder base = new ItemPipeHolder(def);
                        PipeRegistry.INSTANCE.setItemForPipe(def, base);
                        return base;
                    }
                }
            ''')
            for name, ids in (
                ('APSwitchPipes', ('pipe_items_switch','pipe_fluids_switch','pipe_power_switch','pipe_fe_switch')),
                ('APTeleportPipes', ('pipe_items_teleport','pipe_fluids_teleport','pipe_power_teleport','pipe_fe_teleport')),
                ('APInsertionPipes', ('pipe_items_adv_wood','pipe_items_priority')),
                ('APStoragePipes', ('pipe_items_closed','pipe_items_gravity_feed')),
                ('APUtilityPipes', ('pipe_items_distribution','pipe_fluids_water_pump')),
            ):
                method = {'APSwitchPipes':'isSwitch','APTeleportPipes':'isTeleport',
                          'APInsertionPipes':'isInsertion','APStoragePipes':'isStorage',
                          'APUtilityPipes':'isUtility'}[name]
                values = ','.join('"'+value+'"' for value in ids)
                write(prefix + 'pipes/' + name + '.java', f'''
                    package buildcraft.additionalpipes.pipes;
                    public class {name} {{
                        public static boolean {method}(String id) {{
                            return java.util.Set.of({values}).contains(id);
                        }}
                    }}
                ''')
            for name in ('AdvancedWoodMenu', 'ClosedPipeMenu'):
                write(prefix + 'pipes/' + name + '.java', f'''
                    package buildcraft.additionalpipes.pipes;
                    public class {name} {{ public {name}(int id,Object inventory) {{ }} }}
                ''')

            write('buildcraft/additionalpipes/platform/TestHarness.java', '''
                package buildcraft.additionalpipes.platform;
                import buildcraft.additionalpipes.content.APContent;
                import buildcraft.additionalpipes.pipes.*;
                import buildcraft.transport.item.ItemPipeHolder;
                import buildcraft.transport.pipe.PipeRegistry;
                import net.minecraft.world.item.*;
                import net.minecraft.core.NonNullList;
                import net.minecraftforge.registries.RegistryObject;
                import java.util.Map;
                public final class TestHarness {
                    @SuppressWarnings("unchecked")
                    public static void main(String[] args) throws Exception {
                        var field=APContentRegistry.class.getDeclaredField("ALL_ITEMS");
                        field.setAccessible(true);
                        Map<String, RegistryObject<Item>> entries =
                            (Map<String, RegistryObject<Item>>)field.get(null);
                        CreativeModeTab ap=APContentRegistry.creativeTab();
                        CreativeModeTab bc=ItemPipeHolder.BCCE_TAB;
                        int pipes=0;
                        for (APContent.Definition definition : APContent.BLOCKS) {
                            String id=definition.id();
                            Item item=entries.get(id).get();
                            boolean pipe=APSwitchPipes.isSwitch(id) || APTeleportPipes.isTeleport(id)
                                || APInsertionPipes.isInsertion(id) || APStoragePipes.isStorage(id)
                                || APUtilityPipes.isUtility(id);
                            if (pipe) {
                                pipes++;
                                if (!(item instanceof APItemPipeHolder))
                                    throw new AssertionError("Pipe not wrapped " + id);
                                if (!item.getCreativeTabs().contains(ap) || item.getCreativeTabs().contains(bc))
                                    throw new AssertionError("Wrong tab " + id);
                                var apItems=new NonNullList<ItemStack>();
                                item.fillItemCategory(ap, apItems);
                                if (apItems.size()!=1 || apItems.get(0).item!=item)
                                    throw new AssertionError("Missing AP tab item " + id);
                                var bcItems=new NonNullList<ItemStack>();
                                item.fillItemCategory(bc, bcItems);
                                if (!bcItems.isEmpty()) throw new AssertionError("Leaked to BCCE " + id);
                                var search=new NonNullList<ItemStack>();
                                item.fillItemCategory(CreativeModeTab.TAB_SEARCH, search);
                                if (search.size()!=1) throw new AssertionError("Not searchable " + id);
                                if (PipeRegistry.INSTANCE.getItemForPipe(
                                        ((APItemPipeHolder)item).getDefinition())!=item)
                                    throw new AssertionError("BCCE still holds unregistered instance " + id);
                            } else if (!item.getCreativeTabs().contains(ap)) {
                                throw new AssertionError("Other AP content lost from tab " + id);
                            }
                        }
                        if (pipes!=14) throw new AssertionError("Expected 14 registered BCCE pipe adapters: " + pipes);
                        if (!entries.get(APContent.DOG_DEAGGRAVATOR).get().getCreativeTabs().contains(ap))
                            throw new AssertionError("Dog item missing");
                        if (ap.makeIcon().item!=entries.get("pipe_items_teleport").get())
                            throw new AssertionError("Wrong AP icon");
                        Item.assertNoUnregisteredHolders();
                        System.out.println("PASS: 14 AP pipes, 0 BCCE tab leaks, 0 intrusive holders");
                    }
                }
            ''')
            javafiles = [str(p) for p in root.rglob('*.java')]
            result = subprocess.run(['javac', '--release', '17', '-d', str(root/'classes'), *javafiles],
                                    text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run(['java', '-cp', str(root/'classes'),
                                     'buildcraft.additionalpipes.platform.TestHarness'],
                                    text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn('PASS: 14 AP pipes', result.stdout)


if __name__ == '__main__':
    unittest.main()
