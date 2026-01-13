package io.github.samera2022.chinese_chess.ui;

import io.github.samera2022.chinese_chess.rules.RuleRegistry;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;

import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * 玩法设置面板：在左侧展开显示，配置项会接入 GameEngine。
 */
public class RuleSettingsPanel extends JPanel {

    private static final Map<String, JCheckBox> registryNameToCheckBox = new HashMap<>();
    private GameRulesConfig config;

    private final GameRulesConfig.RuleChangeListener configListener = (key, oldVal, newVal, source) -> {
        SwingUtilities.invokeLater(this::refreshUI);
    };

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
        if (this.config != null) {
            this.config.removeRuleChangeListener(configListener);
        }
        this.config = config;
        if (this.config != null) {
            this.config.addRuleChangeListener(configListener);
            refreshUI();
        }
    }

    public void refreshUI() {
        if (config == null) return;
        Map<String, Boolean> enabledMap = config.getAllRuleEnabledStates();
        for (RuleRegistry rule : RuleRegistry.values()) {
            JCheckBox box = registryNameToCheckBox.get(rule.registryName);
            if (box != null) {
                boolean target = config.isRuleEnabled(rule.registryName);
                if (box.isSelected() != target) {
                    box.setSelected(target);
                }
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
                if (config != null) {
                    boolean newValue = box.isSelected();
                    // 触发主类的规则更改监听，Type为UI
                    config.set(rule.registryName, newValue, GameRulesConfig.ChangeSource.UI);
                    
                    // 检查是否实际更改成功（处理依赖/冲突限制）
                    boolean actualValue = config.isRuleEnabled(rule.registryName);
                    if (actualValue != newValue) {
                        box.setSelected(actualValue);
                    }
                    
                    // 刷新 UI 以更新其他复选框的启用/禁用状态（依赖关系）
                    refreshUI();
                }
            });

            registryNameToCheckBox.put(rule.registryName, box);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);

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
