package com.rusdelphi.batterywatchface;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

/**
 * Created by User on 04.01.2015.
 */
public class ListenerService extends WearableListenerService {

    GoogleApiClient googleClient;
    public static final String ACTION_SM = "com.rusdelphi.batterywatchface.action.SM";
    public static final String ACTION_SM_PARAM = "com.rusdelphi.batterywatchface.action.SM.PARAM";
    public static final String WEAR_MESSAGE_PATH = "batterywatchface_message_path";



    @Override
    public void onCreate() {
        super.onCreate();
        googleClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleClient.connect();
    }

    @Override
    public void onDestroy() {
        if (null != googleClient && googleClient.isConnected()) {
            googleClient.disconnect();
        }
        super.onDestroy();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SM.equals(action)) {
                final String param1 = intent.getStringExtra(ACTION_SM_PARAM);
                if (googleClient.isConnected()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleClient).await();
                            for (Node node : nodes.getNodes()) {
                                Wearable.MessageApi.sendMessage(googleClient, node.getId(), WEAR_MESSAGE_PATH, param1.getBytes()).await();
                            }
                        }
                    }).start();

                }
                if (!googleClient.isConnected()) new Thread(new Runnable() {
                    @Override
                    public void run() {
                        googleClient.blockingConnect(30, TimeUnit.SECONDS);
                        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleClient).await();
                        for (Node node : nodes.getNodes()) {
                            Wearable.MessageApi.sendMessage(googleClient, node.getId(), WEAR_MESSAGE_PATH, param1.getBytes()).await();
                        }

                    }
                }).start();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public static String getBatteryLevel(Context c) {
        Intent batteryIntent = c.registerReceiver(null, new IntentFilter(
                Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        // Error checking that probably isn't needed but I added just in case.
        if (level == -1 || scale == -1) {
            return String.valueOf(50.0f);
        }
        String aaa = new java.text.DecimalFormat("00")
                .format((((float) level / (float) scale) * 100.0f));
        aaa = aaa + "%";
        return aaa;
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {

        if (messageEvent.getPath().equals(WEAR_MESSAGE_PATH)) {
            final String message = new String(messageEvent.getData());
            if (message.equals("get_level")) {
                WatchFace.sendMessage(this, getBatteryLevel(this));
                return;
            }
            // Broadcast message to wearable activity for display
            //MainActivity.mSmartphoneLevel = message;
            Intent messageIntent = new Intent();
            messageIntent.setAction(Intent.ACTION_SEND);
            messageIntent.putExtra("message", message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(messageIntent);
        } else {
            super.onMessageReceived(messageEvent);
        }
    }
}
