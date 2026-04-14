package com.example.demo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.model.NodeConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 节点配置列表适配器 - 遵循原项目深色主题风格
 * 第一项为"添加工作流"卡片
 */
public class NodeConfigAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ADD_NODE = 0;
    private static final int TYPE_CONFIG = 1;

    private final List<NodeConfig> configList;
    private final OnConfigClickListener listener;

    public interface OnConfigClickListener {
        void onConfigClick(NodeConfig config);
        void onAddNodeClick();
    }

    public NodeConfigAdapter(List<NodeConfig> configList, OnConfigClickListener listener) {
        this.configList = configList;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_ADD_NODE;
        }
        return TYPE_CONFIG;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ADD_NODE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_add_node, parent, false);
            return new AddNodeViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_node_config, parent, false);
            return new ConfigViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddNodeViewHolder) {
            // Add node item doesn't need binding
        } else if (holder instanceof ConfigViewHolder) {
            NodeConfig config = configList.get(position - 1); // -1 because position 0 is add node
            ((ConfigViewHolder) holder).bind(config);
        }
    }

    @Override
    public int getItemCount() {
        return configList.size() + 1; // +1 for add node item
    }

    /**
     * 添加工作流卡片 ViewHolder
     */
    class AddNodeViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout addNodeLayout;

        AddNodeViewHolder(View itemView) {
            super(itemView);
            addNodeLayout = itemView.findViewById(R.id.addNodeLayout);

            addNodeLayout.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddNodeClick();
                }
            });
        }
    }

    /**
     * 配置项 ViewHolder
     */
    class ConfigViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvDescription;
        private final TextView tvType;
        private final TextView tvDate;
        private final TextView tvActiveLabel;

        ConfigViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvType = itemView.findViewById(R.id.tvType);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvActiveLabel = itemView.findViewById(R.id.tvActiveLabel);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position > 0 && position <= configList.size()) {
                    if (listener != null) {
                        listener.onConfigClick(configList.get(position - 1));
                    }
                }
            });
        }

        void bind(NodeConfig config) {
            tvName.setText(config.getName());
            tvDescription.setText(config.getDescription() != null && !config.getDescription().isEmpty() 
                ? config.getDescription() : "无描述");

            // 设置类型
            String typeText;
            switch (config.getType()) {
                case BUILTIN:
                    typeText = "内置";
                    break;
                case USER_CREATED:
                    typeText = "自定义";
                    break;
                case IMPORTED:
                    typeText = "导入";
                    break;
                default:
                    typeText = "未知";
            }
            tvType.setText(typeText);

            // 设置日期
            tvDate.setText(formatDate(config.getCreatedAt()));

            // 设置激活状态
            if (config.isActive()) {
                tvActiveLabel.setVisibility(View.VISIBLE);
                tvActiveLabel.setText("● 激活中");
                tvActiveLabel.setTextColor(0xFFACE12E);
            } else {
                tvActiveLabel.setVisibility(View.GONE);
            }
        }

        private String formatDate(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}
