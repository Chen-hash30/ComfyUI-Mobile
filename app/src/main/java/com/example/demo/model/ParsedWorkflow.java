package com.example.demo.model;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作流解析结果
 */
public class ParsedWorkflow {
    private String configId;              // 配置 ID
    private String configName;            // 配置名称
    private String configDescription;     // 配置描述
    private JsonObject workflow;          // 原始工作流 JSON
    private List<ParameterNode> parameters;  // 提取的参数节点列表
    private List<String> errors;          // 解析错误列表

    public ParsedWorkflow() {
        this.parameters = new ArrayList<>();
        this.errors = new ArrayList<>();
    }

    public ParsedWorkflow(String configId, String configName, JsonObject workflow) {
        this();
        this.configId = configId;
        this.configName = configName;
        this.workflow = workflow;
    }

    // Getter 和 Setter 方法
    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getConfigDescription() {
        return configDescription;
    }

    public void setConfigDescription(String configDescription) {
        this.configDescription = configDescription;
    }

    public JsonObject getWorkflow() {
        return workflow;
    }

    public void setWorkflow(JsonObject workflow) {
        this.workflow = workflow;
    }

    public List<ParameterNode> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterNode> parameters) {
        this.parameters = parameters;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addParameter(ParameterNode parameterNode) {
        this.parameters.add(parameterNode);
    }

    public void addError(String error) {
        this.errors.add(error);
    }

    /**
     * 检查解析是否成功
     */
    public boolean isValid() {
        return workflow != null && errors.isEmpty();
    }

    /**
     * 获取参数节点列表（按 order 排序）
     */
    public List<ParameterNode> getSortedParameters() {
        List<ParameterNode> sorted = new ArrayList<>(parameters);
        sorted.sort((a, b) -> {
            int aOrder = Integer.MAX_VALUE;
            int bOrder = Integer.MAX_VALUE;
            for (ParameterNode.Parameter p : a.getParams()) {
                if (p.getOrder() < aOrder) aOrder = p.getOrder();
            }
            for (ParameterNode.Parameter p : b.getParams()) {
                if (p.getOrder() < bOrder) bOrder = p.getOrder();
            }
            return Integer.compare(aOrder, bOrder);
        });
        return sorted;
    }

    @Override
    public String toString() {
        return "ParsedWorkflow{" +
                "configId='" + configId + '\'' +
                ", configName='" + configName + '\'' +
                ", isValid=" + isValid() +
                ", parameterCount=" + parameters.size() +
                '}';
    }
}
