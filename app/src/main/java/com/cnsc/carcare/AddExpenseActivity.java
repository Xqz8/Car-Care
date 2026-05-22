package com.cnsc.carcare;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.Locale;

public class AddExpenseActivity extends AppCompatActivity {
    DatabaseHelper db;
    SessionManager session;
    private int vehicleId;

    // Fuel-specific views
    private LinearLayout layoutFuelFields;
    private EditText etLiters, etPricePerLiter;
    private TextView tvEfficiencyPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Add Expense");
        }

        db = new DatabaseHelper(this);
        session = new SessionManager(this);
        vehicleId = session.getActiveVehicleId();

        Spinner spinnerCategory = findViewById(R.id.spinnerCategory);
        EditText etAmount       = findViewById(R.id.etAmount);
        EditText etDate         = findViewById(R.id.etDate);
        EditText etOdometer     = findViewById(R.id.etOdometer);
        EditText etNotes        = findViewById(R.id.etNotes);
        Button btnSave          = findViewById(R.id.btnSave);

        layoutFuelFields    = findViewById(R.id.layoutFuelFields);
        etLiters            = findViewById(R.id.etLiters);
        etPricePerLiter     = findViewById(R.id.etPricePerLiter);
        tvEfficiencyPreview = findViewById(R.id.tvEfficiencyPreview);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Guard — no vehicle selected
        if (vehicleId == -1) {
            Toast.makeText(this, "No active vehicle selected! Go to Garage first.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int currentOdo = db.getVehicleOdometer(vehicleId);
        etOdometer.setHint("Current: " + currentOdo + " km (must be ≥ this)");

        // Default date to today
        Calendar cal = Calendar.getInstance();
        etDate.setText(String.format(Locale.getDefault(), "%d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)));

        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, day) ->
                    etDate.setText(String.format(Locale.getDefault(),
                            "%d-%02d-%02d", year, month + 1, day)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Show/hide fuel fields based on category
        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isFuel = spinnerCategory.getSelectedItem().toString().equals("Fuel");
                layoutFuelFields.setVisibility(isFuel ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Auto-calculate price per liter when amount or liters changes
        TextWatcher fuelCalcWatcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                calculatePricePerLiter(etAmount, etLiters, etPricePerLiter);
            }
        };
        etAmount.addTextChangedListener(fuelCalcWatcher);
        etLiters.addTextChangedListener(fuelCalcWatcher);

        btnSave.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            String odoStr    = etOdometer.getText().toString().trim();
            String category  = spinnerCategory.getSelectedItem().toString();

            if (amountStr.isEmpty()) {
                etAmount.setError("Please enter an amount");
                return;
            }

            // Odometer validation (optional field)
            int inputOdometer = 0;
            if (!odoStr.isEmpty()) {
                try {
                    inputOdometer = Integer.parseInt(odoStr);
                    int latestOdo = db.getVehicleOdometer(vehicleId);
                    if (inputOdometer < latestOdo) {
                        etOdometer.setError("Must be ≥ current reading of " + latestOdo + " km");
                        etOdometer.requestFocus();
                        Toast.makeText(this,
                                "Odometer must be ≥ " + latestOdo + " km",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    etOdometer.setError("Invalid number");
                    etOdometer.requestFocus();
                    return;
                }
            }

            final int finalOdo = inputOdometer;
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Expense")
                    .setMessage("Save ₱" + amountStr + " for " + category + "?")
                    .setPositiveButton("Yes, Save", (dialog, which) ->
                            performSave(spinnerCategory, etAmount, etDate,
                                    etOdometer, etNotes, finalOdo))
                    .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                    .show();
        });
    }

    private void calculatePricePerLiter(EditText etAmount, EditText etLiters,
                                        EditText etPricePerLiter) {
        try {
            double cost   = Double.parseDouble(etAmount.getText().toString());
            double liters = Double.parseDouble(etLiters.getText().toString());
            if (liters > 0) {
                etPricePerLiter.setText(String.format(Locale.getDefault(),
                        "%.2f", cost / liters));
            }
        } catch (NumberFormatException ignored) {}
    }

    private void performSave(Spinner spinnerCategory, EditText etAmount, EditText etDate,
                             EditText etOdometer, EditText etNotes, int inputOdometer) {
        try {
            String category = spinnerCategory.getSelectedItem().toString();
            double amount   = Double.parseDouble(etAmount.getText().toString().trim());
            String date     = etDate.getText().toString().trim();
            String notes    = etNotes.getText().toString().trim();

            // Save the expense first — always
            long result = db.insertExpense(vehicleId, category, amount, date,
                    inputOdometer, notes, "");

            if (result == -1) {
                Toast.makeText(this, "Error saving expense", Toast.LENGTH_SHORT).show();
                return;
            }

            // Update odometer if provided
            if (inputOdometer > 0) {
                db.updateVehicleOdometer(vehicleId, inputOdometer);
            }

            // If Fuel category, also try to save a fuel log for efficiency tracking
            if (category.equals("Fuel")) {
                String litersStr     = etLiters.getText().toString().trim();
                double liters        = litersStr.isEmpty() ? 0 : Double.parseDouble(litersStr);
                double pricePerLiter = liters > 0 ? amount / liters : 0;

                if (inputOdometer > 0 && liters > 0) {
                    // Full data — save fuel log and show efficiency
                    long fuelResult = db.addFuelLog(vehicleId, liters, amount,
                            pricePerLiter, inputOdometer, date);
                    if (fuelResult != -1) {
                        double avgEff = db.getAverageFuelEfficiency(vehicleId);
                        if (avgEff > 0) {
                            Toast.makeText(this,
                                    "Fuel saved! ⛽ Avg: " +
                                            String.format(Locale.getDefault(), "%.1f", avgEff) + " km/L",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this,
                                    "Fuel saved! ⛽ Log one more fill-up to see km/L",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                } else if (inputOdometer > 0) {
                    // Has odometer but no liters
                    Toast.makeText(this,
                            "Fuel expense saved! ⛽ Add liters next time to track km/L",
                            Toast.LENGTH_LONG).show();
                } else {
                    // Amount only — simplest case
                    Toast.makeText(this,
                            "Fuel expense saved! ⛽",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Expense saved! ✅", Toast.LENGTH_SHORT).show();
            }

            finish();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}