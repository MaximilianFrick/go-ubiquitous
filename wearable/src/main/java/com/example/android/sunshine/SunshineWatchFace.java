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

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for active mode (non-ambient).
     */
    private static final long ACTIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(1);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final String COLON_STRING = ":";
    private static final String TAG = "SunshineWatchFace";

    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine implements GoogleApiClient.ConnectionCallbacks, DataApi.DataListener, GoogleApiClient.OnConnectionFailedListener {
        static final int MSG_UPDATE_TIME = 0;
        static final String WEATHER_DATA_PATH = "/WEATHER_DATA_PATH";
        static final String WEATHER_DATA_ICON = "WEATHER_DATA_ICON";
        static final String WEATHER_DATA_HIGH = "WEATHER_DATA_HIGH";
        static final String WEATHER_DATA_LOW = "WEATHER_DATA_LOW";

        Calendar calendar;
        private float mYOffset;
        private float mLineHeight;

        // device features
        boolean lowBitAmbient;
        boolean burnInProtection;

        // graphic objects
        Paint timePaint;
        Paint datePaint;
        Paint degreeMaxPaint;
        Paint degreeMinPaint;
        Paint dividerPaint;
        Bitmap weatherStatusBitmap = null;

        // handler to update the time once a second in interactive mode
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                    ACTIVE_INTERVAL_MS - (timeMs % ACTIVE_INTERVAL_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        // receiver to update the time zone
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /**
         * Unregistering an unregistered receiver throws an exception. Keep track of the
         * registration state to prevent that.
         */
        private boolean mRegisteredReceiver = false;

        private GoogleApiClient client = new GoogleApiClient.Builder(SunshineWatchFace.this).addApi(Wearable.API).addConnectionCallbacks(this).addOnConnectionFailedListener(this).build();
        private String mWeather = "no data";

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            /* initialize your watch face */
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            client.connect();

            Resources resources = getResources();
            mYOffset = resources.getDimension(R.dimen.fit_y_offset);
            mLineHeight = resources.getDimension(R.dimen.fit_line_height);

            timePaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            datePaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, R.color.text_color_70), NORMAL_TYPEFACE);
            dividerPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, R.color.text_color_70), NORMAL_TYPEFACE);
            degreeMaxPaint = createTextPaint(Color.WHITE, NORMAL_TYPEFACE);
            degreeMinPaint = createTextPaint(ContextCompat.getColor(SunshineWatchFace.this, R.color.text_color_70), NORMAL_TYPEFACE);
            calendar = Calendar.getInstance();
        }

        private Paint createTextPaint(int textColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* get device features (burn-in, low-bit ambient) */
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION,
                    false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            /* the time changed */
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            /* the wearable switched between modes */
            if (lowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                timePaint.setAntiAlias(antiAlias);
                degreeMaxPaint.setAntiAlias(antiAlias);
                degreeMinPaint.setAntiAlias(antiAlias);
                dividerPaint.setAntiAlias(antiAlias);
                datePaint.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            float textSize = resources.getDimension(isRound
                    ? R.dimen.fit_text_size_round : R.dimen.fit_text_size);

            timePaint.setTextSize(textSize);
            degreeMaxPaint.setTextSize(resources.getDimension(R.dimen.small_txt_size));
            degreeMinPaint.setTextSize(resources.getDimension(R.dimen.small_txt_size));
            dividerPaint.setTextSize(textSize);
            datePaint.setTextSize(resources.getDimension(R.dimen.small_txt_size));
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            /* draw your watch face */
            float y = mYOffset;
            int centerX = bounds.centerX();
            int midPos = (canvas.getWidth() / 2);

            canvas.drawColor(ContextCompat.getColor(SunshineWatchFace.this, R.color.background));
            calendar.setTimeInMillis(System.currentTimeMillis());

            String hourString = formatTwoDigitNumber(calendar.get(Calendar.HOUR_OF_DAY));
            String minuteString = formatTwoDigitNumber(calendar.get(Calendar.MINUTE));
            String time = hourString + COLON_STRING + minuteString;
            canvas.drawText(time, midPos - (timePaint.measureText(time) / 2), y, timePaint);
            y = y + mLineHeight;
            SimpleDateFormat dateFormat = new SimpleDateFormat("ccc, LLL dd yyyy");
            String date = dateFormat.format(calendar.getTime()).toUpperCase();
            canvas.drawText(date, midPos - (datePaint.measureText(date) / 2), y, datePaint);
            y = y + mLineHeight;
            canvas.drawLine(midPos - getResources().getDimension(R.dimen.divider_leg), y, midPos + getResources().getDimension(R.dimen.divider_leg), y, dividerPaint);
            y = y + mLineHeight;
            if (weatherStatusBitmap != null) {
                canvas.drawBitmap(weatherStatusBitmap, null,
                        new Rect(centerX - 60, (int) y -20, centerX-20, (int) y + 20), null);
                canvas.drawText(mWeather, midPos - 20 , (int) y + 10, degreeMaxPaint);
            } else {
                String noData = "Set Location in app";
                canvas.drawText(noData, midPos - (degreeMaxPaint.measureText(noData) / 2), y + 10, degreeMaxPaint);
            }
        }

        private String formatTwoDigitNumber(int number) {
            return String.format("%02d", number);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            /* the watch face became visible or invisible */
            if (visible && !mRegisteredReceiver) {
                mRegisteredReceiver = true;
                SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));
                calendar.setTimeZone(TimeZone.getDefault());
            } else if (!visible && mRegisteredReceiver) {
                mRegisteredReceiver = false;
                SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
            }
            // Whether the timer should be running depends on whether we're visible and
            // whether we're in ambient mode, so we may need to start or stop the timer
            updateTimer();
        }

        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "onConnected: " + bundle);
            // Now you can use the Data Layer API

            Wearable.DataApi.addListener(client, Engine.this);
            Wearable.MessageApi.sendMessage(client, "", "/messagePath", null)
                    .setResultCallback(new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult) {
                            if (sendMessageResult.getStatus()
                                    .isSuccess()) {
                                Log.d("Wear", "Message Sent!");
                            }
                        }
                    });
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "onConnectionSuspended: " + i);

        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(TAG, "onDataChanged");
            for (DataEvent event : dataEventBuffer) {
                DataItem item = event.getDataItem();
                if (WEATHER_DATA_PATH.equals(item.getUri().getPath())) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    String high = dataMap.getString(WEATHER_DATA_HIGH);
                    String low = dataMap.getString(WEATHER_DATA_LOW);
                    mWeather = high + "/" + low;

                    Asset asset = dataMap.getAsset(WEATHER_DATA_ICON);
                    loadBitmapFromAsset(asset);

                    invalidate();
                }
            }
        }

        private void loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            new AsyncTask<Asset, Void, Bitmap>() {
                @Override
                protected Bitmap doInBackground(Asset... assets) {
                    ConnectionResult result =
                            client.blockingConnect(5000, TimeUnit.MILLISECONDS);
                    if (!result.isSuccess()) {
                        return null;
                    }
                    InputStream assetInputStream =
                            Wearable.DataApi.getFdForAsset(client, assets[0])
                                    .await()
                                    .getInputStream();

                    if (assetInputStream == null) {
                        Log.w(TAG, "Requested an unknown Asset.");
                        return null;
                    }
                    return BitmapFactory.decodeStream(assetInputStream);
                }

                @Override
                protected void onPostExecute(Bitmap bitmap) {
                    super.onPostExecute(bitmap);
                    weatherStatusBitmap = bitmap;
                    invalidate();
                }
            }.execute(asset);

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "onConnectionFailed: " + connectionResult);
        }
    }
}

