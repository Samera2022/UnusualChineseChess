package io.github.samera2022.chinese_chess.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.samera2022.chinese_chess.filter.DocumentInputFilter;
import io.github.samera2022.chinese_chess.rules.RuleConstants;
import io.github.samera2022.chinese_chess.rules.RuleRegistry;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
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

    private static final Map<String, JCheckBox> registryNameToCheckBox = new HashMap<>();
    private GameRulesConfig config;

    public RuleSettingsPanel() {
        setLayout(new BorderLayout(6,6));
        setBorder(new TitledBorder("玩法设置"));
        setPreferredSize(new Dimension(220, 0));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // 三个分组面板
        JPanel outsidePanel = new JPanel();
        outsidePanel.setLayout(new BoxLayout(outsidePanel, BoxLayout.Y_AXIS));
        outsidePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel extendedPanel = new JPanel();
        extendedPanel.setLayout(new BoxLayout(extendedPanel, BoxLayout.Y_AXIS));
        extendedPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel specialPanel = new JPanel();
        specialPanel.setLayout(new BoxLayout(specialPanel, BoxLayout.Y_AXIS));
        specialPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 保持原有TitledBorder样式
        TitledBorder extendedBorder = new TitledBorder("▼ 延申玩法");
        extendedPanel.setBorder(extendedBorder);
        TitledBorder specialBorder = new TitledBorder("▼ 特殊玩法");
        specialPanel.setBorder(specialBorder);

        // 自动生成玩法勾选框
        setupRuleCheckboxes(outsidePanel, extendedPanel, specialPanel);

        // 添加分组到form
        form.add(outsidePanel);
        form.add(Box.createVerticalStrut(12));
        form.add(extendedPanel);
        form.add(Box.createVerticalStrut(12));
        form.add(specialPanel);

        add(new JScrollPane(form), BorderLayout.CENTER);
    }

    public void bindConfig(GameRulesConfig config) {
        this.config = config;
        refreshUIFromConfig();
    }

    private void refreshUIFromConfig() {
        if (config == null) return;
        Map<String, Boolean> enabledMap = config.getAllRuleEnabledStates();
        for (RuleRegistry rule : RuleRegistry.values()) {
            JCheckBox box = registryNameToCheckBox.get(rule.registryName);
            if (box != null) {
                box.setSelected(config.isRuleEnabled(rule.registryName));
                box.setEnabled(rule.canBeEnabled(enabledMap));
            }
        }
    }

    private void setupRuleCheckboxes(JPanel outsidePanel, JPanel extendedPanel, JPanel specialPanel) {
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (!rule.displayOnUI) continue;
            JCheckBox box = new JCheckBox(rule.displayName);
            box.setName(rule.registryName);
            box.addActionListener(e -> {
                JCheckBox jcb = (JCheckBox) e.getSource();

                System.out.println(jcb.getName()+"当前状态："+jcb.isSelected());
            });
            registryNameToCheckBox.put(rule.registryName, box);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.addActionListener(e -> {
                if (config != null) {
                    boolean success = config.setRuleEnabled(rule.registryName, box.isSelected());
                    if (!success) {
                        box.setSelected(config.isRuleEnabled(rule.registryName));
                    }
                    refreshUIFromConfig();
                }
            });
            JPanel targetPanel = null;
            switch (rule.targetComponent) {
                case "outside": targetPanel = outsidePanel; break;
                case "extended": targetPanel = extendedPanel; break;
                case "special": targetPanel = specialPanel; break;
            }
            if (targetPanel != null) {
                targetPanel.add(box);
                targetPanel.add(Box.createVerticalStrut(6));
            }
        }
    }

    /**
     * 设置所有玩法勾选框是否可用（如联网时客户端禁用）
     */
    public static void setAllCheckboxesEnabled(boolean enabled) {
        for (JCheckBox box : registryNameToCheckBox.values()) {
            box.setEnabled(enabled);
        }
    }

    /**
     * 根据玩法注册名判断当前勾选框是否被选中（即玩法是否启用）
     */
    public static boolean isEnabled(String registryName) {
        JCheckBox box = registryNameToCheckBox.get(registryName);
        return box != null && box.isSelected();
    }
}
