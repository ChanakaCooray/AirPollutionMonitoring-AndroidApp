package com.hoho.android.usbserial.examples;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.Log;
import android.widget.Toast;

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.replicator.Replication.ChangeListener;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Provider;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Nuwan Prabhath on 9/1/2015.
 */
public class UsbSerialService extends Service implements ChangeListener {

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */

    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_ERROR = 2;
    public static final int STATUS_DEVICE_FOUND = 3;
    public static final int STATUS_ERROR_COUCHBASE = 4;

    private boolean started = false;

    private UsbManager mUsbManager;
    private List<UsbSerialPort> mEntries;
    private static UsbSerialPort sPort = null;
    private SerialInputOutputManager mSerialIoManager;
    private ExecutorService mExecutor;
    private static final String TAG = "UsbSerialService";
    private Bundle bundle;
    private ResultReceiver receiver;

    protected static Manager manager;
    private Database database;

    public static final String DATABASE_NAME = "grocery-sync";
    public static final String designDocName = "grocery-local";
    public static final String byDateViewName = "byDate";
    public static final String SYNC_URL = "http://192.248.8.247:4984/sync_gateway";

    private LocationManager locationManager;
    private double latitute;
    private double longitude;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");

//                    Toast.makeText(getApplicationContext(),"Runner stopped." ,Toast.LENGTH_LONG);
                    //mDumpTextView.append("Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {

                    String received = new String(data);
                    String[] gasValues = getGasValues(received);
                    bundle.putStringArray("result", gasValues);
                    try {
                        if(gasValues[0]!=null) {
                            createGasDataEntry(gasValues);
                        }
                        receiver.send(STATUS_FINISHED, bundle);
                    } catch (Exception e) {
                        Log.e(TAG, "couchbase can't create entry");
                    }

                }
            };



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // The service is being created
        mEntries = new ArrayList<>();
        mExecutor = Executors.newSingleThreadExecutor();

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()

        Log.d(TAG, "Service Started!");
        receiver = intent.getParcelableExtra("receiver");
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        bundle = new Bundle();

        receiver.send(STATUS_RUNNING, Bundle.EMPTY);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                latitute = location.getLatitude();
                longitude = location.getLongitude();
                Log.d(TAG,"latitude"+latitute);
                Log.d(TAG,"longitude"+longitude);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {

            }
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);


        Thread t = new Thread(){
            @Override
            public void run() {
                Log.d(TAG, "Service Started on Command");
                while(!started) {
                    refreshDeviceList();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        t.start();
        try {
            startCBLite();
        } catch (Exception e) {
            com.couchbase.lite.util.Log.e(TAG, "Error initializing CBLite", e);
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
    }
    //@Override
    protected void onHandleIntent(Intent intent) {
//        Log.d(TAG, "Service Started on handled");
//        receiver = intent.getParcelableExtra("receiver");
//        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
//        bundle = new Bundle();
//
//        receiver.send(STATUS_RUNNING, Bundle.EMPTY);
//
//        while (!started) {
//            refreshDeviceList();
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//
//        Log.d(TAG, "Service Stopping!");


    }

    ////////////////////Couchbase Code////////////////
    protected void startCBLite() throws Exception {

        Manager.enableLogging(TAG, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_SYNC_ASYNC_TASK, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_SYNC, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_QUERY, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_VIEW, com.couchbase.lite.util.Log.VERBOSE);
        Manager.enableLogging(com.couchbase.lite.util.Log.TAG_DATABASE, com.couchbase.lite.util.Log.VERBOSE);

        manager = new Manager(new AndroidContext(this), Manager.DEFAULT_OPTIONS);

        //install a view definition needed by the application
        database = manager.getDatabase(DATABASE_NAME);
        com.couchbase.lite.View viewItemsByDate = database.getView(String.format("%s/%s", designDocName, byDateViewName));
        viewItemsByDate.setMap(new Mapper() {
            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                Object createdAt = document.get("created_at");
                if (createdAt != null) {
                    emitter.emit(createdAt.toString(), null);
                }
            }
        }, "1.0");

        startSync();
    }

    private void startSync() {

        URL syncUrl;
        try {
            syncUrl = new URL(SYNC_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        Replication pullReplication = database.createPullReplication(syncUrl);
        pullReplication.setContinuous(true);

        Replication pushReplication = database.createPushReplication(syncUrl);
        pushReplication.setContinuous(true);

        pullReplication.start();
        pushReplication.start();

        pullReplication.addChangeListener(this);
        pushReplication.addChangeListener(this);

    }

    @Override
    public void changed(Replication.ChangeEvent event) {
        Replication replication = event.getSource();
        com.couchbase.lite.util.Log.d(TAG, "Replication : " + replication + " changed.");
        if (!replication.isRunning()) {
            String msg = String.format("Replicator %s not running", replication);
            com.couchbase.lite.util.Log.d(TAG, msg);
        } else {
            int processed = replication.getCompletedChangesCount();
            int total = replication.getChangesCount();
            String msg = String.format("Replicator processed %d / %d", processed, total);
            com.couchbase.lite.util.Log.d(TAG, msg);
        }

        if (event.getError() != null) {
            receiver.send(STATUS_ERROR_COUCHBASE, Bundle.EMPTY);
        }
    }

    private Document createGasDataEntry(String[] gasData) throws Exception {

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        UUID uuid = UUID.randomUUID();
        Calendar calendar = GregorianCalendar.getInstance();
        long currentTime = calendar.getTimeInMillis();
        String currentTimeString = dateFormatter.format(calendar.getTime());

        String id = currentTime + "-" + uuid.toString();

        Document document = database.createDocument();

        Map<String, Object> properties = new HashMap<>();

        properties.put("_id", id);
        properties.put("CO", gasData[0]);
        properties.put("lat",latitute);
        properties.put("lon",longitude);
        properties.put("created_at", currentTimeString);
        document.putProperties(properties);

        com.couchbase.lite.util.Log.d(TAG, "Created new gas entry item with id: %s", document.getId());
        Log.d(TAG,"CO value"+gasData[0]);
        return document;
    }

    //////////////////End Couchbase Code//////////////


    private boolean refreshDeviceList() {

        Log.d(TAG, "AAAAAAOnService");


        Log.d(TAG, "Refreshing device list ...");

        final List<UsbSerialDriver> drivers =
                UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        final List<UsbSerialPort> result = new ArrayList<>();
        for (final UsbSerialDriver driver : drivers) {
            final List<UsbSerialPort> ports = driver.getPorts();
            Log.d(TAG, String.format("+ %s: %s port%s",
                    driver, Integer.valueOf(ports.size()), ports.size() == 1 ? "" : "s"));
            result.addAll(ports);
        }

        if (!started) {
            mEntries.clear();
            mEntries.addAll(result);

            if (mEntries.size() > 0) {
                receiver.send(STATUS_DEVICE_FOUND, Bundle.EMPTY);
                sPort = mEntries.get(0);
                started = true;
                startConsole();
                return true;
            }

        }
        Log.d(TAG, "hide progress bar");
        return false;

    }

    protected void startConsole() {
        if (sPort == null) {

        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {

                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {

                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private String[] getGasValues(String data) {
        String[] gasses = data.split(";");
        String[] gasValues = new String[3];
        int i = 0;
        for (String gas : gasses) {
            Log.d(TAG, "GAS_VALUE" + gas);
            String[] gasData = gas.split(":");
            if (gasData.length == 3) {
                gasValues[i] = gasData[2];

            }
        }
        return gasValues;
    }


}