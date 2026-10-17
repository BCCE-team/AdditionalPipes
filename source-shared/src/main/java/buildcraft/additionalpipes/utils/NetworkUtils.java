package buildcraft.additionalpipes.utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Correctly sized bitmap packing, including multiples of eight and empty arrays. */
public final class NetworkUtils {
    private NetworkUtils() { }

    public static void writeBooleanArray(DataOutput output, boolean[] values) throws IOException {
        byte[] packed = new byte[(values.length + 7) / 8];
        for (int i = 0; i < values.length; i++) if (values[i]) packed[i >> 3] |= 1 << (i & 7);
        output.write(packed);
    }

    public static boolean[] readBooleanArray(DataInput input, int length) throws IOException {
        if (length < 0 || length > 16384) throw new IOException("Invalid bitmap size: " + length);
        byte[] packed = new byte[(length + 7) / 8];
        input.readFully(packed);
        boolean[] decoded = new boolean[length];
        for (int i = 0; i < length; i++) decoded[i] = (packed[i >> 3] & (1 << (i & 7))) != 0;
        return decoded;
    }
}
