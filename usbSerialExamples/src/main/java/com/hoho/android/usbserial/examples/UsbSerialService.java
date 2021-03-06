package com.hoho.android.usbserial.examples;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.couchbase.lite.CouchbaseLiteException;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
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
    public static final int STATUS_GPS_OFF = 5;
    public static final int STATUS_SYNC_DISABLED = 6;


    private boolean started = false;

    private UsbManager mUsbManager;
    private List<UsbSerialPort> mEntries;
    private static UsbSerialPort sPort = null;
    private SerialInputOutputManager mSerialIoManager;
    private ExecutorService mExecutor;
    private static final String TAG = "UsbSerialService";
    private Bundle bundle;
    private ResultReceiver receiver;
    private NotificationManager mNotifyMgr;
    protected static Manager manager;
    private Database database;
    private NotificationCompat.Builder mBuilderAlert;


    public static final String DATABASE_NAME = "air-pollution-sync";
    public static final String designDocName = "air-pollution-local";
    public static final String byDateViewName = "byDate";
    public static final String SYNC_URL = "http://192.248.8.247:4985/sync_gateway";
    private String userEmail;
    private String buffer;
    private LocationManager locationManager;
    private double latitute;
    private double longitude;
    private boolean locationFound = false;
    private int syncApp;
    private int deviceSleepingPeriod;
    //private int deviceSleepTimePeriod;
    private long startTime;
    private boolean notificationGiven = false;
    private int attenntionNotificationID = 1;
    private boolean startedSyncService = false;
    private boolean insertData = false;
    private Thread searchDevices;
    private volatile boolean runningThreadSearch = false;
    private Replication pushReplication;
    private boolean sleeping;
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {

                    Log.d(TAG, "ON new Data called" +System.currentTimeMillis());
                    String received = new String(data);
                    Log.d(TAG,"vvvvvvvvvvvvvvvvv: "+ received);
                    String[] gasValues = getGasValues(received);

                    if (startTime + deviceSleepingPeriod< System.currentTimeMillis()) {

                        Log.e(TAG, "STARTSLEEPING: " +deviceSleepingPeriod +" : "+System.currentTimeMillis());
                        String s = "S";
                        try {
                            sPort.write(s.getBytes(), 100);
                        } catch (Exception e) {
                            Log.e(TAG, "STARTSLEEPING error is port writing");
                        }
                        startTime = System.currentTimeMillis();
                        sleeping=true;
                    }



                    try {
                        if (gasValues != null && gasValues[0] != null && gasValues[1] != null && gasValues[2] != null) {

                            bundle.putStringArray("result", gasValues);
                            checkCritical(gasValues);
                            //&& insertData
                            if (locationFound) {
                                createGasDataEntry(gasValues);
                                Log.e(TAG, "SENDCOUCH " + gasValues[0] + ":" + gasValues[1] + ": " + gasValues[2]);
                            } else {
                                Log.e(TAG, "GGGGGG");
                            }
                        } else {
                            Log.e(TAG, "SENDCOUCHERROR ");
                        }
                        receiver.send(STATUS_FINISHED, bundle);
                    } catch (Exception e) {
                        Log.e(TAG, "couchbase can't create entry");
                        Log.e(TAG, "SENDCOUCHERROR EXCEPTION" + e.getStackTrace());
                    }


                }
            };

    private void checkCritical(String[] gasValues) {
        int co = Integer.parseInt(gasValues[0].trim());
        int so2 = Integer.parseInt(gasValues[1].trim());

        String gasses = "";
        if (co > AppConstants.CO_MAX) {
            gasses += "CO ";
        }
        if (so2 > AppConstants.SO_MAX) {
            gasses += "SO2 ";
        }


        if (!notificationGiven && (co >= AppConstants.CO_MAX || so2 >= AppConstants.SO_MAX)) {
            mBuilderAlert.setContentTitle("Attention! High " + gasses);
            mNotifyMgr.notify(attenntionNotificationID, mBuilderAlert.build());
            notificationGiven = true;
            Log.e(TAG, "Notification created");

        } else if (notificationGiven && (co < AppConstants.CO_MAX && so2 < AppConstants.SO_MAX)) {
            mNotifyMgr.cancel(attenntionNotificationID);
            notificationGiven = false;
            Log.e(TAG, "Notification removed");
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        // The service is being created
        mEntries = new ArrayList<>();
        mExecutor = Executors.newSingleThreadExecutor();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(spChanged);
    }

    SharedPreferences.OnSharedPreferenceChangeListener spChanged = new
            SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String key) {
                    Log.e(TAG, "++**" + key);
                    if (key.equals("prefSync")) {
                        syncApp = Integer.parseInt(sharedPreferences.getString(key, "1"));
                        Log.e(TAG, "SYNC111 Changed " + syncApp);

                        if (syncApp != 0) {
                            if (syncApp == 2) {
                                ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                if (mWifi.isConnected()) {
                                    Log.e(TAG, "WiFi enabled " + startedSyncService);
                                    if (!startedSyncService) {
                                        startSync(true);
                                    }
                                } else {
                                    Log.e(TAG, "WiFi not enabled " + startedSyncService);
                                    if (startedSyncService) {
                                        startSync(false);
                                        Log.e(TAG, "WiFi not enabled disabeling sync");
                                    }
                                }

                            } else if (syncApp == 1) {
                                Log.e(TAG, "WiFi selected 1 " + startedSyncService);

                                if (!startedSyncService) {

                                    try {

                                        startSync(true);
                                        Log.e(TAG, "WiFi 1 selected starting");
                                    } catch (Exception e) {
                                        Log.e(TAG, "WiFi exception " + e.getMessage());

                                    }
                                }
                            }
                        } else {
                            Log.e(TAG, "WiFi never " + startedSyncService);
                            if (startedSyncService) {
                                startSync(false);
                            }

                        }
                    }
                    else if(key.equals("sleepPeriod")) {
                        deviceSleepingPeriod = Integer.parseInt(sharedPreferences.getString("sleepPeriod", "60"));
                        startTime = System.currentTimeMillis();
                    }

                }
            };


    private void emptyDatabase() {
        try {
            Log.e(TAG, "DELDB " + database.totalDataSize());
            if (database.exists()) {
                database.delete();
                startCBLite();
                startSync(true);
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // The service is starting, due to a call to startService()
        Log.d(TAG, "Service Started!");

        userEmail = UserEmailFetcher.getEmail(this);
        receiver = intent.getParcelableExtra("receiver");
        //syncApp = intent.getBooleanExtra("sync", true);
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(this);
        String syncFreq = SP.getString("prefSync", "1");
        //String sleepTimePeriod = SP.getString("sleepTimePeriod", "1000");
        String sleepPeriod = SP.getString("sleepPeriod", "1000");
        startTime = System.currentTimeMillis();
        syncApp = Integer.parseInt(syncFreq);
        deviceSleepingPeriod = Integer.parseInt(sleepPeriod)*1000*60;
        //Log.d(TAG,"sleepPeriod "+sleepTimePeriod);
        //deviceSleepTimePeriod = (int)(Double.parseDouble(sleepTimePeriod)*60*1000);
        //Log.d(TAG,"sleepPeriod1 "+deviceSleepTimePeriod);
        Log.e(TAG, "SYNC111 " + syncFreq);

        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        bundle = new Bundle();

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mNotifyMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent resultIntent = new Intent(this, DeviceListActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, resultIntent, PendingIntent.FLAG_NO_CREATE);

        mBuilderAlert = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Attention! Hazard Gases Detected")
                .setContentText("Leave the area immediately")
                .setColor(Color.RED)
                .setSound(uri).setVibrate(new long[]{1000, 1000, 1000, 1000, 1000})
                .setContentIntent(pendingIntent);


        //mNotifyMgr.notify(attenntionNotificationID, mBuilderAlert.build());


        receiver.send(STATUS_RUNNING, Bundle.EMPTY);
        if (syncApp == 0) {
            receiver.send(STATUS_SYNC_DISABLED, Bundle.EMPTY);
        }


        ///////////////
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        registerReceiver(mUsbReceiver, filter);
        //////////////

        //////////////
        IntentFilter networkFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkChangeReceiver, networkFilter);
        //////////////

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                latitute = location.getLatitude();
                longitude = location.getLongitude();
                Log.d(TAG, "latitude" + latitute);
                Log.d(TAG, "longitude" + longitude);
                locationFound = true;

            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
                locationFound = true;
            }

            public void onProviderDisabled(String provider) {
                locationFound = false;
                receiver.send(STATUS_GPS_OFF, Bundle.EMPTY);
            }
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        searchDevices = new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "Service Started on Command");
                runningThreadSearch = true;
                while (!started) {
                    refreshDeviceList();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                runningThreadSearch = false;
            }
        };

        searchDevices.start();

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
        mNotifyMgr.cancel(attenntionNotificationID);
        Log.e(TAG, "Service destroyed");
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

        database.addChangeListener(new Database.ChangeListener() {
            public void changed(Database.ChangeEvent event) {
                Log.e(TAG,"DBCHANGED "+event.getChanges());
            }
        });

        if (syncApp != 0) {
            if (syncApp == 2) {
                ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (mWifi.isConnected()) {
                    startSync(true);
                }
            } else if (syncApp == 1) {
                startSync(true);
            }
        }


    }

    private void startSync(boolean start) {

        URL syncUrl;
        try {
            syncUrl = new URL(SYNC_URL);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

//        Replication pullReplication = database.createPullReplication(syncUrl);
//        pullReplication.setContinuous(true);
        //if (pushReplication == null) {
            pushReplication = database.createPushReplication(syncUrl);
            pushReplication.setContinuous(true);
            pushReplication.addChangeListener(this);

        //}
        insertData = start;
        if (start) {
            //pullReplication.start();
            pushReplication.start();
            pushReplication.goOnline();
            //pullReplication.addChangeListener(this);
            startedSyncService = true;
            Log.e(TAG, "WiFi service sync " + start);
        } else {
            //pullReplication.stop();
            pushReplication.stop();
            pushReplication.goOffline();

            startedSyncService = false;
            Log.e(TAG, "WiFi service sync " + start);

        }

    }

    @Override
    public void changed(Replication.ChangeEvent event) {
        Replication replication = event.getSource();
        com.couchbase.lite.util.Log.d(TAG, "Replication : " + replication + " changed.");
        if (!replication.isRunning()) {
            String msg = String.format("Replication %s not running", replication);
            com.couchbase.lite.util.Log.d(TAG, msg);
            Log.e(TAG, "LISTNER " + msg);
        } else {
            int processed = replication.getCompletedChangesCount();
            int total = replication.getChangesCount();
            String msg = String.format("Replication processed %d / %d " + database.totalDataSize(), processed, total);
            com.couchbase.lite.util.Log.d(TAG, msg);
            Log.e(TAG, "LISTNER " + msg);
        }

        if (event.getError() != null) {
            //receiver.send(STATUS_ERROR_COUCHBASE, Bundle.EMPTY);
            Log.e(TAG, "COUCH_INIT_ERROR " + event.getError());
        }
    }

    private Document createGasDataEntry(String[] gasData) throws Exception {

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        UUID uuid = UUID.randomUUID();
        Calendar calendar = GregorianCalendar.getInstance();
        long currentTime = calendar.getTimeInMillis();

        String id = currentTime + "-" + uuid.toString();

        Document document = database.createDocument();

        Map<String, Object> properties = new HashMap<>();

        Date timeNow = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd yyyy hh:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String time = sdf.format(timeNow);

        String city = getNearestCity(latitute, longitude);

        if(city!=null) {
            Address cityAddress = getAddressOfCity(city);
            if (cityAddress != null) {
                double distance = getDistance(latitute, longitude, cityAddress.getLatitude(), cityAddress.getLongitude());

                Log.d(TAG, "ssssssssssssssssss " + distance);
                Log.d(TAG, "ssssssssssssssssss " + cityAddress.getLatitude() + "   " + cityAddress.getLongitude());

                properties.put("City", cityAddress.getLocality());
                properties.put("CityLan", cityAddress.getLatitude());
                properties.put("CityLon", cityAddress.getLongitude());
                properties.put("Distance", distance / 1000.0);
            }
            else{
                Log.e(TAG, "Error in city coordinates.");
            }
        }
        else{
            Log.e(TAG, "Error in city coordinates.");
        }

        properties.put("_id", id);
        properties.put("gases",new String[]{"CO","SO2","NO2"});
        properties.put("CO", gasData[0]);
        properties.put("SO2", gasData[1]);
        properties.put("NO2", gasData[2]);
        properties.put("lat", latitute);
        properties.put("lon", longitude);
        properties.put("email", userEmail);
        properties.put("Time", time);

        document.putProperties(properties);

        com.couchbase.lite.util.Log.d(TAG, "Created new gas entry item with id: %s", document.getId());
        Log.d(TAG, "CO value" + gasData[0]);
        Log.d(TAG, "SO2 value" + gasData[1]);
        Log.e(TAG,"NO2 value"+gasData[2]);
        return document;
    }

    //////////////////End Couchbase Code//////////////


    private double getDistance(double lat1,double lon1,double lat2,double lon2){
        Location loc1 = new Location("");
        loc1.setLatitude(lat1);
        loc1.setLongitude(lon1);

        Location loc2 = new Location("");
        loc2.setLatitude(lat2);
        loc2.setLongitude(lon2);

        double distanceInMeters = loc1.distanceTo(loc2);

        return distanceInMeters;
    }

    private Address getAddressOfCity(String city){
        String location = city;
        Geocoder gc = new Geocoder(this);

        Address address = null;

        try {
            List<Address> addresses= gc.getFromLocationName(location, 1);
            address = addresses.get(0);
        } catch (IOException e) {
            Log.e(TAG, "Error in city coordinates.");
        }

        return address;
    }

    private String getNearestCity(double lat,double lng){

        Log.d(TAG,"sssssssssssssssssssss");

        Geocoder gcd = new Geocoder(getApplicationContext(), Locale.getDefault());
        List<Address> addresses = null;
        try {
            addresses = gcd.getFromLocation(lat, lng, 1);
        } catch (IOException e) {
            Log.e(TAG,"Error");
        }
        if (addresses!=null && addresses.size() > 0) {
            Log.d(TAG, "sssssssssssssssssssss  " + addresses.get(0).getLocality());
            return addresses.get(0).getLocality();
        }
        else{
            return null;
        }
    }

    private boolean refreshDeviceList() {

        Log.d(TAG, "AAAAAAOnServiceRefreshingDeviceList");

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
        //resumeDeviceSleep();
        return false;

    }

    static boolean access_granted = false;
    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
//                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                        access_granted = true;
                    } else {
//                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "detacheddddddddddddddddddddddddddddddddddddddddddd");
                synchronized (this) {
                    if (!runningThreadSearch) {
                        started = false;
                        access_granted = false;
                        searchDevices = new Thread() {
                            @Override
                            public void run() {
                                Log.d(TAG, "Service Started on Command");
                                runningThreadSearch = true;
                                while (!started) {
                                    refreshDeviceList();
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                                runningThreadSearch = false;
                            }
                        };
                        searchDevices.start();
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "attacheddddddddddddddddddddddddddddddddddddddddddd");
            }
        }
    };

    final BroadcastReceiver mNetworkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "1NETWORK CHANGE");
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (null != activeNetwork) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.e(TAG, "1NETWORK WiFI");
                    if (syncApp == 2) {
                        if (!startedSyncService) {
                            startSync(true);
                        }
                    }
                }

                if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    Log.e(TAG, "1NETWORK MOBILE");
                    if (syncApp == 1) {
                        if (!startedSyncService) {
                            startSync(true);
                        }
                    } else if (syncApp == 2) {
                        if (startedSyncService) {
                            startSync(false);
                        }
                    }
                }
            }

        }
    };

    protected synchronized void startConsole() {
        if (sPort == null) {
            Log.e(TAG, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
//            UsbManager.ACTION_USB_ACCESSORY_ATTACHED
            PendingIntent mPermissionIntent;
            mPermissionIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent("com.android.example.USB_PERMISSION"), 0);
            usbManager.requestPermission(sPort.getDriver().getDevice(), mPermissionIntent);
            Log.d(TAG, "CCCCCCCCCGranted");
            while (!access_granted) ;
            Log.d(TAG, "BBBBBBBGranted");

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());

            if (connection == null) {
                Log.e(TAG, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
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

        String[] gasValues = new String[3];

        Log.e(TAG, "REC" + data);
        if (data.contains("*")) {

            if (data.length() == 1) {
                gasValues = splitData(buffer);
                buffer = "";
                return gasValues;
            } else if (data.charAt(0) == '*') {
                gasValues = splitData(buffer);
                buffer = data.substring(data.indexOf("*") + 1);
                return gasValues;
            } else if (data.charAt(data.length() - 1) == '*') {
                buffer += data.substring(0, data.indexOf("*"));
                gasValues = splitData(buffer);
                buffer = "";
                return gasValues;
            } else {
                buffer += data.substring(0, data.indexOf("*"));
                gasValues = splitData(buffer);
                buffer = data.substring(data.indexOf("*") + 1);
                return gasValues;
            }

        } else {
            buffer += data;
        }
        Log.e(TAG, "GOTVALUE" + gasValues[0] + ":" + gasValues[1]);
        return gasValues;
    }

    private String[] splitData(String data) {
        String[] gasses = data.split(";");
        String[] gasValues = new String[3];

        int i = 0;
        Log.d(TAG, "ALLDATA:" + data);
        try{
            for (String gas : gasses) {
                Log.d(TAG, "GAS_VALUE:" + gas);
                String[] gasData = gas.split(":");
                if (gasData.length == 3) {
                    gasValues[i] = gasData[2];

                }
                i += 1;
            }
        }catch (Exception e){
            Log.d(TAG, "ALLDATAEXCEPTION d:" + data.length());
            Log.d(TAG, "ALLDATAEXCEPTION gv:" + Arrays.toString(gasValues));
            Log.d(TAG, "ALLDATAEXCEPTION gs:" + gasses.toString());
            Log.d(TAG, "ALLDATAEXCEPTION:" + e.getMessage());
            buffer = new String("");

        }

        return gasValues;
    }





}
