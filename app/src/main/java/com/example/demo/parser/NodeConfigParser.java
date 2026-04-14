package com.example.demo.parser;

import android.util.Log;

import com.example.demo.model.ParameterNode;
import com.example.demo.model.ParsedWorkflow;
import com.example.demo.model.ValidationResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点配置解析器 - 解析工作流 JSON 并提取可配置参数
 */
public class NodeConfigParser {
    private static final String TAG = "NodeConfigParser";

    // 参数类型识别规则
    private static final Map<String, ParameterNode.Parameter.ParameterType> PARAM_TYPE_RULES = new HashMap<>();
    static {
        // 文本类参数
        PARAM_TYPE_RULES.put("text", ParameterNode.Parameter.ParameterType.TEXT_AREA);
        PARAM_TYPE_RULES.put("positive", ParameterNode.Parameter.ParameterType.TEXT_AREA);
        PARAM_TYPE_RULES.put("negative", ParameterNode.Parameter.ParameterType.TEXT_AREA);

        // 种子参数
        PARAM_TYPE_RULES.put("seed", ParameterNode.Parameter.ParameterType.SEED);
        PARAM_TYPE_RULES.put("noise_seed", ParameterNode.Parameter.ParameterType.SEED);

        // 整数参数
        PARAM_TYPE_RULES.put("steps", ParameterNode.Parameter.ParameterType.INTEGER);
        PARAM_TYPE_RULES.put("width", ParameterNode.Parameter.ParameterType.INTEGER);
        PARAM_TYPE_RULES.put("height", ParameterNode.Parameter.ParameterType.INTEGER);
        PARAM_TYPE_RULES.put("batch_size", ParameterNode.Parameter.ParameterType.INTEGER);
        PARAM_TYPE_RULES.put("start_at_step", ParameterNode.Parameter.ParameterType.INTEGER);
        PARAM_TYPE_RULES.put("end_at_step", ParameterNode.Parameter.ParameterType.INTEGER);

        // 浮点数参数
        PARAM_TYPE_RULES.put("cfg", ParameterNode.Parameter.ParameterType.FLOAT);
        PARAM_TYPE_RULES.put("denoise", ParameterNode.Parameter.ParameterType.FLOAT);
        PARAM_TYPE_RULES.put("strength", ParameterNode.Parameter.ParameterType.FLOAT);
        PARAM_TYPE_RULES.put("scale", ParameterNode.Parameter.ParameterType.FLOAT);
        PARAM_TYPE_RULES.put("shift", ParameterNode.Parameter.ParameterType.FLOAT);

        // 布尔参数
        PARAM_TYPE_RULES.put("return_mask", ParameterNode.Parameter.ParameterType.BOOLEAN);
        PARAM_TYPE_RULES.put("add_mask", ParameterNode.Parameter.ParameterType.BOOLEAN);
    }

    // 下拉选择参数及其可选值
    private static final Map<String, String[]> SELECT_OPTIONS = new HashMap<>();
    static {
        SELECT_OPTIONS.put("sampler_name", new String[]{"euler", "euler_ancestral", "heun", "dpm_2", "dpm_2_ancestral", "lms", "dpm_fast", "dpm_adaptive", "dpmpp_2s_ancestral", "dpmpp_sde", "dpmpp_2m", "dpmpp_2m_sde", "dpmpp_3m_sde", "ddim", "uni_pc", "uni_pc_bh2"});
        SELECT_OPTIONS.put("scheduler", new String[]{"normal", "karras", "exponential", "sgm_uniform", "simple", "ddim_uniform"});
        SELECT_OPTIONS.put("order", new String[]{"order_1", "order_2", "order_3", "order_4"});
        SELECT_OPTIONS.put("type", new String[]{"lumina2", "t5xxl_fp8_e4m3fn", "t5xxl_fp16"});
    }

    /**
     * 解析工作流 JSON
     */
    public ParsedWorkflow parseWorkflow(String jsonContent, String configName) {
        ParsedWorkflow parsed = new ParsedWorkflow();
        parsed.setConfigName(configName);

        try {
            JsonObject workflow = JsonParser.parseString(jsonContent).getAsJsonObject();
            parsed.setWorkflow(workflow);

            // 遍历所有节点
            for (Map.Entry<String, com.google.gson.JsonElement> entry : workflow.entrySet()) {
                String nodeId = entry.getKey();
                JsonElement element = entry.getValue();

                if (element.isJsonObject()) {
                    JsonObject node = element.getAsJsonObject();
                    parseNode(parsed, nodeId, node);
                }
            }

            // 自动检测参数类型
            autoDetectParameterTypes(parsed.getParameters());

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse workflow", e);
            parsed.addError("JSON 解析失败：" + e.getMessage());
        }

        return parsed;
    }

