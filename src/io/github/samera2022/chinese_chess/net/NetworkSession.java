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
        // 新增：对端请求撤销一步
        void onPeerUndo();
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
        System.out.println("[DEBUG] Server: 准备监听端口 " + port);
        serverSocket = new ServerSocket(port);
        ioThread = new Thread(() -> {
            try {
                System.out.println("[DEBUG] Server: 等待客户端连接...");
                socket = serverSocket.accept();
                System.out.println("[DEBUG] Server: 客户端已连接: " + socket.getRemoteSocketAddress());
                setupStreams();
                running.set(true);
                if (listener != null) listener.onConnected(socket.getRemoteSocketAddress().toString());
                readLoop();
            } catch (IOException e) {
                System.out.println("[DEBUG] Server: 发生异常: " + e);
                if (listener != null) listener.onDisconnected("服务器错误: " + e.getMessage());
            } finally {
                System.out.println("[DEBUG] Server: 关闭连接");
                closeInternal();
            }
        }, "LAN-Server-Accept");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public void connect(String host, int port) throws IOException {
        close();
        System.out.println("[DEBUG][Client] 尝试连接到 " + host + ":" + port);
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        System.out.println("[DEBUG][Client] 已连接到服务器: " + socket.getRemoteSocketAddress());
        setupStreams();
        running.set(true);
        ioThread = new Thread(() -> {
            System.out.println("[DEBUG][Client] IO线程启动，开始读取数据");
            try {
                String line;
                while (running.get() && (line = in.readLine()) != null) {
                    System.out.println("[DEBUG][Client] 收到数据: " + line);
                    handleLine(line);
                }
                System.out.println("[DEBUG][Client] 读取循环结束，连接可能被关闭");
            } catch (IOException e) {
                System.out.println("[DEBUG][Client] IO线程异常: " + e);
            } finally {
                System.out.println("[DEBUG][Client] IO线程关闭，通知断开");
                if (listener != null) listener.onDisconnected("连接已关闭");
                closeInternal();
            }
        }, "LAN-Client-IO");
        ioThread.setDaemon(true);
        ioThread.start();
        if (listener != null) listener.onConnected(socket.getRemoteSocketAddress().toString());
    }

    private void setupStreams() throws IOException {
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        System.out.println("[DEBUG] Streams 已初始化");
    }

    private void readLoop() {
        if (serverSocket != null) {
            System.out.println("[DEBUG][Server] IO线程启动，开始读取数据");
        } else {
            System.out.println("[DEBUG][Client] IO线程启动，开始读取数据");
        }
        try {
            String line;
            while (running.get() && (line = in.readLine()) != null) {
                if (serverSocket != null) {
                    System.out.println("[DEBUG][Server] 收到数据: " + line);
                } else {
                    System.out.println("[DEBUG][Client] 收到数据: " + line);
                }
                handleLine(line);
            }
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 读取循环结束，连接可能被关闭");
            } else {
                System.out.println("[DEBUG][Client] 读取循环结束，连接可能被关闭");
            }
        } catch (IOException e) {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] IO线程异常: " + e);
            } else {
                System.out.println("[DEBUG][Client] IO线程异常: " + e);
            }
        } finally {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] IO线程关闭，通知断开");
            } else {
                System.out.println("[DEBUG][Client] IO线程关闭，通知断开");
            }
            if (listener != null) listener.onDisconnected("连接已关闭");
            closeInternal();
        }
    }

    private void handleLine(String line) {
        try {
            if (line.startsWith("MOVE ")) {
                System.out.println("[DEBUG] 处理 MOVE 指令");
                String[] parts = line.split(" ");
                int fr = Integer.parseInt(parts[1]);
                int fc = Integer.parseInt(parts[2]);
                int tr = Integer.parseInt(parts[3]);
                int tc = Integer.parseInt(parts[4]);
                if (listener != null) listener.onPeerMove(fr, fc, tr, tc);
            } else if (line.equals("RESTART")) {
                System.out.println("[DEBUG] 处理 RESTART 指令");
                if (listener != null) listener.onPeerRestart();
            } else if (line.equals("UNDO")) {
                System.out.println("[DEBUG] 处理 UNDO 指令");
                if (listener != null) listener.onPeerUndo();
            } else if (line.equals("DISCONNECT")) {
                System.out.println("[DEBUG] 处理 DISCONNECT 指令");
                if (listener != null) listener.onDisconnected("对方断开");
                closeInternal();
            } else if (line.startsWith("PING ")) {
                System.out.println("[DEBUG] 处理 PING 指令");
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    String ts = parts[1];
                    if (out != null) out.println("PONG " + ts);
                }
            } else if (line.startsWith("PONG ")) {
                System.out.println("[DEBUG] 处理 PONG 指令");
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    long sent = Long.parseLong(parts[1]);
                    long rtt = System.currentTimeMillis() - sent;
                    if (listener != null) listener.onPong(sent, rtt);
                }
            } else if (line.startsWith("SETTINGS ")) {
                System.out.println("[DEBUG] 处理 SETTINGS 指令");
                String json = line.substring("SETTINGS ".length());
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (listener != null) listener.onSettingsReceived(obj);
            }
        } catch (Exception ex) {
            System.out.println("[DEBUG] 处理指令异常: " + ex);
        }
    }

    public void sendMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (out != null) {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 发送 MOVE 指令");
            } else {
                System.out.println("[DEBUG][Client] 发送 MOVE 指令");
            }
            out.println("MOVE " + fromRow + " " + fromCol + " " + toRow + " " + toCol);
        }
    }

    public void sendRestart() {
        if (out != null) {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 发送 RESTART 指令");
            } else {
                System.out.println("[DEBUG][Client] 发送 RESTART 指令");
            }
        }
        if (out != null) out.println("RESTART");
    }

    // 新增：发送 UNDO 指令
    public void sendUndo() {
        if (out != null) {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 发送 UNDO 指令");
            } else {
                System.out.println("[DEBUG][Client] 发送 UNDO 指令");
            }
            out.println("UNDO");
        }
    }

    public void sendSettings(JsonObject settings) {
        if (out != null && settings != null) {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 发送 SETTINGS 指令");
            } else {
                System.out.println("[DEBUG][Client] 发送 SETTINGS 指令");
            }
            out.println("SETTINGS " + settings.toString());
        }
    }

    public void sendPing() {
        if (out != null) {
            long now = System.currentTimeMillis();
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 发送 PING 指令");
            } else {
                System.out.println("[DEBUG][Client] 发送 PING 指令");
            }
            out.println("PING " + now);
        }
    }

    public void disconnect() {
        if (out != null) {
            if (serverSocket != null) {
                System.out.println("[DEBUG][Server] 发送 DISCONNECT 指令");
            } else {
                System.out.println("[DEBUG][Client] 发送 DISCONNECT 指令");
            }
            out.println("DISCONNECT");
        }
        close();
    }

    private void closeInternal() {
        if (serverSocket != null) {
            System.out.println("[DEBUG][Server] 执行 closeInternal，关闭所有资源");
        } else {
            System.out.println("[DEBUG][Client] 执行 closeInternal，关闭所有资源");
        }
        running.set(false);
        try { if (in != null) in.close(); } catch (IOException ex) { System.out.println("[DEBUG] 关闭 in 异常: " + ex); }
        try { if (out != null) out.close(); } catch (Exception ex) { System.out.println("[DEBUG] 关闭 out 异常: " + ex); }
        try { if (socket != null) socket.close(); } catch (IOException ex) { System.out.println("[DEBUG] 关闭 socket 异常: " + ex); }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ex) { System.out.println("[DEBUG] 关闭 serverSocket 异常: " + ex); }
        in = null; out = null; socket = null; serverSocket = null;
    }

    public void close() {
        if (serverSocket != null) {
            System.out.println("[DEBUG][Server] 执行 close，准备关闭连接");
        } else {
            System.out.println("[DEBUG][Client] 执行 close，准备关闭连接");
        }
        running.set(false);
        if (ioThread != null) {
            ioThread.interrupt();
            ioThread = null;
        }
        closeInternal();
    }
}
