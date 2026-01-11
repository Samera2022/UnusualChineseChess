package io.github.samera2022.chinese_chess.ui;

import com.google.gson.JsonObject;
import io.github.samera2022.chinese_chess.UpdateInfo;
import io.github.samera2022.chinese_chess.engine.GameEngine;
import io.github.samera2022.chinese_chess.net.NetModeController;
import io.github.samera2022.chinese_chess.net.NetworkSession;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.*;
import java.awt.event.*;
import java.util.function.BooleanSupplier;

public class NetworkSidePanel extends JPanel {
    private final NetModeController netController;
    private final BoardPanel boardPanel;
    private final GameEngine gameEngine;
    private final JButton undoButton;

    private final DefaultListModel<String> infoModel = new DefaultListModel<>();
    private final JList<String> infoList = new JList<>(infoModel);
    private final JTextArea logArea = new JTextArea(6, 16);

    private final JButton hostBtn = new JButton("创建联机");
    private final JButton joinBtn = new JButton("加入联机");
    private final JButton disconnectBtn = new JButton("断开联机");
    private final JCheckBox allowLocalFlipCheck = new JCheckBox();
    private final JToggleButton localRedBtn = new JToggleButton("本地红方", true);
    private final JButton exportBtn = new JButton("导出残局");
    private final JButton importBtn = new JButton("导入残局");

    private final JLabel connStatusLabel = new JLabel("未连接");
    private Long lastPingSent = null;
    // 维护最近的 RTT 样本（最多保存20个）
    private final java.util.List<Long> rttHistory = new ArrayList<>();

    // Tooltip JWindow
    private JWindow tooltipWindow;
    private JLabel tooltipLabel;
    // local and peer versions for display
    private String localVersion = null;
    private String peerVersion = null;

    // 新增：外部回调，用于切换左侧的设置组件，并查询当前可见性
    private final Runnable onToggleSettings;
    private final BooleanSupplier isSettingsVisible;
    private final Runnable onExportGame;
    private final Runnable onImportGame;

    // 持方同步指令常量
    private static final String SYNC_SIDE_CMD = "SYNC_SIDE";
    private static final String SYNC_SIDE_AUTH_CMD = "SYNC_SIDE_AUTH";

    // 持方切换权：true=本地有权，false=对方有权
    private boolean hasSideAuth = false;
    // 仅主机端调用：根据当前状态计算并同步切换权
    private void updateAndSyncSideAuth() {
        boolean allowFlip = allowLocalFlipCheck.isSelected();
        boolean isHost = netController.isHost();
        if (isHost) {
            hasSideAuth = allowFlip;
            syncSideAuth();
        }
    }

