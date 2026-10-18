package buildcraft.additionalpipes.content;

import java.util.List;

public final class APContent {
    public record Definition(String id, String name) { }

    public static final List<Definition> BLOCKS = List.of(
        new Definition("pipe_items_teleport", "Item Teleport Pipe"),
        new Definition("pipe_fluids_teleport", "Fluid Teleport Pipe"),
        new Definition("pipe_power_teleport", "Power Teleport Pipe"),
        new Definition("pipe_fe_teleport", "FE Teleport Pipe"),
        new Definition("pipe_logistics_teleport", "Logistics Teleport Pipe [WIP]"),
        new Definition("pipe_items_jeweled", "Jeweled Transport Pipe"),
        new Definition("pipe_items_distribution", "Distribution Transport Pipe"),
        new Definition("pipe_items_adv_insertion", "[Deprecated] Insertion Transport Pipe"),
        new Definition("pipe_items_addition", "Addition Transport Pipe"),
        new Definition("pipe_items_priority", "Priority Insertion Pipe"),
        new Definition("pipe_items_adv_wood", "Adv. Wooden Transport Pipe"),
        new Definition("pipe_items_gravity_feed", "Gravity Feed Transport Pipe"),
        new Definition("pipe_items_closed", "Closed Transport Pipe"),
        new Definition("pipe_items_switch", "Switch Transport Pipe"),
        new Definition("pipe_power_switch", "Switch Kinesis Pipe"),
        new Definition("pipe_fe_switch", "Switch FE Pipe"),
        new Definition("pipe_fluids_switch", "Switch Fluid Pipe"),
        new Definition("pipe_fluids_water_pump", "Water Pump Pipe"),
        new Definition("pipe_fluids_obsidian", "Obsidian Fluid Pipe"),
        new Definition("teleport_tether", "Teleport Tether")
    );

    public static final String DOG_DEAGGRAVATOR = "dog_deaggravator";

    private APContent() { }
}
