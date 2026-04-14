package com.example.demo.ui;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.demo.model.ParameterNode;
import com.example.demo.model.UiControlConfig;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态 UI 生成器 - 根据参数配置生成 UI 控件
 */
public class DynamicUiGenerator {
    private static final String TAG = "DynamicUiGenerator";

    private final Activity activity;
    private final Map<String, View> controlMap;
    private final Map<String, ParameterNode.Parameter> paramMap;
    private final Map<LinearLayout, LinearLayout> groupContentMap;  // 存储分组容器和内容容器的映射

    public DynamicUiGenerator(Activity activity) {
        this.activity = activity;
        this.controlMap = new HashMap<>();
        this.paramMap = new HashMap<>();
        this.groupContentMap = new HashMap<>();
    }

    /**
     * 根据参数列表生成 UI 控件配置
     */
    public List<UiControlConfig> generateUiLayout(List<ParameterNode> parameters) {
        List<UiControlConfig> configs = new ArrayList<>();

        for (ParameterNode node : parameters) {
            for (ParameterNode.Parameter param : node.getParams()) {
                UiControlConfig config = createControlConfig(node, param);
                configs.add(config);
            }
        }

        // 按优先级排序
        configs.sort((a, b) -> Integer.compare(a.getLayout().getPriority(), b.getLayout().getPriority()));

        return configs;
    }

    /**
     * 创建控件配置
     */
    private UiControlConfig createControlConfig(ParameterNode node, ParameterNode.Parameter param) {
        UiControlConfig config = new UiControlConfig(param, mapToControlType(param));

        // 设置布局配置
        config.getLayout().setGroup(param.getGroup());
        config.getLayout().setPriority(calculatePriority(param));
        config.getLayout().setVisible(true);

        // 设置验证配置
        if (param.getType() == ParameterNode.Parameter.ParameterType.INTEGER ||
            param.getType() == ParameterNode.Parameter.ParameterType.SEED) {
            config.getValidation().setMinValue(param.getMinValue());
            config.getValidation().setMaxValue(param.getMaxValue());
        }

        return config;
    }

    /**
     * 根据参数类型映射到控件类型
     */
    private UiControlConfig.UiControlType mapToControlType(ParameterNode.Parameter param) {
        switch (param.getType()) {
            case TEXT_AREA:
            case TEXT:
                return UiControlConfig.UiControlType.EDIT_TEXT_MULTI_LINE;
            case INTEGER:
                if (param.getName().equals("width") || param.getName().equals("height")) {
                    return UiControlConfig.UiControlType.RESOLUTION_INPUT;
                }
                return UiControlConfig.UiControlType.SEEK_BAR;
            case FLOAT:
                return UiControlConfig.UiControlType.SEEK_BAR;
            case SEED:
                return UiControlConfig.UiControlType.SEED_INPUT;
            case BOOLEAN:
                return UiControlConfig.UiControlType.SWITCH;
            case STRING_SELECT:
            case INT_SELECT:
                return UiControlConfig.UiControlType.SPINNER;
            default:
                return UiControlConfig.UiControlType.EDIT_TEXT_SINGLE_LINE;
        }
    }

    /**
     * 计算优先级
     */
    private int calculatePriority(ParameterNode.Parameter param) {
        String group = param.getGroup();
        switch (group) {
            case "提示词":
                return 10;
            case "随机种子":
                return 20;
            case "采样参数":
                return 30;
            case "分辨率":
                return 40;
            case "采样设置":
                return 50;
            default:
                return 60;
        }
    }

    /**
     * 动态创建 UI 控件
     */
    public List<View> createUiControls(ViewGroup parent, List<UiControlConfig> configs) {
        List<View> controls = new ArrayList<>();
        LinearLayout root = (LinearLayout) parent;

        String currentGroup = null;
        LinearLayout currentGroupContainer = null;

        for (UiControlConfig config : configs) {
            // 检查是否需要创建新的分组
            String group = config.getLayout().getGroup();
            if (!group.equals(currentGroup)) {
                currentGroup = group;
                currentGroupContainer = createGroupContainer(group);
                root.addView(currentGroupContainer);
            }

            // 创建控件
            View control = createControlForParameter(currentGroupContainer, config);
            if (control != null) {
                controls.add(control);
                String key = config.getParam().getName();
                controlMap.put(key, control);
                paramMap.put(key, config.getParam());
            }
        }

        return controls;
    }

    /**
     * 创建分组容器
     */
    private LinearLayout createGroupContainer(String groupName) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(16, 8, 16, 8);

        // 分组标题
        TextView title = new TextView(activity);
        title.setText(groupName);
        title.setTextSize(16);
        title.setPadding(0, 8, 0, 8);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        container.addView(title, titleParams);

        // 分组内容容器
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(8, 0, 8, 0);

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        contentParams.setMargins(0, 0, 0, 8);
        container.addView(content, contentParams);

        // 存储内容容器引用
        groupContentMap.put(container, content);

