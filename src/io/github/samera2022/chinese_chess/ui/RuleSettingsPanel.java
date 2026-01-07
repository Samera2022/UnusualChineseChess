package io.github.samera2022.chinese_chess.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

/**
 * 玩法设置面板：在左侧展开显示，配置项会接入 GameEngine。
 */
public class RuleSettingsPanel extends JPanel {
    public interface SettingsBinder {
        void setAllowUndo(boolean allowUndo);
        boolean isAllowUndo();
        // 特殊玩法
        void setAllowFlyingGeneral(boolean allow);
        void setPawnCanRetreat(boolean allow);
        void setNoRiverLimit(boolean noLimit);
        void setAdvisorCanLeave(boolean allow);
        void setInternationalKing(boolean allow);
        void setPawnPromotion(boolean allow);
        void setAllowOwnBaseLine(boolean allow);
        void setAllowInsideRetreat(boolean allow);
        void setInternationalAdvisor(boolean allow);
        void setAllowElephantCrossRiver(boolean allow);
        void setAllowAdvisorCrossRiver(boolean allow);
        void setAllowKingCrossRiver(boolean allow);
        void setLeftRightConnected(boolean allow);
        void setUnblockPiece(boolean allow);
        void setUnblockHorseLeg(boolean allow);
        void setUnblockElephantEye(boolean allow);
        void setNoRiverLimitPawn(boolean allow);
        boolean isAllowFlyingGeneral();
        boolean isPawnCanRetreat();
        boolean isNoRiverLimit();
        boolean isAdvisorCanLeave();
        boolean isInternationalKing();
        boolean isPawnPromotion();
        boolean isAllowOwnBaseLine();
        boolean isAllowInsideRetreat();
        boolean isInternationalAdvisor();
        boolean isAllowElephantCrossRiver();
        boolean isAllowAdvisorCrossRiver();
        boolean isAllowKingCrossRiver();
        boolean isLeftRightConnected();
        boolean isUnblockPiece();
        boolean isUnblockHorseLeg();
        boolean isUnblockElephantEye();
        boolean isNoRiverLimitPawn();
    }

    public void refreshFromBinder() {
        Runnable apply = (Runnable) getClientProperty("applyState");
        if (apply != null) apply.run();
    }

