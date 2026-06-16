package com.example.photosender;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MainActivity extends AppCompatActivity {

    private static final String BOT_TOKEN = "8931772855:AAHZSrBgS4SJkEWYA6_8fTiZ-Kk4frsxtCU";
    private static final String CHAT_ID = "8961077299";

    Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sendButton = new Button(this);
        sendButton.setText("ارسال ۲۰ عکس آخر 📸");
        setContentView(sendButton);

        requestPermissions();

        sendButton.setOnClickListener(v -> sendLastPhotos());
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 1);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    private ArrayList<Uri> getLastImages(int limit) {

        ArrayList<Uri> list = new ArrayList<>();

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = getContentResolver().query(
                collection, projection, null, null, sortOrder)) {

            if (cursor != null) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                int count = 0;
                while (cursor.moveToNext() && count < limit) {
                    long id = cursor.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    list.add(uri);
                    count++;
                }
            }
        }

        return list;
    }

    private void sendLastPhotos() {

        ArrayList<Uri> images = getLastImages(20);

        if (images.isEmpty()) {
            Toast.makeText(this, "هیچ عکسی پیدا نشد", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("ارسال عکس‌ها")
                .setMessage(
                        "این برنامه ۲۰ عکس آخر گالری را ارسال می‌کند.\n\n" +
                                "📸 تعداد: " + images.size() + "\n" +
                                "📍 مقصد: تلگرام\n\n" +
                                "آیا تأیید می‌کنید؟"
                )
                .setPositiveButton("تأیید و ارسال", (d, w) -> {

                    Toast.makeText(this,
                            "در حال ارسال...",
                            Toast.LENGTH_SHORT).show();

                    sendToTelegram(images);
                })
                .setNegativeButton("لغو", null)
                .show();
    }

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
                            "ارسال کامل شد: " + finalSuccess + "/20",
                            Toast.LENGTH_LONG).show()
            );

        }).start();
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
}
