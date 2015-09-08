package com.hoho.android.usbserial.examples;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by Nuwan Prabhath on 9/7/2015.
 */
public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

}
