package io.github.samera2022.chinese_chess.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.samera2022.chinese_chess.common.Consts;
import io.github.samera2022.chinese_chess.common.rules.RuleRegistry;

/**
 * 玩法设置面板 - 按 targetComponent 分组显示规则开关，支持上下滚动
 */
public class RuleSettingsPanel extends LinearLayout {

    // ── 规则变更监听器 ──
    public interface OnRuleChangeListener {
        void onRuleChanged(String key, Object value);
    }

    private OnRuleChangeListener ruleChangeListener;
    private final Map<String, Switch> registryNameToSwitch = new HashMap<>();
    private final Map<String, List<RuleRegistry>> rulesByComponent = new HashMap<>();
    private final LinearLayout contentContainer;

    /**
     * 设置规则变更监听器，当用户拨动开关时回调
     */
    public void setOnRuleChangeListener(OnRuleChangeListener listener) {
        this.ruleChangeListener = listener;
    }

    public RuleSettingsPanel(Context context) {
        this(context, null);
    }

    public RuleSettingsPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RuleSettingsPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);

        // 在最外层套一个 ScrollView，支持上下滚动
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        contentContainer = new LinearLayout(getContext());
        contentContainer.setOrientation(VERTICAL);
        scrollView.addView(contentContainer);

        addView(scrollView);

        init();
    }

    private void init() {
        // 按 targetComponent 分组
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (!rule.displayOnUI) continue;
            if (rule.type != Consts.CHECK_BOX) continue;

            List<RuleRegistry> list = rulesByComponent.get(rule.targetComponent);
            if (list == null) {
                list = new ArrayList<>();
                rulesByComponent.put(rule.targetComponent, list);
            }
            list.add(rule);
        }

        // 创建分组面板
        addComponentGroup("outside", "基础设置");
        addComponentGroup("extended", "延申玩法");
        addComponentGroup("special", "特殊玩法");

        // 加载默认值
        loadDefaults();
    }

    private void addComponentGroup(String component, String groupTitle) {
        List<RuleRegistry> rules = rulesByComponent.get(component);
        if (rules == null || rules.isEmpty()) return;

        // 分组标题
        TextView titleView = new TextView(getContext());
        titleView.setText(groupTitle);
        titleView.setTextSize(14);
        titleView.setPadding(8, 12, 8, 4);
        contentContainer.addView(titleView);

        // 每个规则对应一个 Switch 行
        for (RuleRegistry rule : rules) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setPadding(16, 4, 8, 4);

            TextView label = new TextView(getContext());
            label.setText(rule.displayName);
            label.setTextSize(13);
            label.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            Switch sw = new Switch(getContext());
            sw.setTag(rule.registryName);
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (ruleChangeListener != null) {
                    ruleChangeListener.onRuleChanged(rule.registryName, isChecked);
                }
            });
            row.addView(label);
            row.addView(sw);
            contentContainer.addView(row);

            registryNameToSwitch.put(rule.registryName, sw);
        }
    }

    private void loadDefaults() {
        for (RuleRegistry rule : RuleRegistry.values()) {
            if (!rule.displayOnUI) continue;
            if (rule.type != Consts.CHECK_BOX) continue;

            Switch sw = registryNameToSwitch.get(rule.registryName);
            if (sw != null) {
                boolean defaultValue = rule.defaultValue instanceof Boolean
                        ? (Boolean) rule.defaultValue : false;
                sw.setChecked(defaultValue);
            }
        }
    }

    /**
     * 获取当前设置快照
     * @return JsonObject 包含所有规则开关状态
     */
    public JsonObject getSettings() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Switch> entry : registryNameToSwitch.entrySet()) {
            json.addProperty(entry.getKey(), entry.getValue().isChecked());
        }
        return json;
    }

    /**
     * 应用设置快照
     * @param settings JsonObject 包含规则开关状态
     */
    public void applySettings(JsonObject settings) {
        if (settings == null) return;
        for (Map.Entry<String, com.google.gson.JsonElement> entry : settings.entrySet()) {
            String key = entry.getKey();
            Switch sw = registryNameToSwitch.get(key);
            if (sw != null && entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                sw.setChecked(entry.getValue().getAsBoolean());
            }
        }
    }

    /**
     * 收集当前所有规则开关状态到 Map
     */
    public Map<String, Boolean> getRuleValues() {
        Map<String, Boolean> values = new HashMap<>();
        for (Map.Entry<String, Switch> entry : registryNameToSwitch.entrySet()) {
            values.put(entry.getKey(), entry.getValue().isChecked());
        }
        return values;
    }
}
