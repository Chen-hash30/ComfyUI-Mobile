package com.example.demo.manager;

import android.content.Context;
import android.util.Log;

import com.example.demo.model.NodeConfig;
import com.example.demo.model.ParsedWorkflow;
import com.example.demo.model.ValidationResult;
import com.example.demo.parser.NodeConfigParser;
import com.google.gson.JsonObject;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 节点配置管理器 - 负责节点配置的 CRUD 操作
 */
public class NodeConfigManager {
    private static final String TAG = "NodeConfigManager";

    private final Context context;
    private final StorageManager storageManager;
    private final NodeConfigParser parser;

    private static NodeConfigManager instance;

    private NodeConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.storageManager = StorageManager.getInstance(context);
        this.parser = new NodeConfigParser();
    }

    public static synchronized NodeConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new NodeConfigManager(context);
        }
        return instance;
    }

    /**
     * 加载所有节点配置
     */
    public List<NodeConfig> loadAllConfigs() {
        List<NodeConfig> configs = new ArrayList<>();

        // 加载内置配置
        String defaultWorkflow = storageManager.loadBuiltinWorkflow("workflow.json");
        if (defaultWorkflow != null) {
            NodeConfig builtinConfig = new NodeConfig();
            builtinConfig.setId("builtin_default");
            builtinConfig.setName("默认工作流");
            builtinConfig.setDescription("系统内置的默认工作流");
            builtinConfig.setFileName("workflow.json");
            builtinConfig.setActive(false);
            builtinConfig.setType(NodeConfig.ConfigType.BUILTIN);
            configs.add(builtinConfig);
        }

        // 加载用户配置
        List<NodeConfig> userConfigs = storageManager.loadConfigList();
        configs.addAll(userConfigs);

        // 标记激活的配置
        String activeId = storageManager.getActiveConfigId();
        if (activeId != null) {
            for (NodeConfig config : configs) {
                config.setActive(config.getId().equals(activeId));
                if (config.isActive()) {
                    break;
                }
            }
        }

        // 如果没有激活的配置，标记第一个为激活
        if (activeId == null && !configs.isEmpty()) {
            configs.get(0).setActive(true);
            storageManager.setActiveConfigId(configs.get(0).getId());
        }

        return configs;
    }

    /**
     * 根据 ID 获取配置
     */
    public NodeConfig getConfig(String id) {
        if ("builtin_default".equals(id)) {
            NodeConfig config = new NodeConfig();
            config.setId(id);
            config.setName("默认工作流");
            config.setDescription("系统内置的默认工作流");
            config.setFileName("workflow.json");
            config.setType(NodeConfig.ConfigType.BUILTIN);
            return config;
        }

        for (NodeConfig config : storageManager.loadConfigList()) {
            if (config.getId().equals(id)) {
                return config;
            }
        }
        return null;
    }

    /**
     * 获取当前激活的配置
     */
    public NodeConfig getActiveConfig() {
        String activeId = storageManager.getActiveConfigId();
        if (activeId != null) {
            NodeConfig config = getConfig(activeId);
            if (config != null) {
                config.setActive(true);
            }
            return config;
        }
        return null;
    }

    /**
     * 设置激活的配置
     */
    public boolean setActiveConfig(String id) {
        NodeConfig config = getConfig(id);
        if (config == null) {
            Log.e(TAG, "Config not found: " + id);
            return false;
        }

        storageManager.setActiveConfigId(id);

        // 更新配置列表中的激活状态
        List<NodeConfig> configs = storageManager.loadConfigList();
        for (NodeConfig c : configs) {
            c.setActive(c.getId().equals(id));
        }
        storageManager.saveConfigList(configs);

        Log.d(TAG, "Active config set to: " + id);
        return true;
    }

    /**
     * 创建新配置（简版 - 仅名称和 JSON）
     */
    public NodeConfig createConfig(String name, String jsonContent) {
        return createConfig(name, "", jsonContent, NodeConfig.ConfigType.USER_CREATED);
    }
    
    /**
     * 创建新配置（带描述）
     */
    public NodeConfig createConfig(String name, String description, String jsonContent) {
        return createConfig(name, description, jsonContent, NodeConfig.ConfigType.USER_CREATED);
    }
    
    /**
     * 创建新配置（完整版本）
     */

    /**
     * 创建新配置（完整版本）
     */
    public NodeConfig createConfig(String name, String description, String jsonContent, NodeConfig.ConfigType type) {
        // 验证 JSON 内容
        ValidationResult validation = parser.validateWorkflow(jsonContent);
        if (!validation.isValid()) {
            Log.e(TAG, "Invalid workflow JSON: " + validation.getAllIssuesMessage());
            return null;
        }

        // 解析工作流获取参数
        ParsedWorkflow parsed = parser.parseWorkflow(jsonContent, name);
        if (!parsed.isValid()) {
            Log.e(TAG, "Failed to parse workflow");
            return null;
        }

        // 生成唯一 ID 和文件名
        String id = UUID.randomUUID().toString();
        String fileName = storageManager.generateFileName();

        // 创建配置对象
        NodeConfig config = new NodeConfig(id, name, description, type);
        config.setFileName(fileName);

        // 保存配置文件
        String fullJson = buildFullConfigJson(config, jsonContent, parsed);
        boolean saved = storageManager.saveConfigFile(fileName, fullJson, type == NodeConfig.ConfigType.BUILTIN);
        if (!saved) {
            Log.e(TAG, "Failed to save config file");
            return null;
        }

        // 保存到配置列表
        List<NodeConfig> configs = storageManager.loadConfigList();
        configs.add(config);
        storageManager.saveConfigList(configs);

        Log.d(TAG, "Config created: " + id);
        return config;
    }

    /**
     * 更新配置（仅 JSON 内容）
     */
    public boolean updateConfig(String id, String jsonContent) {
        return updateConfig(id, null, null, jsonContent);
    }
    
    /**
     * 更新配置（完整版本）
     */
    public boolean updateConfig(String id, String name, String description, String jsonContent) {
        NodeConfig config = getConfig(id);
        if (config == null || config.getType() == NodeConfig.ConfigType.BUILTIN) {
            Log.e(TAG, "Cannot update builtin or non-existent config");
            return false;
        }
    
        // 验证 JSON
        ValidationResult validation = parser.validateWorkflow(jsonContent);
        if (!validation.isValid()) {
            Log.e(TAG, "Invalid workflow JSON: " + validation.getAllIssuesMessage());
            return false;
        }
    
        // 更新配置元数据
        if (name != null && !name.isEmpty()) {
            config.setName(name);
        }
        if (description != null) {
            config.setDescription(description);
        }
        config.setModifiedAt(System.currentTimeMillis());
    
        // 更新配置文件
        boolean saved = storageManager.saveConfigFile(config.getFileName(), jsonContent, false);
        if (saved) {
            List<NodeConfig> configs = storageManager.loadConfigList();
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).getId().equals(id)) {
                    configs.set(i, config);
                    break;
                }
            }
            storageManager.saveConfigList(configs);
            Log.d(TAG, "Config updated: " + id);
            return true;
        }
    
        return false;
    }

    /**
     * 删除配置
     */
    public boolean deleteConfig(String id) {
        NodeConfig config = getConfig(id);
        if (config == null || config.getType() == NodeConfig.ConfigType.BUILTIN) {
            Log.e(TAG, "Cannot delete builtin or non-existent config");
            return false;
        }

        // 删除配置文件
        storageManager.deleteConfigFile(config.getFileName());

        // 从配置列表中移除
        List<NodeConfig> configs = storageManager.loadConfigList();
        configs.removeIf(c -> c.getId().equals(id));
        storageManager.saveConfigList(configs);

        // 如果删除的是激活配置，重置激活状态
        String activeId = storageManager.getActiveConfigId();
        if (id.equals(activeId) && !configs.isEmpty()) {
            setActiveConfig(configs.get(0).getId());
        }

        Log.d(TAG, "Config deleted: " + id);
        return true;
    }

    /**
     * 从文件导入配置
     */
    public NodeConfig importConfig(String sourcePath) {
        try {
            java.io.InputStream is = new java.io.FileInputStream(sourcePath);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String content = new String(buffer, StandardCharsets.UTF_8);

            String fileName = sourcePath.substring(sourcePath.lastIndexOf('/') + 1);
            if (!fileName.endsWith(".json")) {
                fileName = fileName + ".json";
            }

            return createConfig("导入的工作流 - " + fileName, "从文件导入", content, NodeConfig.ConfigType.IMPORTED);
        } catch (Exception e) {
            Log.e(TAG, "Failed to import config from: " + sourcePath, e);
            return null;
        }
    }

    /**
     * 导出配置到文件
     */
    public boolean exportConfig(String id, String destPath) {
        NodeConfig config = getConfig(id);
        if (config == null) {
            return false;
        }

        String content = storageManager.readConfigFile(config.getFileName());
        if (content == null) {
            return false;
        }

        try {
            java.io.FileWriter writer = new java.io.FileWriter(destPath);
            writer.write(content);
            writer.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to export config to: " + destPath, e);
            return false;
        }
    }

    /**
     * 获取配置的原始 JSON 内容
     */
    public String getConfigJsonContent(String id) {
        NodeConfig config = getConfig(id);
        if (config == null) {
            return null;
        }
        return storageManager.readConfigFile(config.getFileName());
    }

    /**
     * 解析配置的工作流
     */
    public ParsedWorkflow parseConfigWorkflow(String id) {
        String content = getConfigJsonContent(id);
        if (content == null) {
            return null;
        }
        NodeConfig config = getConfig(id);
        return parser.parseWorkflow(content, config != null ? config.getName() : "Unknown");
    }

    /**
     * 构建完整的配置 JSON
     */
    private String buildFullConfigJson(NodeConfig config, String workflowJson, ParsedWorkflow parsed) {
        // 这里简化处理，只保存 workflow 部分
        // 完整实现应该包含 meta 和 ui_mapping 信息
        return workflowJson;
    }
}
