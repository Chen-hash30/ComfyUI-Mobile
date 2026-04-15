package com.example.demo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.view.AppHeader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private EditText etServerUrl;
    private Button btnSaveConfig;
    private AppHeader appHeader;
    private LinearLayout btnCheckUpdate;
    private TextView tvVersion;
    private TextView tvUpdateStatus;
    
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String currentVersion = "1.0.0";

    // 预留的更新检查 URL
    private static final String UPDATE_CHECK_URL = "https://comfyuiup.kkcws.my/version/version.json";
    private static final String APK_DOWNLOAD_BASE_URL = "https://comfyuiup.kkcws.my/apk/app-relese-";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 初始化视图
        etServerUrl = findViewById(R.id.etServerUrl);
        btnSaveConfig = findViewById(R.id.btnSaveConfig);
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate);
        tvVersion = findViewById(R.id.tvVersion);
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus);

        // 显示当前版本号
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersion = pInfo.versionName;
            tvVersion.setText("v" + currentVersion);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // 初始化 AppHeader 组件
        appHeader = findViewById(R.id.appHeader);
        
        // 设置标题
        appHeader.getTvHeaderBrand().setText("设置");
        
        // 显示返回箭头（替换 Logo 为向左箭头，同时隐藏右侧返回按钮）
        
        // 设置点击监听器
        appHeader.setOnHeaderClickListener(new AppHeader.OnHeaderClickListener() {
            @Override
            public void onBackClick() {
                finish();
            }

            @Override
            public void onNodeManagerClick() {
            }

            @Override
            public void onSettingsClick() {
            }

            @Override
            public void onHistoryClick() {
            }

            @Override
            public void onChatClick() {
                android.content.Intent intent = new android.content.Intent(SettingsActivity.this, ChatActivity.class);
                startActivity(intent);
            }

            @Override
            public void onReconnectClick() {
            }
        });

        // 为 GitHub 链接添加点击事件（开发者主页）
        TextView tvGitHub = findViewById(R.id.tvGitHub);
        if (tvGitHub != null) {
            tvGitHub.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Chen-hash30"));
                startActivity(intent);
            });
        }

        // 为 ComfyUI 项目地址添加点击事件
        TextView tvComfyUI = findViewById(R.id.tvComfyUI);
        if (tvComfyUI != null) {
            tvComfyUI.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/comfyanonymous/ComfyUI"));
                startActivity(intent);
            });
        }

        // 检查更新点击事件
        if (btnCheckUpdate != null) {
            btnCheckUpdate.setOnClickListener(v -> {
                checkAppUpdate();
            });
        }

        loadSettings();

        btnSaveConfig.setOnClickListener(v -> {
            String url = etServerUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入配置地址", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!url.startsWith("http")) url = "http://" + url;
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

            saveSettings(url);
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
    }

    /**
     * 检查应用更新
     */
    private void checkAppUpdate() {
        if (UPDATE_CHECK_URL.isEmpty()) {
            Toast.makeText(this, "暂未配置更新地址", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvUpdateStatus.setText("正在检查更新...");
        
        Request request = new Request.Builder()
                .url(UPDATE_CHECK_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    tvUpdateStatus.setText("检查失败: " + e.getMessage());
                    Toast.makeText(SettingsActivity.this, "网络连接失败", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        // 根据用户描述内容是 {0.0.2}，这里按简单字符串处理或解析
                        // 如果是 JSON 格式如 {"version":"0.0.2"}，则使用 JsonParser
                        String remoteVersion;
                        if (json.contains("{") && json.contains("}")) {
                            remoteVersion = json.replace("{", "").replace("}", "").trim();
                        } else {
                            remoteVersion = json.trim();
                        }

                        mainHandler.post(() -> {
                            compareAndNotify(remoteVersion);
                        });
                    } catch (Exception e) {
                        mainHandler.post(() -> {
                            tvUpdateStatus.setText("数据解析失败");
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        tvUpdateStatus.setText("服务器错误: " + response.code());
                    });
                }
            }
        });
    }

    private void compareAndNotify(String remoteVersion) {
        if (remoteVersion == null || remoteVersion.isEmpty()) {
            tvUpdateStatus.setText("无效的版本信息");
            return;
        }

        if (remoteVersion.equals(currentVersion)) {
            tvUpdateStatus.setText("当前已是最新版本");
            Toast.makeText(this, "已经是最新版", Toast.LENGTH_SHORT).show();
        } else {
            tvUpdateStatus.setText("发现新版本: v" + remoteVersion);
            tvUpdateStatus.setTextColor(0xFFACE12E); // 亮绿色显示有更新

            String downloadUrl = APK_DOWNLOAD_BASE_URL + remoteVersion + ".apk";
            
            new AlertDialog.Builder(this)
                .setTitle("发现新版本 v" + remoteVersion)
                .setMessage("检测到新版本，是否前往下载更新？")
                .setPositiveButton("立即更新", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    startActivity(intent);
                })
                .setNegativeButton("稍后再说", null)
                .show();
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("comfy_prefs", MODE_PRIVATE);
        String serverUrl = prefs.getString("server_url", "");
        etServerUrl.setText(serverUrl);
    }

    private void saveSettings(String url) {
        SharedPreferences prefs = getSharedPreferences("comfy_prefs", MODE_PRIVATE);
        prefs.edit().putString("server_url", url).apply();
    }
}
