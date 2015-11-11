package com.hoho.android.usbserial.examples;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lylc.widget.circularprogressbar.CircularProgressBar;

/**
 * Main activity in application which start background process to handle USB data receive and sync.
 */
public class DeviceListActivity extends Activity implements UsbDataReceiver.Receiver {

    private final String TAG = DeviceListActivity.class.getSimpleName();

    private TextView mProgressBarTitle;
//    private TextView mDumpTextView;
    private TextView demoTitle;
//    private TextView mCOValue;
//    private TextView mSO2Value;

    private ProgressBar mProgressBar;
//    private ScrollView mScrollView;
    private UsbDataReceiver mReceiver;

    private ActionBar actionBar;
    private boolean syncData;
    private int updateFrequency;

    CircularProgressBar coBar;
    CircularProgressBar so2Bar;
    CircularProgressBar noxBar;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mProgressBarTitle = (TextView) findViewById(R.id.progressBarTitle);
        demoTitle = (TextView) findViewById(R.id.demoTitle);
//        mDumpTextView = (TextView) findViewById(R.id.consoleText);
//        mCOValue = (TextView) findViewById(R.id.co_value);
//        mSO2Value = (TextView) findViewById(R.id.so2_value);
//
//        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);

        mReceiver = new UsbDataReceiver(new Handler());
        mReceiver.setReceiver(this);

        coBar = (CircularProgressBar) findViewById(R.id.coBar);
        so2Bar = (CircularProgressBar) findViewById(R.id.so2Bar);
        noxBar = (CircularProgressBar) findViewById(R.id.noxBar);

        coBar.setTitle("0");
        coBar.setSubTitle("ppm");

        so2Bar.setTitle("0");
        so2Bar.setSubTitle("ppm");

        noxBar.setTitle("0");
        noxBar.setSubTitle("ppm");

        actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);

        syncData = SP.getBoolean("syncData",true); //Get saved setting value of sync data


        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, UsbSerialService.class);
        intent.putExtra("receiver", mReceiver);
        intent.putExtra("sync",syncData);
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
                startActivityForResult(intent,0);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void showError(final String errorMessage) {

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

//                    mCOValue.setText(results[0]);
//                    mSO2Value.setText(results[1]);

                    coBar.setProgress(Integer.parseInt(results[0])/10);
                    so2Bar.setProgress(Integer.parseInt(results[1]) / 10);
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
