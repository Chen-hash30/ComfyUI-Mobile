package com.example.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.databinding.ActivityChatBinding;
import com.example.demo.view.AppHeader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public class ChatActivity extends AppCompatActivity {

    private ActivityChatBinding binding;
    private OkHttpClient client = new OkHttpClient();
    private Gson gson = new Gson();
    private SharedPreferences prefs;
    private List<JsonObject> messages = new ArrayList<>();
    private StringBuilder currentAiResponse = new StringBuilder();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isWebViewReady = false;
    private Call currentCall = null;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 如果退出时有网络请求，取消掉
        if (currentCall != null) {
            currentCall.cancel();
            currentCall = null;
        }
        // 如果退出时还有没回复完的内容，把它保存下来
        if (currentAiResponse.length() > 0) {
            addMessageToMap("assistant", currentAiResponse.toString());
            saveHistory();
            currentAiResponse.setLength(0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Handle keyboard and system bars insets to push input area up
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets imeInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime() | androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, imeInsets.bottom);
            return insets;
        });

        prefs = getSharedPreferences("ai_chat_prefs", MODE_PRIVATE);
        setupHeader();
        setupWebView();
        setupInput();
        loadHistory();
    }

    private void setupHeader() {
        binding.appHeader.setBrandText("AI CHAT");
        binding.appHeader.hideBackButton();
        binding.appHeader.showSettingsButton();
        binding.appHeader.showHistoryButton();
        binding.appHeader.setBtnChatVisible(false); // Already here
        binding.appHeader.setOnHeaderClickListener(new AppHeader.OnHeaderClickListener() {
            @Override public void onBackClick() { finish(); }
            @Override public void onNodeManagerClick() {}
            @Override public void onSettingsClick() { showConfigDialog(); }
            @Override public void onHistoryClick() { clearHistory(); }
            @Override public void onChatClick() {}
            @Override public void onReconnectClick() {}
        });
    }

    private void setupWebView() {
        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        binding.webView.setBackgroundColor(Color.TRANSPARENT);
        binding.webView.addJavascriptInterface(new WebBridge(), "Android");
        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                isWebViewReady = true;
                displayFullHistory();
            }
        });
        binding.webView.loadUrl("file:///android_asset/chat/index.html");
    }

    private void setupInput() {
        binding.btnSend.setOnClickListener(v -> sendMessage());
        binding.etMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.btnSend.setAlpha(s.length() > 0 ? 1.0f : 0.5f);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void sendMessage() {
        String text = binding.etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        String apiKey = prefs.getString("api_key", "");
        String endpoint = prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions");

        if (TextUtils.isEmpty(apiKey)) {
            Toast.makeText(this, "请在设置中配置 API Key", Toast.LENGTH_SHORT).show();
            showConfigDialog();
            return;
        }

        // Add user message to UI
        addMessageToMap("user", text);
        saveHistory();
        binding.webView.evaluateJavascript("bridge.onReceiveMessage('user', `" + escapeJs(text) + "`)", null);
        binding.etMessage.setText("");

        // Call OpenAI API
        callOpenAi(endpoint, apiKey);
    }

    private void callOpenAi(String endpoint, String apiKey) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", prefs.getString("model", "gpt-3.5-turbo"));
        requestBody.addProperty("stream", true);
        JsonArray msgs = new JsonArray();
        for (JsonObject m : messages) msgs.add(m);
        requestBody.add("messages", msgs);

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(gson.toJson(requestBody), MediaType.get("application/json")))
                .build();

        currentCall = client.newCall(request);
        currentCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) return;
                mainHandler.post(() -> Toast.makeText(ChatActivity.this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "Unknown error";
                    mainHandler.post(() -> Toast.makeText(ChatActivity.this, "API 错误: " + err, Toast.LENGTH_LONG).show());
                    return;
                }

                mainHandler.post(() -> binding.webView.evaluateJavascript("bridge.onStreamStart()", null));
                currentAiResponse.setLength(0);

                try (ResponseBody body = response.body()) {
                    BufferedSource source = body.source();
                    while (!source.exhausted()) {
                        String line = source.readUtf8Line();
                        if (line != null && line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if (data.equals("[DONE]")) break;
                            try {
                                JsonObject json = gson.fromJson(data, JsonObject.class);
                                JsonArray choices = json.getAsJsonArray("choices");
                                if (choices.size() > 0) {
                                    JsonObject delta = choices.get(0).getAsJsonObject().getAsJsonObject("delta");
                                    if (delta.has("content")) {
                                        String content = delta.get("content").getAsString();
                                        currentAiResponse.append(content);
                                        mainHandler.post(() -> {
                                            binding.webView.evaluateJavascript("bridge.onStreamUpdate(`" + escapeJs(currentAiResponse.toString()) + "`)", null);
                                        });
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                mainHandler.post(() -> {
                    if (isDestroyed()) return;
                    binding.webView.evaluateJavascript("bridge.onStreamEnd()", null);
                    addMessageToMap("assistant", currentAiResponse.toString());
                    saveHistory();
                    currentAiResponse.setLength(0);
                });
            }
        });
    }

    private void addMessageToMap(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        messages.add(msg);
    }

    private String escapeJs(String str) {
        return str.replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$");
    }

    private void showConfigDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        final EditText etKey = new EditText(this); etKey.setHint("API Key"); etKey.setText(prefs.getString("api_key", ""));
        final EditText etUrl = new EditText(this); etUrl.setHint("Endpoint URL"); etUrl.setText(prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions"));
        final EditText etModel = new EditText(this); etModel.setHint("Model (e.g. gpt-4)"); etModel.setText(prefs.getString("model", "gpt-3.5-turbo"));

        layout.addView(etKey); layout.addView(etUrl); layout.addView(etModel);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("AI 对话配置")
                .setView(layout)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit()
                        .putString("api_key", etKey.getText().toString())
                        .putString("endpoint", etUrl.getText().toString())
                        .putString("model", etModel.getText().toString())
                        .apply();
                }).show();
    }

    private void loadHistory() {
        String json = prefs.getString("history", "[]");
        JsonArray array = gson.fromJson(json, JsonArray.class);
        for (int i = 0; i < array.size(); i++) {
            messages.add(array.get(i).getAsJsonObject());
        }
    }

    private void displayFullHistory() {
        if (!isWebViewReady) return;
        for (JsonObject m : messages) {
            String role = m.get("role").getAsString();
            String content = m.get("content").getAsString();
            binding.webView.evaluateJavascript("bridge.onReceiveMessage('" + role + "', `" + escapeJs(content) + "`)", null);
        }
    }

    private void saveHistory() {
        prefs.edit().putString("history", gson.toJson(messages)).apply();
    }

    private void clearHistory() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("确定清空对话历史吗？")
            .setPositiveButton("清空", (d, w) -> {
                messages.clear();
                prefs.edit().remove("history").apply();
                binding.webView.reload();
            }).show();
    }

    public class WebBridge {
        @JavascriptInterface
        public void onReady() {
            mainHandler.post(() -> isWebViewReady = true);
        }
    }
}