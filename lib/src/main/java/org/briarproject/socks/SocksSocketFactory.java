package org.briarproject.socks;

import org.briarproject.nullsafety.NotNullByDefault;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import javax.annotation.Nullable;
import javax.net.SocketFactory;

@NotNullByDefault
public class SocksSocketFactory extends SocketFactory {

	private final SocketAddress proxy;
	private final int connectToProxyTimeout;
	private final int extraConnectTimeout, extraSocketTimeout;
	@Nullable
	private final String username, password;

	/**
	 * Creates a socket factory for a SOCKS proxy that requires username/password authentication.
	 *
	 * @param connectToProxyTimeout The timeout in milliseconds for connecting to the proxy.
	 * @param extraConnectTimeout The extra timeout in milliseconds that should be added to the
	 * 		socket's default timeout when connecting to an endpoint via the proxy.
	 * @param extraSocketTimeout The extra timeout in milliseconds that should be added to the
	 * 		socket's default timeout after connecting to an endpoint via the proxy.
	 */
	public SocksSocketFactory(SocketAddress proxy, int connectToProxyTimeout,
			int extraConnectTimeout, int extraSocketTimeout, String username, String password) {
		this.proxy = proxy;
		this.connectToProxyTimeout = connectToProxyTimeout;
		this.extraConnectTimeout = extraConnectTimeout;
		this.extraSocketTimeout = extraSocketTimeout;
		this.username = username;
		this.password = password;
	}

	/**
	 * Creates a socket factory for a SOCKS proxy that does not require authentication.
	 *
	 * @param connectToProxyTimeout The timeout in milliseconds for connecting to the proxy.
	 * @param extraConnectTimeout The extra timeout in milliseconds that should be added to the
	 * 		socket's default timeout when connecting to an endpoint via the proxy.
	 * @param extraSocketTimeout The extra timeout in milliseconds that should be added to the
	 * 		socket's default timeout after connecting to an endpoint via the proxy.
	 */
	public SocksSocketFactory(SocketAddress proxy, int connectToProxyTimeout,
			int extraConnectTimeout, int extraSocketTimeout) {
		this.proxy = proxy;
		this.connectToProxyTimeout = connectToProxyTimeout;
		this.extraConnectTimeout = extraConnectTimeout;
		this.extraSocketTimeout = extraSocketTimeout;
		this.username = null;
		this.password = null;
	}

	@Override
	public Socket createSocket() {
		if (username == null || password == null) {
			return new SocksSocket(proxy, connectToProxyTimeout, extraConnectTimeout,
					extraSocketTimeout);
		} else {
			return new SocksSocket(proxy, connectToProxyTimeout, extraConnectTimeout,
					extraSocketTimeout, username, password);
		}
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		Socket socket = createSocket();
		socket.connect(InetSocketAddress.createUnresolved(host, port));
		return socket;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
			int localPort) {
		throw new UnsupportedOperationException();
	}
}
