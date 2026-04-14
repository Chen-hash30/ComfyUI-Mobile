package com.example.demo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.demo.R;

/**
 * 可复用的应用 Header 组件
 * 包含状态栏占位、品牌标识、连接状态和操作按钮
 */
public class AppHeader extends FrameLayout {

    // 内部视图引用
    private View statusBarPlaceholder;
    private View headerBar;
    private LinearLayout headerLeft;
    private LinearLayout headerRight;
    private ImageView ivHeaderLogo;
    private TextView tvHeaderBrand;
    private LinearLayout connStatusContainer;
    private View connDot;
    private View connProgressBar;
    private TextView connStatus;
    private TextView btnReconnect;
    private ImageButton btnNodeManager;
    private ImageButton btnSettings;
    private ImageButton btnHistory;
    private TextView btnBack;

    // 点击事件监听器
    private OnHeaderClickListener onHeaderClickListener;

    public interface OnHeaderClickListener {
        void onBackClick();
        void onNodeManagerClick();
        void onSettingsClick();
        void onHistoryClick();
        void onReconnectClick();
    }

    public AppHeader(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public AppHeader(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public AppHeader(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.component_app_header, this, true);
        findViews();
        setupInsets();
        setupClickListeners();
    }

    private void findViews() {
        statusBarPlaceholder = findViewById(R.id.statusBarPlaceholder);
        headerBar = findViewById(R.id.headerBar);
        headerLeft = findViewById(R.id.headerLeft);
        headerRight = findViewById(R.id.headerRight);
        ivHeaderLogo = findViewById(R.id.ivHeaderLogo);
        tvHeaderBrand = findViewById(R.id.tvHeaderBrand);
        connStatusContainer = findViewById(R.id.connStatusContainer);
        connDot = findViewById(R.id.connDot);
        connProgressBar = findViewById(R.id.connProgressBar);
        connStatus = findViewById(R.id.connStatus);
        btnReconnect = findViewById(R.id.btnReconnect);
        btnNodeManager = findViewById(R.id.btnNodeManager);
        btnSettings = findViewById(R.id.btnSettings);
        btnHistory = findViewById(R.id.btnHistory);
        btnBack = findViewById(R.id.btnBack);
    }

    private void setupInsets() {
        // 延迟获取 insets，确保视图已经附加到窗口
        post(() -> {
            var windowInsets = ViewCompat.getRootWindowInsets(this);
            if (windowInsets != null) {
                var systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
    
                // 设置状态栏占位高度（使用状态栏高度的 55%）
                // 这样 header 位置适中，同时在不同屏幕上保持相对一致的位置
                if (statusBarPlaceholder != null) {
                    ViewGroup.LayoutParams params = statusBarPlaceholder.getLayoutParams();
                    // 最小 20dp，为状态栏高度的 55%
                    int statusBarHeight = systemBars.top;
                    int placeholderHeight = Math.max(20, (int) (statusBarHeight * 0.55));
                    params.height = placeholderHeight;
                    statusBarPlaceholder.setLayoutParams(params);
                }
            }
        });
    
        // 设置监听器以处理动态变化
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            var systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
    
            // 设置状态栏占位高度（使用状态栏高度的 55%）
            if (statusBarPlaceholder != null) {
                ViewGroup.LayoutParams params = statusBarPlaceholder.getLayoutParams();
                // 最小 20dp，为状态栏高度的 55%
                int statusBarHeight = systemBars.top;
                int placeholderHeight = Math.max(20, (int) (statusBarHeight * 0.55));
                params.height = placeholderHeight;
                statusBarPlaceholder.setLayoutParams(params);
            }
    
            // 不消耗 insets，让父布局也能处理
            return insets;
        });
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            if (onHeaderClickListener != null) {
                onHeaderClickListener.onBackClick();
            }
        });
        
        btnNodeManager.setOnClickListener(v -> {
            if (onHeaderClickListener != null) {
                onHeaderClickListener.onNodeManagerClick();
            }
        });
        
        btnSettings.setOnClickListener(v -> {
            if (onHeaderClickListener != null) {
                onHeaderClickListener.onSettingsClick();
            }
        });
        
        btnHistory.setOnClickListener(v -> {
            if (onHeaderClickListener != null) {
                onHeaderClickListener.onHistoryClick();
            }
        });
        
        btnReconnect.setOnClickListener(v -> {
            if (onHeaderClickListener != null) {
                onHeaderClickListener.onReconnectClick();
            }
        });
    }

    /**
     * 设置点击事件监听器
     */
    public void setOnHeaderClickListener(OnHeaderClickListener listener) {
        this.onHeaderClickListener = listener;
    }

    // ==================== 品牌区域设置 ====================

    public void setBrandText(String text) {
        if (tvHeaderBrand != null) {
            tvHeaderBrand.setText(text);
        }
    }

    public void setLogoResource(int resId) {
        if (ivHeaderLogo != null) {
            ivHeaderLogo.setImageResource(resId);
        }
    }

    // ==================== 连接状态设置 ====================

    public void setConnectionStatus(String status, boolean connected) {
        if (connStatusContainer != null) {
            connStatusContainer.setVisibility(View.VISIBLE);
        }
        if (connStatus != null) {
            connStatus.setText(status);
        }
        if (connDot != null) {
            connDot.setVisibility(View.VISIBLE);
            int color = connected ? 0xFFACE12E : 0xFFFF4D4F;
            connDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        }
        if (connProgressBar != null) {
            connProgressBar.setVisibility(View.GONE);
        }
    }

    /**
     * 设置正在连接状态
     */
    public void setConnectingStatus(String status) {
        if (connStatusContainer != null) {
            connStatusContainer.setVisibility(View.VISIBLE);
        }
        if (connStatus != null) {
            connStatus.setText(status);
        }
        if (connDot != null) {
            connDot.setVisibility(View.GONE);
        }
        if (connProgressBar != null) {
            connProgressBar.setVisibility(View.VISIBLE);
        }
    }

    // ==================== 获取内部视图引用 (供外部直接操作) ====================

    /**
     * 获取品牌文字视图 (供外部设置 HTML 内容等)
     */
    public TextView getTvHeaderBrand() {
        return tvHeaderBrand;
    }

    /**
     * 获取连接状态容器
     */
    public LinearLayout getConnStatusContainer() {
        return connStatusContainer;
    }

    /**
     * 获取重连按钮
     */
    public TextView getBtnReconnect() {
        return btnReconnect;
    }

    public void hideConnectionStatus() {
        if (connStatusContainer != null) {
            connStatusContainer.setVisibility(View.GONE);
        }
    }

    public void setReconnectButtonVisible(boolean visible) {
        if (btnReconnect != null) {
            btnReconnect.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    // ==================== 按钮显示/隐藏设置 ====================

    public void showNodeManagerButton() {
        if (btnNodeManager != null) {
            btnNodeManager.setVisibility(View.VISIBLE);
        }
    }

    public void hideNodeManagerButton() {
        if (btnNodeManager != null) {
            btnNodeManager.setVisibility(View.GONE);
        }
    }

    public void showSettingsButton() {
        if (btnSettings != null) {
            btnSettings.setVisibility(View.VISIBLE);
        }
    }

    public void hideSettingsButton() {
        if (btnSettings != null) {
            btnSettings.setVisibility(View.GONE);
        }
    }

    public void showHistoryButton() {
        if (btnHistory != null) {
            btnHistory.setVisibility(View.VISIBLE);
        }
    }

    public void hideHistoryButton() {
        if (btnHistory != null) {
            btnHistory.setVisibility(View.GONE);
        }
    }

    public void showBackButton() {
        if (btnBack != null) {
            btnBack.setVisibility(View.VISIBLE);
        }
    }

    public void hideBackButton() {
        if (btnBack != null) {
            btnBack.setVisibility(View.GONE);
        }
    }
    
    public void setBackButtonText(String text) {
        if (btnBack != null) {
            btnBack.setText(text);
        }
    }
    
    // ==================== Logo 显示控制 ====================
    
    /**
     * 显示 Logo 图片
     */
    public void showLogo() {
        if (ivHeaderLogo != null) {
            ivHeaderLogo.setVisibility(View.VISIBLE);
            // 恢复为原始 logo
            ivHeaderLogo.setImageResource(R.drawable.logo);
        }
    }
    
    /**
     * 隐藏 Logo 图片
     */
    public void hideLogo() {
        if (ivHeaderLogo != null) {
            ivHeaderLogo.setVisibility(View.GONE);
        }
    }
    
    /**
     * 显示返回箭头（替换 Logo 为向左箭头）
     * 同时隐藏右侧的返回按钮
     */
    public void showBackArrow() {
        if (ivHeaderLogo != null) {
            ivHeaderLogo.setVisibility(View.VISIBLE);
            ivHeaderLogo.setImageResource(R.drawable.ic_arrow_back);
            // 设置点击事件为返回
            ivHeaderLogo.setOnClickListener(v -> {
                if (onHeaderClickListener != null) {
                    onHeaderClickListener.onBackClick();
                }
            });
        }
        // 隐藏右侧的返回按钮
        if (btnBack != null) {
            btnBack.setVisibility(View.GONE);
        }
    }
    
    // ==================== 按钮图标自定义 ====================
    
    /**
     * 设置节点管理按钮图标
     */
    public void setNodeManagerButtonIcon(int resId) {
        if (btnNodeManager != null) {
            btnNodeManager.setImageResource(resId);
        }
    }
    
    /**
     * 设置设置按钮图标
     */
    public void setSettingsButtonIcon(int resId) {
        if (btnSettings != null) {
            btnSettings.setImageResource(resId);
        }
    }
    
    /**
     * 设置历史按钮图标
     */
    public void setHistoryButtonIcon(int resId) {
        if (btnHistory != null) {
            btnHistory.setImageResource(resId);
        }
    }
    
    // ==================== 获取内部视图引用 ====================

    public View getHeaderBar() {
        return headerBar;
    }

    public View getStatusBarPlaceholder() {
        return statusBarPlaceholder;
    }
}
