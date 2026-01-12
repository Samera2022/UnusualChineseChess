package io.github.samera2022.chinese_chess.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.samera2022.chinese_chess.filter.DocumentInputFilter;
import io.github.samera2022.chinese_chess.rules.RuleConstants;

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
        void setAllowForceMove(boolean allowForceMove);
        boolean isAllowForceMove();
        // 特殊玩法
        void setAllowFlyingGeneral(boolean allow);
        void setDisableFacingGenerals(boolean allow);
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
        void setLeftRightConnectedHorse(boolean allow);
        void setLeftRightConnectedElephant(boolean allow);
        void setUnblockPiece(boolean allow);
        void setUnblockHorseLeg(boolean allow);
        void setUnblockElephantEye(boolean allow);
        void setAllowCaptureOwnPiece(boolean allow);
        void setAllowCaptureConversion(boolean allow);
        void setDeathMatchUntilVictory(boolean allow);
        boolean isAllowFlyingGeneral();
        boolean isAllowCaptureOwnPiece();
        boolean isDisableFacingGenerals();
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
        boolean isLeftRightConnectedHorse();
        boolean isLeftRightConnectedElephant();
        boolean isUnblockPiece();
        boolean isUnblockHorseLeg();
        boolean isUnblockElephantEye();
        boolean isDeathMatchUntilVictory();
        void setAllowPieceStacking(boolean allow);
        boolean isAllowPieceStacking();
        void setMaxStackingCount(int count);
        int getMaxStackingCount();
        void setAllowCarryPiecesAbove(boolean allow);
        boolean isAllowCarryPiecesAbove();
        boolean isAllowCaptureConversion();
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
        form.add(Box.createVerticalStrut(6));

        // 新增：允许强制移动勾选框
        JCheckBox chkAllowForceMove = new JCheckBox("允许强制移动");
        chkAllowForceMove.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(chkAllowForceMove);
        form.add(Box.createVerticalStrut(6));

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
        JCheckBox chkDisableFacingGenerals = new JCheckBox("取消对将");
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
        JCheckBox chkAllowCaptureOwnPiece = new JCheckBox("允许自己吃自己");
        JCheckBox chkAllowCaptureConversion = new JCheckBox("允许俘虏（吃子改为转换）");
        JCheckBox chkDeathMatchUntilVictory = new JCheckBox("死战方休（必须吃掉全部棋子）");
        JCheckBox chkAllowPieceStacking = new JCheckBox("允许棋子堆叠数:");

        // 在 Y 轴 BoxLayout 中，逐项左对齐
        chkFlyingGeneral.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkDisableFacingGenerals.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkPawnBack.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkNoRiverLimit.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkAdvisorCanLeave.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkInternationalKing.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkPawnPromotion.setAlignmentX(Component.LEFT_ALIGNMENT);


        specialContent.add(chkFlyingGeneral);
        specialContent.add(Box.createVerticalStrut(6));
        specialContent.add(chkDisableFacingGenerals);
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

        specialContent.add(Box.createVerticalStrut(6));
        chkAllowCaptureOwnPiece.setAlignmentX(Component.LEFT_ALIGNMENT);
        specialContent.add(chkAllowCaptureOwnPiece);
        specialContent.add(Box.createVerticalStrut(6));
        chkAllowCaptureConversion.setAlignmentX(Component.LEFT_ALIGNMENT);
        specialContent.add(chkAllowCaptureConversion);

        // 联动逻辑：只有“取消河界”勾选时“兵”可编辑，否则禁用且为false
        chkNoRiverLimit.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setNoRiverLimit(chkNoRiverLimit.isSelected());
        });

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

        // 在"左右连通"下添加缩进的子选项
        JPanel indentedLeftRightPanel = new JPanel();
        indentedLeftRightPanel.setLayout(new BoxLayout(indentedLeftRightPanel, BoxLayout.X_AXIS));
        indentedLeftRightPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedLeftRightPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JCheckBox chkLeftRightConnectedHorse = new JCheckBox("额外允许马");
        JCheckBox chkLeftRightConnectedElephant = new JCheckBox("额外允许相");

        indentedLeftRightPanel.add(Box.createHorizontalStrut(32));
        indentedLeftRightPanel.add(chkLeftRightConnectedHorse);
        indentedLeftRightPanel.add(Box.createHorizontalStrut(12));
        indentedLeftRightPanel.add(chkLeftRightConnectedElephant);
        indentedLeftRightPanel.add(Box.createHorizontalGlue());

        special2Content.add(indentedLeftRightPanel);

        // 依赖关系：主选项未勾选时，子选项禁用且为false
        chkLeftRightConnected.addActionListener(e -> {
            boolean enabled = chkLeftRightConnected.isSelected();
            chkLeftRightConnectedHorse.setEnabled(enabled);
            chkLeftRightConnectedElephant.setEnabled(enabled);
            if (!enabled) {
                chkLeftRightConnectedHorse.setSelected(false);
                chkLeftRightConnectedElephant.setSelected(false);
            }
        });
        // 初始化禁用
        chkLeftRightConnectedHorse.setEnabled(false);
        chkLeftRightConnectedElephant.setEnabled(false);

        // 添加"死战方休"选项
        special2Content.add(Box.createVerticalStrut(6));
        chkDeathMatchUntilVictory.setAlignmentX(Component.LEFT_ALIGNMENT);
        special2Content.add(chkDeathMatchUntilVictory);

        // 添加"允许棋子堆叠"行
        special2Content.add(Box.createVerticalStrut(6));
        JPanel stackingPanel = new JPanel();
        stackingPanel.setLayout(new BoxLayout(stackingPanel, BoxLayout.X_AXIS));
        stackingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stackingPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        chkAllowPieceStacking.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField txtStackingCount = new JTextField("2", 4);
        txtStackingCount.setMaximumSize(new Dimension(60, 25));
        txtStackingCount.setEnabled(false);

        // 应用 DocumentInputFilter 限制输入为 1-16 的整数
        ((javax.swing.text.AbstractDocument) txtStackingCount.getDocument()).setDocumentFilter(new DocumentInputFilter() {
            @Override
            public boolean isValidContent(String text) {
                // 允许空或 1-16 之间的整数
                if (text.isEmpty()) return true;
                try {
                    int value = Integer.parseInt(text);
                    return value >= 2 && value <= 16;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        });

        stackingPanel.add(chkAllowPieceStacking);
        stackingPanel.add(Box.createHorizontalStrut(6));
        stackingPanel.add(txtStackingCount);
        stackingPanel.add(Box.createHorizontalGlue());

        special2Content.add(stackingPanel);

        // 缩进的子选项：允许背负上方棋子（仅当允许堆叠时可用）
        JCheckBox chkAllowCarryPiecesAbove = new JCheckBox("允许背负上方棋子");
        chkAllowCarryPiecesAbove.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkAllowCarryPiecesAbove.setEnabled(false); // 初始禁用

        JPanel indentedCarryPanel = new JPanel();
        indentedCarryPanel.setLayout(new BoxLayout(indentedCarryPanel, BoxLayout.X_AXIS));
        indentedCarryPanel.add(Box.createHorizontalStrut(20));
        indentedCarryPanel.add(chkAllowCarryPiecesAbove);
        indentedCarryPanel.add(Box.createHorizontalGlue());
        indentedCarryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        indentedCarryPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        special2Content.add(indentedCarryPanel);

        // 依赖关系：主选项未勾选时，输入框禁用
        chkAllowPieceStacking.addActionListener(e -> {
            boolean enabled = chkAllowPieceStacking.isSelected();
            txtStackingCount.setEnabled(enabled);
            chkAllowCarryPiecesAbove.setEnabled(enabled);
            if (!enabled) {
                chkAllowCarryPiecesAbove.setSelected(false);
            }
            // 互斥逻辑：允许棋子堆叠数被选中时，禁止允许自己吃自己
            if (enabled) {
                chkAllowCaptureOwnPiece.setSelected(false);
                chkAllowCaptureOwnPiece.setEnabled(false);
            } else {
                // 只有当对方未选中时才恢复可用
                if (!chkAllowCaptureOwnPiece.isSelected()) {
                    chkAllowCaptureOwnPiece.setEnabled(true);
                }
            }
        });
        chkAllowCaptureOwnPiece.addActionListener(e -> {
            boolean enabled = chkAllowCaptureOwnPiece.isSelected();
            // 互斥逻辑：允许自己吃自己被选中时，禁止允许棋子堆叠数
            if (enabled) {
                chkAllowPieceStacking.setSelected(false);
                chkAllowPieceStacking.setEnabled(false);
                txtStackingCount.setEnabled(false);
                chkAllowCarryPiecesAbove.setSelected(false);
                chkAllowCarryPiecesAbove.setEnabled(false);
                chkAllowCaptureConversion.setEnabled(false);
            } else {
                if (!chkAllowPieceStacking.isSelected()) {
                    chkAllowPieceStacking.setEnabled(true);
                    txtStackingCount.setEnabled(false);
                    chkAllowCarryPiecesAbove.setEnabled(false);
                }
                chkAllowCaptureConversion.setEnabled(true);
            }
        });
        chkAllowCaptureConversion.addActionListener(e -> {
            boolean enabled = chkAllowCaptureConversion.isSelected();
            // 互斥逻辑：允许俘虏与堆叠、自己吃自己互斥
            if (enabled) {
                chkAllowPieceStacking.setSelected(false);
                chkAllowPieceStacking.setEnabled(false);
                txtStackingCount.setEnabled(false);
                chkAllowCarryPiecesAbove.setSelected(false);
                chkAllowCarryPiecesAbove.setEnabled(false);
                chkAllowCaptureOwnPiece.setSelected(false);
                chkAllowCaptureOwnPiece.setEnabled(false);
            } else {
                if (!chkAllowPieceStacking.isSelected()) {
                    chkAllowPieceStacking.setEnabled(true);
                    txtStackingCount.setEnabled(false);
                    chkAllowCarryPiecesAbove.setEnabled(false);
                }
                if (!chkAllowCaptureOwnPiece.isSelected()) {
                    chkAllowCaptureOwnPiece.setEnabled(true);
                }
            }
        });

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
        chkAllowForceMove.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowForceMove(chkAllowForceMove.isSelected());
        });
        chkFlyingGeneral.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowFlyingGeneral(chkFlyingGeneral.isSelected());
        });
        chkDisableFacingGenerals.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setDisableFacingGenerals(chkDisableFacingGenerals.isSelected());
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
        chkLeftRightConnectedHorse.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setLeftRightConnectedHorse(chkLeftRightConnectedHorse.isSelected());
        });
        chkLeftRightConnectedElephant.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setLeftRightConnectedElephant(chkLeftRightConnectedElephant.isSelected());
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
        chkAllowCaptureOwnPiece.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowCaptureOwnPiece(chkAllowCaptureOwnPiece.isSelected());
        });
        chkAllowCaptureConversion.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowCaptureConversion(chkAllowCaptureConversion.isSelected());
        });
        chkDeathMatchUntilVictory.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setDeathMatchUntilVictory(chkDeathMatchUntilVictory.isSelected());
        });
        chkAllowPieceStacking.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowPieceStacking(chkAllowPieceStacking.isSelected());
        });
        // 修复：添加 chkAllowCarryPiecesAbove 的监听器，使其能实时生效
        chkAllowCarryPiecesAbove.addActionListener(e -> {
            SettingsBinder b = (SettingsBinder) getClientProperty("binder");
            if (b != null) b.setAllowCarryPiecesAbove(chkAllowCarryPiecesAbove.isSelected());
        });

        txtStackingCount.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateStackingCount(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateStackingCount(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateStackingCount(); }
            private void updateStackingCount() {
                try {
                    String text = txtStackingCount.getText();
                    if (!text.isEmpty()) {
                        int count = Integer.parseInt(text);
                        SettingsBinder b = (SettingsBinder) getClientProperty("binder");
                        if (b != null) b.setMaxStackingCount(count);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // 复制配置：构造 JSON 并写入剪贴板
        copyBtn.addActionListener(e -> {
            JsonObject obj = new JsonObject();
            obj.addProperty(RuleConstants.ALLOW_UNDO, chkAllowUndo.isSelected());
            obj.addProperty(RuleConstants.ALLOW_FORCE_MOVE, chkAllowForceMove.isSelected());
            obj.addProperty(RuleConstants.ALLOW_FLYING_GENERAL, chkFlyingGeneral.isSelected());
            obj.addProperty(RuleConstants.DISABLE_FACING_GENERALS, chkDisableFacingGenerals.isSelected());
            obj.addProperty(RuleConstants.ADVISOR_CAN_LEAVE, chkAdvisorCanLeave.isSelected());
            obj.addProperty(RuleConstants.INTERNATIONAL_KING, chkInternationalKing.isSelected());
            obj.addProperty(RuleConstants.INTERNATIONAL_ADVISOR, chkInternationalAdvisor.isSelected());
            obj.addProperty(RuleConstants.NO_RIVER_LIMIT, chkNoRiverLimit.isSelected());
            obj.addProperty(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER, chkAllowElephantCrossRiver.isSelected());
            obj.addProperty(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER, chkAllowAdvisorCrossRiver.isSelected());
            obj.addProperty(RuleConstants.ALLOW_KING_CROSS_RIVER, chkAllowKingCrossRiver.isSelected());
            obj.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED, chkLeftRightConnected.isSelected());
            obj.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE, chkLeftRightConnectedHorse.isSelected());
            obj.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT, chkLeftRightConnectedElephant.isSelected());
            obj.addProperty(RuleConstants.PAWN_CAN_RETREAT, chkPawnBack.isSelected());
            obj.addProperty(RuleConstants.ALLOW_INSIDE_RETREAT, chkAllowInsideRetreat.isSelected());
            obj.addProperty(RuleConstants.PAWN_PROMOTION, chkPawnPromotion.isSelected());
            obj.addProperty(RuleConstants.ALLOW_OWN_BASE_LINE, chkAllowOwnBaseLine.isSelected());
            obj.addProperty(RuleConstants.UNBLOCK_PIECE, chkUnblockPiece.isSelected());
            obj.addProperty(RuleConstants.UNBLOCK_HORSE_LEG, chkUnblockHorseLeg.isSelected());
            obj.addProperty(RuleConstants.UNBLOCK_ELEPHANT_EYE, chkUnblockElephantEye.isSelected());
            obj.addProperty(RuleConstants.ALLOW_CAPTURE_OWN_PIECE, chkAllowCaptureOwnPiece.isSelected());
            obj.addProperty(RuleConstants.ALLOW_CAPTURE_CONVERSION, chkAllowCaptureConversion.isSelected());
            obj.addProperty(RuleConstants.DEATH_MATCH_UNTIL_VICTORY, chkDeathMatchUntilVictory.isSelected());
            obj.addProperty(RuleConstants.ALLOW_PIECE_STACKING, chkAllowPieceStacking.isSelected());
            // 确保允许背负也被导出
            obj.addProperty(RuleConstants.ALLOW_CARRY_PIECES_ABOVE, chkAllowCarryPiecesAbove.isSelected());
            try {
                String stackText = txtStackingCount.getText();
                int count = stackText.isEmpty() ? 2 : Integer.parseInt(stackText);
                obj.addProperty(RuleConstants.MAX_STACKING_COUNT, count);
            } catch (NumberFormatException e2) {
                obj.addProperty(RuleConstants.MAX_STACKING_COUNT, 2);
            }
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
            current.addProperty(RuleConstants.ALLOW_UNDO, chkAllowUndo.isSelected());
            current.addProperty(RuleConstants.ALLOW_FORCE_MOVE, chkAllowForceMove.isSelected());
            current.addProperty(RuleConstants.ALLOW_FLYING_GENERAL, chkFlyingGeneral.isSelected());
            current.addProperty(RuleConstants.DISABLE_FACING_GENERALS, chkDisableFacingGenerals.isSelected());
            current.addProperty(RuleConstants.ADVISOR_CAN_LEAVE, chkAdvisorCanLeave.isSelected());
            current.addProperty(RuleConstants.INTERNATIONAL_KING, chkInternationalKing.isSelected());
            current.addProperty(RuleConstants.INTERNATIONAL_ADVISOR, chkInternationalAdvisor.isSelected());
            current.addProperty(RuleConstants.NO_RIVER_LIMIT, chkNoRiverLimit.isSelected());
            current.addProperty(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER, chkAllowElephantCrossRiver.isSelected());
            current.addProperty(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER, chkAllowAdvisorCrossRiver.isSelected());
            current.addProperty(RuleConstants.ALLOW_KING_CROSS_RIVER, chkAllowKingCrossRiver.isSelected());
            current.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED, chkLeftRightConnected.isSelected());
            current.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE, chkLeftRightConnectedHorse.isSelected());
            current.addProperty(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT, chkLeftRightConnectedElephant.isSelected());
            current.addProperty(RuleConstants.PAWN_CAN_RETREAT, chkPawnBack.isSelected());
            current.addProperty(RuleConstants.ALLOW_INSIDE_RETREAT, chkAllowInsideRetreat.isSelected());
            current.addProperty(RuleConstants.PAWN_PROMOTION, chkPawnPromotion.isSelected());
            current.addProperty(RuleConstants.ALLOW_OWN_BASE_LINE, chkAllowOwnBaseLine.isSelected());
            current.addProperty(RuleConstants.UNBLOCK_PIECE, chkUnblockPiece.isSelected());
            current.addProperty(RuleConstants.UNBLOCK_HORSE_LEG, chkUnblockHorseLeg.isSelected());
            current.addProperty(RuleConstants.UNBLOCK_ELEPHANT_EYE, chkUnblockElephantEye.isSelected());
            current.addProperty(RuleConstants.ALLOW_CAPTURE_OWN_PIECE, chkAllowCaptureOwnPiece.isSelected());
            current.addProperty(RuleConstants.ALLOW_CAPTURE_CONVERSION, chkAllowCaptureConversion.isSelected());
            current.addProperty(RuleConstants.DEATH_MATCH_UNTIL_VICTORY, chkDeathMatchUntilVictory.isSelected());
            current.addProperty(RuleConstants.ALLOW_PIECE_STACKING, chkAllowPieceStacking.isSelected());
            current.addProperty(RuleConstants.ALLOW_CARRY_PIECES_ABOVE, chkAllowCarryPiecesAbove.isSelected());
            try {
                String stackText = txtStackingCount.getText();
                int count = stackText.isEmpty() ? 2 : Integer.parseInt(stackText);
                current.addProperty(RuleConstants.MAX_STACKING_COUNT, count);
            } catch (NumberFormatException e2) {
                current.addProperty(RuleConstants.MAX_STACKING_COUNT, 2);
            }
            area.setText(current.toString());

            JScrollPane scroll = new JScrollPane(area);
            int res = JOptionPane.showConfirmDialog(this, scroll, "输入配置 (JSON)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;

            try {
                JsonObject obj = JsonParser.parseString(area.getText().trim()).getAsJsonObject();
                boolean allowUndo = obj.has(RuleConstants.ALLOW_UNDO) && obj.get(RuleConstants.ALLOW_UNDO).getAsBoolean();
                boolean allowForceMove = obj.has(RuleConstants.ALLOW_FORCE_MOVE) && obj.get(RuleConstants.ALLOW_FORCE_MOVE).getAsBoolean();
                boolean allowFlyingGeneral = obj.has(RuleConstants.ALLOW_FLYING_GENERAL) && obj.get(RuleConstants.ALLOW_FLYING_GENERAL).getAsBoolean();
                boolean disableFacing = obj.has(RuleConstants.DISABLE_FACING_GENERALS) && obj.get(RuleConstants.DISABLE_FACING_GENERALS).getAsBoolean();
                boolean pawnBack = obj.has(RuleConstants.PAWN_CAN_RETREAT) && obj.get(RuleConstants.PAWN_CAN_RETREAT).getAsBoolean();
                boolean noRiver = obj.has(RuleConstants.NO_RIVER_LIMIT) && obj.get(RuleConstants.NO_RIVER_LIMIT).getAsBoolean();
                boolean advisorLeave = obj.has(RuleConstants.ADVISOR_CAN_LEAVE) && obj.get(RuleConstants.ADVISOR_CAN_LEAVE).getAsBoolean();
                boolean intlKing = obj.has(RuleConstants.INTERNATIONAL_KING) && obj.get(RuleConstants.INTERNATIONAL_KING).getAsBoolean();
                boolean pawnProm = obj.has(RuleConstants.PAWN_PROMOTION) && obj.get(RuleConstants.PAWN_PROMOTION).getAsBoolean();
                boolean ownBase = obj.has(RuleConstants.ALLOW_OWN_BASE_LINE) && obj.get(RuleConstants.ALLOW_OWN_BASE_LINE).getAsBoolean();
                boolean insideRetreat = obj.has(RuleConstants.ALLOW_INSIDE_RETREAT) && obj.get(RuleConstants.ALLOW_INSIDE_RETREAT).getAsBoolean();
                boolean intlAdvisor = obj.has(RuleConstants.INTERNATIONAL_ADVISOR) && obj.get(RuleConstants.INTERNATIONAL_ADVISOR).getAsBoolean();
                boolean elephantCross = obj.has(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER) && obj.get(RuleConstants.ALLOW_ELEPHANT_CROSS_RIVER).getAsBoolean();
                boolean advisorCross = obj.has(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER) && obj.get(RuleConstants.ALLOW_ADVISOR_CROSS_RIVER).getAsBoolean();
                boolean kingCross = obj.has(RuleConstants.ALLOW_KING_CROSS_RIVER) && obj.get(RuleConstants.ALLOW_KING_CROSS_RIVER).getAsBoolean();
                boolean leftRight = obj.has(RuleConstants.LEFT_RIGHT_CONNECTED) && obj.get(RuleConstants.LEFT_RIGHT_CONNECTED).getAsBoolean();
                boolean leftRightHorse = obj.has(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE) && obj.get(RuleConstants.LEFT_RIGHT_CONNECTED_HORSE).getAsBoolean();
                boolean leftRightElephant = obj.has(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT) && obj.get(RuleConstants.LEFT_RIGHT_CONNECTED_ELEPHANT).getAsBoolean();
                boolean unblockPiece = obj.has(RuleConstants.UNBLOCK_PIECE) && obj.get(RuleConstants.UNBLOCK_PIECE).getAsBoolean();
                boolean unblockHorseLeg = obj.has(RuleConstants.UNBLOCK_HORSE_LEG) && obj.get(RuleConstants.UNBLOCK_HORSE_LEG).getAsBoolean();
                boolean unblockElephantEye = obj.has(RuleConstants.UNBLOCK_ELEPHANT_EYE) && obj.get(RuleConstants.UNBLOCK_ELEPHANT_EYE).getAsBoolean();
                boolean allowCaptureOwnPiece = obj.has(RuleConstants.ALLOW_CAPTURE_OWN_PIECE) && obj.get(RuleConstants.ALLOW_CAPTURE_OWN_PIECE).getAsBoolean();
                boolean allowCaptureConversion = obj.has(RuleConstants.ALLOW_CAPTURE_CONVERSION) && obj.get(RuleConstants.ALLOW_CAPTURE_CONVERSION).getAsBoolean();
                boolean deathMatchUntilVictory = obj.has(RuleConstants.DEATH_MATCH_UNTIL_VICTORY) && obj.get(RuleConstants.DEATH_MATCH_UNTIL_VICTORY).getAsBoolean();
                boolean allowPieceStacking = obj.has(RuleConstants.ALLOW_PIECE_STACKING) && obj.get(RuleConstants.ALLOW_PIECE_STACKING).getAsBoolean();
                boolean allowCarryPiecesAbove = obj.has(RuleConstants.ALLOW_CARRY_PIECES_ABOVE) && obj.get(RuleConstants.ALLOW_CARRY_PIECES_ABOVE).getAsBoolean();
                int maxStackingCount = obj.has(RuleConstants.MAX_STACKING_COUNT) ? obj.get(RuleConstants.MAX_STACKING_COUNT).getAsInt() : 2;
                // 验证范围：只允许 1-16 之间的正整数
                maxStackingCount = Math.max(1, Math.min(16, maxStackingCount));

                chkAllowUndo.setSelected(allowUndo);
                chkAllowForceMove.setSelected(allowForceMove);
                chkFlyingGeneral.setSelected(allowFlyingGeneral);
                chkDisableFacingGenerals.setSelected(disableFacing);
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
                chkLeftRightConnectedHorse.setSelected(leftRightHorse && leftRight);
                chkLeftRightConnectedElephant.setSelected(leftRightElephant && leftRight);
                chkAllowOwnBaseLine.setEnabled(pawnProm);
                chkAllowInsideRetreat.setEnabled(chkPawnBack.isSelected());
                chkAllowElephantCrossRiver.setEnabled(noRiver);
                chkAllowAdvisorCrossRiver.setEnabled(noRiver);
                chkAllowKingCrossRiver.setEnabled(noRiver);
                chkUnblockPiece.setSelected(unblockPiece);
                chkUnblockHorseLeg.setSelected(unblockHorseLeg);
                chkUnblockElephantEye.setSelected(unblockElephantEye);
                chkAllowCaptureOwnPiece.setSelected(allowCaptureOwnPiece);
                chkAllowCaptureConversion.setSelected(allowCaptureConversion);
                chkDeathMatchUntilVictory.setSelected(deathMatchUntilVictory);
                chkAllowPieceStacking.setSelected(allowPieceStacking);
                chkAllowCarryPiecesAbove.setSelected(allowCarryPiecesAbove && allowPieceStacking);
                txtStackingCount.setText(String.valueOf(maxStackingCount));
                // 在JSON导入后和applyState后同步互斥禁用状态
                Runnable updateMutualExclusion = () -> {
                    if (chkAllowCaptureOwnPiece.isSelected()) {
                        chkAllowPieceStacking.setSelected(false);
                        chkAllowPieceStacking.setEnabled(false);
                        txtStackingCount.setEnabled(false);
                        chkAllowCaptureOwnPiece.setEnabled(true);
                        chkAllowCaptureConversion.setEnabled(false);
                    } else if (chkAllowPieceStacking.isSelected()) {
                        chkAllowCaptureOwnPiece.setSelected(false);
                        chkAllowCaptureOwnPiece.setEnabled(false);
                        chkAllowPieceStacking.setEnabled(true);
                        txtStackingCount.setEnabled(true);
                        chkAllowCaptureConversion.setEnabled(false);
                    } else if (chkAllowCaptureConversion.isSelected()) {
                        chkAllowCaptureOwnPiece.setSelected(false);
                        chkAllowCaptureOwnPiece.setEnabled(false);
                        chkAllowPieceStacking.setSelected(false);
                        chkAllowPieceStacking.setEnabled(false);
                        txtStackingCount.setEnabled(false);
                        chkAllowCaptureConversion.setEnabled(true);
                    } else {
                        chkAllowCaptureOwnPiece.setEnabled(true);
                        chkAllowPieceStacking.setEnabled(true);
                        chkAllowCaptureConversion.setEnabled(true);
                        txtStackingCount.setEnabled(false);
                    }
                };
                updateMutualExclusion.run();
                SettingsBinder b = (SettingsBinder) getClientProperty("binder");
                if (b != null) {
                    b.setAllowFlyingGeneral(allowFlyingGeneral);
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
                    b.setLeftRightConnectedHorse(leftRightHorse && leftRight);
                    b.setLeftRightConnectedElephant(leftRightElephant && leftRight);
                    b.setUnblockPiece(unblockPiece);
                    b.setUnblockHorseLeg(unblockHorseLeg);
                    b.setUnblockElephantEye(unblockElephantEye);
                    b.setAllowCaptureOwnPiece(allowCaptureOwnPiece);
                    b.setAllowCaptureConversion(allowCaptureConversion);
                    b.setAllowPieceStacking(allowPieceStacking);
                    b.setAllowCarryPiecesAbove(allowCarryPiecesAbove && allowPieceStacking);
                    b.setMaxStackingCount(maxStackingCount);
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
                chkAllowForceMove.setSelected(b.isAllowForceMove());
                chkFlyingGeneral.setSelected(b.isAllowFlyingGeneral());
                chkDisableFacingGenerals.setSelected(b.isDisableFacingGenerals());
                chkPawnBack.setSelected(b.isPawnCanRetreat());
                chkNoRiverLimit.setSelected(b.isNoRiverLimit());
                chkAdvisorCanLeave.setSelected(b.isAdvisorCanLeave());
                chkInternationalKing.setSelected(b.isInternationalKing());
                chkPawnPromotion.setSelected(b.isPawnPromotion());
                chkAllowOwnBaseLine.setSelected(b.isAllowOwnBaseLine());
                chkAllowInsideRetreat.setSelected(b.isAllowInsideRetreat());
                chkInternationalAdvisor.setSelected(b.isInternationalAdvisor());
                boolean noRiver = b.isNoRiverLimit();
                chkAllowElephantCrossRiver.setSelected(noRiver && b.isAllowElephantCrossRiver());
                chkAllowAdvisorCrossRiver.setSelected(noRiver && b.isAllowAdvisorCrossRiver());
                chkAllowKingCrossRiver.setSelected(noRiver && b.isAllowKingCrossRiver());
                boolean lr = b.isLeftRightConnected();
                chkLeftRightConnected.setSelected(lr);
                chkLeftRightConnectedHorse.setSelected(lr && b.isLeftRightConnectedHorse());
                chkLeftRightConnectedElephant.setSelected(lr && b.isLeftRightConnectedElephant());
                chkUnblockPiece.setSelected(b.isUnblockPiece());
                chkUnblockHorseLeg.setSelected(b.isUnblockPiece() && b.isUnblockHorseLeg());
                chkUnblockElephantEye.setSelected(b.isUnblockPiece() && b.isUnblockElephantEye());
                chkAllowCaptureOwnPiece.setSelected(b.isAllowCaptureOwnPiece());
                chkAllowCaptureConversion.setSelected(b.isAllowCaptureConversion());
                chkDeathMatchUntilVictory.setSelected(b.isDeathMatchUntilVictory());
                chkAllowPieceStacking.setSelected(b.isAllowPieceStacking());
                chkAllowCarryPiecesAbove.setSelected(b.isAllowCarryPiecesAbove());
                txtStackingCount.setText(String.valueOf(b.getMaxStackingCount()));

                // 同步依赖的控件状态
                chkAllowOwnBaseLine.setEnabled(chkPawnPromotion.isSelected());
                chkAllowInsideRetreat.setEnabled(chkPawnBack.isSelected());
                chkAllowElephantCrossRiver.setEnabled(noRiver);
                chkAllowAdvisorCrossRiver.setEnabled(noRiver);
                chkAllowKingCrossRiver.setEnabled(noRiver);
                chkLeftRightConnectedHorse.setEnabled(lr);
                chkLeftRightConnectedElephant.setEnabled(lr);
                boolean unblock = chkUnblockPiece.isSelected();
                chkUnblockHorseLeg.setEnabled(unblock);
                chkUnblockElephantEye.setEnabled(unblock);
                // 修正：联机时主选项被禁用，子选项也必须禁用
                chkUnblockHorseLeg.setEnabled(chkUnblockPiece.isEnabled() && chkUnblockPiece.isSelected());
                chkUnblockElephantEye.setEnabled(chkUnblockPiece.isEnabled() && chkUnblockPiece.isSelected());
                chkLeftRightConnectedHorse.setEnabled(chkLeftRightConnected.isEnabled() && chkLeftRightConnected.isSelected());
                chkLeftRightConnectedElephant.setEnabled(chkLeftRightConnected.isEnabled() && chkLeftRightConnected.isSelected());
                chkAllowCarryPiecesAbove.setEnabled(chkAllowPieceStacking.isEnabled() && chkAllowPieceStacking.isSelected());
                if (chkAllowCaptureOwnPiece.isSelected()) {
                    chkAllowPieceStacking.setEnabled(false);
                    txtStackingCount.setEnabled(false);
                    chkAllowCarryPiecesAbove.setEnabled(false);
                    chkAllowCaptureConversion.setEnabled(false);
                } else if (chkAllowPieceStacking.isSelected()) {
                    chkAllowCaptureOwnPiece.setEnabled(false);
                    chkAllowCaptureConversion.setEnabled(false);
                } else if (chkAllowCaptureConversion.isSelected()) {
                    chkAllowCaptureOwnPiece.setEnabled(false);
                    chkAllowPieceStacking.setEnabled(false);
                    txtStackingCount.setEnabled(false);
                    chkAllowCarryPiecesAbove.setEnabled(false);
                }
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

