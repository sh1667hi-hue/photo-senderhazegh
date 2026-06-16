package com.example.photosender;

import android.content.ContentUris;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MainActivity extends AppCompatActivity {

    private static final String BOT_TOKEN = "YOUR_BOT_TOKEN";
    private static final String CHAT_ID = "YOUR_CHAT_ID";

    private static final String PREFS = "photo_prefs";
    private static final String KEY_OFFSET = "offset";

    Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sendButton = new Button(this);
        sendButton.setText("ارسال ۲۰ عکس آخر 📸");
        setContentView(sendButton);

        sendButton.setOnClickListener(v -> sendPhotos());
    }

    // 📌 گرفتن offset ذخیره‌شده
    private int getOffset() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        return sp.getInt(KEY_OFFSET, 0);
    }

    // 📌 ذخیره offset
    private void saveOffset(int offset) {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.edit().putInt(KEY_OFFSET, offset).apply();
    }

    // 📌 گرفتن عکس‌ها با pagination واقعی
    private ArrayList<Uri> getImages(int limit, int offset) {

        ArrayList<Uri> list = new ArrayList<>();

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Images.Media._ID
        };

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

                int index = 0;

                while (cursor.moveToNext()) {

                    if (index < offset) {
                        index++;
                        continue;
                    }

                    if (list.size() >= limit) break;

                    long id = cursor.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);

                    list.add(uri);
                    index++;
                }
            }
        }

        return list;
    }

    // 📌 دکمه اصلی
    private void sendPhotos() {

        int offset = getOffset();

        ArrayList<Uri> images = getImages(20, offset);

        if (images.isEmpty()) {
            Toast.makeText(this, "عکسی برای ارسال باقی نمانده", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this,
                "در حال ارسال ۲۰ عکس از شماره " + offset,
                Toast.LENGTH_SHORT).show();

        sendToTelegram(images);

        // 🔥 رفتن به ۲۰ عکس بعدی
        saveOffset(offset + 20);
    }

    // 📌 ارسال به تلگرام
    private void sendToTelegram(ArrayList<Uri> images) {

        new Thread(() -> {

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

                    Thread.sleep(250);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            int finalSuccess = success;

            runOnUiThread(() ->
                    Toast.makeText(this,
                            "ارسال شد: " + finalSuccess + "/20",
                            Toast.LENGTH_LONG).show()
            );

        }).start();
    }

    // 📌 تبدیل InputStream
