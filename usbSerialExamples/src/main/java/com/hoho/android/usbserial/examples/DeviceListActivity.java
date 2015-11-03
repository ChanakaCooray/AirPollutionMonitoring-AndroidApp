package com.hoho.android.usbserial.examples;

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity in application which start background process to handle USB data receive and sync.
 */
public class DeviceListActivity extends Activity implements UsbDataReceiver.Receiver {

    private final String TAG = DeviceListActivity.class.getSimpleName();

    private TextView mProgressBarTitle;
    private TextView mDumpTextView;
    private TextView demoTitle;
    private TextView mCOValue;
    private TextView mSO2Value;

    private ProgressBar mProgressBar;
    private ScrollView mScrollView;
    private UsbDataReceiver mReceiver;

    private ActionBar actionBar;
    private boolean syncData;
    private int updateFrequency;
    private NotificationCompat.Builder mBuilderInfo;
    private NotificationManager mNotifyMgr;
    private int infoNotificationID = 2;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mProgressBarTitle = (TextView) findViewById(R.id.progressBarTitle);
        demoTitle = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mCOValue = (TextView) findViewById(R.id.co_value);
        mSO2Value = (TextView) findViewById(R.id.so2_value);

        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        mReceiver = new UsbDataReceiver(new Handler());
        mReceiver.setReceiver(this);


        actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);

        syncData = SP.getBoolean("syncData", true); //Get saved setting value of sync data

        mBuilderInfo = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Current Gas Levels (ppm)")
                .setOngoing(true);
        mNotifyMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, UsbSerialService.class);
        intent.putExtra("receiver", mReceiver);
        intent.putExtra("sync", syncData);
        startService(intent);

    }

    private void hideProgressBar() {
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, 0);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //mNotifyMgr.cancelAll();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //mNotifyMgr.cancelAll();
    }


    protected void showError(final String errorMessage) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String msg = String.format("%s", errorMessage);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });

    }

    /**
     * Receive data from background process
     *
     * @param resultCode
     * @param resultData
     */
    @Override
    public void onReceiveResult(int resultCode, Bundle resultData) {
        Log.d(TAG, "Service Result on DeviceListActivity");
        switch (resultCode) {

            case UsbSerialService.STATUS_RUNNING:
                Log.d(TAG, "SERVICE_RUNNING");
                break;

            case UsbSerialService.STATUS_DEVICE_FOUND:
                mProgressBarTitle.setText("Reading Data");
                break;

            case UsbSerialService.STATUS_FINISHED:
                Log.d(TAG, "USB_STATUS_FINISHED");
                String[] results = resultData.getStringArray("result");
                if (results != null) {
                    Log.d(TAG, "RECEIVED " + results);

                    mCOValue.setText(results[0]);
                    mSO2Value.setText(results[1]);

                    //BuilderInfo.setContentText("CO:" + results[0] + " SO2: " + results[1]);
                    //mNotifyMgr.notify(infoNotificationID, mBuilderInfo.build());
                }
                break;

            case UsbSerialService.STATUS_ERROR:
                String error = resultData.getString(Intent.EXTRA_TEXT);
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.d(TAG, "USB_STATUS_ERROR");
                break;

            case UsbSerialService.STATUS_ERROR_COUCHBASE:
                showError("Error Connecting to Server");
                break;

            case UsbSerialService.STATUS_GPS_OFF:
                Utils.displayPromptForEnablingGPS(this);
                break;

            case UsbSerialService.STATUS_SYNC_DISABLED:
                showError("Data sync disabled");
                break;
        }
    }


}
