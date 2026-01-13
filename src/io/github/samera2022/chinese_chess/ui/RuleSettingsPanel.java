package io.github.samera2022.chinese_chess.ui;

import io.github.samera2022.chinese_chess.consts.Consts;
import io.github.samera2022.chinese_chess.rules.RuleRegistry;
import io.github.samera2022.chinese_chess.rules.GameRulesConfig;

import java.awt.datatransfer.StringSelection;
import java.util.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

/**
 * 玩法设置面板：在左侧展开显示，配置项会接入 GameEngine。
 */
public class RuleSettingsPanel extends JPanel {

    private static final Map<String, JCheckBox> registryNameToCheckBox = new HashMap<>();
    private static final Map<String, JTextField> registryNameToTextField = new HashMap<>();
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

        // 底部按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        JButton copyButton = new JButton("复制配置");
        JButton pasteButton = new JButton("输入配置");

        copyButton.setMargin(new Insets(2, 5, 2, 5));
        pasteButton.setMargin(new Insets(2, 5, 2, 5));
        copyButton.setFont(new Font("SimHei", Font.PLAIN, 12));
        pasteButton.setFont(new Font("SimHei", Font.PLAIN, 12));

        copyButton.addActionListener(e -> copyConfigToClipboard());
        pasteButton.addActionListener(e -> showImportConfigDialog());

        buttonPanel.add(copyButton);
        buttonPanel.add(pasteButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void copyConfigToClipboard() {
        if (config == null) return;
        try {
            String json = config.toJson().toString();
            StringSelection selection = new StringSelection(json);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            JOptionPane.showMessageDialog(this, "配置已复制到剪贴板！", "成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "复制失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showImportConfigDialog() {
        if (config == null) return;

        JTextArea textArea = new JTextArea(10, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        // 预填充当前配置
        try {
            textArea.setText(config.toJson().toString());
        } catch (Exception ignored) {}
        
        JScrollPane scrollPane = new JScrollPane(textArea);

        int result = JOptionPane.showConfirmDialog(this, scrollPane, "请输入配置JSON",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String jsonStr = textArea.getText().trim();
            if (jsonStr.isEmpty()) return;

            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                config.loadFromJson(json);
                // 显式刷新 UI 以确保万无一失
                refreshUI();
                JOptionPane.showMessageDialog(this, "配置已成功导入！", "成功", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "导入失败，请确保输入内容为有效的JSON配置。\n错误: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
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
            if (rule.type == Consts.CHECK_BOX) {
                JCheckBox box = registryNameToCheckBox.get(rule.registryName);
                if (box != null) {
                    boolean target = config.isRuleEnabled(rule.registryName);
                    if (box.isSelected() != target) {
                        box.setSelected(target);
                    }
                    box.setEnabled(rule.canBeEnabled(enabledMap));
                }
            } else if (rule.type == Consts.TEXT_AREA) {
                JTextField field = registryNameToTextField.get(rule.registryName);
                if (field != null) {
                    // 对于 TEXT_AREA，我们可能需要从 config 获取 int 值并更新
                    // 但目前 GameRulesConfig 主要存储 boolean 状态，对于 int 值可能需要扩展
                    // 这里假设 config.getInt 返回正确的值
                    try {
                        int val = config.getInt(rule.registryName);
                        // 只有当值改变且焦点不在该输入框时才更新，避免用户输入时被打断
                        if (!field.hasFocus() && !String.valueOf(val).equals(field.getText())) {
                            field.setText(String.valueOf(val));
                        }
                    } catch (Exception ignored) {}
                    
                    // 检查依赖是否满足以启用/禁用输入框
                    field.setEnabled(rule.canBeEnabled(enabledMap));
                }
            }
        }
    }

    private void setupRuleCheckboxes(JPanel outsidePanel, JPanel extendedPanel, JPanel specialPanel) {
        // 1. Group rules by target component
        Map<String, List<RuleRegistry>> rulesByComponent = new HashMap<>();
        rulesByComponent.put("outside", new ArrayList<>());
        rulesByComponent.put("extended", new ArrayList<>());
        rulesByComponent.put("special", new ArrayList<>());

        for (RuleRegistry rule : RuleRegistry.values()) {
            if (!rule.displayOnUI) continue;
            List<RuleRegistry> list = rulesByComponent.get(rule.targetComponent);
            if (list != null) {
                list.add(rule);
            }
        }

        // 2. Process each panel
        processPanelRules(outsidePanel, rulesByComponent.get("outside"));
        processPanelRules(extendedPanel, rulesByComponent.get("extended"));
        processPanelRules(specialPanel, rulesByComponent.get("special"));
    }

    private void processPanelRules(JPanel panel, List<RuleRegistry> rules) {
        if (rules == null || rules.isEmpty()) return;

        // Map parent -> children
        Map<String, List<RuleRegistry>> dependencyMap = new HashMap<>();
        List<RuleRegistry> roots = new ArrayList<>();

        // Helper to check if a rule is in the current list
        Set<String> rulesInPanel = new HashSet<>();
        for (RuleRegistry r : rules) rulesInPanel.add(r.registryName);

        for (RuleRegistry rule : rules) {
            boolean isChild = false;
            // 对于 TEXT_AREA，它的 targetComponent 实际上指向了它应该跟随的 CHECK_BOX
            // 但在这里我们先处理 CHECK_BOX 的层级关系
            // TEXT_AREA 将在 addRuleRecursively 中作为特殊情况处理，或者在这里预处理
            
            // 现有的逻辑是基于 dependentRegistryNames 来构建树形结构
            // 如果 TEXT_AREA 依赖于某个 CHECK_BOX，它应该已经被包含在 dependentRegistryNames 中
            // 从而被正确归类为 child
            
            if (rule.dependentRegistryNames != null && rule.dependentRegistryNames.length == 1) {
                String parentName = rule.dependentRegistryNames[0];
                if (rulesInPanel.contains(parentName)) {
                    dependencyMap.computeIfAbsent(parentName, k -> new ArrayList<>()).add(rule);
                    isChild = true;
                }
            }
            if (!isChild) {
                roots.add(rule);
            }
        }

        for (RuleRegistry root : roots) {
            if (root.type == Consts.CHECK_BOX) {
                addRuleRecursively(root, panel, dependencyMap, 0);
            } else if (root.type == Consts.TEXT_AREA) {
                // 如果 TEXT_AREA 是 root（即没有依赖），直接添加
                addTextArea(root, panel, 0);
            }
        }
    }

    private void addRuleRecursively(RuleRegistry rule, JPanel panel, Map<String, List<RuleRegistry>> dependencyMap, int indentLevel) {
        JCheckBox box = createCheckBox(rule);

        // Apply indentation
        JPanel itemPanel = new JPanel();
        itemPanel.setLayout(new BoxLayout(itemPanel, BoxLayout.X_AXIS));
        itemPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        if (indentLevel > 0) {
            // Add rigid area for indentation
            itemPanel.add(Box.createRigidArea(new Dimension(20 * indentLevel, 0)));
        }
        itemPanel.add(box);
        // Ensure the panel doesn't expand vertically more than needed
        itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
        panel.add(itemPanel);
        panel.add(Box.createVerticalStrut(6));

        List<RuleRegistry> children = dependencyMap.get(rule.registryName);
        if (children != null) {
            for (RuleRegistry child : children) {
                if (child.type == Consts.CHECK_BOX) {
                    addRuleRecursively(child, panel, dependencyMap, indentLevel + 1);
                } else if (child.type == Consts.TEXT_AREA) {
                    // 如果子项是 TEXT_AREA，将其添加到当前 CHECK_BOX 下方
                    addTextArea(child, panel, indentLevel + 1);
                }
            }
        }
    }
    
    private void addTextArea(RuleRegistry rule, JPanel panel, int indentLevel) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.X_AXIS));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        if (indentLevel > 0) {
            container.add(Box.createRigidArea(new Dimension(20 * indentLevel, 0)));
        }
        
        JLabel label = new JLabel(rule.displayName + ": ");
        JTextField textField = new JTextField(5);
        textField.setName(rule.registryName);
        textField.setMaximumSize(new Dimension(100, 25));
        
        // 添加监听器以更新配置
        textField.addActionListener(e -> updateConfigFromTextField(rule, textField));
        textField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                updateConfigFromTextField(rule, textField);
            }
        });
        
        registryNameToTextField.put(rule.registryName, textField);
        
        container.add(label);
        container.add(textField);
        
        panel.add(container);
        panel.add(Box.createVerticalStrut(6));
    }
    
