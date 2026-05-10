package com.example.demo.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class PromptOptimizer {
    private static final String TAG = "PromptOptimizer";
    private static final String PREFS_NAME = "ai_chat_prefs";
    
    private static volatile PromptOptimizer instance;
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executorService;
    
    private PromptOptimizer(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder().build();
        this.gson = new Gson();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    public static PromptOptimizer getInstance(Context context) {
        if (instance == null) {
            synchronized (PromptOptimizer.class) {
                if (instance == null) {
                    instance = new PromptOptimizer(context);
                }
            }
        }
        return instance;
    }
    
    public interface OnOptimizeListener {
        void onSuccess(String optimizedPrompt);
        void onError(String error);
        void onNotConfigured();
    }
    
    /**
     * 检查是否已配置 AI API
     */
    public boolean isConfigured() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "");
        return !TextUtils.isEmpty(apiKey);
    }
    
    /**
     * 优化提示词（异步调用）
     * @param originalPrompt 用户输入的原始提示词
     * @param listener 结果回调
     */
    public void optimizeAsync(String originalPrompt, OnOptimizeListener listener) {
        if (TextUtils.isEmpty(originalPrompt)) {
            if (listener != null) listener.onError("提示词不能为空");
            return;
        }
        
        // 检查是否配置了 API Key
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "");
        
        if (TextUtils.isEmpty(apiKey)) {
            if (listener != null) listener.onNotConfigured();
            return;
        }
        
        String endpoint = prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions");
        String model = prefs.getString("model", "gpt-3.5-turbo");
        
        executorService.execute(() -> {
            try {
                String result = callOpenAiApi(endpoint, apiKey, model, originalPrompt);
                if (listener != null) {
                    listener.onSuccess(result);
                }
            } catch (Exception e) {
                Log.e(TAG, "优化失败", e);
                if (listener != null) {
                    listener.onError("优化失败: " + e.getMessage());
                }
            }
        });
    }
    
    private String callOpenAiApi(String endpoint, String apiKey, String model, String prompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("stream", false); // 非流式，一次性返回
        
        JsonArray messages = new JsonArray();
        
        // System prompt：定义优化规则
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", buildSystemPrompt());
        messages.add(systemMsg);
        
        // User message：用户的原始提示词
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", prompt);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();
        
        Call call = httpClient.newCall(request);
        Response response = call.execute();
        
        if (!response.isSuccessful()) {
            String errorBody = "";
            try (ResponseBody body = response.body()) {
                if (body != null) errorBody = body.string();
            }
            throw new IOException("API 请求失败 (" + response.code() + "): " + errorBody);
        }
        
        try (ResponseBody body = response.body()) {
            if (body == null) throw new IOException("响应体为空");
            
            String responseBody = body.string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                throw new IOException("API 返回结果为空");
            }
            
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            
            if (message == null || !message.has("content")) {
                throw new IOException("无法解析 API 响应内容");
            }
            
            return message.get("content").getAsString();
        }
    }
    
    /**
     * 构建 System Prompt（优化规则）
     * 这个 prompt 定义了 AI 如何优化用户输入的提示词
     */
    private String buildSystemPrompt() {
        return "你是一个专业的 Stable Diffusion / ComfyUI 提示词优化专家。你的任务是将用户简单的中文描述转换为高质量的英文提示词。\n\n" +
                "## 优化规则：\n\n" +
                "1. **语言转换**：将中文翻译为准确的英文\n" +
                "2. **质量增强**：添加以下质量词汇（根据场景选择）：\n" +
                "   - 基础质量词：masterpiece, best quality, highly detailed, 8k resolution\n" +
                "   - 光影效果：cinematic lighting, soft lighting, dramatic lighting, natural lighting\n" +
                "   - 细节增强：intricate details, sharp focus, professional photography\n\n" +
                "3. **风格适配**：根据内容自动添加风格词：\n" +
                "   - 人物/动物：fluffy texture, expressive eyes, lifelike\n" +
                "   - 场景/风景：atmospheric depth, vibrant colors, photorealistic\n" +
                "   - 动漫/插画：anime style, vibrant colors, clean lines\n" +
                "   - 抽象艺术：artistic, surreal, dreamlike\n\n" +
                "4. **结构组织**：\n" +
                "   - 主要主体放在最前面\n" +
                "   - 环境和背景其次\n" +
                "   - 光影和氛围接着\n" +
                "   - 质量和风格词最后\n" +
                "   - 使用逗号分隔各个元素\n\n" +
                "5. **避免**：\n" +
                "   - 不要添加 nsfw 内容\n" +
                "   - 不要过度堆砌关键词（控制在 80 词以内）\n" +
                "   - 不要改变用户原本的创作意图\n\n" +
                "## 输出格式要求：\n\n" +
                "- 直接输出优化后的英文提示词，不要任何解释\n" +
                "- 只输出提示词本身，不要包含引号或其他标记\n" +
                "- 保持一行，用逗号分隔\n\n" +
                "现在请优化用户提供的提示词：";
    }
}
