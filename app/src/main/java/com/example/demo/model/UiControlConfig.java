package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * UI 控件映射配置
 */
public class UiControlConfig {
    private ParameterNode.Parameter param;
    private UiControlType controlType;
    private LayoutConfig layout;
    private ValidationConfig validation;

    public enum UiControlType {
        EDIT_TEXT_SINGLE_LINE,    // 单行输入框
        EDIT_TEXT_MULTI_LINE,     // 多行输入框
        SEEK_BAR,                 // 滑动条
        NUMBER_PICKER,            // 数字选择器
        SPINNER,                  // 下拉选择
        SWITCH,                   // 开关
        SEED_INPUT,               // 种子输入（带随机按钮）
        RESOLUTION_INPUT          // 分辨率输入（宽高）
    }

    public UiControlConfig() {
        this.layout = new LayoutConfig();
        this.validation = new ValidationConfig();
    }

    public UiControlConfig(ParameterNode.Parameter param, UiControlType controlType) {
        this();
        this.param = param;
        this.controlType = controlType;
    }

    // Getter 和 Setter 方法
    public ParameterNode.Parameter getParam() {
        return param;
    }

    public void setParam(ParameterNode.Parameter param) {
        this.param = param;
    }

    public UiControlType getControlType() {
        return controlType;
    }

    public void setControlType(UiControlType controlType) {
        this.controlType = controlType;
    }

    public LayoutConfig getLayout() {
        return layout;
    }

    public void setLayout(LayoutConfig layout) {
        this.layout = layout;
    }

    public ValidationConfig getValidation() {
        return validation;
    }

    public void setValidation(ValidationConfig validation) {
        this.validation = validation;
    }

    /**
     * 布局配置
     */
    public static class LayoutConfig {
        private int priority;     // 显示优先级（0-100，数值越大越靠前）
        private boolean visible;  // 是否可见
        private String group;     // 分组名称（用于分组显示）

        public LayoutConfig() {
            this.priority = 50;
            this.visible = true;
            this.group = "默认";
        }

        // Getter 和 Setter 方法
        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }
    }

    /**
     * 验证配置
     */
    public static class ValidationConfig {
        private int minValue;     // 最小值
        private int maxValue;     // 最大值
        private int step;         // 步进
        private String pattern;   // 正则表达式验证

        public ValidationConfig() {
            this.minValue = Integer.MIN_VALUE;
            this.maxValue = Integer.MAX_VALUE;
            this.step = 1;
        }

        // Getter 和 Setter 方法
        public int getMinValue() {
            return minValue;
        }

        public void setMinValue(int minValue) {
            this.minValue = minValue;
        }

        public int getMaxValue() {
            return maxValue;
        }

        public void setMaxValue(int maxValue) {
            this.maxValue = maxValue;
        }

        public int getStep() {
            return step;
        }

        public void setStep(int step) {
            this.step = step;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }

    @Override
    public String toString() {
        return "UiControlConfig{" +
                "param=" + param +
                ", controlType=" + controlType +
                ", group=" + layout.group +
                '}';
    }
}
