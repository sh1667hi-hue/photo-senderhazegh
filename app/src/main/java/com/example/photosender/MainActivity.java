package com.example.photosender;

import android.Manifest;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_CODE = 100;

    Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sendButton = new Button(this);
        sendButton.setText("ارسال ۲۰ عکس آخر 📸");
        setContentView(sendButton);

        sendButton.setOnClickListener(v -> checkPermissionAndAsk());
    }

    private void checkPermissionAndAsk() {
        if (hasPermission()) {
            showConfirmDialog();
        } else {
            requestPermission();
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    PERMISSION_CODE);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_CODE);
        }
    }

    private void showConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("ارسال عکس‌ها")
                .setMessage("می‌خوای ۲۰ عکس آخر گالری ارسال بشه؟")
                .setPositiveButton("بله", (dialog, which) -> sendLastPhotos())
                .setNegativeButton("نه", (dialog, which) -> {
                    Toast.makeText(this, "لغو شد ❌", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void sendLastPhotos() {
        ArrayList<Uri> images = getLastImages(20);

        if (images.isEmpty()) {
            Toast.makeText(this, "هیچ عکسی پیدا نشد", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔥 اینجا جای ارسال واقعی است
        Toast.makeText(this,
                "تعداد عکس انتخاب شده: " + images.size(),
                Toast.LENGTH_LONG).show();

        // فعلاً فقط لیست رو نشون میده
        for (Uri uri : images) {
            System.out.println("Image: " + uri.toString());
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showConfirmDialog();
            } else {
                Toast.makeText(this, "دسترسی داده نشد ❌", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
