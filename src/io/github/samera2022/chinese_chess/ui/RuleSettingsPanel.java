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
        JCheckBox chkNoRiverLimit = new JCheckBox("允许过河");
        JCheckBox chkAdvisorCanLeave = new JCheckBox("允许出仕");
        JCheckBox chkInternationalKing = new JCheckBox("国际化将军");
        JCheckBox chkPawnPromotion = new JCheckBox("兵卒底线晋升");
        JCheckBox chkAllowOwnBaseLine = new JCheckBox("允许己方底线晋升");
        JCheckBox chkAllowInsideRetreat = new JCheckBox("允许境内后退");
        JCheckBox chkInternationalAdvisor = new JCheckBox("国际化仕");
        JCheckBox chkAllowElephantCrossRiver = new JCheckBox("相");
        JCheckBox chkAllowAdvisorCrossRiver = new JCheckBox("仕");
        JCheckBox chkAllowKingCrossRiver = new JCheckBox("帥");

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
        // 缩进并水平排列"相"、"仕"、"帥"
        JPanel indentedRiverOptions = new JPanel();
        indentedRiverOptions.setLayout(new BoxLayout(indentedRiverOptions, BoxLayout.X_AXIS));
        indentedRiverOptions.add(Box.createHorizontalStrut(20));
        indentedRiverOptions.add(chkAllowElephantCrossRiver);
        indentedRiverOptions.add(Box.createHorizontalStrut(10));
        indentedRiverOptions.add(chkAllowAdvisorCrossRiver);
        indentedRiverOptions.add(Box.createHorizontalStrut(10));
        indentedRiverOptions.add(chkAllowKingCrossRiver);
        indentedRiverOptions.add(Box.createHorizontalGlue());
        indentedRiverOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedRiverOptions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(indentedRiverOptions);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkPawnBack);
        // 缩进"允许境内后退"
        JPanel indentedRetreat = new JPanel();
        indentedRetreat.setLayout(new BoxLayout(indentedRetreat, BoxLayout.X_AXIS));
        indentedRetreat.add(Box.createHorizontalStrut(20));
        indentedRetreat.add(chkAllowInsideRetreat);
        indentedRetreat.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedRetreat.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(indentedRetreat);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkPawnPromotion);
        // 缩进"允许己方底线晋升"
        JPanel indentedPanel = new JPanel();
        indentedPanel.setLayout(new BoxLayout(indentedPanel, BoxLayout.X_AXIS));
        indentedPanel.add(Box.createHorizontalStrut(20));
        indentedPanel.add(chkAllowOwnBaseLine);
        // 作为 special 的子组件，整体左对齐
        indentedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(indentedPanel);

        special.add(specialContent);
        form.add(special);

        // 操作按钮：复制配置 / 输入配置（在弹窗中点击确定应用）
        JPanel btnBar = new JPanel();
        btnBar.setLayout(new BoxLayout(btnBar, BoxLayout.Y_AXIS));
        btnBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton copyBtn = new JButton("复制配置");
        JButton importBtn = new JButton("输入配置");
        copyBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        importBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnBar.add(Box.createVerticalStrut(8));
        btnBar.add(copyBtn);
        btnBar.add(Box.createVerticalStrut(6));
        btnBar.add(importBtn);
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
            // 依赖：后退开启时才能勾选境内后退
            chkAllowInsideRetreat.setEnabled(chkPawnBack.isSelected());
            if (!chkPawnBack.isSelected()) {
                chkAllowInsideRetreat.setSelected(false);
                SettingsBinder b2 = (SettingsBinder) getClientProperty("binder");
                if (b2 != null) b2.setAllowInsideRetreat(false);
            }
        });
        chkNoRiverLimit.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setNoRiverLimit(chkNoRiverLimit.isSelected());
            // 依赖：允许过河开启时才能勾选子选项
            chkAllowElephantCrossRiver.setEnabled(chkNoRiverLimit.isSelected());
            chkAllowAdvisorCrossRiver.setEnabled(chkNoRiverLimit.isSelected());
            chkAllowKingCrossRiver.setEnabled(chkNoRiverLimit.isSelected());
            if (!chkNoRiverLimit.isSelected()) {
                chkAllowElephantCrossRiver.setSelected(false);
                chkAllowAdvisorCrossRiver.setSelected(false);
                chkAllowKingCrossRiver.setSelected(false);
                SettingsBinder b2 = (SettingsBinder) getClientProperty("binder");
                if (b2 != null) {
                    b2.setAllowElephantCrossRiver(false);
                    b2.setAllowAdvisorCrossRiver(false);
                    b2.setAllowKingCrossRiver(false);
                }
            }
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
            // 同时更新"允许己方底线晋升"的启用状态
            chkAllowOwnBaseLine.setEnabled(chkPawnPromotion.isSelected());
            if (!chkPawnPromotion.isSelected()) {
                chkAllowOwnBaseLine.setSelected(false);
                SettingsBinder b2 = (SettingsBinder) getClientProperty("binder");
                if (b2 != null) b2.setAllowOwnBaseLine(false);
            }
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
            obj.addProperty("pawnCanRetreat", chkPawnBack.isSelected());
            obj.addProperty("allowInsideRetreat", chkAllowInsideRetreat.isSelected());
            obj.addProperty("pawnPromotion", chkPawnPromotion.isSelected());
            obj.addProperty("allowOwnBaseLine", chkAllowOwnBaseLine.isSelected());
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
            current.addProperty("pawnCanRetreat", chkPawnBack.isSelected());
            current.addProperty("allowInsideRetreat", chkAllowInsideRetreat.isSelected());
            current.addProperty("pawnPromotion", chkPawnPromotion.isSelected());
            current.addProperty("allowOwnBaseLine", chkAllowOwnBaseLine.isSelected());
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
                chkAllowOwnBaseLine.setEnabled(pawnProm);
                chkAllowInsideRetreat.setEnabled(chkPawnBack.isSelected());
                chkAllowElephantCrossRiver.setEnabled(noRiver);
                chkAllowAdvisorCrossRiver.setEnabled(noRiver);
                chkAllowKingCrossRiver.setEnabled(noRiver);
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
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "解析失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        // 提供一个方法把引擎状态灌入到UI
        putClientProperty("applyState", (Runnable) () -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) {
                chkAllowUndo.setSelected(b.isAllowUndo());
                chkFlyingGeneral.setSelected(b.isAllowFlyingGeneral());
                chkPawnBack.setSelected(b.isPawnCanRetreat());
                chkNoRiverLimit.setSelected(b.isNoRiverLimit());
                chkAdvisorCanLeave.setSelected(b.isAdvisorCanLeave());
                chkInternationalKing.setSelected(b.isInternationalKing());
                chkPawnPromotion.setSelected(b.isPawnPromotion());
                chkAllowOwnBaseLine.setSelected(b.isAllowOwnBaseLine());
                chkAllowInsideRetreat.setSelected(b.isAllowInsideRetreat());
                chkInternationalAdvisor.setSelected(b.isInternationalAdvisor());
                chkAllowElephantCrossRiver.setSelected(b.isAllowElephantCrossRiver());
                chkAllowAdvisorCrossRiver.setSelected(b.isAllowAdvisorCrossRiver());
                chkAllowKingCrossRiver.setSelected(b.isAllowKingCrossRiver());
                chkAllowOwnBaseLine.setEnabled(b.isPawnPromotion());
                chkAllowInsideRetreat.setEnabled(chkPawnBack.isSelected());
                chkAllowElephantCrossRiver.setEnabled(b.isNoRiverLimit());
                chkAllowAdvisorCrossRiver.setEnabled(b.isNoRiverLimit());
                chkAllowKingCrossRiver.setEnabled(b.isNoRiverLimit());
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
