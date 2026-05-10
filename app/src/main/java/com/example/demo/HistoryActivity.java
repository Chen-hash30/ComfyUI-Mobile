package com.example.demo;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.demo.databinding.ActivityHistoryBinding;
import com.example.demo.manager.HistoryStore;
import com.example.demo.manager.ImageLocalStore;
import com.example.demo.view.AppHeader;
import com.google.gson.JsonObject;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private final List<HistoryStore.HistoryGroup> displayGroups = new ArrayList<>();
    private HistoryCardAdapter cardAdapter;
    private HistoryImageAdapter imageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(getColor(R.color.comfy_bg_dark));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        AppHeader appHeader = binding.appHeader;
        appHeader.hideBackButton();
        appHeader.hideNodeManagerButton();
        appHeader.hideSettingsButton();
        appHeader.hideHistoryButton();
        appHeader.setBtnChatVisible(false);
        appHeader.hideConnectionStatus();
        appHeader.setBrandText("生成历史");
        appHeader.setOnHeaderClickListener(new AppHeader.OnHeaderClickListener() {
            @Override
            public void onBackClick() {
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
            }

            @Override
            public void onReconnectClick() {
            }
        });

        cardAdapter = new HistoryCardAdapter();
        binding.rvHistory.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvHistory.setAdapter(cardAdapter);

        imageAdapter = new HistoryImageAdapter();
        binding.rvModalImages.setLayoutManager(new GridLayoutManager(this, 2));
        binding.rvModalImages.setAdapter(imageAdapter);

        binding.modalOverlay.setOnClickListener(v -> closeModal());
        binding.btnCloseModal.setOnClickListener(v -> closeModal());

        ViewCompat.setOnApplyWindowInsetsListener(binding.modalContent, (view, insets) -> {
            var systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(view.getPaddingLeft(), systemBars.top, view.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        refreshHistory();
    }

    private void refreshHistory() {
        displayGroups.clear();
        List<HistoryStore.HistoryGroup> groups = HistoryStore.loadGroups(this);
        int limit = Math.min(20, groups.size());
        displayGroups.addAll(groups.subList(0, limit));
        cardAdapter.notifyDataSetChanged();

        boolean empty = displayGroups.isEmpty();
        binding.tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.tvEmptyState.setText("暂无生成历史，先去生成一些图片吧");
    }

    private void openGroup(HistoryStore.HistoryGroup group) {
        imageAdapter.setItems(group.entries);
        binding.modalTitle.setText(group.prompt == null || group.prompt.isEmpty() ? "图片详情" : group.prompt);
        binding.modalFooter.setText("生成时间：" + (group.time == null ? "" : group.time) + " · 共 " + group.entries.size() + " 张图片");

        binding.rvModalImages.setLayoutManager(new GridLayoutManager(this, group.entries.size() == 1 ? 1 : 2));
        binding.modalOverlay.setVisibility(View.VISIBLE);
        binding.modalOverlay.setAlpha(0f);
        binding.modalOverlay.animate().alpha(1f).setDuration(220).start();

        binding.modalHeader.setTranslationY(-20f);
        binding.modalFooter.setTranslationY(20f);
        binding.modalHeader.setAlpha(0f);
        binding.modalFooter.setAlpha(0f);
        binding.modalHeader.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
        binding.modalFooter.animate().translationY(0f).alpha(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();

        binding.rvModalImages.post(() -> {
            RecyclerView.LayoutManager manager = binding.rvModalImages.getLayoutManager();
            if (manager != null) {
                manager.scrollToPosition(0);
            }
        });
    }

    private void closeModal() {
        if (binding.modalOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        binding.modalOverlay.animate().alpha(0f).setDuration(180).withEndAction(() -> binding.modalOverlay.setVisibility(View.GONE)).start();
    }

    private final class HistoryCardAdapter extends RecyclerView.Adapter<HistoryCardAdapter.CardHolder> {

        @Override
        public CardHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_card, parent, false);
            return new CardHolder(view);
        }

        @Override
        public void onBindViewHolder(CardHolder holder, int position) {
            HistoryStore.HistoryGroup group = displayGroups.get(position);
            List<HistoryStore.HistoryEntry> entries = group.entries;
            int count = entries.size();

            holder.badge.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
            holder.badge.setText(String.valueOf(count));
            holder.title.setText(group.prompt == null || group.prompt.isEmpty() ? "未命名记录" : group.prompt);
            holder.time.setText(group.time == null ? "Just now" : group.time);

            bindLayer(holder.layer1, entries, 1);
            bindLayer(holder.layer2, entries, 2);
            bindLayer(holder.layer3, entries, 3);
            bindLayer(holder.layerMain, entries, 0);

            if (count <= 1) {
                holder.layer1.setVisibility(View.GONE);
                holder.layer2.setVisibility(View.GONE);
                holder.layer3.setVisibility(View.GONE);
                holder.layerMain.setRotation(0f);
                holder.layerMain.setScaleX(1f);
                holder.layerMain.setScaleY(1f);
                holder.layerMain.setTranslationX(0f);
                holder.layerMain.setTranslationY(0f);
                holder.layerMain.setAlpha(1f);
            } else if (count == 2) {
                holder.layer1.setVisibility(View.VISIBLE);
                holder.layer2.setVisibility(View.GONE);
                holder.layer3.setVisibility(View.GONE);
                
                holder.layer1.setTranslationX(18f);
                holder.layer1.setTranslationY(-12f);
                holder.layer1.setRotation(5f);
                holder.layer1.setScaleX(0.96f);
                holder.layer1.setScaleY(0.96f);
                holder.layer1.setAlpha(1f);

                holder.layerMain.setRotation(0f);
                holder.layerMain.setScaleX(1f);
                holder.layerMain.setScaleY(1f);
                holder.layerMain.setTranslationX(0f);
                holder.layerMain.setTranslationY(0f);
                holder.layerMain.setAlpha(1f);
            } else if (count == 3) {
                holder.layer1.setVisibility(View.VISIBLE);
                holder.layer2.setVisibility(View.VISIBLE);
                holder.layer3.setVisibility(View.GONE);

                holder.layer2.setTranslationX(14f);
                holder.layer2.setTranslationY(14f);
                holder.layer2.setRotation(6f);
                holder.layer2.setScaleX(0.96f);
                holder.layer2.setScaleY(0.96f);
                holder.layer2.setAlpha(1f);

                holder.layer1.setTranslationX(-15f);
                holder.layer1.setTranslationY(-10f);
                holder.layer1.setRotation(-4f);
                holder.layer1.setScaleX(0.93f);
                holder.layer1.setScaleY(0.93f);
                holder.layer1.setAlpha(1f);

                holder.layerMain.setRotation(0f);
                holder.layerMain.setScaleX(1f);
                holder.layerMain.setScaleY(1f);
                holder.layerMain.setTranslationX(0f);
                holder.layerMain.setTranslationY(0f);
                holder.layerMain.setAlpha(1f);
            } else {
                holder.layer1.setVisibility(View.VISIBLE);
                holder.layer2.setVisibility(View.VISIBLE);
                holder.layer3.setVisibility(View.VISIBLE);

                holder.layer3.setTranslationX(14f);
                holder.layer3.setTranslationY(14f);
                holder.layer3.setRotation(6f);
                holder.layer3.setScaleX(0.96f);
                holder.layer3.setScaleY(0.96f);
                holder.layer3.setAlpha(1f);

                holder.layer2.setTranslationX(-15f);
                holder.layer2.setTranslationY(-10f);
                holder.layer2.setRotation(-4f);
                holder.layer2.setScaleX(0.93f);
                holder.layer2.setScaleY(0.93f);
                holder.layer2.setAlpha(1f);

                holder.layer1.setTranslationX(18f);
                holder.layer1.setTranslationY(-15f);
                holder.layer1.setRotation(3f);
                holder.layer1.setScaleX(0.90f);
                holder.layer1.setScaleY(0.90f);
                holder.layer1.setAlpha(1f);

                holder.layerMain.setRotation(0f);
                holder.layerMain.setScaleX(1f);
                holder.layerMain.setScaleY(1f);
                holder.layerMain.setTranslationX(0f);
                holder.layerMain.setTranslationY(0f);
                holder.layerMain.setAlpha(1f);
            }

            holder.cardView.setOnClickListener(v -> {
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(120).withEndAction(() -> openGroup(group)).start();
            });

            holder.cardView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("确认删除")
                        .setMessage("确定删除这条历史记录吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            HistoryStore.deleteByPrompt(HistoryActivity.this, group.prompt);
                            Toast.makeText(HistoryActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                            refreshHistory();
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return true;
            });

            holder.retry.setOnClickListener(v -> {
                JsonObject params = group.entries.isEmpty() ? null : group.entries.get(0).params;
                if (params == null) {
                    Toast.makeText(HistoryActivity.this, "没有可回填的参数", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(HistoryActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("retry_history_params", params.toString());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return displayGroups.size();
        }

        private void bindLayer(ImageView imageView, List<HistoryStore.HistoryEntry> entries, int index) {
            if (index >= entries.size()) {
                imageView.setVisibility(View.GONE);
                return;
            }

            imageView.setVisibility(View.VISIBLE);
            HistoryStore.HistoryEntry entry = entries.get(index);
            Glide.with(HistoryActivity.this)
                    .load(entry.url)
                    .transform(new CenterCrop(), new RoundedCorners(28))
                    .dontAnimate()
                    .placeholder(new ColorDrawable(Color.parseColor("#1AFFFFFF")))
                    .into(imageView);
        }

        final class CardHolder extends RecyclerView.ViewHolder {
            final CardView cardView;
            final FrameLayout thumbnail;
            final ImageView layer1;
            final ImageView layer2;
            final ImageView layer3;
            final ImageView layerMain;
            final TextView badge;
            final TextView title;
            final TextView time;
            final ImageView retry;

            CardHolder(View itemView) {
                super(itemView);
                cardView = itemView.findViewById(R.id.cardRoot);
                thumbnail = itemView.findViewById(R.id.thumbnailContainer);
                layer1 = itemView.findViewById(R.id.layer1);
                layer2 = itemView.findViewById(R.id.layer2);
                layer3 = itemView.findViewById(R.id.layer3);
                layerMain = itemView.findViewById(R.id.layerMain);
                badge = itemView.findViewById(R.id.badge);
                title = itemView.findViewById(R.id.cardTitle);
                time = itemView.findViewById(R.id.cardTime);
                retry = itemView.findViewById(R.id.btnRetry);
            }
        }
    }

    private final class HistoryImageAdapter extends RecyclerView.Adapter<HistoryImageAdapter.ImageHolder> {

        private final List<HistoryStore.HistoryEntry> items = new ArrayList<>();

        void setItems(List<HistoryStore.HistoryEntry> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public ImageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history_image, parent, false);
            return new ImageHolder(view);
        }

        @Override
        public void onBindViewHolder(ImageHolder holder, int position) {
            HistoryStore.HistoryEntry entry = items.get(position);
            holder.image.setAlpha(0f);
            holder.image.setScaleX(0.6f);
            holder.image.setScaleY(0.6f);
            holder.image.setRotation(position % 2 == 0 ? 3f : -2f);

            // 优先从本地路径加载图片
            if (entry.localPath != null && !entry.localPath.isEmpty() && 
                ImageLocalStore.getInstance(HistoryActivity.this).isImageExists(entry.localPath)) {
                Glide.with(HistoryActivity.this)
                        .load(new java.io.File(entry.localPath))
                        .transform(new CenterCrop(), new RoundedCorners(30))
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.parseColor("#1AFFFFFF")))
                        .into(holder.image);
            } else if (entry.url != null && !entry.url.isEmpty()) {
                // 本地不存在时，尝试从远程URL加载
                Glide.with(HistoryActivity.this)
                        .load(entry.url)
                        .transform(new CenterCrop(), new RoundedCorners(30))
                        .dontAnimate()
                        .placeholder(new ColorDrawable(Color.parseColor("#1AFFFFFF")))
                        .error(new ColorDrawable(Color.parseColor("#33FF0000")))
                        .into(holder.image);
            } else {
                // 都没有时显示占位符
                holder.image.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
            }

            holder.image.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setStartDelay(position * 90L)
                    .setDuration(420L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class ImageHolder extends RecyclerView.ViewHolder {
            final ImageView image;

            ImageHolder(View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.modalImage);
            }
        }
    }
}