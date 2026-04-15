package com.example.demo;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.demo.view.AppHeader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

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
    
    // GitHub 版本检查 URL
    private static final String UPDATE_CHECK_URL = "https://api.github.com/repos/Chen-hash30/ComfyUI-Mobile/releases/latest";
    private static final String ACCEPT_HEADER = "application/vnd.github.v3+json";
    private static final String DOWNLOAD_DIR_NAME = "app-release.apk";

    // 更新相关变量
    private long downloadId = -1;
    private Timer progressTimer;
    private AlertDialog progressDialog;
    private ProgressBar pbDownload;
    private TextView tvProgressPercent;
    private TextView tvDownloadSize;
    private TextView tvDownloadSource;

    private String originalUrl;
    private String versionForUrl; 
    private boolean isUsingProxy = false;
    private long downloadStartTime;
    private final long PROXY_TIMEOUT = 10000; // 10秒超时
    private boolean hasSwitchedToOriginal = false;

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                stopProgressTimer();
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                installApk();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置状态栏颜色和图标反色
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(getColor(R.color.comfy_bg_dark));
            // 如果背景是深色，图标应该是浅色。ComfyUI 是深色背景，所以不设置 SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        setContentView(R.layout.activity_settings);

        // 注册下载广播 (增加导出标志以兼容 Android 14+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

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
        if (UPDATE_CHECK_URL == null || UPDATE_CHECK_URL.isEmpty()) {
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
                        String remoteVersion = null;
                        String downloadUrl = null;
                        if (json.contains("{") && json.contains("}")) {
                            try {
                                JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                                if (jsonObject.has("tag_name")) {
                                    remoteVersion = jsonObject.get("tag_name").getAsString();
                                }
                                if (jsonObject.has("assets") && jsonObject.getAsJsonArray("assets").size() > 0) {
                                    JsonObject firstAsset = jsonObject.getAsJsonArray("assets").get(0).getAsJsonObject();
                                    if (firstAsset.has("browser_download_url")) {
                                        downloadUrl = firstAsset.get("browser_download_url").getAsString();
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            remoteVersion = json.trim();
                        }
                        
                        final String finalRemoteVersion = remoteVersion;
                        final String finalDownloadUrl = downloadUrl;
                        if (finalRemoteVersion != null && finalDownloadUrl != null) {
                            mainHandler.post(() -> {
                                compareAndNotify(finalRemoteVersion, finalDownloadUrl);
                            });
                        } else {
                            mainHandler.post(() -> {
                                tvUpdateStatus.setText("无法获取有效版本信息");
                            });
                        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(downloadReceiver);
        stopProgressTimer();
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            progressTimer.cancel();
            progressTimer = null;
        }
    }

    private void installApk() {
        File apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR_NAME);
        if (apkFile.exists()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri apkUri = FileProvider.getUriForFile(
                    SettingsActivity.this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    apkFile
                );
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "安装失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, "文件未找到，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    private void startProgressTimer() {
        stopProgressTimer();
        progressTimer = new Timer();
        progressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateDownloadProgress();
            }
        }, 0, 500); // 每500毫秒更新一次
    }

    private void updateDownloadProgress() {
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                int bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));

                mainHandler.post(() -> {
                    if (status == DownloadManager.STATUS_FAILED) {
                        stopProgressTimer();
                        if (progressDialog != null) progressDialog.dismiss();
                        Toast.makeText(SettingsActivity.this, "下载失败，请检查网络", Toast.LENGTH_SHORT).show();
                    } else if (bytesTotal > 0) {
                        if (isUsingProxy && !hasSwitchedToOriginal) {
                            if (bytesDownloaded > 0) {
                                if (tvDownloadSource != null) {
                                    tvDownloadSource.setText("加速地址有效，正在加速下载...");
                                    tvDownloadSource.setTextColor(0xFFACE12E); 
                                }
                            } else if (System.currentTimeMillis() - downloadStartTime > PROXY_TIMEOUT) {
                                switchToOriginal();
                                return;
                            }
                        }

                        int progress = (int) (bytesDownloaded * 100L / bytesTotal);
                        if (pbDownload != null) pbDownload.setProgress(progress);
                        if (tvProgressPercent != null) tvProgressPercent.setText(progress + "%");
                        if (tvDownloadSize != null) {
                            String currentMb = String.format("%.2f", bytesDownloaded / (1024.0 * 1024.0));
                            String totalMb = String.format("%.2f", bytesTotal / (1024.0 * 1024.0));
                            tvDownloadSize.setText(currentMb + " MB / " + totalMb + " MB");
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void compareAndNotify(String remoteVersion, String downloadUrl) {
        if (remoteVersion == null || remoteVersion.isEmpty()) {
            tvUpdateStatus.setText("无效的版本信息");
            return;
        }
    
        if (remoteVersion.equals(currentVersion)) {
            tvUpdateStatus.setText("当前已是最新版本");
            Toast.makeText(this, "已经是最新版", Toast.LENGTH_SHORT).show();
        } else {
            tvUpdateStatus.setText("发现新版本: " + remoteVersion);
            tvUpdateStatus.setTextColor(0xFFACE12E); // 亮绿色显示有更新
    
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_confirm, null);
            AlertDialog confirmDialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(true)
                    .create();
            
            if (confirmDialog.getWindow() != null) {
                confirmDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }

            TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
            TextView tvMessage = dialogView.findViewById(R.id.tvMessage);
            Button btnCancel = dialogView.findViewById(R.id.btnCancel);
            Button btnConfirm = dialogView.findViewById(R.id.btnConfirm);

            tvTitle.setText("发现新版本 " + remoteVersion);
            tvMessage.setText("检测到新版本，是否前往下载更新？");

            btnCancel.setOnClickListener(v -> confirmDialog.dismiss());
            btnConfirm.setOnClickListener(v -> {
                confirmDialog.dismiss();
                startDownload(remoteVersion, downloadUrl);
            });

            confirmDialog.show();
        }
    }

    private void startDownload(String remoteVersion, String downloadUrl) {
        this.originalUrl = downloadUrl;
        this.versionForUrl = remoteVersion.startsWith("v") ? remoteVersion : "v" + remoteVersion;
        this.isUsingProxy = true;
        this.hasSwitchedToOriginal = false;
        this.downloadStartTime = System.currentTimeMillis();

        String proxyUrl = "https://gh.llkk.cc/" + downloadUrl;
        
        executeDownload(proxyUrl, remoteVersion);

        // 显示下载进度弹窗
        showProgressDialog(remoteVersion);
        startProgressTimer();
    }

    private void executeDownload(String url, String remoteVersion) {
        // 使用外部私有目录，避免权限问题
        File oldApk = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR_NAME);
        if (oldApk.exists()) oldApk.delete();

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("ComfyUI-Mobile 更新");
        request.setDescription("正在下载新版本 " + remoteVersion);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, DOWNLOAD_DIR_NAME);
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        
        downloadId = downloadManager.enqueue(request);
    }

    private void switchToOriginal() {
        if (hasSwitchedToOriginal) return;
        hasSwitchedToOriginal = true;
        isUsingProxy = false;

        if (tvDownloadSource != null) {
            tvDownloadSource.setText("加速地址无效，正在切换为原地址...");
            tvDownloadSource.setTextColor(Color.RED);
        }

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        downloadManager.remove(downloadId); // 停止当前下载
        
        mainHandler.postDelayed(() -> {
            executeDownload(originalUrl, versionForUrl);
            downloadStartTime = System.currentTimeMillis(); 
        }, 1000); // 延迟1秒给切换提示
    }

    private void showProgressDialog(String remoteVersion) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_update_progress, null);
        progressDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvUpdateVersion = dialogView.findViewById(R.id.tvUpdateVersion);
        pbDownload = dialogView.findViewById(R.id.pbDownload);
        tvProgressPercent = dialogView.findViewById(R.id.tvProgressPercent);
        tvDownloadSize = dialogView.findViewById(R.id.tvDownloadSize);
        tvDownloadSource = dialogView.findViewById(R.id.tvDownloadSource);
        Button btnCancelDownload = dialogView.findViewById(R.id.btnCancelDownload);

        tvUpdateVersion.setText("版本: " + remoteVersion);
        if (isUsingProxy) {
            tvDownloadSource.setText("正在尝试加速地址...");
            tvDownloadSource.setTextColor(0xFFACE12E); 
        }

        btnCancelDownload.setOnClickListener(v -> {
            DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            downloadManager.remove(downloadId);
            stopProgressTimer();
            progressDialog.dismiss();
        });

        progressDialog.show();
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
