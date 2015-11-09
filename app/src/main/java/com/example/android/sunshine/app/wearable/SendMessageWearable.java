package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.example.android.sunshine.app.MainActivity;
import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by daniellujanvillarreal on 11/9/15.
 */
public class SendMessageWearable extends AsyncTask<Void, Void, Void> {

    private GoogleApiClient mGoogleApiClient;
    private Context mContext;
    private String LOG_TAG = "SendMessageWearableTask";
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    public SendMessageWearable(Context context, GoogleApiClient googleApiClient){
        mGoogleApiClient = googleApiClient;
        mContext = context;
    }

    @Override
    protected Void doInBackground(Void... params) {
        int waits = 0;
        while(!mGoogleApiClient.isConnected() && waits < 5){
            Log.i(LOG_TAG, "waiting for googleApiClient");
            try {
                Thread.sleep(1000);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            waits++;
        }

        if(waits < 5) {
            String message = getWeather();
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
            Log.i(LOG_TAG, "connected nodes :: " + nodes.getNodes().size());
            for (Node node : nodes.getNodes()) {
//            MessageApi.SendMessageResult result =
                Log.i(LOG_TAG, "sending message ....");

                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient, node.getId()
                        , MainActivity.VOICE_TRANSCRIPTION_MESSAGE_PATH, message.getBytes())
                        .setResultCallback(
                                new ResultCallback<MessageApi.SendMessageResult>() {
                                    @Override
                                    public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                        Log.i(LOG_TAG, "message sent ....... result success :: "
                                                + sendMessageResult.getStatus().isSuccess());
                                    }
                                }
                        );
            }
        }else{
            Log.d(LOG_TAG, "GoogleApiClient never connected... wearable not updated");
        }
        return null;
    }

    public String getWeather(){
        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(mContext);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = mContext.getContentResolver()
                .query(weatherForLocationUri, FORECAST_COLUMNS, null,
                        null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return null;
        }
        if (!data.moveToFirst()) {
            data.close();
            return null;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);

        String weather_data =
                weatherId+"::" +
                        Math.round(maxTemp)+"::"+Math.round(minTemp);
        return weather_data;
    }
}
