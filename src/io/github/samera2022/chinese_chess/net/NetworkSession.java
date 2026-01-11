package io.github.samera2022.chinese_chess.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.samera2022.chinese_chess.UpdateInfo;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
        // 新增：收到对端版本信息
        void onPeerVersion(String version);
        // 新增：收到对端强制走子请求（包含 seq 与 historyLen）
        void onForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol, long seq, int historyLen);
        // 新增：收到对端对强制走子的确认（包含 seq）
        void onForceMoveConfirm(int fromRow, int fromCol, int toRow, int toCol, long seq);
        // 新增：对端请求撤销一步
        void onPeerUndo();

        void onForceMoveApplied(int fromRow, int fromCol, int toRow, int toCol, long seq);

        // 新增：收到对端对强制走子的拒绝（包含 seq 与原因）
        void onForceMoveReject(int fromRow, int fromCol, int toRow, int toCol, long seq, String reason);
    }

    private ServerSocket serverSocket;
    private Socket socket;
    private Thread ioThread;
    private PrintWriter out;
    private BufferedReader in;
    // per-connection ephemeral tokens used to compute a simple HMAC key
    private String localToken;
    private String peerToken;
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
        // Send our version and a HELLO token as part of handshake
        try {
            // version (legacy)
            if (out != null) {
                out.println("VERSION " + UpdateInfo.getLatestVersion());
                System.out.println("[DEBUG] Sent local VERSION " + UpdateInfo.getLatestVersion());
            }
            // generate and send HELLO token
            localToken = UUID.randomUUID().toString();
            JsonObject hello = new JsonObject();
            hello.addProperty("cmd", "HELLO");
            hello.addProperty("token", localToken);
            hello.addProperty("version", UpdateInfo.getLatestVersion());
            if (out != null) { out.println(hello); System.out.println("[DEBUG] Sent HELLO token"); }
        } catch (Throwable ignored) {}
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
            } else if (line.startsWith("VERSION ")) {
                System.out.println("[DEBUG] 处理 VERSION 指令");
                String ver = line.substring("VERSION ".length()).trim();
                if (listener != null) listener.onPeerVersion(ver);
            } else if (line.trim().startsWith("{")) {
                // Try JSON frame parsing (preferred for new control messages)
                try {
                    JsonObject jo = JsonParser.parseString(line).getAsJsonObject();
                    String cmd = jo.has("cmd") ? jo.get("cmd").getAsString() : null;
                    // HELLO
                    if ("HELLO".equals(cmd) && jo.has("token")) {
                        peerToken = jo.get("token").getAsString();
                        System.out.println("[DEBUG] Received HELLO token from peer");
                        // don't require signature for HELLO
                        if (listener != null && jo.has("version")) listener.onPeerVersion(jo.get("version").getAsString());
                        // stop handling this line
                        return;
                    }

                    // verify signature when present (and when both tokens are available)
                    String sig = jo.has("sig") ? jo.get("sig").getAsString() : null;
                    JsonObject payload = jo.deepCopy();
                    if (sig != null) { payload.remove("sig"); }
                    if (sig != null && localToken != null && peerToken != null) {
                        // 发送端用 localToken + peerToken 计算签名
                        // 接收端的 localToken 是发送端的 peerToken，接收端的 peerToken 是发送端的 localToken
                        // 所以接收端应该用 peerToken + localToken 来验证
                        String computed = computeHmacWithKey(payload.toString(), peerToken + localToken);
                        System.out.println("[DEBUG] 签名验证: 收到=" + sig);
                        System.out.println("[DEBUG] 签名验证: 计算=" + computed);
                        System.out.println("[DEBUG] 签名验证: localToken=" + localToken);
                        System.out.println("[DEBUG] 签名验证: peerToken=" + peerToken);
                        if (computed == null || !computed.equals(sig)) {
                            System.out.println("[DEBUG] Signature mismatch, ignoring frame");
                            // ignore this frame
                            return;
                        }
                        System.out.println("[DEBUG] 签名验证通过");
                    }

                    if ("FORCE_MOVE_REQUEST".equals(cmd)) {
                        int fr = jo.getAsJsonArray("from").get(0).getAsInt();
                        int fc = jo.getAsJsonArray("from").get(1).getAsInt();
                        int tr = jo.getAsJsonArray("to").get(0).getAsInt();
                        int tc = jo.getAsJsonArray("to").get(1).getAsInt();
                        long seq = jo.has("seq") ? jo.get("seq").getAsLong() : 0L;
                        int history = jo.has("historyLen") ? jo.get("historyLen").getAsInt() : -1;
                        System.out.println("[DEBUG] [NetworkSession] 收到JSON FORCE_MOVE_REQUEST: " + fr + "," + fc + " -> " + tr + "," + tc + " (seq=" + seq + ", historyLen=" + history + ")");
                        if (listener != null) {
                            System.out.println("[DEBUG] [NetworkSession] 调用listener.onForceMoveRequest");
                            listener.onForceMoveRequest(fr, fc, tr, tc, seq, history);
                        } else {
                            System.out.println("[DEBUG] [NetworkSession] listener为空！");
                        }
                    } else if ("FORCE_MOVE_CONFIRM".equals(cmd)) {
                        int fr = jo.getAsJsonArray("from").get(0).getAsInt();
                        int fc = jo.getAsJsonArray("from").get(1).getAsInt();
                        int tr = jo.getAsJsonArray("to").get(0).getAsInt();
                        int tc = jo.getAsJsonArray("to").get(1).getAsInt();
                        long seq = jo.has("seq") ? jo.get("seq").getAsLong() : 0L;
                        System.out.println("[DEBUG] [NetworkSession] 收到JSON FORCE_MOVE_CONFIRM: " + fr + "," + fc + " -> " + tr + "," + tc + " (seq=" + seq + ")");
                        if (listener != null) {
                            System.out.println("[DEBUG] [NetworkSession] 调用listener.onForceMoveConfirm");
                            listener.onForceMoveConfirm(fr, fc, tr, tc, seq);
                        } else {
                            System.out.println("[DEBUG] [NetworkSession] listener为空！");
                        }
                    } else if ("FORCE_MOVE_REJECT".equals(cmd)) {
                        int fr = jo.getAsJsonArray("from").get(0).getAsInt();
                        int fc = jo.getAsJsonArray("from").get(1).getAsInt();
                        int tr = jo.getAsJsonArray("to").get(0).getAsInt();
                        int tc = jo.getAsJsonArray("to").get(1).getAsInt();
                        long seq = jo.has("seq") ? jo.get("seq").getAsLong() : 0L;
                        String reason = jo.has("reason") ? jo.get("reason").getAsString() : "rejected";
                        System.out.println("[DEBUG] [NetworkSession] 收到JSON FORCE_MOVE_REJECT: " + fr + "," + fc + " -> " + tr + "," + tc + " (seq=" + seq + ", reason=" + reason + ")");
                        if (listener != null) {
                            System.out.println("[DEBUG] [NetworkSession] 调用listener.onForceMoveReject");
                            listener.onForceMoveReject(fr, fc, tr, tc, seq, reason);
                        } else {
                            System.out.println("[DEBUG] [NetworkSession] listener为空！");
                        }
                    }
                } catch (Throwable t) {
                    // fall back to plain parsing
                }
            } else if (line.startsWith("FORCE_MOVE_REQUEST ")) {
                // legacy plain-text support: FORCE_MOVE_REQUEST fr fc tr tc seq(optional)
                System.out.println("[DEBUG] 处理 legacy FORCE_MOVE_REQUEST 指令: " + line);
                String[] parts = line.split(" ");
                if (parts.length >= 5) {
                    try {
                        int fr = Integer.parseInt(parts[1]);
                        int fc = Integer.parseInt(parts[2]);
                        int tr = Integer.parseInt(parts[3]);
                        int tc = Integer.parseInt(parts[4]);
                        long seq = parts.length >= 6 ? Long.parseLong(parts[5]) : 0L;
                        int history = parts.length >= 7 ? Integer.parseInt(parts[6]) : -1;
                        System.out.println("[DEBUG] [NetworkSession] 解析legacy FORCE_MOVE_REQUEST: " + fr + "," + fc + " -> " + tr + "," + tc + " (seq=" + seq + ", historyLen=" + history + ")");
                        if (listener != null) {
                            System.out.println("[DEBUG] [NetworkSession] 调用listener.onForceMoveRequest");
                            listener.onForceMoveRequest(fr, fc, tr, tc, seq, history);
                        } else {
                            System.out.println("[DEBUG] [NetworkSession] listener为空！");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("[DEBUG] [NetworkSession] legacy FORCE_MOVE_REQUEST解析异常: " + e);
                    }
                }
            } else if (line.startsWith("FORCE_MOVE_CONFIRM ")) {
                // legacy confirm
                System.out.println("[DEBUG] 处理 legacy FORCE_MOVE_CONFIRM 指令");
                String[] parts = line.split(" ");
                if (parts.length >= 6) {
                    try {
                        int fr = Integer.parseInt(parts[1]);
                        int fc = Integer.parseInt(parts[2]);
                        int tr = Integer.parseInt(parts[3]);
                        int tc = Integer.parseInt(parts[4]);
                        long seq = Long.parseLong(parts[5]);
                        if (listener != null) listener.onForceMoveConfirm(fr, fc, tr, tc, seq);
                    } catch (NumberFormatException ignored) {}
                }
              }
         } catch (Exception ex) {
             System.out.println("[DEBUG] 处理指令异常: " + ex);
         }
     }

    // compute HMAC-SHA256 over payload string using key = localToken + peerToken
    private String computeHmac(String payload) {
        return computeHmacWithKey(payload, localToken + peerToken);
    }

    // compute HMAC-SHA256 over payload string using custom key
    private String computeHmacWithKey(String payload, String key) {
        try {
            if (key == null) return null;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Throwable t) { return null; }
    }

    // helper: send a JSON envelope and attach signature when possible
    private void sendJsonEnvelope(JsonObject payload) {
        if (out == null) {
            System.out.println("[DEBUG] sendJsonEnvelope: out is null, cannot send!");
            return;
        }
        try {
            // compute signature if both tokens available
            JsonObject copy = payload.deepCopy();
            String sig = null;
            if (localToken != null && peerToken != null) {
                sig = computeHmac(copy.toString());
                System.out.println("[DEBUG] 发送签名: sig=" + sig);
                System.out.println("[DEBUG] 发送签名: localToken=" + localToken);
                System.out.println("[DEBUG] 发送签名: peerToken=" + peerToken);
                System.out.println("[DEBUG] 发送签名: key=" + (localToken + peerToken));
            }
            if (sig != null) copy.addProperty("sig", sig);
            System.out.println("[DEBUG] sendJsonEnvelope: 实际发送的完整消息: " + copy);
            out.println(copy);
            out.flush(); // 确保立即发送
            System.out.println("[DEBUG] sendJsonEnvelope: 消息已通过 out.println 发送并 flush");
        } catch (Throwable t) {
            System.out.println("[DEBUG] sendJsonEnvelope 异常: " + t);
            t.printStackTrace();
            // fallback to raw
            out.println(payload);
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
            out.println("SETTINGS " + settings);
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

    /**
     * Request the peer to allow forcing a move: used when client cannot validate a move
     * produced by server's newer features. Only send from client to server.
     */
    public void sendForceMoveRequest(int fromRow, int fromCol, int toRow, int toCol, long seq, int historyLen) {
        if (out == null) return;
        try {
            JsonObject jo = new JsonObject();
            jo.addProperty("cmd", "FORCE_MOVE_REQUEST");
            com.google.gson.JsonArray fromArr = new com.google.gson.JsonArray(); fromArr.add(fromRow); fromArr.add(fromCol);
            com.google.gson.JsonArray toArr = new com.google.gson.JsonArray(); toArr.add(toRow); toArr.add(toCol);
            jo.add("from", fromArr);
            jo.add("to", toArr);
            jo.addProperty("seq", seq);
            jo.addProperty("historyLen", historyLen);
            sendJsonEnvelope(jo);
            System.out.println("[DEBUG] 发送 JSON FORCE_MOVE_REQUEST: " + jo);
        } catch (Throwable t) {
            // fallback to legacy
            System.out.println("[DEBUG] sendForceMoveRequest fallback legacy");
            out.println("FORCE_MOVE_REQUEST " + fromRow + " " + fromCol + " " + toRow + " " + toCol + " " + seq + " " + historyLen);
        }
    }

    /**
     * Send confirm frame so the requester will force-apply the move.
     */
    public void sendForceMoveConfirm(int fromRow, int fromCol, int toRow, int toCol, long seq) {
        if (out == null) return;
        try {
            JsonObject jo = new JsonObject();
            jo.addProperty("cmd", "FORCE_MOVE_CONFIRM");
            com.google.gson.JsonArray fromA = new com.google.gson.JsonArray(); fromA.add(fromRow); fromA.add(fromCol);
            com.google.gson.JsonArray toA = new com.google.gson.JsonArray(); toA.add(toRow); toA.add(toCol);
            jo.add("from", fromA);
            jo.add("to", toA);
            jo.addProperty("seq", seq);
            sendJsonEnvelope(jo);
            System.out.println("[DEBUG] 发送 JSON FORCE_MOVE_CONFIRM: " + jo);
        } catch (Throwable t) {
            System.out.println("[DEBUG] sendForceMoveConfirm fallback legacy");
            out.println("FORCE_MOVE_CONFIRM " + fromRow + " " + fromCol + " " + toRow + " " + toCol + " " + seq);
        }
    }

    /**
     * Send a rejection for a previously requested force-move.
     */
    public void sendForceMoveReject(int fromRow, int fromCol, int toRow, int toCol, long seq, String reason) {
        if (out == null) return;
        try {
            JsonObject jo = new JsonObject();
            jo.addProperty("cmd", "FORCE_MOVE_REJECT");
            com.google.gson.JsonArray farr = new com.google.gson.JsonArray(); farr.add(fromRow); farr.add(fromCol);
            com.google.gson.JsonArray tarr = new com.google.gson.JsonArray(); tarr.add(toRow); tarr.add(toCol);
            jo.add("from", farr);
            jo.add("to", tarr);
            jo.addProperty("seq", seq);
            if (reason != null) jo.addProperty("reason", reason);
            sendJsonEnvelope(jo);
            System.out.println("[DEBUG] 发送 JSON FORCE_MOVE_REJECT: " + jo);
        } catch (Throwable t) {
            System.out.println("[DEBUG] sendForceMoveReject fallback legacy");
            out.println("FORCE_MOVE_REJECT " + fromRow + " " + fromCol + " " + toRow + " " + toCol + " " + seq + " " + (reason == null ? "" : reason));
        }
    }
}
