package io.github.samera2022.chinese_chess.app.ui;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.app.net.WsClient;

import javax.swing.*;
import java.awt.*;

/**
 * 大厅面板：连接服务端、创建/加入房间、快速匹配
 */
public class LobbyPanel extends JPanel {

    private final JTextField hostField = new JTextField("127.0.0.1", 15);
    private final JTextField portField = new JTextField("8080", 6);
    private final JButton connectBtn = new JButton("连接");
    private final JButton createRoomBtn = new JButton("创建房间");
    private final JButton joinRoomBtn = new JButton("加入房间");
    private final JTextField roomIdField = new JTextField(10);
    private final JButton matchBtn = new JButton("快速匹配");
    private final JLabel statusLabel = new JLabel("未连接");

    private final WsClient wsClient = new WsClient();

    public LobbyPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 连接区域
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        connectPanel.setBorder(BorderFactory.createTitledBorder("服务端连接"));
        connectPanel.add(new JLabel("地址:"));
        connectPanel.add(hostField);
        connectPanel.add(new JLabel("端口:"));
        connectPanel.add(portField);
        connectPanel.add(connectBtn);

        // 房间操作区域
        JPanel roomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        roomPanel.setBorder(BorderFactory.createTitledBorder("房间操作"));
        roomPanel.add(createRoomBtn);
        roomPanel.add(new JLabel("房间号:"));
        roomPanel.add(roomIdField);
        roomPanel.add(joinRoomBtn);
        roomPanel.add(matchBtn);

        // 状态区域
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(new JLabel("状态:"));
        statusLabel.setForeground(Color.RED);
        statusPanel.add(statusLabel);

        add(connectPanel, BorderLayout.NORTH);
        add(roomPanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        // 事件绑定
        connectBtn.addActionListener(e -> connect());
        createRoomBtn.addActionListener(e -> sendCommand("create_room", new JsonObject()));
        joinRoomBtn.addActionListener(e -> {
            String roomId = roomIdField.getText().trim();
            if (!roomId.isEmpty()) {
                JsonObject data = new JsonObject();
                data.addProperty("roomId", roomId);
                sendCommand("join_room", data);
            }
        });
        matchBtn.addActionListener(e -> sendCommand("match", new JsonObject()));

        wsClient.setOnMessage(msg -> {
            SwingUtilities.invokeLater(() -> statusLabel.setText("收到消息: " + msg));
        });
        wsClient.setOnError(err -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("错误: " + err);
                statusLabel.setForeground(Color.RED);
            });
        });
        wsClient.setOnClose(() -> {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("已断开");
                statusLabel.setForeground(Color.RED);
            });
        });
    }

    private void connect() {
        try {
            String host = hostField.getText().trim();
            int port = Integer.parseInt(portField.getText().trim());
            wsClient.connect(host, port);
            statusLabel.setText("已连接: " + host + ":" + port);
            statusLabel.setForeground(Color.GREEN);
            connectBtn.setEnabled(false);
        } catch (Exception e) {
            statusLabel.setText("连接失败: " + e.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private void sendCommand(String cmd, JsonObject data) {
        if (!wsClient.isConnected()) {
            statusLabel.setText("请先连接服务端");
            statusLabel.setForeground(Color.RED);
            return;
        }
        wsClient.sendCommand(cmd, data);
        statusLabel.setText("已发送: " + cmd);
    }
}
