package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流中的可配置参数节点
 */
public class ParameterNode {
    private String nodeId;            // 节点 ID（如 "8", "10"）
    private String nodeName;          // 节点显示名称
    private String classType;         // 节点类型（如 "CLIPTextEncode"）
    private List<Parameter> params;   // 参数列表

    public ParameterNode() {
        this.params = new ArrayList<>();
    }

    public ParameterNode(String nodeId, String classType) {
        this();
        this.nodeId = nodeId;
        this.classType = classType;
    }

    // Getter 和 Setter 方法
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getClassType() {
        return classType;
    }

    public void setClassType(String classType) {
        this.classType = classType;
    }

    public List<Parameter> getParams() {
        return params;
    }

    public void setParams(List<Parameter> params) {
        this.params = params;
    }

    public void addParameter(Parameter param) {
        this.params.add(param);
    }

    public Parameter findParameter(String paramName) {
        for (Parameter param : params) {
            if (param.name.equals(paramName)) {
                return param;
            }
        }
        return null;
    }

    /**
     * 参数定义
     */
    public static class Parameter {
        private String name;          // 参数名（如 "text", "seed", "steps"）
        private ParameterType type;   // 参数类型
        private Object defaultValue;  // 默认值
        private String displayName;   // 显示名称
        private int order;            // 显示顺序
        private String group;         // 分组名称
        private int minValue;         // 最小值
        private int maxValue;         // 最大值
        private float step;           // 步进

        public enum ParameterType {
            TEXT,           // 文本输入
            TEXT_AREA,      // 多行文本
            NUMBER,         // 数字输入
            INTEGER,        // 整数输入
            FLOAT,          // 浮点数输入
            SEED,           // 种子（特殊整数）
            BOOLEAN,        // 布尔值
            STRING_SELECT,  // 字符串下拉选择
            INT_SELECT,     // 整数下拉选择
            FILE_PATH,      // 文件路径
            LATENT_IMAGE    // latent 图像参数
        }

        public Parameter() {
            this.order = 0;
            this.minValue = Integer.MIN_VALUE;
            this.maxValue = Integer.MAX_VALUE;
            this.step = 1.0f;
        }

        public Parameter(String name, ParameterType type) {
            this();
            this.name = name;
            this.type = type;
        }

        // Getter 和 Setter 方法
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public ParameterType getType() {
            return type;
        }

        public void setType(ParameterType type) {
            this.type = type;
        }

        public Object getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

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

        public float getStep() {
            return step;
        }

        public void setStep(float step) {
            this.step = step;
        }

        @Override
        public String toString() {
            return "Parameter{" +
                    "name='" + name + '\'' +
                    ", type=" + type +
                    ", defaultValue=" + defaultValue +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "ParameterNode{" +
                "nodeId='" + nodeId + '\'' +
                ", classType='" + classType + '\'' +
                ", params=" + params +
                '}';
    }
}
