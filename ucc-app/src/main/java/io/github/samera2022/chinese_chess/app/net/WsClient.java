package io.github.samera2022.chinese_chess.app.net;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Java 11 java.net.http.WebSocket 客户端
 */
public class WsClient {

    private WebSocket webSocket;
    private final HttpClient httpClient;
    private Consumer<String> onMessageCallback;
    private Consumer<String> onErrorCallback;
    private Runnable onCloseCallback;
    private volatile boolean connected = false;

    public WsClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    private static final java.util.concurrent.TimeUnit SEC = java.util.concurrent.TimeUnit.SECONDS;

    public void connect(String host, int port) {
        connect(host, port, "/chess");
    }

    public void connect(String host, int port, String path) {
        URI uri = URI.create(String.format("ws://%s:%d%s", host, port, path));
        CountDownLatch latch = new CountDownLatch(1);

        httpClient.newWebSocketBuilder()
            .buildAsync(uri, new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    WsClient.this.webSocket = webSocket;
                    connected = true;
                    latch.countDown();
                    WebSocket.Listener.super.onOpen(webSocket);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    if (onMessageCallback != null) {
                        onMessageCallback.accept(data.toString());
                    }
                    return WebSocket.Listener.super.onText(webSocket, data, last);
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    connected = false;
                    latch.countDown();
                    if (onErrorCallback != null) {
                        onErrorCallback.accept(error.getMessage());
                    }
                    WebSocket.Listener.super.onError(webSocket, error);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    connected = false;
                    if (onCloseCallback != null) {
                        onCloseCallback.run();
                    }
                    return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                }
            });

        try {
            if (!latch.await(5, SEC)) {
                connected = false;
                throw new RuntimeException("WebSocket connection timeout (5s)");
            }
            if (!connected) {
                throw new RuntimeException("WebSocket connection failed");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WebSocket connection interrupted", e);
        }
    }

    public void sendCommand(String cmd, JsonObject data) {
        if (webSocket == null || !connected) {
            throw new IllegalStateException("WebSocket not connected");
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("cmd", cmd);
        msg.add("data", data);
        webSocket.sendText(msg.toString(), true);
    }

    public void sendText(String text) {
        if (webSocket != null && connected) {
            webSocket.sendText(text, true);
        }
    }

    public void close() {
        if (webSocket != null) {
            connected = false;
            webSocket.sendClose(1000, "client closing");
        }
    }

    public boolean isConnected() { return connected; }

    public void setOnMessage(Consumer<String> callback) { this.onMessageCallback = callback; }
    public void setOnError(Consumer<String> callback) { this.onErrorCallback = callback; }
    public void setOnClose(Runnable callback) { this.onCloseCallback = callback; }
}
