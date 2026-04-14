package com.example.demo.api;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/**
 * API 请求构建器 - 动态构建 ComfyUI API 请求
 */
public class ApiRequestBuilder {
    private static final String TAG = "ApiRequestBuilder";

    /**
     * 构建生图请求体
     */
    public JsonObject buildPromptRequest(JsonObject workflow, Map<String, Object> params) {
        // 克隆工作流以避免修改原始对象
        JsonObject prompt = workflow.deepCopy();

        // 更新每个参数
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 解析 key 格式：nodeId.paramName
            String[] parts = key.split("\\.");
            if (parts.length == 2) {
                String nodeId = parts[0];
                String paramName = parts[1];
                updateNodeParameter(prompt, nodeId, paramName, value);
            } else {
                // 尝试直接匹配参数名
                updateParameterByname(prompt, key, value);
            }
        }

        return prompt;
    }

    /**
     * 更新单个节点参数
     */
    public void updateNodeParameter(JsonObject workflow, String nodeId, String paramName, Object value) {
        if (!workflow.has(nodeId)) {
            Log.w(TAG, "Node not found: " + nodeId);
            return;
        }

        JsonObject node = workflow.getAsJsonObject(nodeId);
        if (!node.has("inputs")) {
            node.add("inputs", new JsonObject());
        }

        JsonObject inputs = node.getAsJsonObject("inputs");

        // 根据值类型添加属性
        if (value instanceof String) {
            inputs.addProperty(paramName, (String) value);
        } else if (value instanceof Integer) {
            inputs.addProperty(paramName, (Integer) value);
        } else if (value instanceof Long) {
            inputs.addProperty(paramName, (Long) value);
        } else if (value instanceof Float) {
            inputs.addProperty(paramName, (Float) value);
        } else if (value instanceof Double) {
            inputs.addProperty(paramName, (Double) value);
        } else if (value instanceof Boolean) {
            inputs.addProperty(paramName, (Boolean) value);
        } else {
            inputs.addProperty(paramName, value.toString());
        }
    }

    /**
     * 通过参数名更新（自动查找节点）
     */
    private void updateParameterByname(JsonObject workflow, String paramName, Object value) {
        // 遍历所有节点查找包含该参数的节点
        for (Map.Entry<String, JsonElement> entry : workflow.entrySet()) {
            String nodeId = entry.getKey();
            JsonElement element = entry.getValue();

            if (element.isJsonObject()) {
                JsonObject node = element.getAsJsonObject();
                if (node.has("inputs")) {
                    JsonObject inputs = node.getAsJsonObject("inputs");
                    if (inputs.has(paramName)) {
                        updateNodeParameter(workflow, nodeId, paramName, value);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 获取最终的工作流 JSON
     */
    public JsonObject getFinalPrompt(JsonObject workflow) {
        return workflow;
    }

    /**
     * 验证参数完整性
     */
    public boolean validateParameters(JsonObject workflow, Map<String, Object> params) {
        // 检查必需参数
        String[] requiredParams = {"text", "seed", "steps", "cfg", "width", "height"};

        for (String param : requiredParams) {
            boolean found = false;
            for (Map.Entry<String, JsonElement> entry : workflow.entrySet()) {
                JsonObject node = entry.getValue().getAsJsonObject();
                if (node.has("inputs")) {
                    JsonObject inputs = node.getAsJsonObject("inputs");
                    if (inputs.has(param)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                Log.w(TAG, "Missing required parameter: " + param);
            }
        }

        return true;
    }

    /**
     * 构建完整的 API 请求体（包含 prompt 和 client_id）
     */
    public JsonObject buildCompleteRequest(JsonObject workflow, Map<String, Object> params, String clientId) {
        JsonObject root = new JsonObject();

        // 添加 prompt
        JsonObject prompt = buildPromptRequest(workflow, params);
        root.add("prompt", prompt);

        // 添加 client_id
        if (clientId != null && !clientId.isEmpty()) {
            root.addProperty("client_id", clientId);
        } else {
            root.addProperty("client_id", "android_client");
        }

        return root;
    }

    /**
     * 从 JSON 字符串解析工作流
     */
    public JsonObject parseWorkflow(String jsonContent) {
        try {
            return JsonParser.parseString(jsonContent).getAsJsonObject();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse workflow JSON", e);
            return null;
        }
    }

    /**
     * 将工作流转换为 JSON 字符串
     */
    public String workflowToJson(JsonObject workflow) {
        return workflow.toString();
    }
}
