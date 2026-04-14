package com.example.demo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ComfyForegroundService extends Service {
    private static final String CHANNEL_ID = "ComfyServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = "COMFY MOBILE 已就绪";
        String content = "正在后台守护您的连接...";
        int progress = -1;

        if (intent != null) {
            // 只有当 Intent 明确传入这些 Key 时才覆盖默认值
            if (intent.hasExtra("title")) title = intent.getStringExtra("title");
            if (intent.hasExtra("content")) content = intent.getStringExtra("content");
            progress = intent.getIntExtra("progress", -1);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.drawable.logo)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        
        if (progress >= 0) {
            builder.setProgress(100, progress, false);
            builder.setContentText(content + " (" + progress + "%)");
        }

        Notification notification = builder.build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ComfyUI Background Service",
                    NotificationManager.IMPORTANCE_LOW 
            );
            serviceChannel.setSound(null, null);
            serviceChannel.enableLights(false);
            serviceChannel.enableVibration(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                
                // 为完成通知创建一个高优先级频道以显示横幅
                NotificationChannel completionChannel = new NotificationChannel(
                    "ComfyCompletionChannel",
                    "ComfyUI Task Completion",
                    NotificationManager.IMPORTANCE_HIGH
                );
                completionChannel.enableLights(true);
                completionChannel.enableVibration(true);
                manager.createNotificationChannel(completionChannel);
            }
        }
    }
}