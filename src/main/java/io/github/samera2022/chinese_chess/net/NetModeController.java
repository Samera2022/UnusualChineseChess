package io.github.samera2022.chinese_chess.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 提供创建/加入局域网对局的便捷入口，并提供当前连接信息（地址与端口）。
 */
public class NetModeController {
    private final NetworkSession session = new NetworkSession();
    private String hostAddress;
    private int port;
    private boolean isHost;

    public NetworkSession getSession() { return session; }

    public boolean isActive() { return session.isConnected(); }

    public boolean isHost() { return isHost; }

    public String getLocalIPv4() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        try { return InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) { return "127.0.0.1"; }
    }

    public void host(int port) throws Exception {
        this.port = port;
        this.isHost = true;
        session.startServer(port);
        hostAddress = getLocalIPv4();
    }

    public void join(String host, int port) throws Exception {
        this.port = port;
        this.isHost = false;
        this.hostAddress = host;
        session.connect(host, port);
    }

    public String displayAddress() {
        if (isHost) {
            return hostAddress + ":" + port + " (Host)";
        } else {
            return hostAddress + ":" + port + " (Client)";
        }
    }
}