    /**
     * 解析单个节点
     */
    private void parseNode(ParsedWorkflow parsed, String nodeId, JsonObject node) {
        String classType = node.has("class_type") ? node.get("class_type").getAsString() : "";
        String title = node.has("_meta") && node.getAsJsonObject("_meta").has("title")
                ? node.getAsJsonObject("_meta").get("title").getAsString() : "";

        ParameterNode parameterNode = new ParameterNode(nodeId, classType);
        parameterNode.setNodeName(title);

        // 解析 inputs
        if (node.has("inputs") && node.get("inputs").isJsonObject()) {
            JsonObject inputs = node.getAsJsonObject("inputs");

            for (Map.Entry<String, JsonElement> entry : inputs.entrySet()) {
                String paramName = entry.getKey();
                JsonElement value = entry.getValue();

                ParameterNode.Parameter param = new ParameterNode.Parameter();
                param.setName(paramName);

                // 设置默认值
                if (value.isJsonPrimitive()) {
                    com.google.gson.JsonPrimitive primitive = value.getAsJsonPrimitive();
                    if (primitive.isString()) {
                        param.setDefaultValue(primitive.getAsString());
                        // 检查是否是下拉选择
                        if (SELECT_OPTIONS.containsKey(paramName)) {
                            param.setType(ParameterNode.Parameter.ParameterType.STRING_SELECT);
                        } else {
                            param.setType(PARAM_TYPE_RULES.getOrDefault(paramName, ParameterNode.Parameter.ParameterType.TEXT));
                        }
                    } else if (primitive.isNumber()) {
                        String numStr = primitive.getAsString();
                        if (numStr.contains(".")) {
                            param.setDefaultValue(primitive.getAsFloat());
                            param.setType(PARAM_TYPE_RULES.getOrDefault(paramName, ParameterNode.Parameter.ParameterType.FLOAT));
                        } else {
                            param.setDefaultValue(primitive.getAsInt());
                            param.setType(PARAM_TYPE_RULES.getOrDefault(paramName, ParameterNode.Parameter.ParameterType.INTEGER));
                        }
                    } else if (primitive.isBoolean()) {
                        param.setDefaultValue(primitive.getAsBoolean());
                        param.setType(ParameterNode.Parameter.ParameterType.BOOLEAN);
                    }
                } else if (value.isJsonArray()) {
                    // 连接类型（如 ["10", 0]）
                    param.setType(ParameterNode.Parameter.ParameterType.LATENT_IMAGE);
                    param.setDefaultValue(value.toString());
                }

                // 设置显示名称
                param.setDisplayName(formatParamName(paramName));

                // 设置默认分组和顺序
                assignGroupAndOrder(param);

                parameterNode.addParameter(param);
            }
        }

        // 只有包含可配置参数的节点才添加
        if (!parameterNode.getParams().isEmpty()) {
            parsed.addParameter(parameterNode);
        }
    }

    /**
     * 自动检测参数类型
     */
    public void autoDetectParameterTypes(List<ParameterNode> parameters) {
        int orderCounter = 1;
        for (ParameterNode node : parameters) {
            for (ParameterNode.Parameter param : node.getParams()) {
                if (param.getType() == null) {
                    // 根据默认值类型推断
                    Object defaultValue = param.getDefaultValue();
                    if (defaultValue instanceof Integer) {
                        param.setType(ParameterNode.Parameter.ParameterType.INTEGER);
                    } else if (defaultValue instanceof Float || defaultValue instanceof Double) {
                        param.setType(ParameterNode.Parameter.ParameterType.FLOAT);
                    } else if (defaultValue instanceof Boolean) {
                        param.setType(ParameterNode.Parameter.ParameterType.BOOLEAN);
                    } else {
                        param.setType(ParameterNode.Parameter.ParameterType.TEXT);
                    }
                }
                param.setOrder(orderCounter++);
            }
        }
    }

    /**
     * 验证工作流有效性
     */
    public ValidationResult validateWorkflow(String jsonContent) {
        ValidationResult result = new ValidationResult();

        try {
            JsonObject workflow = JsonParser.parseString(jsonContent).getAsJsonObject();

            // 检查是否为空
            if (workflow.entrySet().isEmpty()) {
                result.addError("工作流为空");
            }

            // 检查每个节点
            for (Map.Entry<String, JsonElement> entry : workflow.entrySet()) {
                String nodeId = entry.getKey();
                JsonElement element = entry.getValue();

                if (!element.isJsonObject()) {
                    result.addWarning("节点 " + nodeId + " 格式不正确");
                    continue;
                }

                JsonObject node = element.getAsJsonObject();

                // 检查必需字段
                if (!node.has("class_type")) {
                    result.addWarning("节点 " + nodeId + " 缺少 class_type 字段");
                }

                // 检查 inputs
                if (node.has("inputs") && !node.get("inputs").isJsonObject()) {
                    result.addWarning("节点 " + nodeId + " 的 inputs 格式不正确");
                }
            }

        } catch (Exception e) {
            result.addError("JSON 格式错误：" + e.getMessage());
        }

        return result;
    }

    /**
     * 格式化参数名（首字母大写，下划线转空格）
     */
    private String formatParamName(String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return paramName;
        }

        // 下划线转空格，每个单词首字母大写
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : paramName.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 分配分组和顺序
     */
    private void assignGroupAndOrder(ParameterNode.Parameter param) {
        String paramName = param.getName().toLowerCase();

        if (paramName.equals("text") || paramName.equals("positive") || paramName.equals("negative")) {
            param.setGroup("提示词");
        } else if (paramName.equals("seed") || paramName.equals("noise_seed")) {
            param.setGroup("随机种子");
        } else if (paramName.equals("steps") || paramName.equals("cfg") || paramName.equals("denoise")) {
            param.setGroup("采样参数");
        } else if (paramName.equals("width") || paramName.equals("height")) {
            param.setGroup("分辨率");
        } else if (paramName.equals("sampler_name") || paramName.equals("scheduler")) {
            param.setGroup("采样设置");
        } else {
            param.setGroup("其他");
        }
    }
}
