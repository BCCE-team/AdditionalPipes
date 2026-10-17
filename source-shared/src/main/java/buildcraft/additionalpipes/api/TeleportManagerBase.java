package buildcraft.additionalpipes.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/** Stable addon-facing surface retained from classic Additional Pipes. */
public abstract class TeleportManagerBase {
    public abstract void add(ITeleportPipe pipe, int frequency);
    public abstract void remove(ITeleportPipe pipe, int frequency);
    public abstract void reset();
    public abstract <T extends ITeleportPipe> ArrayList<T> getConnectedPipes(
        T pipe, boolean includeSend, boolean includeReceive);
    public abstract Collection<ITeleportPipe> getPipes(PipeType type);
    public abstract String getFrequencyName(int frequency);
    public abstract void setFrequencyName(int frequency, String name);
    public abstract Map<Integer, String> frequencyNames();
}
