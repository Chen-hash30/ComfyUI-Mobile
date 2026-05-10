package com.example.demo.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ImageLocalStore {
    private static final String TAG = "ImageLocalStore";
    private static final String IMAGE_DIR = "generated_images";
    
    private static volatile ImageLocalStore instance;
    private final Context context;
    private final File imageDir;
    private final OkHttpClient httpClient;
    private final ExecutorService executorService;
    
    private ImageLocalStore(Context context) {
        this.context = context.getApplicationContext();
        this.imageDir = new File(context.getFilesDir(), IMAGE_DIR);
        this.httpClient = new OkHttpClient.Builder().build();
        this.executorService = Executors.newSingleThreadExecutor();
        
        if (!imageDir.exists()) {
            imageDir.mkdirs();
        }
    }
    
    public static ImageLocalStore getInstance(Context context) {
        if (instance == null) {
            synchronized (ImageLocalStore.class) {
                if (instance == null) {
                    instance = new ImageLocalStore(context);
                }
            }
        }
        return instance;
    }
    
    public interface OnImageSavedListener {
        void onSuccess(String localPath);
        void onError(String error);
    }
    
    public void saveImageFromUrlAsync(String imageUrl, String prompt, OnImageSavedListener listener) {
        executorService.execute(() -> {
            try {
                String localPath = saveImageFromUrl(imageUrl, prompt);
                if (localPath != null) {
                    if (listener != null) {
                        listener.onSuccess(localPath);
                    }
                } else {
                    if (listener != null) {
                        listener.onError("保存图片失败");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "保存图片异常", e);
                if (listener != null) {
                    listener.onError("保存图片异常: " + e.getMessage());
                }
            }
        });
    }
    
    public String saveImageFromUrl(String imageUrl, String prompt) throws IOException {
        Request request = new Request.Builder()
                .url(imageUrl)
                .get()
                .build();
        
        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("下载失败，状态码: " + response.code());
        }
        
        InputStream inputStream = response.body().byteStream();
        String fileName = generateFileName(prompt);
        File outputFile = new File(imageDir, fileName);
        
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.close();
        inputStream.close();
        
        Log.d(TAG, "图片已保存到: " + outputFile.getAbsolutePath());
        return outputFile.getAbsolutePath();
    }
    
    private String generateFileName(String prompt) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safePrompt = prompt != null ? 
                prompt.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_").substring(0, Math.min(20, prompt.length())) : 
                "image";
        return timestamp + "_" + uuid + "_" + safePrompt + ".png";
    }
    
    public boolean isImageExists(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return false;
        }
        File file = new File(localPath);
        return file.exists() && file.isFile();
    }
    
    public File getImageFile(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return null;
        }
        File file = new File(localPath);
        return file.exists() && file.isFile() ? file : null;
    }
    
    public Bitmap loadImageBitmap(String localPath) {
        File file = getImageFile(localPath);
        if (file == null) {
            return null;
        }
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }
    
    public boolean deleteImage(String localPath) {
        if (localPath == null || localPath.isEmpty()) {
            return false;
        }
        File file = new File(localPath);
        if (file.exists() && file.isFile()) {
            boolean deleted = file.delete();
            if (deleted) {
                Log.d(TAG, "图片已删除: " + localPath);
            }
            return deleted;
        }
        return false;
    }
    
    public long getTotalSizeBytes() {
        long totalSize = 0;
        File[] files = imageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    totalSize += file.length();
                }
            }
        }
        return totalSize;
    }
    
    public int getImageCount() {
        File[] files = imageDir.listFiles();
        return files != null ? files.length : 0;
    }
    
    public void clearAllImages() {
        File[] files = imageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    file.delete();
                }
            }
        }
        Log.d(TAG, "已清空所有本地图片");
    }
}