    public RuleSettingsPanel() {
        setLayout(new BorderLayout(6,6));
        setBorder(new TitledBorder("玩法设置"));
        setPreferredSize(new Dimension(220, 0));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JCheckBox chkAllowUndo = new JCheckBox("允许悔棋");
        // 保证在 BoxLayout 中左对齐
        chkAllowUndo.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(chkAllowUndo);

        form.add(Box.createVerticalStrut(10));

        // 延申玩法分组（可折叠）
        JPanel special = new JPanel();
        special.setLayout(new BoxLayout(special, BoxLayout.Y_AXIS));
        // 作为 form 的子组件，整体左对齐
        special.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 创建可点击的TitledBorder
        TitledBorder titledBorder = new TitledBorder("▼ 延申玩法");
        special.setBorder(titledBorder);

        // 创建内容面板，用于折叠/展开
        JPanel specialContent = new JPanel();
        specialContent.setLayout(new BoxLayout(specialContent, BoxLayout.Y_AXIS));
        specialContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 标记用于折叠状态
        final boolean[] isExpanded = {true};

        // 使TitledBorder的标题可点击
        special.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // 检查是否点击了标题区域（大约在顶部20像素内）
                if (e.getY() < 20) {
                    isExpanded[0] = !isExpanded[0];
                    specialContent.setVisible(isExpanded[0]);
                    titledBorder.setTitle(isExpanded[0] ? "▼ 延申玩法" : "▶ 延申玩法");
                    // 折叠时设置为30像素高度，展开时根据内容设置高度
                    if (isExpanded[0]) {
                        special.setMaximumSize(new Dimension(Integer.MAX_VALUE, specialContent.getPreferredSize().height + 20));
                    } else {
                        special.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                    }
                    special.revalidate();
                    special.repaint();
                }
            }
        });
        JCheckBox chkFlyingGeneral = new JCheckBox("允许飞将");
        JCheckBox chkPawnBack = new JCheckBox("兵卒可后退");
        JCheckBox chkNoRiverLimit = new JCheckBox("取消河界");
        JCheckBox chkAdvisorCanLeave = new JCheckBox("允许出仕");
        JCheckBox chkInternationalKing = new JCheckBox("国际化将军");
        JCheckBox chkPawnPromotion = new JCheckBox("兵卒底线晋升");
        JCheckBox chkAllowOwnBaseLine = new JCheckBox("允许己方底线晋升");
        JCheckBox chkAllowInsideRetreat = new JCheckBox("允许境内后退");
        JCheckBox chkInternationalAdvisor = new JCheckBox("国际化仕");
        JCheckBox chkAllowElephantCrossRiver = new JCheckBox("相");
        JCheckBox chkAllowAdvisorCrossRiver = new JCheckBox("仕");
        JCheckBox chkAllowKingCrossRiver = new JCheckBox("帥");
        JCheckBox chkLeftRightConnected = new JCheckBox("左右连通");

        // 在 Y 轴 BoxLayout 中，逐项左对齐
        chkFlyingGeneral.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkPawnBack.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkNoRiverLimit.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkAdvisorCanLeave.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkInternationalKing.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkPawnPromotion.setAlignmentX(Component.LEFT_ALIGNMENT);

        specialContent.add(chkFlyingGeneral);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkAdvisorCanLeave);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkInternationalKing);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkInternationalAdvisor);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkNoRiverLimit);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkPawnBack);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkPawnPromotion);
        specialContent.add(Box.createVerticalStrut(6));
        // 删除所有和“相”、“仕”、“帥”、“兵”有关的显示和逻辑
        // 只保留“取消河界”主勾选框
        // specialContent.add(chkNoRiverLimit);
        // 删除“相”、“仕”、“帥”、“兵”相关的缩进面板和勾选框
        // 新增：取消卡子及其子选项
        JCheckBox chkUnblockPiece = new JCheckBox("取消卡子");
        chkUnblockPiece.setAlignmentX(Component.LEFT_ALIGNMENT);
        specialContent.add(chkUnblockPiece);
        // 缩进子选项（上下排列）
        JPanel indentedUnblockPanel = new JPanel();
        indentedUnblockPanel.setLayout(new BoxLayout(indentedUnblockPanel, BoxLayout.Y_AXIS));
        indentedUnblockPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedUnblockPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        JCheckBox chkUnblockHorseLeg = new JCheckBox("取消卡马脚");
        JCheckBox chkUnblockElephantEye = new JCheckBox("取消卡象眼");
        JPanel horsePanel = new JPanel();
        horsePanel.setLayout(new BoxLayout(horsePanel, BoxLayout.X_AXIS));
        horsePanel.add(Box.createHorizontalStrut(32));
        horsePanel.add(chkUnblockHorseLeg);
        horsePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel elephantPanel = new JPanel();
        elephantPanel.setLayout(new BoxLayout(elephantPanel, BoxLayout.X_AXIS));
        elephantPanel.add(Box.createHorizontalStrut(32));
        elephantPanel.add(chkUnblockElephantEye);
        elephantPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedUnblockPanel.add(horsePanel);
        indentedUnblockPanel.add(Box.createVerticalStrut(2));
        indentedUnblockPanel.add(elephantPanel);
        specialContent.add(indentedUnblockPanel);
        // 依赖关系：主选项未勾选时，子选项禁用且为false
        chkUnblockPiece.addActionListener(e -> {
            boolean enabled = chkUnblockPiece.isSelected();
            chkUnblockHorseLeg.setEnabled(enabled);
            chkUnblockElephantEye.setEnabled(enabled);
            if (!enabled) {
                chkUnblockHorseLeg.setSelected(false);
                chkUnblockElephantEye.setSelected(false);
            }
        });
        // 初始化禁用
        chkUnblockHorseLeg.setEnabled(false);
        chkUnblockElephantEye.setEnabled(false);

        // 新增：取消河界下的“兵”勾选框，和引擎联动
        // JCheckBox chkNoRiverLimitPawn = new JCheckBox("兵");
        // chkNoRiverLimitPawn.setAlignmentX(Component.LEFT_ALIGNMENT);
        // JPanel indentedRiverOptions2 = new JPanel();
        // indentedRiverOptions2.setLayout(new BoxLayout(indentedRiverOptions2, BoxLayout.X_AXIS));
        // indentedRiverOptions2.add(Box.createHorizontalStrut(20));
        // indentedRiverOptions2.add(chkNoRiverLimitPawn);
        // indentedRiverOptions2.add(Box.createHorizontalGlue());
        // indentedRiverOptions2.setAlignmentX(Component.LEFT_ALIGNMENT);
        // indentedRiverOptions2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        // specialContent.add(Box.createVerticalStrut(6));
        // specialContent.add(indentedRiverOptions2);
        // 联动逻辑：只有“取消河界”勾选时“兵”可编辑，否则禁用且为false
        chkNoRiverLimit.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setNoRiverLimit(chkNoRiverLimit.isSelected());
        });
        // ActionListener：同步到引擎
        // chkNoRiverLimitPawn.addActionListener(e -> {
        //     SettingsBinder b = (SettingsBinder) getClientProperty("binder");
        //     if (b != null) b.setNoRiverLimitPawn(chkNoRiverLimitPawn.isSelected());
        // });

        special.add(specialContent);
        form.add(special);

        // 强制计算specialContent的大小，用于初始化special的高度
        SwingUtilities.invokeLater(() -> {
            Dimension contentSize = specialContent.getPreferredSize();
            special.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentSize.height + 30));
            special.revalidate();
        });

        // 增加延申玩法和特殊玩法之间的间距
        form.add(Box.createVerticalStrut(12));

        // 特殊玩法分组（可折叠）
        JPanel special2 = new JPanel();
        special2.setLayout(new BoxLayout(special2, BoxLayout.Y_AXIS));
        // 作为 form 的子组件，整体左对齐
        special2.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 创建可点击的TitledBorder
        TitledBorder titledBorder2 = new TitledBorder("▼ 特殊玩法");
        special2.setBorder(titledBorder2);

        // 创建内容面板，用于折叠/展开
        JPanel special2Content = new JPanel();
        special2Content.setLayout(new BoxLayout(special2Content, BoxLayout.Y_AXIS));
        special2Content.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 标记用于折叠状态
        final boolean[] isExpanded2 = {true};

        // 使TitledBorder的标题可点击
        special2.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // 检查是否点击了标题区域（大约在顶部20像素内）
                if (e.getY() < 20) {
                    isExpanded2[0] = !isExpanded2[0];
                    special2Content.setVisible(isExpanded2[0]);
                    titledBorder2.setTitle(isExpanded2[0] ? "▼ 特殊玩法" : "▶ 特殊玩法");
                    // 折叠时设置为30像素高度，展开时根据内容设置高度
                    if (isExpanded2[0]) {
                        special2.setMaximumSize(new Dimension(Integer.MAX_VALUE, special2Content.getPreferredSize().height + 20));
                    } else {
                        special2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                    }
                    special2.revalidate();
                    special2.repaint();
                }
            }
        });

        special2.add(special2Content);
        form.add(special2);

        // 强制计算special2Content的大小，用于初始化special2的高度
        SwingUtilities.invokeLater(() -> {
            Dimension contentSize = special2Content.getPreferredSize();
            special2.setMaximumSize(new Dimension(Integer.MAX_VALUE, contentSize.height + 30));
            special2.revalidate();
        });

        // 在special2Content中添加"左右连通"复选框
        chkLeftRightConnected.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkLeftRightConnected.setToolTipText("仍在开发中，存在bug。");
        special2Content.add(chkLeftRightConnected);

        // 增加特殊玩法和操作按钮之间的间距
        form.add(Box.createVerticalStrut(12));

        // 操作按钮：复制配置 / 输入配置（在弹窗中点击确定应用）
        JPanel btnBar = new JPanel();
        btnBar.setLayout(new BoxLayout(btnBar, BoxLayout.X_AXIS));
        btnBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton copyBtn = new JButton("复制配置");
        JButton importBtn = new JButton("输入配置");
        btnBar.add(copyBtn);
        btnBar.add(Box.createHorizontalStrut(6));
        btnBar.add(importBtn);
        btnBar.add(Box.createHorizontalGlue());
        btnBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        form.add(btnBar);

        add(new JScrollPane(form), BorderLayout.CENTER);

        // 默认不绑定 GameEngine；由外部调用 bindSettings 进行接入
        putClientProperty("binder", null);

        // 同步复选框与引擎
        chkAllowUndo.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowUndo(chkAllowUndo.isSelected());
        });
        chkFlyingGeneral.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowFlyingGeneral(chkFlyingGeneral.isSelected());
        });
        chkPawnBack.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setPawnCanRetreat(chkPawnBack.isSelected());
        });
        chkNoRiverLimit.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setNoRiverLimit(chkNoRiverLimit.isSelected());
        });
        chkAdvisorCanLeave.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAdvisorCanLeave(chkAdvisorCanLeave.isSelected());
        });
        chkInternationalKing.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setInternationalKing(chkInternationalKing.isSelected());
        });
        chkInternationalAdvisor.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setInternationalAdvisor(chkInternationalAdvisor.isSelected());
        });
        chkPawnPromotion.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setPawnPromotion(chkPawnPromotion.isSelected());
        });
        chkAllowOwnBaseLine.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowOwnBaseLine(chkAllowOwnBaseLine.isSelected());
        });
        chkAllowInsideRetreat.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowInsideRetreat(chkAllowInsideRetreat.isSelected());
        });
        chkAllowElephantCrossRiver.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowElephantCrossRiver(chkAllowElephantCrossRiver.isSelected());
        });
        chkAllowAdvisorCrossRiver.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowAdvisorCrossRiver(chkAllowAdvisorCrossRiver.isSelected());
        });
        chkAllowKingCrossRiver.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowKingCrossRiver(chkAllowKingCrossRiver.isSelected());
        });
        chkLeftRightConnected.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setLeftRightConnected(chkLeftRightConnected.isSelected());
        });
        chkUnblockPiece.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setUnblockPiece(chkUnblockPiece.isSelected());
        });
        chkUnblockHorseLeg.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setUnblockHorseLeg(chkUnblockHorseLeg.isSelected());
        });
        chkUnblockElephantEye.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setUnblockElephantEye(chkUnblockElephantEye.isSelected());
        });

        // 复制配置：构造 JSON 并写入剪贴板
        copyBtn.addActionListener(e -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("allowFlyingGeneral", chkFlyingGeneral.isSelected());
            obj.addProperty("advisorCanLeave", chkAdvisorCanLeave.isSelected());
            obj.addProperty("internationalKing", chkInternationalKing.isSelected());
            obj.addProperty("internationalAdvisor", chkInternationalAdvisor.isSelected());
            obj.addProperty("noRiverLimit", chkNoRiverLimit.isSelected());
            obj.addProperty("allowElephantCrossRiver", chkAllowElephantCrossRiver.isSelected());
            obj.addProperty("allowAdvisorCrossRiver", chkAllowAdvisorCrossRiver.isSelected());
            obj.addProperty("allowKingCrossRiver", chkAllowKingCrossRiver.isSelected());
            obj.addProperty("leftRightConnected", chkLeftRightConnected.isSelected());
            obj.addProperty("pawnCanRetreat", chkPawnBack.isSelected());
            obj.addProperty("allowInsideRetreat", chkAllowInsideRetreat.isSelected());
            obj.addProperty("pawnPromotion", chkPawnPromotion.isSelected());
            obj.addProperty("allowOwnBaseLine", chkAllowOwnBaseLine.isSelected());
            obj.addProperty("unblockPiece", chkUnblockPiece.isSelected());
            obj.addProperty("unblockHorseLeg", chkUnblockHorseLeg.isSelected());
            obj.addProperty("unblockElephantEye", chkUnblockElephantEye.isSelected());
            StringSelection sel = new StringSelection(obj.toString());
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(sel, sel);
        });

        // 输入配置：弹窗输入 JSON，点击“确定”后应用到复选框并同步 binder
        importBtn.addActionListener(e -> {
            JTextArea area = new JTextArea(5, 24);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            // 预填当前配置，方便修改
            JsonObject current = new JsonObject();
            current.addProperty("allowFlyingGeneral", chkFlyingGeneral.isSelected());
            current.addProperty("advisorCanLeave", chkAdvisorCanLeave.isSelected());
            current.addProperty("internationalKing", chkInternationalKing.isSelected());
            current.addProperty("internationalAdvisor", chkInternationalAdvisor.isSelected());
            current.addProperty("noRiverLimit", chkNoRiverLimit.isSelected());
            current.addProperty("allowElephantCrossRiver", chkAllowElephantCrossRiver.isSelected());
            current.addProperty("allowAdvisorCrossRiver", chkAllowAdvisorCrossRiver.isSelected());
            current.addProperty("allowKingCrossRiver", chkAllowKingCrossRiver.isSelected());
            current.addProperty("leftRightConnected", chkLeftRightConnected.isSelected());
            current.addProperty("pawnCanRetreat", chkPawnBack.isSelected());
            current.addProperty("allowInsideRetreat", chkAllowInsideRetreat.isSelected());
            current.addProperty("pawnPromotion", chkPawnPromotion.isSelected());
            current.addProperty("allowOwnBaseLine", chkAllowOwnBaseLine.isSelected());
            current.addProperty("unblockPiece", chkUnblockPiece.isSelected());
            current.addProperty("unblockHorseLeg", chkUnblockHorseLeg.isSelected());
            current.addProperty("unblockElephantEye", chkUnblockElephantEye.isSelected());
            // current.addProperty("noRiverLimitPawn",chkNoRiverLimitPawn.isSelected());
            area.setText(current.toString());

            JScrollPane scroll = new JScrollPane(area);
            int res = JOptionPane.showConfirmDialog(this, scroll, "输入配置 (JSON)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;

            try {
                JsonObject obj = JsonParser.parseString(area.getText().trim()).getAsJsonObject();
                boolean allowFG = obj.has("allowFlyingGeneral") && obj.get("allowFlyingGeneral").getAsBoolean();
                boolean pawnBack = obj.has("pawnCanRetreat") && obj.get("pawnCanRetreat").getAsBoolean();
                boolean noRiver = obj.has("noRiverLimit") && obj.get("noRiverLimit").getAsBoolean();
                boolean advisorLeave = obj.has("advisorCanLeave") && obj.get("advisorCanLeave").getAsBoolean();
                boolean intlKing = obj.has("internationalKing") && obj.get("internationalKing").getAsBoolean();
                boolean pawnProm = obj.has("pawnPromotion") && obj.get("pawnPromotion").getAsBoolean();
                boolean ownBase = obj.has("allowOwnBaseLine") && obj.get("allowOwnBaseLine").getAsBoolean();
                boolean insideRetreat = obj.has("allowInsideRetreat") && obj.get("allowInsideRetreat").getAsBoolean();
                boolean intlAdvisor = obj.has("internationalAdvisor") && obj.get("internationalAdvisor").getAsBoolean();
                boolean elephantCross = obj.has("allowElephantCrossRiver") && obj.get("allowElephantCrossRiver").getAsBoolean();
                boolean advisorCross = obj.has("allowAdvisorCrossRiver") && obj.get("allowAdvisorCrossRiver").getAsBoolean();
                boolean kingCross = obj.has("allowKingCrossRiver") && obj.get("allowKingCrossRiver").getAsBoolean();
                boolean leftRight = obj.has("leftRightConnected") && obj.get("leftRightConnected").getAsBoolean();
                boolean unblockPiece = obj.has("unblockPiece") && obj.get("unblockPiece").getAsBoolean();
                boolean unblockHorseLeg = obj.has("unblockHorseLeg") && obj.get("unblockHorseLeg").getAsBoolean();
                boolean unblockElephantEye = obj.has("unblockElephantEye") && obj.get("unblockElephantEye").getAsBoolean();

                chkFlyingGeneral.setSelected(allowFG);
                chkPawnBack.setSelected(pawnBack);
                chkNoRiverLimit.setSelected(noRiver);
                chkAdvisorCanLeave.setSelected(advisorLeave);
                chkInternationalKing.setSelected(intlKing);
                chkPawnPromotion.setSelected(pawnProm);
                chkAllowOwnBaseLine.setSelected(ownBase);
                chkAllowInsideRetreat.setSelected(insideRetreat);
                chkInternationalAdvisor.setSelected(intlAdvisor);
                chkAllowElephantCrossRiver.setSelected(elephantCross);
                chkAllowAdvisorCrossRiver.setSelected(advisorCross);
                chkAllowKingCrossRiver.setSelected(kingCross);
                chkLeftRightConnected.setSelected(leftRight);
                chkAllowOwnBaseLine.setEnabled(pawnProm);
                chkAllowInsideRetreat.setEnabled(chkPawnBack.isSelected());
                chkAllowElephantCrossRiver.setEnabled(noRiver);
                chkAllowAdvisorCrossRiver.setEnabled(noRiver);
                chkAllowKingCrossRiver.setEnabled(noRiver);
                chkUnblockPiece.setSelected(unblockPiece);
                chkUnblockHorseLeg.setSelected(unblockHorseLeg);
                chkUnblockElephantEye.setSelected(unblockElephantEye);
                SettingsBinder b = (SettingsBinder) getClientProperty("binder");
                if (b != null) {
                    b.setAllowFlyingGeneral(allowFG);
                    b.setPawnCanRetreat(pawnBack);
                    b.setNoRiverLimit(noRiver);
                    b.setAdvisorCanLeave(advisorLeave);
                    b.setInternationalKing(intlKing);
                    b.setPawnPromotion(pawnProm);
                    b.setAllowOwnBaseLine(ownBase);
                    b.setAllowInsideRetreat(insideRetreat);
                    b.setInternationalAdvisor(intlAdvisor);
                    b.setAllowElephantCrossRiver(elephantCross);
                    b.setAllowAdvisorCrossRiver(advisorCross);
                    b.setAllowKingCrossRiver(kingCross);
                    b.setLeftRightConnected(leftRight);
                    b.setUnblockPiece(unblockPiece);
                    b.setUnblockHorseLeg(unblockHorseLeg);
                    b.setUnblockElephantEye(unblockElephantEye);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "解析失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 提供一个方法把引擎状态灌入到UI
        putClientProperty("applyState", (Runnable) () -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) {
                chkNoRiverLimit.setSelected(b.isNoRiverLimit());
                chkPawnBack.setSelected(b.isPawnCanRetreat());
                chkPawnPromotion.setSelected(b.isPawnPromotion());
            }
        });
    }

    // 由外部注入绑定器，并回填状态
    public void bindSettings(SettingsBinder binder) {
        putClientProperty("binder", binder);
        Runnable apply = (Runnable) getClientProperty("applyState");
        if (apply != null) apply.run();
    }
}
