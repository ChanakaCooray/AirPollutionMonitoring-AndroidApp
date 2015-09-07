package com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, UsbSerialService.class);
        intent.putExtra("receiver", mReceiver);
        startService(intent);
    }

    private void hideProgressBar() {
        mProgressBar.setVisibility(View.INVISIBLE);
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

                    mCOValue.setText(results[0]);
                    mSO2Value.setText(results[1]);
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

        }
    }


}
