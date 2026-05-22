package com.cnsc.carcare;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 1. Initialize Session Manager
        session = new SessionManager(this);

        // 2. SET UP THE BACK BUTTON (Added this)
        // This matches the android:id="@+id/btnBack" in your revised XML
        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            finish(); // This closes Settings and goes back to the previous screen
        });

        // 3. Update User Information
        ((TextView) findViewById(R.id.tvSettingsName)).setText(session.getUserName());
        ((TextView) findViewById(R.id.tvSettingsEmail)).setText(session.getUserEmail());

        // 4. Button Navigation Logic
        findViewById(R.id.btnManageVehicles).setOnClickListener(v ->
                startActivity(new Intent(this, VehicleManagementActivity.class)));

        findViewById(R.id.btnViewHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        findViewById(R.id.btnAnalytics).setOnClickListener(v ->
                startActivity(new Intent(this, AnalyticsActivity.class)));

        // 5. Logout Logic
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            session.logout();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity(); // Clears all activities from the stack
        });
    }
}