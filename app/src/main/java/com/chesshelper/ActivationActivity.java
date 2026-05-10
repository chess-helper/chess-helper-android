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
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class ActivationActivity extends AppCompatActivity {
    private static final String API_URL = "http://92.5.89.151:8765";
    private SharedPreferences prefs;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("chess_helper", MODE_PRIVATE);
        client = new OkHttpClient();
        if (prefs.getBoolean("activated", false)) { openMain(); return; }
        setContentView(R.layout.activity_activation);
        EditText codeInput = findViewById(R.id.codeInput);
        Button activateBtn = findViewById(R.id.activateBtn);
        TextView msgText = findViewById(R.id.msgText);
        codeInput.addTextChangedListener(new TextWatcher() {
            boolean isFormatting = false;
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;
                String clean = s.toString().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
                if (clean.length() > 12) clean = clean.substring(0, 12);
                StringBuilder fmt = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    if (i == 4 || i == 8) fmt.append('-');
                    fmt.append(clean.charAt(i));
                }
                s.replace(0, s.length(), fmt.toString());
                isFormatting = false;
            }
        });
        activateBtn.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (code.replace("-", "").length() < 12) {
                msgText.setText("Введите полный код (XXXX-XXXX-XXXX)");
                msgText.setTextColor(0xFFEF4444);
                return;
            }
            activateBtn.setEnabled(false);
            activateBtn.setText("Проверяем...");
            activateCode(code, activateBtn, msgText);
        });
    }

    private void activateCode(String code, Button btn, TextView msg) {
        String deviceId = prefs.getString("device_id", null);
        if (deviceId == null) {
            deviceId = "android_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            prefs.edit().putString("device_id", deviceId).apply();
        }
        String url = API_URL + "/verify?code=" + code + "&device_id=" + deviceId;
        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> { msg.setText("Ошибка соединения"); msg.setTextColor(0xFFEF4444); btn.setEnabled(true); btn.setText("Активировать"); });
            }
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    boolean valid = json.getBoolean("valid");
                    runOnUiThread(() -> {
                        if (valid) {
                            prefs.edit().putBoolean("activated", true).apply();
                            msg.setText("✅ Активация успешна!");
                            msg.setTextColor(0xFF22C55E);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> openMain(), 1000);
                        } else {
                            msg.setText("❌ " + json.optString("error", "Неверный код"));
                            msg.setTextColor(0xFFEF4444);
                            btn.setEnabled(true); btn.setText("Активировать");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> { msg.setText("Ошибка сервера"); msg.setTextColor(0xFFEF4444); btn.setEnabled(true); btn.setText("Активировать"); });
                }
            }
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
