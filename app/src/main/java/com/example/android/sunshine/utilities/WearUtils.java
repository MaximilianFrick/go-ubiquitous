package com.example.android.sunshine.utilities;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.data.SunshinePreferences;
import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;


public class WearUtils {
    public static final String[] WEATHER_WEAR_PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    public static final int INDEX_WEATHER_ID = 0;
    public static final int INDEX_MAX_TEMP = 1;
    public static final int INDEX_MIN_TEMP = 2;

    private GoogleApiClient googleApiClient;
    private Context context;

    public WearUtils(Context context) {
        this.context = context;
    }

    public void updateWeatherForWear() {
        Log.d("WearUtils", "updateWeatherForWear");
        googleApiClient = new GoogleApiClient.Builder(context).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                prepareData(context);
//                googleApiClient.disconnect();
            }

            @Override
            public void onConnectionSuspended(int i) {
                Log.d("WearUtils", "onConnectionSuspended: " + i);
            }
        }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                Log.d("WearUtils", "onConnectionFailed: " + connectionResult);
            }
        })
        .addApi(Wearable.API).build();
        googleApiClient.connect();


    }

    private void prepareData(Context context) {
    /* Build the URI for today's weather in order to show up to date data in notification */
        Uri todaysWeatherUri = WeatherContract.WeatherEntry
                .buildWeatherUriWithDate(SunshineDateUtils.normalizeDate(System.currentTimeMillis()));

        /*
         * The MAIN_FORECAST_PROJECTION array passed in as the second parameter is defined in our WeatherContract
         * class and is used to limit the columns returned in our cursor.
         */
        Cursor todayWeatherCursor = context.getContentResolver().query(
                todaysWeatherUri,
                WEATHER_WEAR_PROJECTION,
                null,
                null,
                null);

        /*
         * If todayWeatherCursor is empty, moveToFirst will return false. If our cursor is not
         * empty, we want to show the notification.
         */
        if (todayWeatherCursor.moveToFirst()) {

            /* Weather ID as returned by API, used to identify the icon to be used */
            int weatherId = todayWeatherCursor.getInt(INDEX_WEATHER_ID);
            double high = todayWeatherCursor.getDouble(INDEX_MAX_TEMP);
            double low = todayWeatherCursor.getDouble(INDEX_MIN_TEMP);

            Resources resources = context.getResources();
            int smallArtRessourceId = SunshineWeatherUtils
                    .getSmallArtResourceIdForWeatherCondition(weatherId);

            Bitmap smallIcon = BitmapFactory.decodeResource(
                    resources,
                    smallArtRessourceId);

            final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            smallIcon.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            Asset.createFromBytes(byteStream.toByteArray());
            Asset weatherIcon = Asset.createFromBytes(byteStream.toByteArray());

            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/WEATHER_DATA_PATH");
            putDataMapRequest.getDataMap().putAsset("WEATHER_DATA_ICON", weatherIcon);
            putDataMapRequest.getDataMap().putString("WEATHER_DATA_HIGH", SunshineWeatherUtils.formatTemperature(context, high));
            putDataMapRequest.getDataMap().putString("WEATHER_DATA_LOW", SunshineWeatherUtils.formatTemperature(context, low));
            putDataMapRequest.getDataMap()
                    .putLong("Time", System.currentTimeMillis());
            putDataMapRequest.setUrgent();
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            Wearable.DataApi.putDataItem(googleApiClient, putDataRequest);
        }

        /* Always close your cursor when you're done with it to avoid wasting resources. */
        todayWeatherCursor.close();
    }

}
