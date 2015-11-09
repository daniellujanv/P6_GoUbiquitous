/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextPaint;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService{
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    public static final String UPDATE_WEARABLE_PLEASE = "update_me";
    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private GoogleApiClient mGoogleApiClient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine
            implements GoogleApiClient.ConnectionCallbacks, MessageApi.MessageListener
            , GoogleApiClient.OnConnectionFailedListener {
        Paint mBackgroundPaintInteractive;
        Paint mBackgroundPaintAmbient;
        Paint mHandPaint;
        boolean mAmbient;
        Time mTime;
        Bitmap mClearBitmap;
        Bitmap mClearBitmapIcon;
        Calendar mCalendar;

        private float mCenterX;
        private float mCenterY;

        TextPaint mTextHourPaint;
        TextPaint mTextDatePaint;
        TextPaint mTextTempPaint;

        private float mTextHourSize;
        private float mTextDateSize;
        private float mTextTempSize;

        private float mTextHourY;
        private float mTextHourX;
        private float mTextDateX;
        private float mTextDateY;
        private float mTextTempX;
        private float mTextTempY;

        String mDate = "---";
        String mHighTemp = "-";
        String mLowTemp = "-";
        int mWeatherId = 0;

        float mScale = 1;

        private GoogleApiClient mGoogleApiClient;
        public static final String VOICE_TRANSCRIPTION_MESSAGE_PATH = "/weather_update";

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setHotwordIndicatorGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaintInteractive = new Paint();
            mBackgroundPaintInteractive.setColor(resources.getColor(R.color.primary));

            mTextHourPaint = new TextPaint();
            mTextHourPaint.setTextSize(50);
            mTextHourPaint.setColor(resources.getColor(R.color.analog_white));
            mTextHourPaint.setTextAlign(Paint.Align.CENTER);

            mTextDatePaint = new TextPaint();
            mTextDatePaint.setTextSize(50);
            mTextDatePaint.setColor(resources.getColor(R.color.analog_white));
            mTextDatePaint.setTextAlign(Paint.Align.CENTER);

            mTextTempPaint = new TextPaint();
            mTextTempPaint.setTextSize(50);
            mTextTempPaint.setColor(resources.getColor(R.color.analog_white));
            mTextTempPaint.setTextAlign(Paint.Align.CENTER);

            mBackgroundPaintAmbient = new Paint();
            mBackgroundPaintAmbient.setColor(resources.getColor(R.color.analog_background));

            mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clear);
            mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_cloudy);

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();

            mCalendar = Calendar.getInstance();
            mCalendar.setTimeZone(TimeZone.getDefault());

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mGoogleApiClient.connect();

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
//            mTime.setToNow();
            mCalendar.setTimeInMillis(System.currentTimeMillis());
            mDate = mCalendar.toString();

            mDate = mCalendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                    +", "+ mCalendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
                    +" "+mCalendar.get(Calendar.DAY_OF_MONTH)
                    +" "+mCalendar.get(Calendar.YEAR)
            ;
            String hour = Integer.toString(mCalendar.get(Calendar.HOUR_OF_DAY))+":";
            String minutes = String.format("%02d", mCalendar.get(Calendar.MINUTE));
            float hours_halfwidth = mTextHourPaint.measureText(hour)/2f;
            float temp_halfwidth = mTextTempPaint.measureText(mHighTemp)/2;


            if (!mAmbient) {
                // Draw the background.
                /**
                 * Interactive Mode
                 */

                /***** BACKGROUND *****/
                canvas.drawColor(mBackgroundPaintInteractive.getColor());
                canvas.drawBitmap(mClearBitmap, mCenterX - mClearBitmap.getWidth() / 2
                        , mCenterY - mClearBitmap.getHeight() / 2, mHandPaint);

                canvas.drawColor(getResources().getColor(R.color.scrim));
                /***** HOURS ********/
                mTextHourPaint.setFakeBoldText(true);
                mTextHourPaint.setTypeface(Typeface.DEFAULT_BOLD);

                /***** DATE ********/
                canvas.drawText(hour, mTextHourX - hours_halfwidth, mTextHourY, mTextHourPaint);
                mTextHourPaint.setFakeBoldText(false);
                mTextHourPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(minutes, mTextHourX + hours_halfwidth, mTextHourY, mTextHourPaint);

                canvas.drawText(mDate, mTextDateX, mTextDateY, mTextDatePaint);

                /***** TEMP ********/
                mTextTempPaint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(mHighTemp, mTextTempX - temp_halfwidth, mTextTempY, mTextTempPaint);
                mTextTempPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(mLowTemp, mTextTempX+temp_halfwidth, mTextTempY, mTextTempPaint);
//
//                float secX = (float) Math.sin(secRot) * secLength;
//                float secY = (float) -Math.cos(secRot) * secLength;

//                canvas.drawLine(mCenterX, mCenterY, mCenterX + secX, mCenterY + secY, mHandPaint);

            } else {
                // Draw the background.
                /**
                 * Ambient Mode
                 */
                canvas.drawColor(mBackgroundPaintAmbient.getColor());
                /***** HOURS ********/
                mTextHourPaint.setFakeBoldText(true);
                mTextHourPaint.setTypeface(Typeface.DEFAULT_BOLD);

                canvas.drawText(hour, mTextHourX - hours_halfwidth, mTextHourY, mTextHourPaint);
                mTextHourPaint.setFakeBoldText(false);
                mTextHourPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(minutes, mTextHourX + hours_halfwidth, mTextHourY, mTextHourPaint);

                /***** DATE ********/
                canvas.drawText(mDate, mTextDateX, mTextDateY, mTextDatePaint);

                /***** TEMP ********/
                mTextTempPaint.setTypeface(Typeface.DEFAULT_BOLD);
                canvas.drawText(mHighTemp, mTextTempX - temp_halfwidth, mTextTempY, mTextTempPaint);
                mTextTempPaint.setTypeface(Typeface.DEFAULT);
                canvas.drawText(mLowTemp, mTextTempX+temp_halfwidth, mTextTempY, mTextTempPaint);

//                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaintAmbient);
                canvas.drawBitmap(mClearBitmapIcon, mCenterX - mClearBitmapIcon.getWidth()/2
                        , mTextTempY+temp_halfwidth, mHandPaint);
            }

