package com.cnsc.carcare;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "CarCareSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_ACTIVE_VEHICLE = "activeVehicleId";
    private static final String KEY_ONBOARDING = "onboardingDone";

    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = pref.edit();
    }

    public void createLoginSession(int userId, String name, String email) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USER_NAME, name);
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public boolean isLoggedIn() { return pref.getBoolean(KEY_IS_LOGGED_IN, false); }
    public int getUserId() { return pref.getInt(KEY_USER_ID, -1); }
    public String getUserName() { return pref.getString(KEY_USER_NAME, ""); }
    public String getUserEmail() { return pref.getString(KEY_USER_EMAIL, ""); }

    public void setActiveVehicle(int vehicleId) {
        editor.putInt(KEY_ACTIVE_VEHICLE, vehicleId);
        editor.apply();
    }
    public int getActiveVehicleId() { return pref.getInt(KEY_ACTIVE_VEHICLE, -1); }

    public void setOnboardingDone(boolean done) {
        editor.putBoolean(KEY_ONBOARDING, done);
        editor.apply();
    }
    public boolean isOnboardingDone() { return pref.getBoolean(KEY_ONBOARDING, false); }

    public void logout() {
        editor.clear();
        editor.apply();
    }
}