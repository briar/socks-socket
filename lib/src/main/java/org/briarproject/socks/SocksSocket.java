package org.briarproject.socks;

import org.briarproject.nullsafety.NotNullByDefault;
import org.briarproject.util.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.annotation.Nullable;

import static java.lang.System.arraycopy;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;
import static org.briarproject.util.ByteUtils.writeUint16;
import static org.briarproject.util.IoUtils.readFully;

@NotNullByDefault
class SocksSocket extends Socket {

	private static final String[] ERRORS = {
			"Succeeded",
			"General SOCKS server failure",
			"Connection not allowed by ruleset",
			"Network unreachable",
			"Host unreachable",
			"Connection refused",
			"TTL expired",
			"Command not supported",
			"Address type not supported"
	};

	@SuppressWarnings("MismatchedReadAndWriteOfArray")
	private static final byte[] UNSPECIFIED_ADDRESS = new byte[4];

	// StandardCharsets is not available on old Android versions
	@SuppressWarnings("CharsetObjectCanBeUsed")
	private static final Charset UTF_8 = Charset.forName("UTF-8");

	private final SocketAddress proxy;
	private final int connectToProxyTimeout;
	private final int extraConnectTimeout, extraSocketTimeout;
	@Nullable
	private final String username, password;

	SocksSocket(SocketAddress proxy, int connectToProxyTimeout, int extraConnectTimeout,
			int extraSocketTimeout, String username, String password) {
		this.proxy = proxy;
		this.connectToProxyTimeout = connectToProxyTimeout;
		this.extraConnectTimeout = extraConnectTimeout;
		this.extraSocketTimeout = extraSocketTimeout;
		this.username = username;
		this.password = password;
	}

	SocksSocket(SocketAddress proxy, int connectToProxyTimeout, int extraConnectTimeout,
			int extraSocketTimeout) {
		this.proxy = proxy;
		this.connectToProxyTimeout = connectToProxyTimeout;
		this.extraConnectTimeout = extraConnectTimeout;
		this.extraSocketTimeout = extraSocketTimeout;
		this.username = null;
		this.password = null;
	}

	@Override
	public void connect(SocketAddress endpoint, int timeout) throws IOException {

		// Validate the endpoint
		if (!(endpoint instanceof InetSocketAddress)) throw new IllegalArgumentException();
		InetSocketAddress inet = (InetSocketAddress) endpoint;
		InetAddress address = inet.getAddress();
		if (address != null && !Arrays.equals(address.getAddress(), UNSPECIFIED_ADDRESS)) {
			throw new IllegalArgumentException();
		}
		String host = inet.getHostName();
		if (host.length() > 255) throw new IllegalArgumentException();
		int port = inet.getPort();

		// Connect to the proxy
		super.connect(proxy, connectToProxyTimeout);
		OutputStream out = IoUtils.getOutputStream(this);
		InputStream in = IoUtils.getInputStream(this);

		// Request SOCKS 5 with username/password authentication
		sendMethodRequest(out);
		receiveMethodResponse(in);

		if (username != null && password != null) {
			// Send username and password, see https://www.rfc-editor.org/rfc/rfc1929.html
			sendAuthRequest(out);
			receiveAuthResponse(in);
		}

		// Use the supplied timeout temporarily, plus any configured extra
		int oldTimeout = getSoTimeout();
		setSoTimeout(timeout + extraConnectTimeout);

		// Connect to the endpoint via the proxy
		sendConnectRequest(out, host, port);
		receiveConnectResponse(in);

		// Restore the old timeout, plus any configured extra
		setSoTimeout(oldTimeout + extraSocketTimeout);
	}

	private void sendMethodRequest(OutputStream out) throws IOException {
		byte method;
		if (username == null || password == null) method = 0; // No authentication
		else method = 2; // Username/password authentication
		byte[] methodRequest = new byte[]{
				5, // SOCKS version is 5
				1, // Number of methods is 1
				method
		};
		out.write(methodRequest);
		out.flush();
	}

	private void receiveMethodResponse(InputStream in) throws IOException {
		byte[] methodResponse = new byte[2];
		readFully(in, methodResponse);
		byte version = methodResponse[0];
		byte method = methodResponse[1];
		if (version != 5) {
			throw new IOException("Unsupported SOCKS version: " + version);
		}
		if (method == (byte) 255) {
			throw new IOException("Proxy requires authentication");
		}
		byte requestedMethod;
		if (username == null || password == null) requestedMethod = 0; // No authentication
		else requestedMethod = 2; // Username/password authentication
		if (method != requestedMethod) {
			throw new IOException("Unsupported auth method: " + method);
		}
	}

	private void sendAuthRequest(OutputStream out) throws IOException {
		byte[] usernameBytes = requireNonNull(username).getBytes(UTF_8);
		byte[] passwordBytes = requireNonNull(password).getBytes(UTF_8);
		byte[] authRequest = new byte[3 + usernameBytes.length + passwordBytes.length];
		authRequest[0] = 1; // Subnegotiation version is 1
		authRequest[1] = (byte) usernameBytes.length;
		arraycopy(usernameBytes, 0, authRequest, 2, usernameBytes.length);
		authRequest[usernameBytes.length + 2] = (byte) passwordBytes.length;
		arraycopy(passwordBytes, 0, authRequest, usernameBytes.length + 3, passwordBytes.length);
		out.write(authRequest);
		out.flush();
	}

	private void receiveAuthResponse(InputStream in) throws IOException {
		byte[] authResponse = new byte[2];
		readFully(in, authResponse);
		byte version = authResponse[0];
		byte status = authResponse[1];
		if (version != 1)
			throw new IOException("Unsupported subnegotiation version: " + version);
		if (status != 0)
			throw new IOException("Authentication failed, status: " + status);
	}

	private void sendConnectRequest(OutputStream out, String host, int port) throws IOException {
		byte[] connectRequest = new byte[7 + host.length()];
		connectRequest[0] = 5; // SOCKS version is 5
		connectRequest[1] = 1; // Command is 1, connect
		connectRequest[3] = 3; // Address type is 3, domain name
		connectRequest[4] = (byte) host.length(); // Length of domain name
		for (int i = 0; i < host.length(); i++) {
			connectRequest[5 + i] = (byte) host.charAt(i);
		}
		writeUint16(port, connectRequest, connectRequest.length - 2);
		out.write(connectRequest);
		out.flush();
	}

	private void receiveConnectResponse(InputStream in) throws IOException {
		byte[] connectResponse = new byte[4];
		readFully(in, connectResponse);
		int version = connectResponse[0] & 0xFF;
		int reply = connectResponse[1] & 0xFF;
		int addressType = connectResponse[3] & 0xFF;
		if (version != 5) {
			throw new IOException("Unsupported SOCKS version: " + version);
		}
		if (reply != 0) {
			if (reply < ERRORS.length) {
				throw new IOException("Connection failed: " + ERRORS[reply]);
			} else {
				throw new IOException("Connection failed: " + reply);
			}
		}
		if (addressType == 1) readFully(in, new byte[4]); // IPv4
		else if (addressType == 4) readFully(in, new byte[16]); // IPv6
		else throw new IOException("Unsupported address type: " + addressType);
		readFully(in, new byte[2]); // Port number
	}
}
