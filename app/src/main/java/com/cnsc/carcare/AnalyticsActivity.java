package com.cnsc.carcare;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageButton; // Added this import
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class AnalyticsActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private PieChart pieChart;
    private BarChart barChart;
    private TextView tvAnalyticTotal, tvAnalyticFuel;
    private int vehicleId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analytics);

        // --- 1. BACK BUTTON LOGIC ---
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                finish(); // Returns to SettingsActivity or MainActivity
            });
        }

        // Hide the default Action Bar if it exists to clean up the UI
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // --- 2. INITIALIZATION ---
        SessionManager session = new SessionManager(this);
        vehicleId = session.getActiveVehicleId();
        db = new DatabaseHelper(this);

        tvAnalyticTotal = findViewById(R.id.tvAnalyticTotal);
        tvAnalyticFuel = findViewById(R.id.tvAnalyticFuel);
        pieChart = findViewById(R.id.pieChart);
        barChart = findViewById(R.id.barChart);

        // Load data to the UI
        loadSummaryCards();
        setupPieChart();
        setupBarChart();
    }

    private void loadSummaryCards() {
        double totalSpent = db.getTotalExpensesByVehicle(vehicleId);
        tvAnalyticTotal.setText("₱" + String.format("%.2f", totalSpent));

        double avgEff = db.getAverageFuelEfficiency(vehicleId);
        tvAnalyticFuel.setText(String.format("%.1f", avgEff) + " km/L");
    }

    private void setupPieChart() {
        ArrayList<PieEntry> entries = new ArrayList<>();
        HashMap<String, Float> categoryMap = new HashMap<>();

        Cursor cursor = db.getExpensesByVehicle(vehicleId);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                // Adjust index based on your DatabaseHelper column order
                String category = cursor.getString(2);
                float amount = cursor.getFloat(3);

                if (categoryMap.containsKey(category)) {
                    categoryMap.put(category, categoryMap.get(category) + amount);
                } else {
                    categoryMap.put(category, amount);
                }
            }
            cursor.close();
        }

        if (entries.isEmpty() && categoryMap.isEmpty()) {
            pieChart.setNoDataText("No expense data available for this vehicle.");
        }

        for (Map.Entry<String, Float> entry : categoryMap.entrySet()) {
            entries.add(new PieEntry(entry.getValue(), entry.getKey()));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);
        dataSet.setSliceSpace(3f); // Adds small gap between slices for better look

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(pieChart));

        pieChart.setData(data);
        pieChart.setUsePercentValues(true);
        pieChart.getDescription().setEnabled(false);
        pieChart.setCenterText("Expenses");
        pieChart.setCenterTextSize(16f);
        pieChart.setHoleRadius(45f); // Modern "Donut" style
        pieChart.animateY(1000);
        pieChart.invalidate();
    }

    private void setupBarChart() {
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        // Dynamically build last 4 months relative to today
        for (int i = 3; i >= 0; i--) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.MONTH, -i);
            String monthQuery = new java.text.SimpleDateFormat("yyyy-MM",
                    java.util.Locale.getDefault()).format(cal.getTime());
            String monthName = new java.text.SimpleDateFormat("MMM",
                    java.util.Locale.getDefault()).format(cal.getTime());

            float monthlyTotal = (float) db.getMonthlyExpenses(vehicleId, monthQuery);
            entries.add(new BarEntry(3 - i, monthlyTotal));
            labels.add(monthName);
        }

        BarDataSet dataSet = new BarDataSet(entries, "Monthly Spending");
        dataSet.setColors(ColorTemplate.LIBERTY_COLORS);
        dataSet.setValueTextSize(11f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        barChart.setData(data);
        barChart.getDescription().setEnabled(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);

        barChart.getAxisRight().setEnabled(false);
        barChart.animateY(1000);
        barChart.invalidate();
    }
}