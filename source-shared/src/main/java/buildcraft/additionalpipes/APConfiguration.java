package buildcraft.additionalpipes;

import buildcraft.additionalpipes.utils.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Properties;

/**
 * Loader-neutral replacement for the 1.8 Forge Configuration object.
 * Preserves the classic property names and defaults. Values are sanitized before use.
 * Only one config file is used for all seven targets: config/additionalpipes.properties.
 */
public final class APConfiguration {
    public static final String FILE_NAME = "additionalpipes.properties";
    public static final int MAX_CHUNK_SIGHT_RANGE = 31;
    private static volatile APConfiguration current = defaults();

    public final int chunkSightRange;
    public final boolean chunkSightAutorefresh;
    public final int laserKeyCode;
    public final boolean enableDebugLog;
    public final boolean allowWRRemove;
    public final double powerTransmittanceCfg;
    public final int waterPumpWaterPerTick;
    public final int gravityFeedPipeTicksPerPull;
    public final boolean enableTriggers;
    public final boolean filterRightclicks;
    public final int chunkLoaderMaxRadius;

    private APConfiguration(Properties p) {
        chunkSightRange = boundedInt(p, "chunkSightRange", 8, 0, MAX_CHUNK_SIGHT_RANGE);
        chunkSightAutorefresh = flag(p, "chunkSightAutorefresh", true);
        laserKeyCode = boundedInt(p, "laserKeyChar", 68, -1, 1024);
        enableDebugLog = flag(p, "enableDebugLog", false);
        allowWRRemove = flag(p, "allowWRRemove", true);
        powerTransmittanceCfg = boundedInt(p, "powerTransmittance", 90, 0, 100) / 100.0;
        waterPumpWaterPerTick = boundedInt(p, "waterPumpWaterPerTick", 90, 0, 1000000);
        gravityFeedPipeTicksPerPull = boundedInt(p, "gravityFeedPipeTicksPerPull", 48, 1, 120000);
        enableTriggers = flag(p, "enableTriggers", true);
        filterRightclicks = flag(p, "filterRightclicks", false);
        chunkLoaderMaxRadius = boundedInt(p, "chunkLoaderMaxRadius", 4, 0, 8);
    }

    public static APConfiguration get() { return current; }
    public static APConfiguration defaults() { return new APConfiguration(new Properties()); }

    public static synchronized APConfiguration load(Path configDirectory) throws IOException {
        Objects.requireNonNull(configDirectory, "configDirectory");
        Path file = configDirectory.resolve(FILE_NAME);
        Properties p = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        }
        APConfiguration result = new APConfiguration(p);
        // Keep comments and non-AP keys from existing files, but normalize unsafe values.
        result.writeTo(p);
        writeAtomically(file, p);
        current = result;
        Log.setDebugEnabled(result.enableDebugLog);
        return result;
    }

    private void writeTo(Properties p) {
        p.setProperty("chunkSightRange", Integer.toString(chunkSightRange));
        p.setProperty("chunkSightAutorefresh", Boolean.toString(chunkSightAutorefresh));
        p.setProperty("laserKeyChar", Integer.toString(laserKeyCode));
        p.setProperty("enableDebugLog", Boolean.toString(enableDebugLog));
        p.setProperty("allowWRRemove", Boolean.toString(allowWRRemove));
        p.setProperty("powerTransmittance", Integer.toString((int) Math.round(powerTransmittanceCfg * 100)));
        p.setProperty("waterPumpWaterPerTick", Integer.toString(waterPumpWaterPerTick));
        p.setProperty("gravityFeedPipeTicksPerPull", Integer.toString(gravityFeedPipeTicksPerPull));
        p.setProperty("enableTriggers", Boolean.toString(enableTriggers));
        p.setProperty("filterRightclicks", Boolean.toString(filterRightclicks));
        p.setProperty("chunkLoaderMaxRadius", Integer.toString(chunkLoaderMaxRadius));
    }

    private static int boundedInt(Properties p, String key, int fallback, int min, int max) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        try { return Math.max(min, Math.min(max, Integer.parseInt(raw.trim()))); }
        catch (NumberFormatException ex) { Log.warn("Invalid config value " + key + "=" + raw + ", using " + fallback); return fallback; }
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        if ("true".equalsIgnoreCase(raw.trim())) return true;
        if ("false".equalsIgnoreCase(raw.trim())) return false;
        Log.warn("Invalid config value " + key + "=" + raw + ", using " + fallback);
        return fallback;
    }

    public static void writeAtomically(Path path, Properties properties) throws IOException {
        Path absolute = path.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".ap-config-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporary)) {
                properties.store(out, "Additional Pipes for BuildCraft Community Edition");
            }
            try { Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
