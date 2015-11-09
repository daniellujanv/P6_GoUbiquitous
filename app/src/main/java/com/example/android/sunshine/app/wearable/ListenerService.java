package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

/**
 * Created by daniellujanvillarreal on 11/9/15.
 */
public class ListenerService extends WearableListenerService{

    GoogleApiClient googleApiClient;
    Context mContext;

    private String LOG_TAG = "ListenerService";

    @Override
    public void onCreate(){
        super.onCreate();
        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        googleApiClient.connect();
        mContext = getApplicationContext();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.i(LOG_TAG, "message received");
        new SendMessageWearable(mContext, googleApiClient).execute();

    }
}
