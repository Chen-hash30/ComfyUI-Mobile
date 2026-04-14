package com.example.demo.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.demo.model.NodeConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 存储管理器 - 负责节点配置文件的读写
 */
public class StorageManager {
    private static final String TAG = "StorageManager";
    private static final String PREFS_NAME = "comfy_node_prefs";
    private static final String ACTIVE_CONFIG_ID_KEY = "active_config_id";
    private static final String CONFIG_LIST_KEY = "config_list";

    private static final String CONFIGS_DIR = "node_configs";
    private static final String BUILTIN_DIR = "builtin";
    private static final String USER_DIR = "user";

    private final Context context;
    private final File configsDir;
    private final File builtinDir;
    private final File userDir;
    private final Gson gson;

    private static StorageManager instance;

    private StorageManager(Context context) {
        this.context = context.getApplicationContext();
        this.configsDir = new File(context.getFilesDir(), CONFIGS_DIR);
        this.builtinDir = new File(configsDir, BUILTIN_DIR);
        this.userDir = new File(configsDir, USER_DIR);
        this.gson = new Gson();
        ensureDirectoriesExist();
    }

    public static synchronized StorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new StorageManager(context);
        }
        return instance;
    }

    private void ensureDirectoriesExist() {
        if (!configsDir.exists()) {
            configsDir.mkdirs();
        }
        if (!builtinDir.exists()) {
            builtinDir.mkdirs();
        }
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
    }

    /**
     * 获取 SharedPreferences
     */
    private SharedPreferences getPrefs() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取当前激活的配置 ID
     */
    public String getActiveConfigId() {
        return getPrefs().getString(ACTIVE_CONFIG_ID_KEY, null);
    }

    /**
     * 设置当前激活的配置 ID
     */
    public void setActiveConfigId(String configId) {
        getPrefs().edit().putString(ACTIVE_CONFIG_ID_KEY, configId).apply();
    }

    /**
     * 保存配置列表
     */
    public void saveConfigList(List<NodeConfig> configs) {
        String json = gson.toJson(configs);
        getPrefs().edit().putString(CONFIG_LIST_KEY, json).apply();
    }

    /**
     * 获取保存的配置列表
     */
    public List<NodeConfig> loadConfigList() {
        String json = getPrefs().getString(CONFIG_LIST_KEY, "[]");
        try {
            return gson.fromJson(json, new com.google.gson.reflect.TypeToken<List<NodeConfig>>(){}.getType());
        } catch (Exception e) {
            Log.e(TAG, "Failed to load config list", e);
            return new ArrayList<>();
        }
    }

    /**
     * 读取配置文件内容
     */
    public String readConfigFile(String fileName) {
        File file = findConfigFile(fileName);
        if (file == null || !file.exists()) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read config file: " + fileName, e);
            return null;
        }
    }

    /**
     * 保存配置文件内容
     */
    public boolean saveConfigFile(String fileName, String content, boolean isBuiltin) {
        File dir = isBuiltin ? builtinDir : userDir;
        File file = new File(dir, fileName);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to save config file: " + fileName, e);
            return false;
        }
    }

    /**
     * 删除配置文件
     */
    public boolean deleteConfigFile(String fileName) {
        File file = findConfigFile(fileName);
        if (file != null && file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * 查找配置文件（在 builtin 和 user 目录中查找）
     */
    private File findConfigFile(String fileName) {
        File builtinFile = new File(builtinDir, fileName);
        if (builtinFile.exists()) {
            return builtinFile;
        }
        File userFile = new File(userDir, fileName);
        if (userFile.exists()) {
            return userFile;
        }
        return null;
    }

    /**
     * 从 assets 加载内置工作流
     */
    public String loadBuiltinWorkflow(String assetName) {
        try {
            InputStream is = context.getAssets().open(assetName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load builtin workflow: " + assetName, e);
            return null;
        }
    }

    /**
     * 获取工作流 JSON 对象
     */
    public JsonObject parseWorkflowJson(String jsonContent) {
        try {
            return JsonParser.parseString(jsonContent).getAsJsonObject();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse workflow JSON", e);
            return null;
        }
    }

    /**
     * 生成唯一文件名
     */
    public String generateFileName() {
        return UUID.randomUUID().toString() + "_workflow.json";
    }

    /**
     * 获取用户配置目录路径
     */
    public String getUserConfigDirPath() {
        return userDir.getAbsolutePath();
    }
}
