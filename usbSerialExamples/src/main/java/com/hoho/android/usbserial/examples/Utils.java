package com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;

/**
 * Created by Nuwan Prabhath on 9/3/2015.
 */
public class Utils {

    public static void displayPromptForEnablingGPS(final Activity activity) {

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final String action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        final String message = "Please enable GPS to find current location.";

        builder.setMessage(message)
                .setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                activity.startActivity(new Intent(action));
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                d.cancel();
                            }
                        });

        builder.create().show();
    }
}
