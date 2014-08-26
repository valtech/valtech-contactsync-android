package com.valtech.contactsync;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Settings {
  public static boolean isSyncEnabled(Context context, String countryCode) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    return prefs.getBoolean("sync_" + countryCode, false);
  }

  public static void setSyncEnabled(Context context, String countryCode, boolean enabled) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    prefs.edit().putBoolean("sync_" + countryCode, enabled).commit();
  }
}
