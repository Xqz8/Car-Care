package com.cnsc.carcare;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {
    int currentPage = 0;

    String[] emojis = {"🚗", "⛽", "📊"};
    String[] titles = {"Track Everything", "Monitor Fuel", "View Analytics"};
    String[] subtitles = {
            "Log expenses, maintenance,\nand repairs all in one place",
            "Track fuel consumption\nand calculate efficiency",
            "Beautiful charts showing\nwhere your money goes"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        updatePage();

        findViewById(R.id.btnNext).setOnClickListener(v -> {
            currentPage++;
            if (currentPage >= titles.length) {
                new SessionManager(this).setOnboardingDone(true);
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                updatePage();
            }
        });
    }

    private void updatePage() {
        ((TextView) findViewById(R.id.tvEmoji)).setText(emojis[currentPage]);
        ((TextView) findViewById(R.id.tvTitle)).setText(titles[currentPage]);
        ((TextView) findViewById(R.id.tvSubtitle)).setText(subtitles[currentPage]);

        Button btnNext = findViewById(R.id.btnNext);
        btnNext.setText(currentPage == titles.length - 1 ? "Get Started" : "Next");
    }

    @Override
    public void onBackPressed() {
        // Prevent going back during onboarding
        if (currentPage > 0) {
            currentPage--;
            updatePage();
        }
        // Do nothing on first page — user can't exit onboarding via back
    }
}