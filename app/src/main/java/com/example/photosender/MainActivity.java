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
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQ = 100;
    private List<Uri> images = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Button btn = new Button(this);
        btn.setText("waiy?!");

        setContentView(btn);

        checkPermission();

        btn.setOnClickListener(v -> {
            if (images.isEmpty()) {
                Toast.makeText(this, "تجارتم شکست خورد سید", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("تأیید")
                    .setMessage("are you smart")
                    .setPositiveButton("yes", (d, w) -> send())
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void checkPermission() {
        String permission = Build.VERSION.SDK_INT >= 33 ?
                Manifest.permission.READ_MEDIA_IMAGES :
                Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, REQ);
        } else {
            loadImages();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadImages();
        }
    }

    private void loadImages() {
        images.clear();

        Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID},
                null,
                null,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
        );

        if (cursor != null) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int count = 0;

            while (cursor.moveToNext() && count < 20) {
                long id = cursor.getLong(idColumn);

                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                );

                images.add(uri);
                count++;
            }
            cursor.close();
        }

        Toast.makeText(this,
                "your iQ: " + images.size(),
                Toast.LENGTH_SHORT).show();
    }

    private void send() {
        Toast.makeText(this,
                "fireplace brake",
                Toast.LENGTH_SHORT).show();
    }
}
