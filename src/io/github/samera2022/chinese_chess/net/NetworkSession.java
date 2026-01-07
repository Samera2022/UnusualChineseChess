package io.github.samera2022.chinese_chess.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 简易局域网会话：支持主机/客户端，传输指令（MOVE/RESTART/DISCONNECT/PING/PONG）。
 */
public class NetworkSession {
    public interface Listener {
        void onPeerMove(int fromRow, int fromCol, int toRow, int toCol);
        void onPeerRestart();
        void onDisconnected(String reason);
        void onConnected(String peerInfo);
        // 新增：收到对端的 PONG（延迟测量）
        void onPong(long sentMillis, long rttMillis);
        // 新增：接收对端同步的设置
        void onSettingsReceived(JsonObject settings);
    }

    private ServerSocket serverSocket;
    private Socket socket;
    private Thread ioThread;
    private PrintWriter out;
    private BufferedReader in;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return running.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void startServer(int port) throws IOException {
        close();
        serverSocket = new ServerSocket(port);
        ioThread = new Thread(() -> {
            try {
                socket = serverSocket.accept();
                setupStreams();
                running.set(true);
                if (listener != null) listener.onConnected(socket.getRemoteSocketAddress().toString());
                readLoop();
            } catch (IOException e) {
                if (listener != null) listener.onDisconnected("服务器错误: " + e.getMessage());
            } finally {
                closeInternal();
            }
        }, "LAN-Server-Accept");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public void connect(String host, int port) throws IOException {
        close();
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        setupStreams();
        running.set(true);
        ioThread = new Thread(this::readLoop, "LAN-Client-IO");
        ioThread.setDaemon(true);
        ioThread.start();
        if (listener != null) listener.onConnected(socket.getRemoteSocketAddress().toString());
    }

    private void setupStreams() throws IOException {
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private void readLoop() {
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                handleLine(line);
            }
        } catch (IOException e) {
            // 通知断开
        } finally {
            if (listener != null) listener.onDisconnected("连接已关闭");
            closeInternal();
        }
    }

    private void handleLine(String line) {
        try {
            if (line.startsWith("MOVE ")) {
                String[] parts = line.split(" ");
                int fr = Integer.parseInt(parts[1]);
                int fc = Integer.parseInt(parts[2]);
                int tr = Integer.parseInt(parts[3]);
                int tc = Integer.parseInt(parts[4]);
                if (listener != null) listener.onPeerMove(fr, fc, tr, tc);
            } else if (line.equals("RESTART")) {
                if (listener != null) listener.onPeerRestart();
            } else if (line.equals("DISCONNECT")) {
                if (listener != null) listener.onDisconnected("对方断开");
                closeInternal();
            } else if (line.startsWith("PING ")) {
                // 对端发来 PING，回 PONG 原样时间戳
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    String ts = parts[1];
                    if (out != null) out.println("PONG " + ts);
                }
            } else if (line.startsWith("PONG ")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    long sent = Long.parseLong(parts[1]);
                    long rtt = System.currentTimeMillis() - sent;
                    if (listener != null) listener.onPong(sent, rtt);
                }
            } else if (line.startsWith("SETTINGS ")) {
                String json = line.substring("SETTINGS ".length());
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (listener != null) listener.onSettingsReceived(obj);
            }
        } catch (Exception ignored) {
        }
    }

    public void sendMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (out != null) {
            out.println("MOVE " + fromRow + " " + fromCol + " " + toRow + " " + toCol);
        }
    }

    public void sendRestart() {
        if (out != null) {
            out.println("RESTART");
        }
    }

    public void sendSettings(JsonObject settings) {
        if (out != null && settings != null) {
            out.println("SETTINGS " + settings.toString());
        }
    }

    public void sendPing() {
        if (out != null) {
            long now = System.currentTimeMillis();
            out.println("PING " + now);
        }
    }

    public void disconnect() {
        if (out != null) {
            out.println("DISCONNECT");
        }
        close();
    }

    private void closeInternal() {
        running.set(false);
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        in = null; out = null; socket = null; serverSocket = null;
    }

    public void close() {
        running.set(false);
        if (ioThread != null) {
            ioThread.interrupt();
            ioThread = null;
        }
        closeInternal();
    }
}
