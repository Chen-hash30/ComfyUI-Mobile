package com.example.demo.model;

import java.util.Date;

/**
 * 节点配置元数据
 */
public class NodeConfig {
    private String id;              // 唯一标识符（UUID）
    private String name;            // 配置名称
    private String description;     // 描述
    private String fileName;        // 对应的 JSON 文件名
    private boolean isActive;       // 是否当前激活
    private long createdAt;         // 创建时间（时间戳）
    private long modifiedAt;        // 修改时间（时间戳）
    private String thumbnailPath;   // 缩略图路径（可选）
    private ConfigType type;        // 配置类型

    public enum ConfigType {
        BUILTIN,        // 内置工作流
        USER_CREATED,   // 用户创建
        IMPORTED        // 导入的工作流
    }

    public NodeConfig() {
        this.createdAt = System.currentTimeMillis();
        this.modifiedAt = System.currentTimeMillis();
    }

    public NodeConfig(String id, String name, String description, ConfigType type) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
    }

    // Getter 和 Setter 方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public ConfigType getType() {
        return type;
    }

    public void setType(ConfigType type) {
        this.type = type;
    }

    /**
     * 转换为 JSON 字符串
     */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":\"").append(id).append("\",");
        json.append("\"name\":\"").append(escapeJson(name)).append("\",");
        json.append("\"description\":\"").append(escapeJson(description)).append("\",");
        json.append("\"fileName\":\"").append(fileName).append("\",");
        json.append("\"isActive\":").append(isActive).append(",");
        json.append("\"createdAt\":").append(createdAt).append(",");
        json.append("\"modifiedAt\":").append(modifiedAt).append(",");
        json.append("\"type\":\"").append(type.name()).append("\"");
        json.append("}");
        return json.toString();
    }

    /**
     * 从 JSON 字符串解析
     */
    public static NodeConfig fromJson(String json) {
        NodeConfig config = new NodeConfig();
        // 简单解析，实际项目中建议使用 Gson
        try {
            config.id = extractString(json, "id");
            config.name = extractString(json, "name");
            config.description = extractString(json, "description");
            config.fileName = extractString(json, "fileName");
            config.isActive = extractBoolean(json, "isActive");
            config.createdAt = extractLong(json, "createdAt");
            config.modifiedAt = extractLong(json, "modifiedAt");
            String typeStr = extractString(json, "type");
            config.type = typeStr != null ? ConfigType.valueOf(typeStr) : ConfigType.USER_CREATED;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return config;
    }

    private static String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"([^\"]*)\"";
        int start = json.indexOf("\"" + key + "\":\"");
        if (start == -1) return null;
        start += ("\"" + key + "\":\"").length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }

    private static boolean extractBoolean(String json, String key) {
        String pattern = "\"" + key + "\":true";
        return json.contains("\"" + key + "\":true");
    }

    private static long extractLong(String json, String key) {
        int start = json.indexOf("\"" + key + "\":");
        if (start == -1) return 0;
        start += ("\"" + key + "\":").length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "NodeConfig{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", isActive=" + isActive +
                '}';
    }
}
