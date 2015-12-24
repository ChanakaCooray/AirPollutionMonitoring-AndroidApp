package com.hoho.android.usbserial.examples;

import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
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
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.lylc.widget.circularprogressbar.CircularProgressBar;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;

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
    private NotificationCompat.Builder mBuilderInfo;
    private NotificationManager mNotifyMgr;
    private int infoNotificationID = 2;

    CircularProgressBar coBar;
    CircularProgressBar so2Bar;
    CircularProgressBar noxBar;

    private static final Random RANDOM = new Random();
    private LineGraphSeries<DataPoint> coSeries;
    private LineGraphSeries<DataPoint> so2Series;
    private LineGraphSeries<DataPoint> noSeries;

    Calendar calendar = Calendar.getInstance();

    private long lastX = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        GraphView graph = (GraphView) findViewById(R.id.graph);
        coSeries = new LineGraphSeries<>();
        so2Series = new LineGraphSeries<>();
        noSeries = new LineGraphSeries<>();

        graph.addSeries(coSeries);
        graph.addSeries(so2Series);
        graph.addSeries(noSeries);

        coSeries.setColor(Color.parseColor("#00E3FF"));
        so2Series.setColor(Color.parseColor("#EB61FF"));
        noSeries.setColor(Color.parseColor("#FFB200"));

        Viewport viewport = graph.getViewport();

        coSeries.setTitle("CO");
        so2Series.setTitle("SO2");
        noSeries.setTitle("NO2");

        graph.getGridLabelRenderer().setHorizontalLabelsVisible(false);

        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

        viewport.setMinY(0);
        viewport.setMaxY(1000);

        viewport.setMinX(0);
        viewport.setMaxX(900);

        viewport.setYAxisBoundsManual(true);
        viewport.setXAxisBoundsManual(true);
        viewport.setScrollable(false);

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                while (true){
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            addEntry();
//                        }
//                    });
//
//                    try {
//                        Thread.sleep(600);
//                    } catch (InterruptedException e) {
//
//                    }
//                }
//            }
//        }).start();

        mProgressBarTitle = (TextView) findViewById(R.id.progressBarTitle);
        demoTitle = (TextView) findViewById(R.id.demoTitle);
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

        //syncData = SP.getBoolean("syncData", true); //Get saved setting value of sync data

        mBuilderInfo = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Current Gas Levels (ppm)")
                .setOngoing(true);
        mNotifyMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent intent = new Intent(Intent.ACTION_SYNC, null, this, UsbSerialService.class);
        intent.putExtra("receiver", mReceiver);
        //intent.putExtra("sync", syncData); //syncApp in Service
        startService(intent);
    }

    private void addEntry() {
        coSeries.appendData(new DataPoint(lastX++, RANDOM.nextDouble() * 50d), true, 10);
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

//                    mCOValue.setText(results[0]);
//                    mSO2Value.setText(results[1]);

                    coBar.setProgress(Integer.parseInt(results[0])/10);
                    so2Bar.setProgress(Integer.parseInt(results[1])/10);
                    noxBar.setProgress(Integer.parseInt(results[2])/10);

                    coBar.setTitle(results[0]);
                    so2Bar.setTitle(results[1]);
                    noxBar.setTitle(results[2]);

//                    Date date = new Date();
//                    calendar.setTime(date);
//
//                    int minutes = calendar.get(Calendar.MINUTE);
//                    int seconds = calendar.get(Calendar.SECOND);
//
//                    double xAxis = minutes+seconds*100.0/60;

                    int dataPoints = 10;

                    if(lastX%100==0) {
                        long x = lastX;
                        coSeries.appendData(new DataPoint(x, Double.parseDouble(results[0])), true, dataPoints);
                        so2Series.appendData(new DataPoint(x, Double.parseDouble(results[1])), true, dataPoints);
                        noSeries.appendData(new DataPoint(x, 0), true, dataPoints);
                    }

                    lastX++;
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