    private void updateConfigFromTextField(RuleRegistry rule, JTextField textField) {
        if (config != null) {
            try {
                String text = textField.getText().trim();
                if (!text.isEmpty()) {
                    int value = Integer.parseInt(text);
                    config.set(rule.registryName, value, GameRulesConfig.ChangeSource.UI);
                }
            } catch (NumberFormatException ex) {
                // 忽略无效输入，或者重置为旧值
                refreshUI();
            }
        }
    }

    private JCheckBox createCheckBox(RuleRegistry rule) {
        JCheckBox box = new JCheckBox(rule.displayName);
        box.setName(rule.registryName);
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

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
        return box;
    }

    /**
     * 设置所有玩法勾选框是否可用（如联网时客户端禁用）
     */
    public static void setAllCheckboxesEnabled(boolean enabled) {
        for (JCheckBox box : registryNameToCheckBox.values()) {
            box.setEnabled(enabled);
        }
        for (JTextField field : registryNameToTextField.values()) {
            field.setEnabled(enabled);
        }
    }

    /**
     * 根据玩法注册名判断当前勾选框是否被选中（即玩法是否启用）
     */
    public static boolean isEnabled(String registryName) {
        JCheckBox box = registryNameToCheckBox.get(registryName);
        return box != null && box.isSelected();
    }
    
    /**
     * 获取文本框的值
     */
    public static String getValue(String registryName) {
        JTextField field = registryNameToTextField.get(registryName);
        return field != null ? field.getText() : null;
    }
}