//            float minX = (float) Math.sin(minRot) * minLength;
//            float minY = (float) -Math.cos(minRot) * minLength;
//            canvas.drawLine(mCenterX, mCenterY, mCenterX + minX, mCenterY + minY, mHandPaint);
//
//            float hrX = (float) Math.sin(hrRot) * hrLength;
//            float hrY = (float) -Math.cos(hrRot) * hrLength;
//            canvas.drawLine(mCenterX, mCenterY, mCenterX + hrX, mCenterY + hrY, mHandPaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

             /* Scale loaded background image (more efficient) if surface dimensions change. */
            mScale = ((float) width) / (float) mClearBitmap.getWidth();

            mClearBitmap = Bitmap.createScaledBitmap(mClearBitmap,
                    (int) (mClearBitmap.getWidth() * mScale),
                    (int) (mClearBitmap.getHeight() * mScale), true);


            mTextHourSize = height/6f;
            mTextHourX = mCenterX;
            mTextHourY = height/2.2f;

            mTextDateSize = height/14f;
            mTextDateX = mCenterX;
            mTextDateY = height/1.8f;

            mTextTempSize = height/12f;
            mTextTempX = mCenterX;
            mTextTempY = height/1.5f;

            mTextHourPaint.setTextSize(mTextHourSize);
            mTextDatePaint.setTextSize(mTextDateSize);
            mTextTempPaint.setTextSize(mTextTempSize);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        /****** CONNECTION *******/
        private class SendMessageDevice extends AsyncTask<String, Void, Void> {

            @Override
            protected Void doInBackground(String... params) {
                int waits = 0;
                while(!mGoogleApiClient.isConnected() && waits < 5){
                    try {
                        Thread.sleep(1000);
                    }catch (InterruptedException e){
                        e.printStackTrace();
                    }
                    waits++;
                }

                String message = params[0];
                NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
                Log.i("Wearable", "connected nodes :: "+nodes.getNodes().size());
                for(Node node : nodes.getNodes()) {
                    Log.i("Wearable", "sending message ....");

                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, node.getId()
                            , VOICE_TRANSCRIPTION_MESSAGE_PATH, message.getBytes())
                            .setResultCallback(
                                    new ResultCallback<MessageApi.SendMessageResult>() {
                                        @Override
                                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                            Log.i("Wearable", "message sent ....... result success :: "
                                                    + sendMessageResult.getStatus().isSuccess());
                                        }
                                    }
                            );
                }
                return null;
            }
        }

        @Override
        public void onConnected(Bundle bundle) {
            Wearable.MessageApi.addListener( mGoogleApiClient, this);
            new SendMessageDevice().execute(UPDATE_WEARABLE_PLEASE);
//            Wearable.MessageApi.addListener(mGoogleApiClient, new ListenerService());
            Log.i("Wearable", "connected GoogleApiClient .. registered listener");
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.i("SunshineWear", "connection suspended");
        }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            if (messageEvent.getPath().equalsIgnoreCase(VOICE_TRANSCRIPTION_MESSAGE_PATH)) {

                String[] message = new String(messageEvent.getData()).split("::");
                if(message.length != 0){
                    mWeatherId = Integer.parseInt(message[0]);
                    setArtResourceForWeatherCondition(mWeatherId, mAmbient);
                    mHighTemp = message[1]+"º|";
                    mLowTemp = message[2]+"º";
                }
                Log.i("SunshineWear", "WUW :: " + new String(messageEvent.getData()));
            }
        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            Log.i("SunshineWear", "connection failed :: " + connectionResult.toString());
        }

        /**** weather ******/

        /**
         * copied from sunshine app
         * Helper method to provide the art resource id according to the weather condition id returned
         * by the OpenWeatherMap call.
         * @param weatherId from OpenWeatherMap API response
         * @return resource id for the corresponding icon. -1 if no relation is found.
         */
        public void setArtResourceForWeatherCondition(int weatherId, boolean ambient) {
            // Based on weather code data found at:
            // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
            Resources resources = getResources();

            if (weatherId >= 200 && weatherId <= 232) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_storm);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_storm);
