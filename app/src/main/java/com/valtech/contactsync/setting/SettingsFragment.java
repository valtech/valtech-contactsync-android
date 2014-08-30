package com.valtech.contactsync.setting;

import android.os.Bundle;
import android.preference.PreferenceFragment;
import com.valtech.contactsync.R;

public class SettingsFragment extends PreferenceFragment {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
  }
}
