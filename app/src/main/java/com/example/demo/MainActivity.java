package com.example.demo;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Html;
import android.net.Uri;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Environment;
import android.view.ScaleGestureDetector;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.demo.databinding.ActivityMainBinding;
import com.example.demo.manager.NodeConfigManager;
import com.example.demo.model.NodeConfig;
import com.example.demo.model.ParsedWorkflow;
import com.example.demo.model.ParameterNode;
import com.example.demo.api.ApiRequestBuilder;
import com.example.demo.view.AppHeader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.widget.RadioGroup;
import android.widget.RadioButton;
import android.widget.ImageView;
import android.widget.Button;
import android.app.AlertDialog;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import android.content.ContentValues;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();

    private String serverUrl = "";
    private WebSocket webSocket;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private JsonObject workflowTemplate;
    private String currentPromptId = "";
    private boolean isAppInBackground = false;

    // 节点管理相关
    private NodeConfigManager configManager;
    private NodeConfig activeConfig;
    private ApiRequestBuilder requestBuilder;
    private final Map<String, View> dynamicControls = new HashMap<>();
    // 手势缩放相关
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;

    @Override
    protected void onStart() {
        super.onStart();
        isAppInBackground = false;
        // 如果当前由于切出去导致 WebSocket 本身断开了，回来时才重连
        // 但如果 Service 还在运行（生成中），WebSocket 理论上不应该断
        if (webSocket == null && !serverUrl.isEmpty()) {
            reconnectCount = 0;
            connectWebSocket();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 只有在没有前台服务跑任务时，才真正标记为后台
        // 如果 Service 正在跑，我们希望逻辑继续保持“活跃”状态感
        isAppInBackground = true;
    }

    private void sendCompletionNotification(String title, String message) {
        android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, "ComfyCompletionChannel")
                    .setContentTitle(title)
                    .setContentText(message)
                    .setSmallIcon(R.drawable.logo)
                    .setAutoCancel(true)
                    .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH);

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(pendingIntent);
            manager.notify(2, builder.build());
        }
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> settingsLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                loadSettings();
                updateConnStatus(false);
                connectWebSocket();
            }
        });
    
    private final androidx.activity.result.ActivityResultLauncher<Intent> nodeManagerLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            // 从节点管理返回后，重新加载配置
            loadActiveConfig();
        });

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "需要通知权限以在后台提醒生成进度", Toast.LENGTH_LONG).show();
                }
            });

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    
        // 初始化节点管理器
        configManager = NodeConfigManager.getInstance(this);
        requestBuilder = new ApiRequestBuilder();
    
        checkAndRequestPermissions();
        loadWorkflowTemplate();
        loadSettings();
        loadActiveConfig();  // 加载激活的配置
        refreshHistoryDrawer();
    
        // 启动常驻保活服务
        startService(new Intent(this, ComfyForegroundService.class));

        // 初始化 AppHeader 组件
        AppHeader appHeader = binding.appHeader;
        
        // 设置品牌文字
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appHeader.getTvHeaderBrand().setText(Html.fromHtml("COMFY <font color='#dfff00'>MOBILE</font>", Html.FROM_HTML_MODE_LEGACY));
            // 初始化手势缩放检测器
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scaleFactor *= detector.getScaleFactor();
                    // 限制缩放范围 1.0~3.0
                    scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 3.0f));
                    binding.ivFullscreenPreview.setScaleX(scaleFactor);
                    binding.ivFullscreenPreview.setScaleY(scaleFactor);
                    return true;
                }
            });
            // 将触摸事件分发给手势检测器
            binding.ivFullscreenPreview.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            });
            // 长按保存图片到相册（仅在全屏预览可见时）
            binding.ivFullscreenPreview.setOnLongClickListener(v -> {
                // 确认全屏预览已显示
                if (binding.previewOverlay.getVisibility() != View.VISIBLE) {
                    return true;
                }
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("保存图片")
                    .setMessage("是否将当前图片保存到相册？")
                    .setPositiveButton("保存", (dialog, which) -> {
                        android.graphics.drawable.Drawable drawable = binding.ivFullscreenPreview.getDrawable();
                        if (drawable instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ContentValues values = new ContentValues();
                                String fileName = System.currentTimeMillis() + ".png";
                                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Demo");
                                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
                            } else {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                                    Toast.makeText(this, "需要存储权限，请重新长按保存", Toast.LENGTH_SHORT).show();
                                } else {
                                    java.io.File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                                    java.io.File appDir = new java.io.File(picturesDir, "Demo");
                                    if (!appDir.exists()) appDir.mkdirs();
                                    java.io.File file = new java.io.File(appDir, System.currentTimeMillis() + ".png");
                                    try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    try {
                                        MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), null);
                                        Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
                                    } catch (java.io.FileNotFoundException e) {
                                        e.printStackTrace();
                                        Toast.makeText(this, "保存失败: 文件未找到", Toast.LENGTH_LONG).show();
                                        return;
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(this, "当前未显示图片", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return true;
            });
        } else {
            appHeader.getTvHeaderBrand().setText(Html.fromHtml("COMFY <font color='#dfff00'>MOBILE</font>"));
            // 初始化手势缩放检测器
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scaleFactor *= detector.getScaleFactor();
                    // 限制缩放范围 1.0~3.0
                    scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 3.0f));
                    binding.ivFullscreenPreview.setScaleX(scaleFactor);
                    binding.ivFullscreenPreview.setScaleY(scaleFactor);
                    return true;
                }
            });
            // 将触摸事件分发给手势检测器
            binding.ivFullscreenPreview.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            });
        }

        // 显示需要的按钮
        appHeader.showNodeManagerButton();
        appHeader.showSettingsButton();
        appHeader.showHistoryButton();

        // 设置点击监听器
        appHeader.setOnHeaderClickListener(new AppHeader.OnHeaderClickListener() {
            @Override
            public void onBackClick() {
                // 不需要返回按钮
            }

            @Override
            public void onNodeManagerClick() {
                openNodeManager();
            }

            @Override
            public void onSettingsClick() {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                settingsLauncher.launch(intent);
            }

            @Override
            public void onHistoryClick() {
                binding.drawerLayout.openDrawer(GravityCompat.START);
            }

            @Override
            public void onReconnectClick() {
                handleReconnect();
            }
        });

        // 自适应小白条与系统栏
        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // 1. 历史抽屉内容处理 (防止被状态栏遮挡)
            binding.historyDrawer.setPadding(0, systemBars.top, 0, 0);

            // 2. 底部安全区域
            int bottomMargin = systemBars.bottom > 0 ? systemBars.bottom : 60;
            binding.bottomBar.setPadding(
                binding.bottomBar.getPaddingLeft(),
                binding.bottomBar.getPaddingTop(),
                binding.bottomBar.getPaddingRight(),
                bottomMargin
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // Close preview on back button
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.previewOverlay.getVisibility() == View.VISIBLE) {
                    binding.previewOverlay.setVisibility(View.GONE);
                    // 隐藏提示并重置缩放
                    binding.ivFullscreenPreview.setScaleX(1.0f);
                    binding.ivFullscreenPreview.setScaleY(1.0f);
                    scaleFactor = 1.0f;
                    // 重新打开历史抽屉
                    binding.drawerLayout.openDrawer(GravityCompat.START);
                } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    onBackPressed();
                }
            }
        });

        binding.btnGenerate.setOnClickListener(v -> {
            String positive = binding.etPromptPositive.getText().toString();
            if (positive.isEmpty()) {
                Toast.makeText(this, "请输入正向提示词", Toast.LENGTH_SHORT).show();
                return;
            }
            startGeneration(positive, binding.etPromptNegative.getText().toString());
        });

        binding.btnCancelTask.setOnClickListener(v -> {
            if (serverUrl.isEmpty()) return;
            Request request = new Request.Builder()
                    .url(serverUrl + "/interrupt")
                    .post(RequestBody.create(new byte[0], null))
                    .build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        Toast.makeText(MainActivity.this, "取消失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    mainHandler.post(() -> {
                        binding.statusBanner.setVisibility(View.GONE);
                        binding.btnGenerate.setEnabled(true);
                        binding.btnGenerate.setText("开始生成");
                        resetServiceStatus();
                        Toast.makeText(MainActivity.this, "任务已取消", Toast.LENGTH_SHORT).show();
                    });
                }
            });
            });
    
            binding.btnRandomSeed.setOnClickListener(v -> {
            long randomSeed = (long) (Math.random() * 999999999999999L);
            binding.etSeed.setText(String.valueOf(randomSeed));
        });

        // Preview Overlay Close Listeners
        binding.btnClosePreview.setOnClickListener(v -> {
            binding.previewOverlay.setVisibility(View.GONE);
            // 隐藏提示并重置缩放
            binding.ivFullscreenPreview.setScaleX(1.0f);
            binding.ivFullscreenPreview.setScaleY(1.0f);
            scaleFactor = 1.0f;
            // 隐藏设为壁纸按钮
            binding.btnSetWallpaper.setVisibility(View.GONE);
            // 隐藏保存图片按钮
            binding.btnSaveImage.setVisibility(View.GONE);
            // 手动关闭预览时也重新打开抽屉
            binding.drawerLayout.openDrawer(GravityCompat.START);
        });
        binding.previewOverlay.setOnClickListener(v -> {
            binding.previewOverlay.setVisibility(View.GONE);
            // 隐藏设为壁纸按钮
            binding.btnSetWallpaper.setVisibility(View.GONE);
            // 隐藏保存图片按钮
            binding.btnSaveImage.setVisibility(View.GONE);
            binding.drawerLayout.openDrawer(GravityCompat.START);
            });
            binding.btnSetWallpaper.setOnClickListener(v -> {
                // Get current fullscreen preview drawable as bitmap
                android.graphics.drawable.Drawable drawable = binding.ivFullscreenPreview.getDrawable();
                if (!(drawable instanceof BitmapDrawable)) {
                    Toast.makeText(MainActivity.this, "无法获取图片", Toast.LENGTH_SHORT).show();
                    return;
                }
                final Bitmap originalBitmap = ((BitmapDrawable) drawable).getBitmap();
                // Inflate dialog layout
                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                View dialogView = inflater.inflate(R.layout.dialog_wallpaper_options, null);
                final ImageView ivPreview = dialogView.findViewById(R.id.ivWallpaperPreview);
                RadioGroup rgMode = dialogView.findViewById(R.id.rgWallpaperMode);
                Button btnApply = dialogView.findViewById(R.id.btnApplyWallpaper);
                // Helper to process bitmap based on selected mode
                final Bitmap[] processedBitmap = new Bitmap[1];
                DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
                int screenW = dm.widthPixels;
                int screenH = dm.heightPixels;
                Runnable updatePreview = () -> {
                    int checkedId = rgMode.getCheckedRadioButtonId();
                    if (checkedId == R.id.rbCrop) {
                        // Crop to screen size, centered
                        int x = Math.max(0, (originalBitmap.getWidth() - screenW) / 2);
                        int y = Math.max(0, (originalBitmap.getHeight() - screenH) / 2);
                        processedBitmap[0] = Bitmap.createBitmap(originalBitmap, x, y, Math.min(screenW, originalBitmap.getWidth()), Math.min(screenH, originalBitmap.getHeight()));
                    } else if (checkedId == R.id.rbTile) {
                        Bitmap tiled = Bitmap.createBitmap(screenW, screenH, originalBitmap.getConfig() != null ? originalBitmap.getConfig() : Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(tiled);
                        for (int i = 0; i < screenW; i += originalBitmap.getWidth()) {
                            for (int j = 0; j < screenH; j += originalBitmap.getHeight()) {
                                canvas.drawBitmap(originalBitmap, i, j, null);
                            }
                        }
                        processedBitmap[0] = tiled;
                    } else { // Center
                        processedBitmap[0] = originalBitmap;
                    }
                    ivPreview.setImageBitmap(processedBitmap[0]);
                };
                // Set default selection and preview
                rgMode.check(R.id.rbCrop);
                updatePreview.run();
                // Listen for mode changes
                rgMode.setOnCheckedChangeListener((group, checkedId) -> {
                    // 更新按钮选中状态的 UI 效果
                    for (int i = 0; i < group.getChildCount(); i++) {
                        View child = group.getChildAt(i);
                        if (child instanceof RadioButton) {
                            RadioButton rb = (RadioButton) child;
                            if (rb.getId() == checkedId) {
                                rb.setBackgroundResource(R.drawable.comfy_preset_selected_bg);
                                rb.setTextColor(Color.BLACK);
                            } else {
                                rb.setBackgroundResource(android.R.color.transparent);
                                rb.setTextColor(Color.parseColor("#777777"));
                            }
                        }
                    }
                    updatePreview.run();
                });
                // Build and show dialog
                AlertDialog wallpaperDialog = new AlertDialog.Builder(MainActivity.this)
                        .setView(dialogView)
                        .create();
                
                // 去掉系统默认背景，使用我们布局里的背景
                if (wallpaperDialog.getWindow() != null) {
                    wallpaperDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                }

                // Apply button action
                btnApply.setOnClickListener(v2 -> {
                    if (processedBitmap[0] == null) {
                        Toast.makeText(MainActivity.this, "未处理图片", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        WallpaperManager wm = WallpaperManager.getInstance(MainActivity.this);
                        wm.setBitmap(processedBitmap[0]);
                        Toast.makeText(MainActivity.this, "壁纸已设置", Toast.LENGTH_SHORT).show();
                        wallpaperDialog.dismiss();
                    } catch (IOException e) {
                        Toast.makeText(MainActivity.this, "设置壁纸失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });

                wallpaperDialog.show();
            });

            // 保存图片按钮点击事件
            binding.btnSaveImage.setOnClickListener(v -> {
                android.graphics.drawable.Drawable d = binding.ivFullscreenPreview.getDrawable();
                if (d instanceof BitmapDrawable) {
                    Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                    // 检查权限（Android 10 以下）
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                            // 权限请求后将在 onRequestPermissionsResult 中处理保存
                            return;
                        }
                    }
                    saveImageToGallery(bmp);
                } else {
                    Toast.makeText(MainActivity.this, "无法获取图片", Toast.LENGTH_SHORT).show();
                }
            });
    
            // 初始化分辨率点击监听
        setupResolutionPresets();

        // 分辨率输入框点击自动全选
        View.OnFocusChangeListener selectAllOnFocus = (v, hasFocus) -> {
            if (hasFocus && v instanceof android.widget.EditText) {
                ((android.widget.EditText) v).selectAll();
            }
        };
        binding.etWidth.setOnFocusChangeListener(selectAllOnFocus);
        binding.etHeight.setOnFocusChangeListener(selectAllOnFocus);
        binding.etWidth.setOnClickListener(v -> binding.etWidth.selectAll());
        binding.etHeight.setOnClickListener(v -> binding.etHeight.selectAll());

        // 如果没有设置过 URL，显示设置界面
        if (serverUrl.isEmpty()) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsLauncher.launch(intent);
            updateConnStatus(false);
        } else {
            updateConnStatus(false);
            connectWebSocket();
        }
    }

    private void updateConnStatus(boolean connected) {
        runOnUiThread(() -> {
            AppHeader appHeader = binding.appHeader;
            if (appHeader != null) {
                appHeader.setConnectionStatus(connected ? "已连接" : "未连接", connected);
                appHeader.getBtnReconnect().setVisibility(connected ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void setupResolutionPresets() {
        LinearLayout presetRow = findViewById(R.id.presetRow);
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            final View view = presetRow.getChildAt(i);
            if (view instanceof TextView) {
                view.setOnClickListener(v -> {
                    String label = ((TextView) v).getText().toString();
                    if (label.contains("512px")) {
                        binding.etWidth.setText("512");
                        binding.etHeight.setText("512");
                    } else if (label.contains("1K")) {
                        binding.etWidth.setText("1024");
                        binding.etHeight.setText("1024");
                    } else if (label.contains("720P")) {
                        binding.etWidth.setText("1280");
                        binding.etHeight.setText("720");
                    } else if (label.contains("1080P")) {
                        binding.etWidth.setText("1920");
                        binding.etHeight.setText("1080");
                    } else if (label.contains("4K")) {
                        binding.etWidth.setText("3840");
                        binding.etHeight.setText("2160");
                    } else if (label.contains("8K")) {
                        binding.etWidth.setText("7680");
                        binding.etHeight.setText("4320");
                    }
                    updatePresetUI((TextView) v);
                });
            }
        }
    }

    private void updatePresetUI(TextView selected) {
        LinearLayout presetRow = findViewById(R.id.presetRow);
        for (int i = 0; i < presetRow.getChildCount(); i++) {
            View v = presetRow.getChildAt(i);
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                if (tv == selected) {
                    tv.setBackgroundResource(R.drawable.comfy_preset_selected_bg);
                    tv.setBackgroundTintList(null);
                    tv.setTextColor(0xFF000000);
                    tv.setAlpha(1.0f);
                    tv.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    tv.setBackgroundResource(R.drawable.comfy_action_btn_bg);
                    tv.setBackgroundTintList(null);
                    tv.setTextColor(0xFF777777);
                    tv.setAlpha(1.0f);
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
        }
    }

    private void loadSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("comfy_prefs", MODE_PRIVATE);
        serverUrl = prefs.getString("server_url", "");
    }
    
    private void saveSettings(String url) {
        serverUrl = url;
        getSharedPreferences("comfy_prefs", MODE_PRIVATE).edit().putString("server_url", url).apply();
    }
    
    /**
     * 加载当前激活的节点配置
     */
    private void loadActiveConfig() {
        activeConfig = configManager.getActiveConfig();
        if (activeConfig != null) {
            // 解析配置的工作流
            ParsedWorkflow parsed = configManager.parseConfigWorkflow(activeConfig.getId());
            if (parsed != null && parsed.isValid()) {
                workflowTemplate = parsed.getWorkflow();
                // TODO: 这里可以动态生成 UI，目前保持原有 UI 兼容
                // generateDynamicUi(parsed.getParameters());
            }
        } else {
            // 如果没有激活配置，加载默认内置工作流
            loadWorkflowTemplate();
        }
    }
    
    /**
     * 打开节点管理界面
     */
    private void openNodeManager() {
        Intent intent = new Intent(this, NodeManagerActivity.class);
        nodeManagerLauncher.launch(intent);
    }

    private void loadWorkflowTemplate() {
        try {
            InputStream is = getAssets().open("workflow.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            workflowTemplate = JsonParser.parseString(json).getAsJsonObject();
        } catch (IOException e) {
            e.printStackTrace();
            workflowTemplate = new JsonObject();
        }
    }

    private int reconnectCount = 0;
    private static final int MAX_RECONNECT_COUNT = 3;

    private void handleReconnect() {
        if (reconnectCount < MAX_RECONNECT_COUNT) {
            reconnectCount++;
            mainHandler.postDelayed(() -> {
                if (!serverUrl.isEmpty()) {
                    connectWebSocket();
                }
            }, 2000); // 延迟2秒重连
        }
    }

    private void connectWebSocket() {
        if (serverUrl.isEmpty()) return;
        
        // 只有当没有生成任务在跑时，才显示连接状态。如果只是静默重连，不要干扰 UI
        if (binding.statusBanner.getVisibility() != View.VISIBLE) {
            AppHeader appHeader = binding.appHeader;
            if (appHeader != null) {
                appHeader.setConnectingStatus("正在连接...");
                appHeader.getBtnReconnect().setVisibility(View.GONE);
            }
        }

        String wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://") + "/ws?clientId=android_client";
        
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                reconnectCount = 0;
                mainHandler.post(() -> {
                    updateConnStatus(true);
                });
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
                    String type = msg.get("type").getAsString();
                    mainHandler.post(() -> {
                        if ("status".equals(type)) {
                            int queue = msg.getAsJsonObject("data").getAsJsonObject("status")
                                    .getAsJsonObject("exec_info").get("queue_remaining").getAsInt();
                            if (queue > 0) {
                                binding.statusBanner.setVisibility(View.VISIBLE);
                                binding.statusLabel.setText("排队中: " + queue);
                                binding.btnGenerate.setText("排队中 (" + queue + ")");
                            }
                        } else if ("execution_start".equals(type)) {
                            binding.statusBanner.setVisibility(View.VISIBLE);
                            binding.statusLabel.setText("开始执行...");
                            binding.progressBar.setProgress(0);
                            binding.statusPercentage.setText("0%");
                            binding.btnGenerate.setText("正在执行...");
                            binding.btnGenerate.setEnabled(false);
                        } else if ("executing".equals(type)) {
                            if (msg.getAsJsonObject("data").get("node").isJsonNull()) {
                                // 结束，某些情况下 ComfyUI 结束时不发送 executed 或结果在历史中
                                String msgPromptId = msg.getAsJsonObject("data").has("prompt_id") ? 
                                    msg.getAsJsonObject("data").get("prompt_id").getAsString() : "";
                                if (!currentPromptId.isEmpty() && (msgPromptId.isEmpty() || msgPromptId.equals(currentPromptId))) {
                                    pollStatus(currentPromptId);
                                }
                            } else {
                                String node = msg.getAsJsonObject("data").get("node").getAsString();
                                binding.statusLabel.setText("正在处理节点: " + node);
                            }
                        } else if ("executed".equals(type)) {
                            // 节点执行完成，检查是否有图片输出
                            JsonObject data = msg.getAsJsonObject("data");
                            if (data.has("output") && data.getAsJsonObject("output").has("images")) {
                                JsonArray images = data.getAsJsonObject("output").getAsJsonArray("images");
                                if (images.size() > 0) {
                                    // 虽然有 executed，但为了保险起见，如果 index.vue 倾向于从 history 获取，我们也保持 pollStatus 的逻辑一致性
                                    // 或者这里直接处理当前节点的输出，但 index.vue 在执行结束时会调用 fetchResult(promptId)
                                    if (!currentPromptId.isEmpty()) {
                                        pollStatus(currentPromptId);
                                    }
                                }
                            }
                        } else if ("execution_success".equals(type)) {
                            // 成功，确保获取结果
                            if (!currentPromptId.isEmpty()) {
                                pollStatus(currentPromptId);
                            }
                        } else if ("execution_error".equals(type)) {
                            mainHandler.post(() -> {
                                binding.statusBanner.setVisibility(View.GONE);
                                binding.btnGenerate.setEnabled(true);
                                binding.btnGenerate.setText("开始生成");
                                resetServiceStatus();
                                Toast.makeText(MainActivity.this, "执行出错", Toast.LENGTH_SHORT).show();
                                if (isAppInBackground) {
                                    sendCompletionNotification("生成发生中断", "工作流执行期间发生错误，请重试。");
                                }
                            });
                        } else if ("progress".equals(type)) {
                            binding.statusBanner.setVisibility(View.VISIBLE);
                            int value = msg.getAsJsonObject("data").get("value").getAsInt();
                            int max = msg.getAsJsonObject("data").get("max").getAsInt();
                            final int progress = (int) ((float) value / max * 100);
                            binding.statusPercentage.setText(progress + "%");
                            binding.progressBar.setProgress(progress, true);
                            final String statusText = "正在采样: " + value + "/" + max;
                            binding.statusLabel.setText(statusText);

                            if (isAppInBackground) {
                                Intent progressIntent = new Intent(MainActivity.this, ComfyForegroundService.class);
                                progressIntent.putExtra("content", statusText);
                                progressIntent.putExtra("progress", progress);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    startForegroundService(progressIntent);
                                } else {
                                    startService(progressIntent);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                updateConnStatus(false);
                handleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                mainHandler.post(() -> {
                    updateConnStatus(false);
                    handleReconnect();
                });
            }
        });
    }

    private void resetServiceStatus() {
        Intent serviceIntent = new Intent(this, ComfyForegroundService.class);
        serviceIntent.putExtra("title", "COMFY MOBILE 已就绪");
        serviceIntent.putExtra("content", "正在后台守护您的连接...");
        serviceIntent.putExtra("progress", -1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void startGeneration(String positive, String negative) {
        if (serverUrl.isEmpty()) {
            Intent intent = new Intent(this, SettingsActivity.class);
            settingsLauncher.launch(intent);
            return;
        }
    
        // 启动前台服务保活
        Intent serviceIntent = new Intent(this, ComfyForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    
        binding.btnGenerate.setEnabled(false);
        binding.btnGenerate.setText("正在提交任务...");
    
        // 构建请求体 - 使用动态 API 请求构建器
        JsonObject root = new JsonObject();
        JsonObject promptObj;
    
        if (workflowTemplate != null) {
            // 使用动态构建器
            promptObj = workflowTemplate.deepCopy();
    
            // 收集参数
            Map<String, Object> params = new HashMap<>();
    
            // 添加提示词参数
            params.put("8.text", positive);  // 正向提示词节点
            params.put("3.text", negative);  // 负向提示词节点
    
            // 添加分辨率参数
            try {
                int width = Integer.parseInt(binding.etWidth.getText().toString());
                int height = Integer.parseInt(binding.etHeight.getText().toString());
                params.put("5.width", width);
                params.put("5.height", height);
            } catch (NumberFormatException e) {
                // 使用默认值
            }
    
            // 添加采样参数
            try {
                int steps = Integer.parseInt(binding.etSteps.getText().toString());
                double cfg = Double.parseDouble(binding.etCFG.getText().toString());
                params.put("10.steps", steps);
                params.put("10.cfg", cfg);
            } catch (NumberFormatException e) {
                // 使用默认值
            }
    
            // 添加种子参数
            long seed;
            if (binding.etSeed.getText().toString().isEmpty()) {
                seed = (long) (Math.random() * 999999999999999L);
            } else {
                try {
                    seed = Long.parseLong(binding.etSeed.getText().toString());
                } catch (NumberFormatException e) {
                    seed = (long) (Math.random() * 999999999999999L);
                }
            }
            // 尝试更新所有可能的种子节点
            params.put("9.seed", seed);
            params.put("10.seed", seed);
            params.put("13.seed", seed);
    
            // 使用 ApiRequestBuilder 更新参数
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String[] parts = entry.getKey().split("\\.");
                if (parts.length == 2) {
                    String nodeId = parts[0];
                    String paramName = parts[1];
                    requestBuilder.updateNodeParameter(promptObj, nodeId, paramName, entry.getValue());
                }
            }
        } else {
            // 回退到手动构建 (以防万一)
            promptObj = new JsonObject();
            JsonObject node8 = new JsonObject();
            node8.addProperty("class_type", "CLIPTextEncode");
            JsonObject node8Inputs = new JsonObject();
            node8Inputs.addProperty("text", positive);
            node8.add("inputs", node8Inputs);
            promptObj.add("8", node8);
        }
    
        root.add("prompt", promptObj);
        root.addProperty("client_id", "android_client");

        RequestBody body = RequestBody.create(
                root.toString(),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(serverUrl + "/prompt")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showError("连接失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body() != null ? response.body().string() : "{}";
                    JsonObject jsonResponse = JsonParser.parseString(responseData).getAsJsonObject();
                    if (jsonResponse.has("prompt_id")) {
                        currentPromptId = jsonResponse.get("prompt_id").getAsString();
                    }
                    mainHandler.post(() -> {
                        binding.btnGenerate.setEnabled(true);
                        binding.btnGenerate.setText("正在排队中...");
                        Toast.makeText(MainActivity.this, "提交成功", Toast.LENGTH_SHORT).show();
                    });
                    // 后续通过 WebSocket 监听执行状态，或轮询结果
                } else {
                    final String errorBody = response.body() != null ? response.body().string() : "";
                    showError("服务器错误: " + response.code() + " " + errorBody);
                }
            }
        });
    }

    private void showError(String msg) {
        mainHandler.post(() -> {
            binding.btnGenerate.setEnabled(true);
            binding.btnGenerate.setText("重新生成");
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            resetServiceStatus();
            if (isAppInBackground) {
                sendCompletionNotification("生成发生中断", "执行发生错误: " + msg);
            }
        });
    }

    private void pollStatus(String promptId) {
        if (serverUrl.isEmpty() || promptId.isEmpty() || !promptId.equals(currentPromptId)) return;
        
        mainHandler.post(() -> {
            binding.statusLabel.setText("正在获取结果...");
        });

        Request request = new Request.Builder()
                .url(serverUrl + "/history/" + promptId)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 静默重试
                mainHandler.postDelayed(() -> pollStatus(promptId), 2000);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    JsonObject json = JsonParser.parseString(responseData).getAsJsonObject();
                    if (json.has(promptId)) {
                        JsonObject historyData = json.getAsJsonObject(promptId);
                        
                        // 寻找输出图片：参考 index.vue 的嵌套解析逻辑
                        // data.outputs?.images?.[0] || data.outputs?.[0] || (data.outputs && Object.values(data.outputs)[0]?.images?.[0])
                        JsonObject image = null;
                        if (historyData.has("outputs")) {
                            JsonObject outputs = historyData.getAsJsonObject("outputs");
                            // 尝试直接查找 images 数组（某些简单节点结构）
                            if (outputs.has("images")) {
                                JsonArray images = outputs.getAsJsonArray("images");
                                if (images.size() > 0) image = images.get(0).getAsJsonObject();
                            }
                            
                            // 如果没找到，遍历所有节点 ID
                            if (image == null) {
                                for (String key : outputs.keySet()) {
                                    JsonObject nodeOutput = outputs.getAsJsonObject(key);
                                    if (nodeOutput.has("images")) {
                                        JsonArray images = nodeOutput.getAsJsonArray("images");
                                        if (images.size() > 0) {
                                            image = images.get(0).getAsJsonObject();
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        if (image != null) {
                            String filename = image.get("filename").getAsString();
                            String subfolder = image.has("subfolder") ? image.get("subfolder").getAsString() : "";
                            String imageType = image.has("type") ? image.get("type").getAsString() : "output";
                            
                            final String imageUrl = serverUrl + "/view?filename=" + filename + 
                                                   "&subfolder=" + subfolder + "&type=" + imageType;

                            mainHandler.post(() -> {
                                binding.statusLabel.setText("已完成");
                                binding.statusPercentage.setText("100%");
                                binding.progressBar.setProgress(100);
                                
                                binding.resultCard.setVisibility(View.VISIBLE);
                                Glide.with(MainActivity.this)
                                     .load(imageUrl)
                                     .placeholder(new ColorDrawable(Color.parseColor("#1AFFFFFF")))
                                     .into(binding.resultImage);
                                
                                binding.statusBanner.postDelayed(() -> {
                                    binding.statusBanner.setVisibility(View.GONE);
                                    binding.btnGenerate.setEnabled(true);
                                    binding.btnGenerate.setText("开始生成");
                                }, 1000);

                                binding.mainScrollView.postDelayed(() -> 
                                    binding.mainScrollView.smoothScrollTo(0, 0), 300);

                                // 设置按钮点击事件
                                if (binding.btnPreview != null) {
                                    binding.btnPreview.setOnClickListener(v -> {
                                        binding.previewOverlay.setVisibility(View.VISIBLE);
                                        // 显示设为壁纸按钮
                                        binding.btnSetWallpaper.setVisibility(View.VISIBLE);
                                        // 显示保存图片按钮
                                        binding.btnSaveImage.setVisibility(View.VISIBLE);
                                        // 设置设为壁纸点击事件
                                        binding.btnSetWallpaper.setOnClickListener(v2 -> {
                                            android.graphics.drawable.Drawable drawable = binding.ivFullscreenPreview.getDrawable();
                                            if (drawable instanceof BitmapDrawable) {
                                                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                                                try {
                                                    WallpaperManager wm = WallpaperManager.getInstance(MainActivity.this);
                                                    wm.setBitmap(bitmap);
                                                    Toast.makeText(MainActivity.this, "壁纸已设置", Toast.LENGTH_SHORT).show();
                                                } catch (IOException e) {
                                                    Toast.makeText(MainActivity.this, "设置壁纸失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                                }
                                            } else {
                                                Toast.makeText(MainActivity.this, "无法获取图片", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        // 保存图片点击事件
                                        binding.btnSaveImage.setOnClickListener(v2 -> {
                                            new AlertDialog.Builder(MainActivity.this)
                                                .setTitle("保存图片")
                                                .setMessage("是否将当前图片保存到相册？")
                                                .setPositiveButton("确认", (dialog, which) -> {
                                                    android.graphics.drawable.Drawable d = binding.ivFullscreenPreview.getDrawable();
                                                    if (d instanceof BitmapDrawable) {
                                                        Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                                                        // 检查权限（Android 10 以下）
                                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                                            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                                                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
                                                                // 权限请求后将在 onRequestPermissionsResult 中处理保存
                                                                return;
                                                            }
                                                        }
                                                        saveImageToGallery(bmp);
                                                    } else {
                                                        Toast.makeText(MainActivity.this, "无法获取图片", Toast.LENGTH_SHORT).show();
                                                    }
                                                })
                                                .setNegativeButton("取消", null)
                                                .show();
                                        });
                                        Glide.with(MainActivity.this)
                                             .load(imageUrl)
                                             .into(binding.ivFullscreenPreview);
                                    });
                                }
                                if (binding.btnDownload != null) {
                                    binding.btnDownload.setOnClickListener(v -> {
                                        DownloadManager.Request downloadRequest = new DownloadManager.Request(Uri.parse(imageUrl));
                                        downloadRequest.setTitle("ComfyUI " + filename);
                                        downloadRequest.setDescription("Downloading generated image...");
                                        downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                        downloadRequest.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, filename);
                                        
                                        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                                        if (manager != null) {
                                            manager.enqueue(downloadRequest);
                                            Toast.makeText(MainActivity.this, "已保存至相册", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }

                                // 记录到历史中 (Drawer) - 使用 SharedPreferences
                                saveToHistory(imageUrl, binding.etPromptPositive.getText().toString());
                                currentPromptId = ""; // 防止后续重复由于执行不同阶段产生的相同 promptId 的 pollStatus 保存
                                
                                resetServiceStatus();
                                Toast.makeText(MainActivity.this, "生成完成", Toast.LENGTH_SHORT).show();
                                if (isAppInBackground) {
                                    sendCompletionNotification("图像生成完成", "您的图像已在后台生成成功！");
                                }
                            });
                        } else {
                            mainHandler.post(() -> binding.statusLabel.setText("未找到图片"));
                            mainHandler.postDelayed(() -> {
                                binding.statusBanner.setVisibility(View.GONE);
                                binding.btnGenerate.setEnabled(true);
                                binding.btnGenerate.setText("开始生成");
                                resetServiceStatus();
                                if (isAppInBackground) {
                                    sendCompletionNotification("生成失败", "最终结果未找到图片，请重试。");
                                }
                            }, 2000);
                        }
                    } else {
                        // 如果历史还没更新，延迟后再试一次
                        mainHandler.postDelayed(() -> pollStatus(promptId), 1500);
                    }
                } else {
                    mainHandler.postDelayed(() -> pollStatus(promptId), 2000);
                }
            }
        });
    }

    private void saveToHistory(String url, String prompt) {
        android.content.SharedPreferences prefs = getSharedPreferences("comfy_history", MODE_PRIVATE);
        String historyStr = prefs.getString("items", "[]");
        JsonArray historyArray = JsonParser.parseString(historyStr).getAsJsonArray();
        
        // --- 去重逻辑：如果该 URL 已经存在，则将其删掉，之后再把最新的插入到最前面 ---
        JsonArray newArray = new JsonArray();
        JsonObject newItem = new JsonObject();
        newItem.addProperty("url", url);
        newItem.addProperty("prompt", prompt);
        
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault());
        newItem.addProperty("time", sdf.format(new java.util.Date()));
        
        newArray.add(newItem);

        for(int i = 0; i < historyArray.size(); i++) {
            JsonObject item = historyArray.get(i).getAsJsonObject();
            if (!item.get("url").getAsString().equals(url)) {
                if (newArray.size() < 20) {
                    newArray.add(item);
                }
            }
        }

        prefs.edit().putString("items", newArray.toString()).apply();
        refreshHistoryDrawer();
    }

    private void refreshHistoryDrawer() {
        android.content.SharedPreferences prefs = getSharedPreferences("comfy_history", MODE_PRIVATE);
        String historyStr = prefs.getString("items", "[]");
        
        // 读取时进行去重 (兼容旧数据被保存了3次的情况)
        JsonArray rawArray = JsonParser.parseString(historyStr).getAsJsonArray();
        JsonArray historyArray = new JsonArray();
        java.util.HashSet<String> seenUrls = new java.util.HashSet<>();
        for (int i = 0; i < rawArray.size(); i++) {
            JsonObject item = rawArray.get(i).getAsJsonObject();
            String url = item.get("url").getAsString();
            if (!seenUrls.contains(url)) {
                seenUrls.add(url);
                historyArray.add(item);
            }
        }
        // 如果去重了旧数据，则覆写回去保持干净
        if (historyArray.size() < rawArray.size()) {
            prefs.edit().putString("items", historyArray.toString()).apply();
        }

        androidx.recyclerview.widget.RecyclerView rvHistory = findViewById(R.id.rvHistory);
        if (rvHistory != null) {
            rvHistory.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 2));
            rvHistory.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                class HistoryViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    android.widget.ImageView iv;
                    TextView tvPrompt, tvTime;
                    HistoryViewHolder(View layout) {
                        super(layout);
                        androidx.cardview.widget.CardView card = (androidx.cardview.widget.CardView) layout;
                        android.widget.RelativeLayout rl = (android.widget.RelativeLayout) card.getChildAt(0);
                        iv = (android.widget.ImageView) rl.getChildAt(0);
                        
                        LinearLayout textLayout = (LinearLayout) rl.getChildAt(1);
                        tvPrompt = (TextView) textLayout.getChildAt(0);
                        tvTime = (TextView) textLayout.getChildAt(1);
                    }
                }

                @androidx.annotation.NonNull
                @Override
                public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
                    androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(MainActivity.this);
                    androidx.recyclerview.widget.GridLayoutManager.LayoutParams params = new androidx.recyclerview.widget.GridLayoutManager.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    int margin = (int) (4 * getResources().getDisplayMetrics().density);
                    params.setMargins(margin, margin, margin, margin);
                    card.setLayoutParams(params);
                    card.setRadius(12 * getResources().getDisplayMetrics().density);
                    card.setCardBackgroundColor(Color.parseColor("#2A2A2A"));
                    card.setCardElevation(0);

                    android.widget.RelativeLayout rl = new android.widget.RelativeLayout(MainActivity.this);
                    rl.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

                    android.widget.ImageView iv = new android.widget.ImageView(MainActivity.this);
                    iv.setId(View.generateViewId());
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                    int minHeight = (int) (100 * getResources().getDisplayMetrics().density);
                    iv.setMinimumHeight(minHeight);
                    rl.addView(iv, new android.widget.RelativeLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

                    LinearLayout textLayout = new LinearLayout(MainActivity.this);
                    android.widget.RelativeLayout.LayoutParams textParams = new android.widget.RelativeLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    textParams.addRule(android.widget.RelativeLayout.BELOW, iv.getId());
                    textLayout.setLayoutParams(textParams);
                    textLayout.setOrientation(LinearLayout.VERTICAL);
                    int padding = (int) (8 * getResources().getDisplayMetrics().density);
                    textLayout.setPadding(padding, padding, padding, padding);

                    TextView tvPrompt = new TextView(MainActivity.this);
                    tvPrompt.setTextColor(Color.WHITE);
                    tvPrompt.setTextSize(10);
                    tvPrompt.setMaxLines(2);
                    tvPrompt.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    textLayout.addView(tvPrompt);

                    TextView tvTime = new TextView(MainActivity.this);
                    tvTime.setTextColor(Color.parseColor("#999999"));
                    tvTime.setTextSize(8);
                    tvTime.setPadding(0, (int)(4*getResources().getDisplayMetrics().density), 0, 0);
                    textLayout.addView(tvTime);

                    rl.addView(textLayout);

                    card.addView(rl);

                    return new HistoryViewHolder(card);
                }

                @Override
                public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                    HistoryViewHolder h = (HistoryViewHolder) holder;
                    JsonObject item = historyArray.get(position).getAsJsonObject();
                    h.iv.setOnClickListener(v -> {
                        // 关闭抽屉
                        binding.drawerLayout.closeDrawer(GravityCompat.START);
                        
                        binding.previewOverlay.setVisibility(View.VISIBLE);
                        // 显示缩放提示
                        // 显示设为壁纸按钮
                        binding.btnSetWallpaper.setVisibility(View.VISIBLE);
                        // 显示保存图片按钮
                        binding.btnSaveImage.setVisibility(View.VISIBLE);
                        Glide.with(MainActivity.this)
                             .load(item.get("url").getAsString())
                             .into(binding.ivFullscreenPreview);
                    });
                    
                    h.iv.setOnLongClickListener(v -> {
                        new android.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("确认删除")
                            .setMessage("确定要删除这条生成历史吗？")
                            .setPositiveButton("删除", (dialog, which) -> {
                                int adapterPos = holder.getAdapterPosition();
                                if (adapterPos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                                    // 从本地 JSON 列表中删除
                                    historyArray.remove(adapterPos);
                                    prefs.edit().putString("items", historyArray.toString()).apply();
                                    if (rvHistory.getAdapter() != null) {
                                        rvHistory.getAdapter().notifyItemRemoved(adapterPos);
                                        rvHistory.getAdapter().notifyItemRangeChanged(adapterPos, historyArray.size() - adapterPos);
                                    }
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                        return true;
                    });

                    Glide.with(MainActivity.this).load(item.get("url").getAsString()).into(h.iv);
                    h.tvPrompt.setText(item.get("prompt").getAsString());
                    h.tvTime.setText(item.has("time") ? item.get("time").getAsString() : "");
                }

                @Override
                public int getItemCount() {
                    return historyArray.size();
                }
            });
        }
    }

    private void saveImageToGallery(Bitmap bitmap) {
        String filename = "IMG_" + System.currentTimeMillis() + ".png";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ComfyDemo");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "保存失败: Uri 为 null", Toast.LENGTH_LONG).show();
                }
            } else {
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File appDir = new File(picturesDir, "ComfyDemo");
                if (!appDir.exists()) appDir.mkdirs();
                File file = new File(appDir, filename);
                try (OutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                // 通知系统相册更新
                Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scanIntent.setData(Uri.fromFile(file));
                sendBroadcast(scanIntent);
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                android.graphics.drawable.Drawable d = binding.ivFullscreenPreview.getDrawable();
                if (d instanceof BitmapDrawable) {
                    Bitmap bmp = ((BitmapDrawable) d).getBitmap();
                    saveImageToGallery(bmp);
                }
            } else {
                Toast.makeText(this, "未获取存储权限，无法保存图片", Toast.LENGTH_LONG).show();
            }
        }
    }
}