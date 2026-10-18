package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.ITeleportPipe;
import buildcraft.additionalpipes.api.PipeType;
import buildcraft.additionalpipes.api.TeleportManagerBase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/**
 * Frequency channels scoped to one server. Unloaded pipes are not resolved by the registry.
 */
public final class TeleportManager extends TeleportManagerBase {
    private record Channel(PipeType type, int frequency) { }
    private final Map<Channel, LinkedHashSet<ITeleportPipe>> channels = new HashMap<>();
    private final IdentityHashMap<ITeleportPipe, Channel> indexed = new IdentityHashMap<>();
    private final Map<Integer, String> names = new HashMap<>();

    public synchronized void add(ITeleportPipe pipe, int frequency) {
        Objects.requireNonNull(pipe, "pipe");
        requireFrequency(frequency);
        removeIndexed(pipe);
        Channel channel = new Channel(Objects.requireNonNull(pipe.getType(), "pipe type"), frequency);
        channels.computeIfAbsent(channel, ignored -> new LinkedHashSet<>()).add(pipe);
        indexed.put(pipe, channel);
    }

    public synchronized void remove(ITeleportPipe pipe, int frequency) {
        // Do not rely on the supplied frequency; callers may have already changed it.
        if (pipe != null) removeIndexed(pipe);
    }

    private void removeIndexed(ITeleportPipe pipe) {
        Channel key = indexed.remove(pipe);
        if (key == null) return;
        LinkedHashSet<ITeleportPipe> members = channels.get(key);
        if (members != null) {
            members.remove(pipe);
            if (members.isEmpty()) channels.remove(key);
        }
    }

    @Override
    public synchronized void reset() {
        channels.clear();
        indexed.clear();
        names.clear();
    }

    @Override
    public synchronized <T extends ITeleportPipe> ArrayList<T> getConnectedPipes(
        T pipe, boolean includeSend, boolean includeReceive) {
        Objects.requireNonNull(pipe, "pipe");
        ArrayList<T> result = new ArrayList<>();
        if (!includeSend && !includeReceive) return result;
        LinkedHashSet<ITeleportPipe> members = channels.get(new Channel(pipe.getType(), pipe.getFrequency()));
        if (members == null) return result;
        for (ITeleportPipe other : members) {
            if (other == pipe || !other.isValid()) continue;
            if (!((includeSend && other.canSend()) || (includeReceive && other.canReceive()))) continue;
            if (pipe.isPublic() == other.isPublic()
                && (pipe.isPublic() || pipe.getOwnerUUID() != null && pipe.getOwnerUUID().equals(other.getOwnerUUID()))) {
                @SuppressWarnings("unchecked") T candidate = (T) other;
                result.add(candidate);
            }
        }
        return result;
    }

    @Override
    public synchronized Collection<ITeleportPipe> getPipes(PipeType type) {
        ArrayList<ITeleportPipe> result = new ArrayList<>();
        for (Map.Entry<Channel, LinkedHashSet<ITeleportPipe>> entry : channels.entrySet()) {
            if (entry.getKey().type == type) {
                for (ITeleportPipe member : entry.getValue()) if (member.isValid()) result.add(member);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized String getFrequencyName(int frequency) { return names.getOrDefault(frequency, ""); }

    @Override
    public synchronized void setFrequencyName(int frequency, String name) {
        requireFrequency(frequency);
        String trimmed = Objects.requireNonNull(name, "name").trim();
        if (trimmed.length() > 64) throw new IllegalArgumentException("Frequency name longer than 64 characters");
        if (trimmed.isEmpty()) names.remove(frequency);
        else names.put(frequency, trimmed);
    }

    @Override
    public synchronized Map<Integer, String> frequencyNames() { return Map.copyOf(names); }

    private static void requireFrequency(int frequency) {
        if (frequency < 0) throw new IllegalArgumentException("Negative teleport frequency");
    }
}
