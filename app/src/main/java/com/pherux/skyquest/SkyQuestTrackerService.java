package com.pherux.skyquest;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.pherux.skyquest.managers.Persistence;
import com.pherux.skyquest.utils.Tracker;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import static com.pherux.skyquest.Constants.INITIAL_DELAY_SECONDS;
import static com.pherux.skyquest.Constants.INTERVAL_SMS_SECONDS;
import static com.pherux.skyquest.Constants.INTERVAL_TRACKER_SECONDS;

/**
 * Created by Fernando Valdez on 8/18/15
 */
public class SkyQuestTrackerService extends Service {
    private static final String TAG = SkyQuestTrackerService.class.getName();
    PowerManager.WakeLock wakeLock = null;
    LocationManager locationManager = null;
    Timer smsTimer = null;
    Timer webTrackerTimer = null;
    NMEALogListener nmeaListener = null;
    NoopLocationListener noopListener = null;

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onCreate() {
        noopListener = new NoopLocationListener();
        nmeaListener = new NMEALogListener();
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Starting Service");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SkyQuestTrackerServiceLock");
        wakeLock.acquire();
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    150, 0, noopListener);
            locationManager.addNmeaListener(nmeaListener);
        } catch (SecurityException e) {
            Log.d(TAG, "SecurityException: " + e.getMessage());
        }
        if (App.useSMSTracker()) {
            smsTimer = new Timer();
            smsTimer.schedule(new SMSTimerTask(), INITIAL_DELAY_SECONDS * 1000, INTERVAL_SMS_SECONDS * 1000);
        }
        if (App.useWebTracker()) {
            webTrackerTimer = new Timer();
            webTrackerTimer.schedule(new TrackerTimerTask(), INITIAL_DELAY_SECONDS * 1000, INTERVAL_TRACKER_SECONDS * 1000);
        }
        return START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Stopping Service");
        if (App.useSMSTracker()) {
            smsTimer.cancel();
            smsTimer.purge();
        }
        if (App.useWebTracker()) {
            webTrackerTimer.cancel();
            webTrackerTimer.purge();
        }
        try {
            locationManager.removeUpdates(noopListener);
            locationManager.removeNmeaListener(nmeaListener);
        } catch (SecurityException e) {
            Log.d(TAG, "SecurityException: " + e.getMessage());
        }
        wakeLock.release();
        super.onDestroy();
    }

    public class SMSTimerTask extends TimerTask {

        @Override
        public void run() {
            Log.d(TAG, "SMSTimerTask begin run");
            Tracker.sendLocationSMS();
        }
    }

    public class TrackerTimerTask extends TimerTask {

        @Override
        public void run() {
            Log.d(TAG, "TrackerTimerTask begin run");
            Tracker.sendTrackerPing();
        }

    }

    public class NMEALogListener implements GpsStatus.NmeaListener {
        @Override
        public void onNmeaReceived(long timestamp, String nmea) {
            Tracker.appendToFile(Tracker.nmeaLogFile, nmea);
        }
    }

    public class NoopLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
//            Log.d(TAG, "NoopLocationListener onLocationChanged");

            DecimalFormat df = new DecimalFormat("#.########");
            String latitude = df.format(location.getLatitude());
            String longitude = df.format(location.getLongitude());
            df = new DecimalFormat("#.#");
            String altitude = df.format(location.getAltitude());

            String gpsStatus = "GPS " + new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(location.getTime())) + " Pos: " + latitude + " / " + longitude + " / " + altitude;
            Persistence.putStringVal(Tracker.gpsStatusKey, gpsStatus);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }
}