        return container;
    }

    /**
     * 获取分组内容容器
     */
    private LinearLayout getGroupContentContainer(LinearLayout container) {
        return groupContentMap.get(container);
    }

    /**
     * 根据参数类型创建单个控件
     */
    public View createControlForParameter(ViewGroup parent, UiControlConfig config) {
        ParameterNode.Parameter param = config.getParam();
        UiControlConfig.UiControlType controlType = config.getControlType();

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 4, 0, 4);

        // 标签
        TextView label = new TextView(activity);
        label.setText(param.getDisplayName());
        label.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, 0, 0, 4);
        container.addView(label, labelParams);

        View control;
        switch (controlType) {
            case EDIT_TEXT_SINGLE_LINE:
                control = createEditText(param, false);
                break;
            case EDIT_TEXT_MULTI_LINE:
                control = createEditText(param, true);
                break;
            case SEEK_BAR:
                control = createSeekBar(param);
                break;
            case SEED_INPUT:
                control = createSeedInput(param);
                break;
            case SPINNER:
                control = createSpinner(param);
                break;
            case RESOLUTION_INPUT:
                control = createResolutionInput(param);
                break;
            default:
                control = createEditText(param, false);
        }

        container.addView(control);
        parent.addView(container);

        return container;
    }

    /**
     * 创建 EditText
     */
    private EditText createEditText(ParameterNode.Parameter param, boolean multiLine) {
        EditText editText = new EditText(activity);
        editText.setHint(param.getDisplayName());

        if (multiLine) {
            editText.setMinLines(3);
            editText.setMaxLines(5);
            editText.setHorizontallyScrolling(false);
        }

        // 设置默认值
        if (param.getDefaultValue() != null) {
            editText.setText(param.getDefaultValue().toString());
        }

        return editText;
    }

    /**
     * 创建 SeekBar
     */
    private View createSeekBar(ParameterNode.Parameter param) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);

        EditText editText = new EditText(activity);
        editText.setHint("值");
        editText.setWidth(100);

        // 设置默认值
        if (param.getDefaultValue() != null) {
            editText.setText(param.getDefaultValue().toString());
        }

        // 这里简化处理，实际应该创建真正的 SeekBar
        container.addView(editText);

        return container;
    }

    /**
     * 创建种子输入
     */
    private View createSeedInput(ParameterNode.Parameter param) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);

        EditText editText = new EditText(activity);
        editText.setHint("随机种子");
        editText.setWidth(200);

        if (param.getDefaultValue() != null) {
            editText.setText(param.getDefaultValue().toString());
        }

        Button randomBtn = new Button(activity);
        randomBtn.setText("随机");
        randomBtn.setOnClickListener(v -> {
            long randomSeed = (long) (Math.random() * 999999999999999L);
            editText.setText(String.valueOf(randomSeed));
        });

        container.addView(editText);
        container.addView(randomBtn);

        return container;
    }

    /**
     * 创建下拉选择
     */
    private Spinner createSpinner(ParameterNode.Parameter param) {
        Spinner spinner = new Spinner(activity);

        // 获取可选值
        String[] options = getSelectOptions(param.getName());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // 设置默认值
        if (param.getDefaultValue() != null) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(param.getDefaultValue().toString())) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }

        return spinner;
    }

    /**
     * 获取下拉选项
     */
    private String[] getSelectOptions(String paramName) {
        switch (paramName) {
            case "sampler_name":
                return new String[]{"euler", "euler_ancestral", "heun", "dpm_2", "dpm_2_ancestral", "lms", "dpm_fast", "dpm_adaptive"};
            case "scheduler":
                return new String[]{"normal", "karras", "exponential", "sgm_uniform", "simple", "ddim_uniform"};
            default:
                return new String[]{"default"};
        }
    }

    /**
     * 创建分辨率输入
     */
    private View createResolutionInput(ParameterNode.Parameter param) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);

        EditText widthEdit = new EditText(activity);
        widthEdit.setHint("宽");
        widthEdit.setWidth(100);
        widthEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        EditText heightEdit = new EditText(activity);
        heightEdit.setHint("高");
        heightEdit.setWidth(100);
        heightEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        if (param.getName().equals("width") && param.getDefaultValue() != null) {
            widthEdit.setText(param.getDefaultValue().toString());
        } else if (param.getName().equals("height") && param.getDefaultValue() != null) {
            heightEdit.setText(param.getDefaultValue().toString());
        }

        container.addView(widthEdit);
        container.addView(heightEdit);

        return container;
    }

    /**
     * 从 UI 控件收集用户输入值
     */
    public void collectUiValues(List<View> controls, Map<String, Object> values) {
        for (Map.Entry<String, View> entry : controlMap.entrySet()) {
            String key = entry.getKey();
            View control = entry.getValue();

            if (control instanceof EditText) {
                String text = ((EditText) control).getText().toString();
                if (!text.isEmpty()) {
                    ParameterNode.Parameter param = paramMap.get(key);
                    if (param != null) {
                        switch (param.getType()) {
                            case INTEGER:
                            case SEED:
                                try {
                                    values.put(key, Long.parseLong(text));
                                } catch (NumberFormatException e) {
                                    values.put(key, text);
                                }
                                break;
                            case FLOAT:
                                try {
                                    values.put(key, Float.parseFloat(text));
                                } catch (NumberFormatException e) {
                                    values.put(key, text);
                                }
                                break;
                            default:
                                values.put(key, text);
                        }
                    }
                }
            } else if (control instanceof Spinner) {
                Spinner spinner = (Spinner) control;
                String selected = spinner.getSelectedItem() != null ?
                        spinner.getSelectedItem().toString() : "";
                values.put(key, selected);
            }
        }
    }

    /**
     * 重置 UI 控件到默认值
     */
    public void resetUiControls(List<View> controls, List<ParameterNode> parameters) {
        for (ParameterNode node : parameters) {
            for (ParameterNode.Parameter param : node.getParams()) {
                View control = controlMap.get(param.getName());
                if (control instanceof EditText) {
                    if (param.getDefaultValue() != null) {
                        ((EditText) control).setText(param.getDefaultValue().toString());
                    } else {
                        ((EditText) control).setText("");
                    }
                }
            }
        }
    }

    /**
     * 清空控件映射
     */
    public void clearControls() {
        controlMap.clear();
        paramMap.clear();
    }

    /**
     * 获取控件映射
     */
    public Map<String, View> getControlMap() {
        return controlMap;
    }
}
