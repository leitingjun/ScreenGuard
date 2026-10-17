package com.screenguard.app.ui;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.screenguard.app.R;
import com.screenguard.app.db.AppDatabase;
import com.screenguard.app.model.AppRule;
import com.screenguard.app.model.UsageRecord;

import java.util.ArrayList;
import java.util.List;

public class AppRuleAdapter extends RecyclerView.Adapter<AppRuleAdapter.ViewHolder> {

    public interface OnRuleActionListener {
        void onEdit(AppRule rule);
        void onDelete(AppRule rule);
        void onToggle(AppRule rule, boolean enabled);
    }

    private List<AppRule> rules = new ArrayList<>();
    private final AppDatabase db;
    private final PackageManager pm;
    private final OnRuleActionListener listener;

    public AppRuleAdapter(AppDatabase db, PackageManager pm, OnRuleActionListener listener) {
        this.db = db;
        this.pm = pm;
        this.listener = listener;
    }

    public void setRules(List<AppRule> rules) {
        this.rules = rules;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_rule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppRule rule = rules.get(position);

        // 应用名和图标
        holder.tvAppName.setText(rule.appName);
        try {
            Drawable icon = pm.getApplicationIcon(rule.packageName);
            holder.ivAppIcon.setImageDrawable(icon);
        } catch (PackageManager.NameNotFoundException e) {
            holder.ivAppIcon.setImageResource(R.drawable.ic_shield);
        }

        // 限制信息
        holder.tvLimitInfo.setText(String.format("限额: %d分钟 · %d次/天 · 冷却%d分钟",
                rule.dailyLimitMinutes, rule.maxOpensPerDay, rule.cooldownMinutes));

        // 今日使用情况
        UsageRecord record = db.getTodayUsage(rule.packageName);
        long usedMinutes = 0;
        int openCount = 0;
        if (record != null) {
            usedMinutes = record.totalSeconds / 60;
            openCount = record.openCount;
        }

        holder.tvUsageInfo.setText(String.format("今日: %d分/%d次",
                usedMinutes, openCount));

        // 进度条
        int progress = rule.dailyLimitMinutes > 0
                ? (int) (usedMinutes * 100 / rule.dailyLimitMinutes)
                : 0;
        holder.progressUsage.setProgress(Math.min(progress, 100));

        // 封锁状态
        if (db.isBlocked(rule.packageName)) {
            long blockedUntil = db.getBlockedUntil(rule.packageName);
            long remainMinutes = (blockedUntil - System.currentTimeMillis()) / 60000;
            holder.tvBlockStatus.setVisibility(View.VISIBLE);
            holder.tvBlockStatus.setText("已锁定，剩余 " + Math.max(1, remainMinutes) + " 分钟");
        } else {
            holder.tvBlockStatus.setVisibility(View.GONE);
        }

        // 开关
        holder.switchEnable.setOnCheckedChangeListener(null);
        holder.switchEnable.setChecked(rule.isEnabled);
        holder.switchEnable.setOnCheckedChangeListener((btn, checked) -> {
            listener.onToggle(rule, checked);
        });

        // 操作按钮
        holder.btnEdit.setOnClickListener(v -> listener.onEdit(rule));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(rule));
    }

    @Override
    public int getItemCount() {
        return rules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAppIcon;
        TextView tvAppName, tvLimitInfo, tvUsageInfo, tvBlockStatus;
        ProgressBar progressUsage;
        Switch switchEnable;
        ImageButton btnEdit, btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            tvAppName = itemView.findViewById(R.id.tvAppName);
            tvLimitInfo = itemView.findViewById(R.id.tvLimitInfo);
            tvUsageInfo = itemView.findViewById(R.id.tvUsageInfo);
            tvBlockStatus = itemView.findViewById(R.id.tvBlockStatus);
            progressUsage = itemView.findViewById(R.id.progressUsage);
            switchEnable = itemView.findViewById(R.id.switchEnable);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
