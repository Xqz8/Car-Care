package com.cnsc.carcare;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;

public class AddVehicleActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_vehicle);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Vehicle");
        }

        db = new DatabaseHelper(this);
        session = new SessionManager(this);

        EditText etName     = findViewById(R.id.etVehicleName);
        Spinner spinnerType = findViewById(R.id.spinnerType);
        EditText etBrand    = findViewById(R.id.etBrand);
        EditText etModel    = findViewById(R.id.etModel);
        EditText etYear     = findViewById(R.id.etYear);
        EditText etPlate    = findViewById(R.id.etPlate);
        EditText etOdometer = findViewById(R.id.etOdometer);
        Button btnSave      = findViewById(R.id.btnSaveVehicle);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        btnSave.setOnClickListener(v -> {
            String name   = etName.getText().toString().trim();
            String brand  = etBrand.getText().toString().trim();
            String model  = etModel.getText().toString().trim();
            String year   = etYear.getText().toString().trim();
            String odoStr = etOdometer.getText().toString().trim();

            if (name.isEmpty())  { etName.setError("Nickname is required"); return; }
            if (brand.isEmpty()) { etBrand.setError("Brand is required"); return; }
            if (model.isEmpty()) { etModel.setError("Model is required"); return; }
            if (year.isEmpty())  { etYear.setError("Year is required"); return; }
            if (!year.matches("\\d{4}")
                    || Integer.parseInt(year) < 1900
                    || Integer.parseInt(year) > Calendar.getInstance().get(Calendar.YEAR) + 1) {
                etYear.setError("Enter a valid 4-digit year"); return;
            }
            if (!odoStr.isEmpty() && Integer.parseInt(odoStr) < 0) {
                etOdometer.setError("Odometer cannot be negative"); return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("Confirm")
                    .setMessage("Save " + name + " to your garage?")
                    .setPositiveButton("Yes", (dialog, which) ->
                            performSave(etName, spinnerType, etBrand, etModel, etYear, etPlate, etOdometer))
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    private void performSave(EditText etName, Spinner spinnerType, EditText etBrand,
                             EditText etModel, EditText etYear, EditText etPlate, EditText etOdometer) {

        String name   = etName.getText().toString().trim();
        String type   = spinnerType.getSelectedItem().toString();
        String brand  = etBrand.getText().toString().trim();
        String model  = etModel.getText().toString().trim();
        String year   = etYear.getText().toString().trim();
        String plate  = etPlate.getText().toString().trim();
        String odoStr = etOdometer.getText().toString().trim();

        int odometer = odoStr.isEmpty() ? 0 : Integer.parseInt(odoStr);
        int userId   = session.getUserId();

        long vehicleId = db.addVehicle(userId, name, type, brand, model, year, plate, odometer, "");

        if (vehicleId != -1) {
            session.setActiveVehicle((int) vehicleId);

            // Automatically seed all standard PMS tasks for this vehicle
            db.seedPMSTasks((int) vehicleId, odometer, type);

            Toast.makeText(this, "Vehicle saved! 🚗", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, PmsConfirmActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Error saving vehicle", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}