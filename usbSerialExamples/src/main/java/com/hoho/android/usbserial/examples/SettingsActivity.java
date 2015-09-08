package com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by Nuwan Prabhath on 9/7/2015.
 */
public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

}
