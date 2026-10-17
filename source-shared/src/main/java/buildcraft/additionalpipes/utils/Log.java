package buildcraft.additionalpipes.utils;

/** Logging without a Forge dependency: safe on dedicated servers and in unit tests. */
public final class Log {
    private static final System.Logger LOGGER = System.getLogger("AdditionalPipes");
    private static volatile boolean debugEnabled;

    private Log() { }

    public static void setDebugEnabled(boolean enabled) { debugEnabled = enabled; }
    public static void debug(Object value) {
        if (debugEnabled) LOGGER.log(System.Logger.Level.INFO, "[debug] " + value);
    }
    public static void info(Object value) { LOGGER.log(System.Logger.Level.INFO, String.valueOf(value)); }
    public static void warn(Object value) { LOGGER.log(System.Logger.Level.WARNING, String.valueOf(value)); }
    public static void error(Object value) { LOGGER.log(System.Logger.Level.ERROR, String.valueOf(value)); }
    public static void error(String message, Throwable cause) { LOGGER.log(System.Logger.Level.ERROR, message, cause); }
}
