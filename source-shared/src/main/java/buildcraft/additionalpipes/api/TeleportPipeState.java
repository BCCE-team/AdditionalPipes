package buildcraft.additionalpipes.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent classic pipe settings; legacy keys are retained for future NBT migration.
 * NBT/Data Components bindings intentionally remain with Minecraft-version adapters.
 */
public record TeleportPipeState(int frequency, byte state, boolean isPublic, UUID ownerUUID, String ownerName) {
    public TeleportPipeState {
        if (frequency < 0 || state < 0 || state > 3) throw new IllegalArgumentException("Invalid teleport state");
        Objects.requireNonNull(ownerName, "ownerName");
        if (ownerName.length() > 64) throw new IllegalArgumentException("Owner name too long");
    }

    public static TeleportPipeState defaults() { return new TeleportPipeState(0, (byte) 1, false, null, ""); }

    public Map<String, String> toLegacyFields() {
        Map<String, String> values = new HashMap<>();
        values.put("freq", Integer.toString(frequency));
        values.put("state", Byte.toString(state));
        values.put("isPublic", Boolean.toString(isPublic));
        if (ownerUUID != null) {
            values.put("ownerUUID", ownerUUID.toString());
            values.put("ownerName", ownerName);
        }
        return Map.copyOf(values);
    }

    public static TeleportPipeState fromLegacyFields(Map<String, String> values) {
        Objects.requireNonNull(values);
        int frequency = number(values.get("freq"), 0);
        int state = number(values.get("state"), 1);
        UUID owner = null;
        try { if (values.containsKey("ownerUUID")) owner = UUID.fromString(values.get("ownerUUID")); }
        catch (IllegalArgumentException ignored) { }
        String name = values.getOrDefault("ownerName", "");
        if (name.length() > 64) name = name.substring(0, 64);
        return new TeleportPipeState(Math.max(0, frequency), (byte) Math.max(0, Math.min(3, state)),
            Boolean.parseBoolean(values.getOrDefault("isPublic", "false")), owner, name);
    }
    private static int number(String raw, int fallback) {
        if (raw == null) return fallback;
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException ex) { return fallback; }
    }
}