//                return ambient? R.drawable.ic_storm : R.drawable.art_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {

                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_light_rain);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_light_rain);

//                return ambient? R.drawable.ic_light_rain : R.drawable.art_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_rain);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_rain);
//                return ambient? R.drawable.ic_rain : R.drawable.art_rain;
            } else if (weatherId == 511) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_snow);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_snow);
//                return ambient? R.drawable.ic_snow : R.drawable.art_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_rain);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_rain);
//                return ambient? R.drawable.ic_rain : R.drawable.art_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_snow);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_snow);
//                return ambient? R.drawable.ic_snow :R.drawable.art_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_fog);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_fog);
//                return ambient? R.drawable.ic_fog :R.drawable.art_fog;
            } else if (weatherId == 761 || weatherId == 781) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_storm);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_storm);
//                return ambient? R.drawable.ic_storm :R.drawable.art_storm;
            } else if (weatherId == 800) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clear);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_clear);
//                return ambient? R.drawable.ic_clear :R.drawable.art_clear;
            } else if (weatherId == 801) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_light_clouds);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_light_clouds);
//                return ambient? R.drawable.ic_light_clouds :R.drawable.art_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clouds);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_cloudy);
//                return ambient? R.drawable.ic_cloudy :R.drawable.art_clouds;
            } else {
                mClearBitmap = BitmapFactory.decodeResource(resources, R.drawable.art_clear);
                mClearBitmapIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_clear);
            }
            mClearBitmap = Bitmap.createScaledBitmap(mClearBitmap,
                    (int) (mClearBitmap.getWidth() * mScale),
                    (int) (mClearBitmap.getHeight() * mScale), true);
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
