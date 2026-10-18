package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.content.BuildCraftContentIds;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.pipe.ItemTransportProfile;
import buildcraft.api.v2.pipe.PipeComponentType;
import buildcraft.api.v2.pipe.PipeConnectionComponent;
import buildcraft.api.v2.pipe.PipeConnectionContext;
import buildcraft.api.v2.pipe.PipeConnectionDecision;
import buildcraft.api.v2.pipe.ExternalEnergyTransportProfile;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.api.v2.pipe.PowerTransportProfile;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition.PipeDefinitionBuilder;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class APSwitchPipes {
    public static final String ITEM = "pipe_items_switch";
    public static final String FLUID = "pipe_fluids_switch";
    public static final String POWER = "pipe_power_switch";
    public static final String FE = "pipe_fe_switch";
    private static final ResourceLocation ITEM_ID = id(ITEM);
    private static final ResourceLocation FLUID_ID = id(FLUID);
    private static final ResourceLocation POWER_ID = id(POWER);
    private static final ResourceLocation FE_ID = id(FE);
    private static final ResourceLocation CONNECTION_ID = id("switch_connection");
    private static final Set<String> SWITCH_IDS = Set.of(ITEM, FLUID, POWER, FE);
    private static boolean registered;

    private APSwitchPipes() { }

    public static boolean isSwitch(String id) {
        return SWITCH_IDS.contains(id);
    }

    public static synchronized void register() {
        if (registered) return;

        var components = BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES);
        components.register(CONNECTION_ID,
            new PipeComponentType<>(CONNECTION_ID, pipe -> new SwitchConnection(pipe.typeId().equals(FLUID_ID)), null),
            () -> AdditionalPipes.MOD_ID);

        var types = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES);
        PipeType goldFluid = requireType(BuildCraftContentIds.Pipes.GOLD_FLUID);
        PowerTransportProfile iron = requireType(BuildCraftContentIds.Pipes.IRON_POWER).mjProfile().orElseThrow();
        PowerTransportProfile gold = requireType(BuildCraftContentIds.Pipes.GOLD_POWER).mjProfile().orElseThrow();
        long capacity = (iron.maxPerTick().microMj() + gold.maxPerTick().microMj()) / 2L;
        long resistance = (iron.resistancePerTick() + gold.resistancePerTick()) / 2L;
        var ironFe = requireType(BuildCraftContentIds.Pipes.IRON_FE).externalEnergyProfile().orElseThrow();
        var goldFe = requireType(BuildCraftContentIds.Pipes.GOLD_FE).externalEnergyProfile().orElseThrow();
        long feCapacity = (ironFe.maxPerTick() + goldFe.maxPerTick()) / 2L;

        types.register(ITEM_ID, PipeType.builder(ITEM_ID)
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(CONNECTION_ID).build(), () -> AdditionalPipes.MOD_ID);
        types.register(FLUID_ID, PipeType.builder(FLUID_ID)
            .fluidProfile(goldFluid.fluidProfile().orElseThrow())
            .component(CONNECTION_ID).build(), () -> AdditionalPipes.MOD_ID);
        types.register(POWER_ID, PipeType.builder(POWER_ID)
            .mjProfile(PowerTransportProfile.fromResistance(MjAmount.ofMicro(capacity), resistance, false))
            .component(CONNECTION_ID).build(), () -> AdditionalPipes.MOD_ID);
        types.register(FE_ID, PipeType.builder(FE_ID)
            .externalEnergyProfile(new ExternalEnergyTransportProfile(feCapacity, false))
            .component(CONNECTION_ID).build(), () -> AdditionalPipes.MOD_ID);

        define(ITEM_ID, PipeApi.flowItems, "20", "21");
        define(FLUID_ID, PipeApi.flowFluids, "22", "23");
        define(POWER_ID, PipeApi.flowPower, "16", "17");
        define(FE_ID, PipeApi.flowForgeEnergy, "41", "42");
        registered = true;
    }

    public static Item createItem(String path) {
        if (!isSwitch(path)) throw new IllegalArgumentException("Unknown switch pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }

    private static PipeType requireType(ResourceLocation id) {
        PipeType type = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES).get(id);
        if (type == null) throw new IllegalStateException("Missing BCCE pipe type: " + id);
        return type;
    }

    private static void define(ResourceLocation id, buildcraft.transport.internal.pipe.PipeFlowType flow,
                               String connectedTexture, String closedTexture) {
        PipeDefinitionBuilder builder = new PipeDefinitionBuilder();
        builder.identifier = id;
        builder.texPrefixDirect("additionalpipes:block/")
            .texSuffixes(connectedTexture, closedTexture)
            .logic(SwitchPipeBehaviour::new, SwitchPipeBehaviour::new)
            .flow(flow)
            .disableColouring()
            .define();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(AdditionalPipes.MOD_ID + ":" + path);
    }

    private static final class SwitchConnection implements PipeConnectionComponent {
        private final boolean onlyPipeNeighbours;

        private SwitchConnection(boolean onlyPipeNeighbours) {
            this.onlyPipeNeighbours = onlyPipeNeighbours;
        }

        @Override
        public ResourceLocation typeId() {
            return CONNECTION_ID;
        }

        @Override
        public PipeConnectionDecision connection(PipeConnectionContext context) {
            if (context.level().hasNeighborSignal(context.pipe().position())) {
                return PipeConnectionDecision.DENY;
            }
            if (onlyPipeNeighbours && BuildCraftApi.service(BuildCraftServices.PIPES)
                .pipe(context.level(), context.pipe().position().relative(context.side())).isEmpty()) {
                return PipeConnectionDecision.DENY;
            }
            return PipeConnectionDecision.PASS;
        }
    }
}
