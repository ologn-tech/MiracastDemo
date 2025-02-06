package com.ologn.miracast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.hardware.display.DisplayManager;
//import android.hardware.display.WifiDisplay;
//import android.hardware.display.WifiDisplayStatus;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;

import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;

import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.content.pm.PackageManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MiracastDemo";

    private static final int CHANGE_SETTINGS = 1 << 0;
    private static final int CHANGE_ROUTES = 1 << 1;
    private static final int CHANGE_WIFI_DISPLAY_STATUS = 1 << 2;
    private static final int CHANGE_ALL = -1;

    private static final int ROUTE_TYPE_REMOTE_DISPLAY = 1 << 2;

    private final Handler mHandler;

    private MediaRouter mRouter;
    private DisplayManager mDisplayManager;

    private boolean mStarted;
    private int mPendingChanges;

    private boolean mWifiDisplayOnSetting;
    private WifiP2pManager mWifiP2pManager;
    private Channel mWifiP2pChannel;

    ListView mDeviceListView;
    TextView mEmptyText;

    private final List<String> deviceList = new ArrayList<>();

    public MainActivity() {
        mHandler = new Handler();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!displayIsAvailable())
            Log.d(TAG,"Device is not supported mirror screen");

        mRouter = (MediaRouter)getSystemService(Context.MEDIA_ROUTER_SERVICE);
        mDisplayManager = (DisplayManager)getSystemService(Context.DISPLAY_SERVICE);
        mWifiP2pManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        mWifiP2pChannel = mWifiP2pManager.initialize(this, getMainLooper(), null);

        mDeviceListView = findViewById(R.id.device_list);
        mEmptyText = findViewById(R.id.no_device_text);

    }
    public void enableWifiDisplay(Context context) {
        try {
            Settings.Global.putInt(context.getContentResolver(), "wifi_display_on", 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStart(){
        super.onStart();

        mStarted = true;
        IntentFilter filter = new IntentFilter();
        filter.addAction(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        registerReceiver(mReceiver, filter);

        mRouter.addCallback(ROUTE_TYPE_REMOTE_DISPLAY, mRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

        update(CHANGE_ALL);
    }
    @Override
    public void onResume(){
        super.onResume();
        update(CHANGE_ALL);
    }


    @Override
    public void onStop() {
        super.onStop();
        mStarted = false;

        unregisterReceiver(mReceiver);
        mRouter.removeCallback(mRouterCallback);

        unscheduleUpdate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id  = item.getItemId();
        if (id == R.id.atn_direct_discover){
            scheduleUpdate(CHANGE_ROUTES);
            try {
                startActivity(new Intent("android.settings.CAST_SETTINGS"));

            } catch (Exception exception1) {
                Toast.makeText(getApplicationContext(), "Device not supported", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public boolean displayIsAvailable() {
        return getSystemService(Context.DISPLAY_SERVICE) != null
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
                && getSystemService(Context.WIFI_P2P_SERVICE) != null;
    }

    private void scheduleUpdate(int changes) {
        if (mStarted) {
            if (mPendingChanges == 0) {
                mHandler.post(mUpdateRunnable);
            }
            mPendingChanges |= changes;
        }
    }

    private void unscheduleUpdate() {
        if (mPendingChanges != 0) {
            mPendingChanges = 0;
            mHandler.removeCallbacks(mUpdateRunnable);
        }
    }


    private void update(int changes) {
        boolean invalidateOptions = false;

        // Update settings.
        if ((changes & CHANGE_SETTINGS) != 0) {
//            mWifiDisplayOnSetting = Settings.Global.getInt(getContentResolver(),
//                    Settings.Global.WIFI_DISPLAY_ON, 0) != 0;
//            mWifiDisplayCertificationOn = Settings.Global.getInt(getContentResolver(),
//                    Settings.Global.WIFI_DISPLAY_CERTIFICATION_ON, 0) != 0;
//            mWpsConfig = Settings.Global.getInt(getContentResolver(),
//                    Settings.Global.WIFI_DISPLAY_WPS_CONFIG, WpsInfo.INVALID);

            // The wifi display enabled setting may have changed.
            invalidateOptions = true;
        }

        // Update wifi display state.
        if ((changes & CHANGE_WIFI_DISPLAY_STATUS) != 0) {
//             Display[] mDisplays =  mDisplayManager.getDisplays();

//            mWifiDisplayStatus = mDisplayManager.getWifiDisplayStatus();

            // The wifi display feature state may have changed.
            invalidateOptions = true;
        }

        // Rebuild the routes.

        deviceList.clear();
        MediaRouter.RouteInfo curentRouteInfo =  mRouter.getSelectedRoute(ROUTE_TYPE_REMOTE_DISPLAY);
        if (curentRouteInfo != null){
            Log.d(TAG,"Current route info null");
        }
        {
            Log.d(TAG,"Current route info:" + curentRouteInfo.toString());
        }
        // Add all known remote display routes.

        final int routeCount = mRouter.getRouteCount();
        for (int i = 0; i < routeCount; i++) {
            MediaRouter.RouteInfo route = mRouter.getRouteAt(i);
            Log.d(TAG,"Route "+i+": "+ route.toString());
            if ((route.getSupportedTypes() & ROUTE_TYPE_REMOTE_DISPLAY) != 0  ) {
                String statusConnect = "";
                if (route.getName() == curentRouteInfo.getName()) {
                    if (route.isConnecting())
                        statusConnect = "Connecting";
                    else
                        statusConnect = "Connected";
                }
                else{
                    statusConnect = "Available";
                }
                deviceList.add(route.getName().toString() + ": " + statusConnect +"\n" + route.getDeviceType() + "    " + route.getSupportedTypes());
            }
        }
        if (routeCount>1){
            mEmptyText.setVisibility(View.INVISIBLE);



        }else{
            mEmptyText.setVisibility(View.VISIBLE);
        }
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list, R.id.textView, deviceList);
        mDeviceListView.setAdapter(arrayAdapter);

        Display[] mDisplays =  mDisplayManager.getDisplays();
        for (int i =0; i< mDisplays.length; i++){
            Log.d(TAG, "Display "+i+": "+ mDisplays[i]);
//            mRouter.
        }

        // Additional features for wifi display routes.
//        if (mWifiDisplayStatus != null
//                && mWifiDisplayStatus.getFeatureState() == WifiDisplayStatus.FEATURE_STATE_ON) {
            // Add all unpaired wifi displays.
//            for (WifiDisplay display : mWifiDisplayStatus.getDisplays()) {
//                if (!display.isRemembered() && display.isAvailable()
//                        && !display.equals(mWifiDisplayStatus.getActiveDisplay())) {
//                    preferenceScreen.addPreference(new UnpairedWifiDisplayPreference(
//                            getPrefContext(), display));
//                }
//            }
//
//            // Add the certification menu if enabled in developer options.
//            if (mWifiDisplayCertificationOn) {
//                buildCertificationMenu(preferenceScreen);
//            }
//        }

        // Invalidate menu options if needed.
//        if (invalidateOptions) {
//           invalidateOptionsMenu();
//        }
    }

//    private WifiDisplay findWifiDisplay(String deviceAddress) {
//        if (mWifiDisplayStatus != null && deviceAddress != null) {
//            for (WifiDisplay display : mWifiDisplayStatus.getDisplays()) {
//                if (display.getDeviceAddress().equals(deviceAddress)) {
//                    return display;
//                }
//            }
//        }
//        return null;
//    }


    private final MediaRouter.Callback mRouterCallback = new MediaRouter.SimpleCallback() {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteAdded"+info.toString());

        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteChanged"+info.toString());
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteRemoved"+info.toString());
        }

        @Override
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteSelected"+info.toString());
        }

        @Override
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteUnselected"+info.toString());
        }
    };

    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            final int changes = mPendingChanges;
            mPendingChanges = 0;
            update(changes);
        }
    };
    private void toggleRoute(MediaRouter.RouteInfo route) {
        mRouter.selectRoute(route.getSupportedTypes(),route);

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG,"BroadcastReceiver: "+action.toString());

//            if (action.equals(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED)) {
//                scheduleUpdate(CHANGE_WIFI_DISPLAY_STATUS);
//            }
        }
    };
}