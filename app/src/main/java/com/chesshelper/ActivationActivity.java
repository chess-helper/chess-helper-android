package com.chesshelper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

public class ActivationActivity extends AppCompatActivity {
    private static final String API_URL = "https://api.homark.ru";
    private static final long LICENSE_OFFLINE_GRACE_MS = TimeUnit.DAYS.toMillis(7);
    private static final long TRIAL_OFFLINE_GRACE_MS = TimeUnit.MINUTES.toMillis(30);

    private SharedPreferences prefs;
    private OkHttpClient client;
    private TextView statusText;
    private TextView trialTimeText;
    private TextView msgText;
    private Button continueBtn;
    private Button activateBtn;
    private EditText codeInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        prefs = getSharedPreferences("chess_helper", MODE_PRIVATE);
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();

        statusText = findViewById(R.id.statusText);
        trialTimeText = findViewById(R.id.trialTimeText);
        msgText = findViewById(R.id.msgText);
        continueBtn = findViewById(R.id.continueBtn);
        activateBtn = findViewById(R.id.activateBtn);
        codeInput = findViewById(R.id.codeInput);

        continueBtn.setEnabled(false);
        continueBtn.setOnClickListener(v -> openMain());

        codeInput.addTextChangedListener(new TextWatcher() {
            boolean isFormatting = false;

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String clean = s.toString()
                        .replaceAll("[^A-Za-z0-9]", "")
                        .toUpperCase();

                if (clean.length() > 12) clean = clean.substring(0, 12);

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    if (i == 4 || i == 8) formatted.append('-');
                    formatted.append(clean.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString());
                isFormatting = false;
            }
        });

        activateBtn.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (code.replace("-", "").length() != 12) {
                showMessage("Введите полный код (XXXX-XXXX-XXXX)", 0xFFEF4444);
                return;
            }

            activateBtn.setEnabled(false);
            activateBtn.setText("Проверяем...");
            activateCode(code);
        });

        checkStatus();
    }

    private String getLicenseDeviceId() {
        String deviceId = prefs.getString("device_id", null);
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = "android_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            prefs.edit().putString("device_id", deviceId).apply();
        }
        return deviceId;
    }

    private void checkStatus() {
        statusText.setText("Проверяем доступ...");
        statusText.setTextColor(0xFFFFFFFF);
        trialTimeText.setText("");
        continueBtn.setEnabled(false);

        HttpUrl url = HttpUrl.parse(API_URL + "/status").newBuilder()
                .addQueryParameter("device_id", getLicenseDeviceId())
                .build();

        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> useOfflineCacheOrShowError());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                try {
                    JSONObject json = new JSONObject(body);
                    String state = json.optString("state", "");
                    boolean access = json.optBoolean("access", false);

                    runOnUiThread(() -> applyServerState(state, access, json));
                } catch (Exception e) {
                    runOnUiThread(() -> useOfflineCacheOrShowError());
                }
            }
        });
    }

    private void applyServerState(String state, boolean access, JSONObject json) {
        long now = System.currentTimeMillis();

        if ("LICENSED".equals(state) && access) {
            prefs.edit()
                    .putBoolean("cached_licensed", true)
                    .putLong("last_license_check", now)
                    .apply();

            statusText.setText("✅ Лицензия активна");
            statusText.setTextColor(0xFF22C55E);
            trialTimeText.setText("Полный доступ без ограничения по времени");
            continueBtn.setEnabled(true);
            continueBtn.setText("Продолжить");
            return;
        }

        if ("TRIAL_ACTIVE".equals(state) && access) {
            long remaining = json.optLong("remaining_seconds", 0);

            prefs.edit()
                    .putBoolean("cached_licensed", false)
                    .putLong("trial_remaining_seconds", remaining)
                    .putLong("trial_cached_at", now)
                    .apply();

            statusText.setText("🕒 Пробный период активен");
            statusText.setTextColor(0xFF3B82F6);
            trialTimeText.setText("Осталось: " + formatRemaining(remaining));
            continueBtn.setEnabled(true);
            continueBtn.setText("Продолжить");
            return;
        }

        if ("TRIAL_EXPIRED".equals(state)) {
            prefs.edit()
                    .putBoolean("cached_licensed", false)
                    .putLong("trial_remaining_seconds", 0)
                    .apply();

            statusText.setText("⛔ Пробный период закончился");
            statusText.setTextColor(0xFFEF4444);
            trialTimeText.setText("Введите код лицензии для продолжения");
            continueBtn.setEnabled(false);
            return;
        }

        useOfflineCacheOrShowError();
    }

    private void useOfflineCacheOrShowError() {
        long now = System.currentTimeMillis();

        boolean cachedLicensed = prefs.getBoolean("cached_licensed", false);
        long lastLicenseCheck = prefs.getLong("last_license_check", 0);

        if (cachedLicensed && lastLicenseCheck > 0 &&
                now - lastLicenseCheck <= LICENSE_OFFLINE_GRACE_MS) {
            statusText.setText("✅ Лицензия активна");
            statusText.setTextColor(0xFF22C55E);
            trialTimeText.setText("Сервер временно недоступен — используется сохранённый статус");
            continueBtn.setEnabled(true);
            continueBtn.setText("Продолжить");
            return;
        }

        long trialCachedAt = prefs.getLong("trial_cached_at", 0);
        long cachedRemaining = prefs.getLong("trial_remaining_seconds", 0);

        if (trialCachedAt > 0 && now - trialCachedAt <= TRIAL_OFFLINE_GRACE_MS) {
            long elapsedSeconds = Math.max(0, (now - trialCachedAt) / 1000);
            long remaining = Math.max(0, cachedRemaining - elapsedSeconds);

            if (remaining > 0) {
                statusText.setText("🕒 Пробный период активен");
                statusText.setTextColor(0xFF3B82F6);
                trialTimeText.setText("Осталось: " + formatRemaining(remaining));
                continueBtn.setEnabled(true);
                continueBtn.setText("Продолжить");
                return;
            }
        }

        statusText.setText("Нет соединения с сервером лицензирования");
        statusText.setTextColor(0xFFEF4444);
        trialTimeText.setText("Проверьте интернет и повторите попытку");
        continueBtn.setEnabled(false);
    }

    private void activateCode(String code) {
        HttpUrl url = HttpUrl.parse(API_URL + "/verify").newBuilder()
                .addQueryParameter("code", code)
                .addQueryParameter("device_id", getLicenseDeviceId())
                .build();

        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showMessage("Ошибка соединения", 0xFFEF4444);
                    resetActivateButton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";

                try {
                    JSONObject json = new JSONObject(body);
                    boolean valid = json.optBoolean("valid", false)
                            || (json.optBoolean("ok", false) && json.optBoolean("access", false));

                    runOnUiThread(() -> {
                        if (valid) {
                            showMessage("✅ Активация успешна!", 0xFF22C55E);
                            new Handler(Looper.getMainLooper()).postDelayed(
                                    () -> checkStatus(), 500
                            );
                        } else {
                            showMessage("❌ " + json.optString("error", "Неверный код"), 0xFFEF4444);
                        }

                        resetActivateButton();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        showMessage("Ошибка сервера", 0xFFEF4444);
                        resetActivateButton();
                    });
                }
            }
        });
    }

    private void resetActivateButton() {
        activateBtn.setEnabled(true);
        activateBtn.setText("Активировать");
    }

    private void showMessage(String text, int color) {
        msgText.setText(text);
        msgText.setTextColor(color);
    }

    private String formatRemaining(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + " ч " + minutes + " мин";
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
