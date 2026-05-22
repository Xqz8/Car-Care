package com.cnsc.carcare;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        EditText etEmail = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvRegister = findViewById(R.id.tvRegister);

        ImageButton btnTogglePassword = findViewById(R.id.btnTogglePassword);
        final boolean[] isVisible = {false};
        btnTogglePassword.setOnClickListener(v -> {
            isVisible[0] = !isVisible[0];
            if (isVisible[0]) {
                etPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            Cursor c = db.loginUser(email, password);
            if (c.moveToFirst()) {
                int userId = c.getInt(0);
                String name = c.getString(1);
                session.createLoginSession(userId, name, email);
                c.close();

                // Check if user has vehicles
                Cursor vehicles = db.getVehiclesByUser(userId);
                if (vehicles.getCount() == 0) {
                    startActivity(new Intent(this, AddVehicleActivity.class));
                } else {
                    // Set first vehicle as active if none selected
                    if (session.getActiveVehicleId() == -1) {
                        vehicles.moveToFirst();
                        session.setActiveVehicle(vehicles.getInt(0));
                    }
                    startActivity(new Intent(this, MainActivity.class));
                }
                vehicles.close();
                finish();
            } else {
                Toast.makeText(this, "Invalid email or password", Toast.LENGTH_SHORT).show();
            }
        });

        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }
}