    public NetworkSidePanel(NetModeController netController, BoardPanel boardPanel, GameEngine gameEngine, JButton undoButton,
                            Runnable onToggleSettings, BooleanSupplier isSettingsVisible,
                            Runnable onExportGame, Runnable onImportGame) {
        this.netController = Objects.requireNonNull(netController);
        this.boardPanel = Objects.requireNonNull(boardPanel);
        this.gameEngine = Objects.requireNonNull(gameEngine);
        this.undoButton = Objects.requireNonNull(undoButton);
        this.onToggleSettings = Objects.requireNonNull(onToggleSettings);
        this.isSettingsVisible = Objects.requireNonNull(isSettingsVisible);
        this.onExportGame = Objects.requireNonNull(onExportGame);
        this.onImportGame = Objects.requireNonNull(onImportGame);

        setLayout(new BorderLayout(6, 6));
        setPreferredSize(new Dimension(130, 0));
        setBorder(new TitledBorder("联机与信息"));

        // 顶部控制区
        JPanel controls = new JPanel(new GridLayout(0, 1, 4, 4));
        controls.add(hostBtn);
        controls.add(joinBtn);
        JPanel localSidePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        allowLocalFlipCheck.setSelected(false);
        allowLocalFlipCheck.setToolTipText("勾选以强制翻转棋盘来切换红/黑下棋");
        allowLocalFlipCheck.setMargin(new Insets(0, 0, 0, 0));
        allowLocalFlipCheck.addActionListener(e -> applyLocalSide(localRedBtn.isSelected()));
        localSidePanel.add(allowLocalFlipCheck);
        localSidePanel.add(localRedBtn);
        controls.add(localSidePanel);
        controls.add(disconnectBtn);
        controls.add(exportBtn);
        controls.add(importBtn);
        add(controls, BorderLayout.NORTH);

        // 设置"本地红方"按钮的提示文字
        localRedBtn.setToolTipText("切换本地控制的方：红方在下 / 黑方在下");

        // 设置"本地红方"按钮的监听器
        localRedBtn.addActionListener(e -> applyLocalSide(localRedBtn.isSelected()));
        applyLocalSide(true);

        // 中部信息列表 + 顶部"玩法"按钮 + 连接状态
        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));

        // 顶部按钮区域（包含显示玩法、更新日志、关于作者）
        JPanel topButtonsPanel = new JPanel(new GridLayout(0, 1, 4, 4));

        JToggleButton settingsBtn = new JToggleButton();
        settingsBtn.setFont(new Font("SimHei", Font.PLAIN, 12));
        // 让按钮与上方按钮同高度，宽度由布局撑满
        Dimension btnSize = hostBtn.getPreferredSize();
        settingsBtn.setPreferredSize(new Dimension(btnSize.width, btnSize.height));
        settingsBtn.addActionListener(e -> {
            onToggleSettings.run();
            boolean visible = isSettingsVisible.getAsBoolean();
            settingsBtn.setSelected(visible);
            settingsBtn.setText(visible ? "收起玩法" : "自定义玩法");
        });
        boolean initialSettingsVisible = isSettingsVisible.getAsBoolean();
        settingsBtn.setSelected(initialSettingsVisible);
        settingsBtn.setText(initialSettingsVisible ? "收起玩法" : "自定义玩法");
        topButtonsPanel.add(settingsBtn);

        // 新增：更新日志按钮
        JButton changelogBtn = new JButton("更新日志");
        changelogBtn.setFont(new Font("SimHei", Font.PLAIN, 12));
        changelogBtn.addActionListener(e -> new UpdateInfoDialog().setVisible(true));
        topButtonsPanel.add(changelogBtn);

        // 新增：关于作者按钮
        JButton aboutBtn = new JButton("关于作者");
        aboutBtn.setFont(new Font("SimHei", Font.PLAIN, 12));
        aboutBtn.addActionListener(e -> new AboutDialog().setVisible(true));
        topButtonsPanel.add(aboutBtn);

        centerPanel.add(topButtonsPanel, BorderLayout.NORTH);

        // 信息列表与状态
        infoList.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        JScrollPane infoScroll = new JScrollPane(infoList);
        centerPanel.add(infoScroll, BorderLayout.CENTER);
        connStatusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        connStatusLabel.setFont(new Font("SimHei", Font.PLAIN, 12));
        centerPanel.add(connStatusLabel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);

        // 底部日志区
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder("网络日志"));
        add(logScroll, BorderLayout.SOUTH);

        // 初始按钮状态
        disconnectBtn.setEnabled(false);

        // 绑定事件（监听由外部总控设置，避免覆盖）
        bindActions();

        // 每5秒刷新一次状态与延迟（使用 Swing Timer）
        javax.swing.Timer timer = new javax.swing.Timer(5000, e -> refreshStatus());
        timer.setRepeats(true);
        timer.start();

        // 安装鼠标提示窗口（仅在“未连接”标签上）
        installQualityTooltip();

        setupSideSync();
    }

    private void installQualityTooltip() {
        tooltipWindow = new JWindow(SwingUtilities.getWindowAncestor(this));
        tooltipLabel = new JLabel();
        tooltipLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100,100,100)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        tooltipLabel.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        tooltipWindow.getContentPane().add(tooltipLabel);
        tooltipWindow.setAlwaysOnTop(true);

        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateTooltipContent();
                Point p = e.getLocationOnScreen();
                tooltipWindow.pack();
                tooltipWindow.setLocation(p.x + 12, p.y + 12);
                tooltipWindow.setVisible(true);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                tooltipWindow.setVisible(false);
            }
        };
        // 在整面板范围和子组件上都响应移动（便于体验）
        connStatusLabel.addMouseListener(adapter);
        connStatusLabel.addMouseMotionListener(adapter);
    }

    private void updateTooltipContent() {
        boolean connected = netController.isActive();
        String title = connected ? "连接中" : "未连接";
        String role = connected ? (netController.isHost() ? "主机(红)" : "客户端(黑)") : "不可用";
        String addr = netController.displayAddress();

        String detail;
        if (connected && !rttHistory.isEmpty()) {
            long count = rttHistory.size();
            long min = Collections.min(rttHistory);
            long max = Collections.max(rttHistory);
            double avg = rttHistory.stream().mapToLong(Long::longValue).average().orElse(0);
            double mad = rttHistory.stream().mapToDouble(v -> Math.abs(v - avg)).average().orElse(0);
            detail = String.format("延迟: 平均 %.0fms | 最小 %dms | 最大 %dms | 抖动 %.0fms | 样本 %d", avg, min, max, mad, count);
        } else if (connected) {
            detail = "延迟: 测量中...";
        } else {
            detail = "连接未建立";
        }

        String versionLine = "";
        String compatLine = "";
        if (localVersion != null || peerVersion != null) {
            versionLine = "<b>本机版本:</b> " + (localVersion == null ? "未知" : localVersion) + "<br>" +
                    "<b>对端版本:</b> " + (peerVersion == null ? "未知" : peerVersion) + "<br>";
            // compatibility indicator
            if (localVersion != null && peerVersion != null) {
                try {
                    if (UpdateInfo.isNewer(peerVersion, localVersion)) {
                        compatLine = "<b>兼容性:</b> 对端版本较新，可能不兼容。<br>";
                    } else if (UpdateInfo.isNewer(localVersion, peerVersion)) {
                        compatLine = "<b>兼容性:</b> 本机版本较新。<br>";
                    } else {
                        compatLine = "<b>兼容性:</b> 版本相同。<br>";
                    }
                } catch (Throwable ignored) { }
            }
        }
        String html = "<html>" +
                "<b>状态:</b> " + title + "<br>" +
                "<b>角色:</b> " + role + "<br>" +
                "<b>地址:</b> " + addr + "<br>" +
                "<b>质量:</b> " + detail +
                versionLine + compatLine +
                 "</html>";
        tooltipLabel.setText(html);
        tooltipWindow.getContentPane().setBackground(new Color(255, 255, 225));
    }

    public void setLocalVersion(String v) {
        this.localVersion = v;
    }

    public void setPeerVersion(String v) {
        this.peerVersion = v;
    }

    private void refreshStatus() {
        boolean connected = netController.isActive();
        if (!connected) {
            connStatusLabel.setText("未连接");
            return;
        }
        // 触发一次PING
        lastPingSent = System.currentTimeMillis();
        netController.getSession().sendPing();
        // 显示正在测量
        connStatusLabel.setText("已连接 - 测量中...");
    }

    private void bindActions() {
        hostBtn.addActionListener(e -> onHost());
        joinBtn.addActionListener(e -> onJoin());
        disconnectBtn.addActionListener(e -> onDisconnect());
        exportBtn.addActionListener(e -> onExportGame.run());
        importBtn.addActionListener(e -> onImportGame.run());
    }

    private void onHost() {
        String portStr = JOptionPane.showInputDialog(this, "请输入端口", "12345");
        if (portStr == null || portStr.trim().isEmpty()) return;
        try {
            int port = Integer.parseInt(portStr.trim());
            // 删除“请选择本地执子方”的弹窗，主机默认可切换
            netController.host(port);
            // 默认红方，可根据主机操作切换
            applyLocalSide(true);
            localRedBtn.setSelected(true);
            undoButton.setEnabled(false);
            disconnectBtn.setEnabled(true);

            infoModel.clear();
            addInfo("本机地址: " + netController.getLocalIPv4());
            addInfo("监听端口: " + port);
            addInfo("等待对方连接...");
            log("Host started at " + netController.getLocalIPv4() + ":" + port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "创建失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            log("Host error: " + ex.getMessage());
        }
    }

    private void onJoin() {
        JTextField hostField = new JTextField("127.0.0.1");
        JTextField portField = new JTextField("12345");
        JPanel p = new JPanel(new GridLayout(0,1));
        p.add(new JLabel("主机地址:")); p.add(hostField);
        p.add(new JLabel("端口:")); p.add(portField);
        int ok = JOptionPane.showConfirmDialog(this, p, "加入联机", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;
        try {
            int port = Integer.parseInt(portField.getText().trim());
            String host = hostField.getText().trim();
            netController.join(host, port);
            // 客户端执黑
            applyLocalSide(false);
            undoButton.setEnabled(false);
            disconnectBtn.setEnabled(true);

            infoModel.clear();
            addInfo("连接到: " + host);
            addInfo("端口: " + port);
            log("Client connecting to " + host + ":" + port);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "加入失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            log("Join error: " + ex.getMessage());
        }
    }

    private void onDisconnect() {
        try {
            if (netController.isActive()) {
                netController.getSession().disconnect();
            }
        } finally {
            undoButton.setEnabled(true);
            boardPanel.setLocalControlsRed(null);
            disconnectBtn.setEnabled(false);
            // copy 按钮仍可以复制当前记录的地址（若有）
            log("Disconnected locally");
            addInfo("已断开本地会话");
        }
    }


    // 监听由外部设置（ChineseChessFrame 内）以统一联机逻辑

    private void addInfo(String text) {
        infoModel.addElement(text);
        int last = infoModel.size() - 1;
        if (last >= 0) infoList.ensureIndexIsVisible(last);
    }

    private void log(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    public void onConnected(String peerInfo) {
        addInfo("已连接: " + peerInfo);
        connStatusLabel.setText("已连接");
        log("Connected: " + peerInfo);
        // 客户端禁用勾选框，只有主机能操作
        boolean isHost = netController.isHost();
        allowLocalFlipCheck.setEnabled(isHost);
        // 主机连接时同步切换权和持方状态
        if (isHost) {
            updateAndSyncSideAuth();
            if (netController.isActive()) {
                syncSide(localRedBtn.isSelected());
            }
        }
    }

    public void onDisconnected(String reason) {
        addInfo("断开: " + reason);
        undoButton.setEnabled(true);
        boardPanel.setLocalControlsRed(null);
        disconnectBtn.setEnabled(false);
        connStatusLabel.setText("未连接");
        tooltipWindow.setVisible(false);
        log("Disconnected: " + reason);
        // 恢复 allowLocalFlipCheck 可用性
        allowLocalFlipCheck.setEnabled(true);
        hasSideAuth = false;
    }

    public void onPong(long sentMillis, long rttMillis) {
        connStatusLabel.setText("已连接 - 延迟 " + rttMillis + "ms");
        rttHistory.add(rttMillis);
        if (rttHistory.size() > 20) {
            rttHistory.remove(0);
        }
    }

    // 同步持方切换权
    private void syncSideAuth() {
        if (netController.isActive()) {
            boolean allowFlip = allowLocalFlipCheck.isSelected();
            boolean isHost = netController.isHost();
            // 主机发送切换权：主机勾选时主机有权（客户端收到false），未勾选时客户端有权（客户端收到true）
            if (isHost) {
                boolean clientAuth = !allowFlip; // 客户端切换权：主机未勾选时为true
                netController.getSession().sendSettings(makeSideAuthJson(clientAuth));
            }
        }
    }

    private JsonObject makeSideAuthJson(boolean auth) {
        JsonObject obj = new JsonObject();
        obj.addProperty("cmd", SYNC_SIDE_AUTH_CMD);
        obj.addProperty("auth", auth);
        return obj;
    }

    // 同步持方状态
    private void syncSide(boolean isRed) {
        if (netController.isActive()) {
            boolean isHost = netController.isHost();
            // 有权的一方发送持方状态
            JsonObject obj = new JsonObject();
            obj.addProperty("cmd", SYNC_SIDE_CMD);
            obj.addProperty("side", isRed ? "red" : "black");
            netController.getSession().sendSettings(obj);
        }
    }

    // 处理收到的持方同步指令
    public void onSettingsReceived(JsonObject settings) {
        if (!settings.has("cmd")) return;
        String cmd = settings.get("cmd").getAsString();
        if (SYNC_SIDE_CMD.equals(cmd)) {
            String side = settings.get("side").getAsString();
            boolean peerIsRed = "red".equals(side);
            // 对方的持方，本地切换为相反持方（互斥）
            boolean localIsRed = !peerIsRed;
            localRedBtn.setSelected(localIsRed);
            applyLocalSide(localIsRed, true); // 标记为网络同步
            boardPanel.repaint();
        } else if (SYNC_SIDE_AUTH_CMD.equals(cmd)) {
            // 客户端收到切换权指令
            boolean clientAuth = settings.get("auth").getAsBoolean();
            hasSideAuth = clientAuth;
            localRedBtn.setEnabled(hasSideAuth);
            // 客户端勾选框启用/禁用逻辑：
            // 主机未勾选 -> 客户端有权 -> 客户端勾选框启用且显示为勾选
            // 主机勾选 -> 客户端无权 -> 客户端勾选框禁用且强制为false
            allowLocalFlipCheck.setEnabled(clientAuth);
            allowLocalFlipCheck.setSelected(clientAuth);
        }
    }

    private void applyLocalSide(boolean preferredRed) {
        applyLocalSide(preferredRed, false);
    }

    private void applyLocalSide(boolean preferredRed, boolean isNetworkSync) {
        // 获取本地复选框状态
        boolean allowFlip = allowLocalFlipCheck.isSelected();

        // 1. 计算实际的执方 (红/黑)
        // 网络同步时直接使用 preferredRed；
        // 本地操作时，如果没勾选"允许翻转"，则强制复位为红方
        boolean effectiveRed = isNetworkSync ? preferredRed : (allowFlip ? preferredRed : true);

        // 2. 更新按钮 UI
        localRedBtn.setEnabled(hasSideAuth);
        localRedBtn.setSelected(effectiveRed);
        localRedBtn.setText(effectiveRed ? "本地红方" : "本地黑方");

        // 3. 修正核心逻辑：确定是否启用翻转/控制逻辑
        // 如果是网络同步过来的指令，无论本地复选框是否勾选，都必须生效
        boolean shouldApplyFlipLogic = allowFlip || isNetworkSync;

        // 4. 应用到棋盘
        // 只有在 (启用翻转逻辑 且 当前是黑方) 时才翻转棋盘
        boardPanel.setBoardFlipped(shouldApplyFlipLogic && !effectiveRed);

        // 5. 设置本地控制权
        // 如果启用了逻辑，则绑定红/黑控制权；否则设为 null (默认状态)
        boardPanel.setLocalControlsRed(shouldApplyFlipLogic ? effectiveRed : true);
    }

    // 监听"本地X方"切换
    private void setupSideSync() {
        // 勾选框变化时的处理
        allowLocalFlipCheck.addActionListener(e -> {
            boolean isHost = netController.isHost();
            if (isHost) {
                // 主机端：同步切换权和当前持方状态
                updateAndSyncSideAuth();
                applyLocalSide(localRedBtn.isSelected());
                // 主机端同步当前持方状态
                if (netController.isActive()) {
                    syncSide(localRedBtn.isSelected());
                }
            } else {
                // 客户端：更新自己的 hasSideAuth 并刷新 UI
                hasSideAuth = allowLocalFlipCheck.isSelected();
                applyLocalSide(localRedBtn.isSelected());
                // 客户端勾选/取消勾选后，同步当前持方状态给主机
                if (netController.isActive()) {
                    syncSide(localRedBtn.isSelected());
                }
            }
        });
        // 持方按钮变化时，只有有权的一方才同步
        localRedBtn.addActionListener(e -> {
            if (hasSideAuth && netController.isActive()) {
                syncSide(localRedBtn.isSelected());
            }
        });
    }
}
