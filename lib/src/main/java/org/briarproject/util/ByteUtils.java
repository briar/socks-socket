package org.briarproject.util;

import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public class ByteUtils {

	/**
	 * The maximum value that can be represented as an unsigned 16-bit integer.
	 */
	private static final int MAX_16_BIT_UNSIGNED = 65535; // 2^16 - 1

	public static void writeUint16(int src, byte[] dest, int offset) {
		if (src < 0) throw new IllegalArgumentException();
		if (src > MAX_16_BIT_UNSIGNED) throw new IllegalArgumentException();
		if (dest.length < offset + 2)
			throw new IllegalArgumentException();
		dest[offset] = (byte) (src >> 8);
		dest[offset + 1] = (byte) (src & 0xFF);
	}
}
