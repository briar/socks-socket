package org.briarproject.util;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@NotNullByDefault
public class IoUtils {

	public static void readFully(InputStream in, byte[] b) throws IOException {
		int offset = 0;
		while (offset < b.length) {
			int read = in.read(b, offset, b.length - offset);
			if (read == -1) throw new EOFException();
			offset += read;
		}
	}

	/**
	 * Workaround for a bug in Android 7, see
	 * https://android-review.googlesource.com/#/c/271775/
	 */
	public static InputStream getInputStream(Socket s) throws IOException {
		try {
			return s.getInputStream();
		} catch (NullPointerException e) {
			throw new IOException(e);
		}
	}


	/**
	 * Workaround for a bug in Android 7, see
	 * https://android-review.googlesource.com/#/c/271775/
	 */
	public static OutputStream getOutputStream(Socket s) throws IOException {
		try {
			return s.getOutputStream();
		} catch (NullPointerException e) {
			throw new IOException(e);
		}
	}
}
