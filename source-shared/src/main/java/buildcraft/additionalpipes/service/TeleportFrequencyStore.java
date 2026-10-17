package buildcraft.additionalpipes.service;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.api.TeleportManagerBase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Per-save persistent frequency labels. Endpoints are NEVER persisted here:
 * they are re-indexed by their owning pipe components as chunks load.
 */
public final class TeleportFrequencyStore {
    private static final String FILE_NAME = "additionalpipes-frequencies.properties";
    private TeleportFrequencyStore() { }

    public static void load(Path worldRoot, TeleportManagerBase manager) throws IOException {
        Path file = location(worldRoot);
        if (!Files.isRegularFile(file)) return;
        Properties entries = new Properties();
        try (InputStream input = Files.newInputStream(file)) { entries.load(input); }
        for (String key : entries.stringPropertyNames()) {
            if (!key.startsWith("frequency.")) continue;
            try {
                int frequency = Integer.parseInt(key.substring("frequency.".length()));
                manager.setFrequencyName(frequency, entries.getProperty(key));
            } catch (IllegalArgumentException ignored) {
                // Corrupt or malicious world data must not prevent the rest of the save from loading.
            }
        }
    }

    public static void save(Path worldRoot, TeleportManagerBase manager) throws IOException {
        Properties entries = new Properties();
        for (Map.Entry<Integer, String> item : manager.frequencyNames().entrySet()) {
            entries.setProperty("frequency." + item.getKey(), item.getValue());
        }
        APConfiguration.writeAtomically(location(worldRoot), entries);
    }

    private static Path location(Path worldRoot) {
        return worldRoot.resolve("data").resolve(FILE_NAME);
    }
}
