package com.ologn.miracast;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.appcompat.app.AppCompatActivity;

import android.hardware.display.DisplayManager;
import android.media.MediaRouter;
import android.media.MediaRouter.RouteInfo;

import android.provider.Settings;
import android.util.Log;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import android.content.pm.PackageManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;


import java.util.ArrayList;
import java.util.List;
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MiracastDemo";

    private static final int CHANGE_ROUTES = 1 << 1;
    private static final int CHANGE_ALL = -1;

    private static final int ROUTE_TYPE_REMOTE_DISPLAY = 1 << 2;

    private final Handler mHandler;

    private MediaRouter mRouter;

    private boolean mStarted;
    private int mPendingChanges;
    MediaRouter.RouteInfo curentRouteInfo;

    ListView mDeviceListView;
    TextView mEmptyText;

//    private boolean isConnectAgain = false;
    private String statusConnect;

    private final List<String> deviceList = new ArrayList<>();

    public MainActivity() {
        mHandler = new Handler();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isWifiEnabled()) {
            showExitDialog();
        }
        if (!displayIsAvailable())
            Log.d(TAG, "Device is not supported mirror screen");

        mRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);

        mDeviceListView = findViewById(R.id.device_list);
        mEmptyText = findViewById(R.id.no_device_text);

        mDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MediaRouter.RouteInfo mRouteSelected = mRouter.getRouteAt(position + 1);
                if (mRouteSelected.getName() != curentRouteInfo.getName()) {
                    mRouter.selectRoute(ROUTE_TYPE_REMOTE_DISPLAY, mRouteSelected);
                } else {
                    if (statusConnect.equals("Connected"))
                        showDisconnectDialog(mRouteSelected.getName().toString());
                }
            }
        });

    }

    @Override
    public void onStart(){
        super.onStart();

        if (isWifiEnabled()) {
            mStarted = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            registerReceiver(mReceiver, filter);

            mRouter.addCallback(ROUTE_TYPE_REMOTE_DISPLAY, mRouterCallback,
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

            update(CHANGE_ALL);
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        Log.d(TAG,"onResume");
        if (!mStarted){
            mStarted = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            registerReceiver(mReceiver, filter);

            mRouter.addCallback(ROUTE_TYPE_REMOTE_DISPLAY, mRouterCallback,
                    MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);

            update(CHANGE_ALL);
        }
//        isConnectAgain = true;
    }


    @Override
    public void onStop() {
        super.onStop();

        if (mStarted) {
            mStarted = false;

            unregisterReceiver(mReceiver);
            mRouter.removeCallback(mRouterCallback);

            unScheduleUpdate();
        }
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
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean displayIsAvailable() {
        return getSystemService(Context.DISPLAY_SERVICE) != null
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
                && getSystemService(Context.WIFI_P2P_SERVICE) != null;
    }

    private boolean isWifiEnabled() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.WIFI_ON,0) != 0 ;
    }

    private void scheduleUpdate(int changes) {
        if (mStarted) {
            if (mPendingChanges == 0) {
                mHandler.post(mUpdateRunnable);
            }
            mPendingChanges |= changes;
        }
    }

    private void unScheduleUpdate() {
        if (mPendingChanges != 0) {
            mPendingChanges = 0;
            mHandler.removeCallbacks(mUpdateRunnable);
        }
    }

    private void update(int changes) {

        // Rebuild the routes.
        deviceList.clear();
        curentRouteInfo =  mRouter.getSelectedRoute(ROUTE_TYPE_REMOTE_DISPLAY);
        if (curentRouteInfo != null){
            Log.d(TAG,"Current route info null\n");
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
                statusConnect = "";
                if (route.getName() == curentRouteInfo.getName()) {
                    if (route.isConnecting())
                        statusConnect = "Connecting";
                    else
                        statusConnect = "Connected";
                }
                else{
                    statusConnect = "Available";
                }
                deviceList.add(route.getName().toString() + ": " + statusConnect +"\n" + route.getDescription());
            }
        }
        if (routeCount>1){
            mEmptyText.setVisibility(View.INVISIBLE);
        }else{
            mEmptyText.setVisibility(View.VISIBLE);
        }
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(this, R.layout.device_list, R.id.textView, deviceList);
        mDeviceListView.setAdapter(arrayAdapter);

    }

    private final MediaRouter.Callback mRouterCallback = new MediaRouter.SimpleCallback() {
        @Override
        public void onRouteAdded(MediaRouter router, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteAdded "+info.toString());
//            if (curentRouteInfo.getName() != info.getName() && info.getName().toString().equals(getNameLastRouteConnected())){
//                Log.d(TAG,"Connect again\n" +getNameLastRouteConnected()+"\n"  + isConnectAgain);
//                if (isConnectAgain) {
//                    mRouter.selectRoute(ROUTE_TYPE_REMOTE_DISPLAY, info);
//                    isConnectAgain = false;
//                }
//            }
        }

        @Override
        public void onRouteChanged(MediaRouter router, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteChanged "+info.getName());

//            if (curentRouteInfo.getName() != info.getName() && info.getName().toString().equals(getNameLastRouteConnected())){
//                Log.d(TAG,"Connect again\n" +getNameLastRouteConnected()+"\n"  + isConnectAgain);
//                if (isConnectAgain) {
//                    mRouter.selectRoute(ROUTE_TYPE_REMOTE_DISPLAY, info);
//                    isConnectAgain = false;
//                }
//            }
        }

        @Override
        public void onRouteRemoved(MediaRouter router, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            Log.d(TAG,"onRouteRemoved"+info.toString());
        }

        @Override
        public void onRouteSelected(MediaRouter router, int type, RouteInfo info) {
            scheduleUpdate(CHANGE_ROUTES);
            putNameLastRouteConnected(info.getName().toString());
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

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG,"BroadcastReceiver: "+ action);
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)){
                if (!isWifiEnabled())
                    showExitDialog();
            }
        }
    };

    private void showDisconnectDialog(String name) {
        new AlertDialog.Builder(this)
                .setMessage("Are you sure you want to disconnect " +name +"?")
                .setPositiveButton("Disconnect", (dialog, which) -> {
                    mRouter.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_VIDEO,mRouter.getDefaultRoute());
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Wi-Fi Required")
                .setMessage("Wi-Fi is OFF. Please enable Wi-Fi.")
                .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish(); // Close the app
                    }
                })
                .setCancelable(false) // Prevent dismissing by tapping outside
                .show();
    }

    private String getNameLastRouteConnected(){
        SharedPreferences prefs = getSharedPreferences("miracast", MODE_PRIVATE);
        return prefs.getString("nameLastRouteConnected", "");
    }

    private void putNameLastRouteConnected(String name){
        SharedPreferences prefs = getSharedPreferences("miracast", MODE_PRIVATE);
        prefs.edit().putString("nameLastRouteConnected",name).apply();
    }

}

