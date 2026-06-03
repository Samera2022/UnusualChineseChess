package io.github.samera2022.chinese_chess.app.ui;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.samera2022.chinese_chess.api.net.NetModeController;
import io.github.samera2022.chinese_chess.api.net.NetworkSession;

/**
 * 局域网大厅 — 复用桌面端 NetworkSession (Raw TCP) + mDNS 设备发现
 */
public class LobbyPanel extends LinearLayout {

    public interface LobbyCallback {
        void onConnected(String host, int port, boolean isHost);
        void onDisconnected();
        void onError(String message);
    }

    private static final int SERVER_PORT = 9876;

    private LobbyCallback callback;
    private final NetModeController netController = new NetModeController();
    private NsdManager nsdManager;

    private final TextView statusText;
    private final EditText ipInput;
    private final Button hostBtn, joinBtn, localBtn;

    public LobbyPanel(Context context) {
        this(context, null);
    }

    public LobbyPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LobbyPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        statusText = new TextView(context);
        statusText.setText("准备就绪");
        addView(statusText);

        ipInput = new EditText(context);
        ipInput.setHint("输入对方 IP 地址");
        addView(ipInput);

        localBtn = new Button(context);
        localBtn.setText("本地游戏");
        localBtn.setOnClickListener(v -> startLocalGame());
        addView(localBtn);

        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(HORIZONTAL);

        hostBtn = new Button(context);
        hostBtn.setText("创建房间");
        hostBtn.setOnClickListener(v -> startHost());
        btnRow.addView(hostBtn);

        joinBtn = new Button(context);
        joinBtn.setText("加入房间");
        joinBtn.setOnClickListener(v -> joinRoom());
        btnRow.addView(joinBtn);

        addView(btnRow);

        nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void setLobbyCallback(LobbyCallback cb) { this.callback = cb; }

    private void startLocalGame() {
        // 直接启动本地游戏，不经过网络
        android.content.Intent intent = new android.content.Intent(getContext(),
            io.github.samera2022.chinese_chess.app.ui.GameActivity.class);
        getContext().startActivity(intent);
    }

    private void startHost() {
        try {
            netController.host(SERVER_PORT);
            String ip = netController.getLocalIPv4();
            statusText.setText("主机已创建: " + ip + ":" + SERVER_PORT);
            registerNsdService();
            if (callback != null) callback.onConnected(ip, SERVER_PORT, true);
        } catch (Exception e) {
            statusText.setText("创建失败: " + e.getMessage());
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    private void joinRoom() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            Toast.makeText(getContext(), "请输入 IP 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            netController.join(ip, SERVER_PORT);
            statusText.setText("已连接到: " + ip);
            if (callback != null) callback.onConnected(ip, SERVER_PORT, false);
        } catch (Exception e) {
            statusText.setText("连接失败: " + e.getMessage());
            if (callback != null) callback.onError(e.getMessage());
        }
    }

    private void registerNsdService() {
        if (nsdManager == null) return;
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName("UCC-" + netController.getLocalIPv4());
        info.setServiceType("_ucc-chess._tcp.");
        info.setPort(SERVER_PORT);
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, null);
    }

    public void cleanup() {
        try { netController.getSession().close(); } catch (Exception ignored) {}
    }
}
