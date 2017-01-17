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

import com.example.android.sunshine.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;


public class WearUtils {
    private static final String[] WEATHER_WEAR_PROJECTION = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;
    private static GoogleApiClient googleApiClient;

    public static void updateWeatherForWear(final Context context) {
        googleApiClient = new GoogleApiClient.Builder(context).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                prepareData(context);
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

    private static void prepareData(Context context) {
        Uri todaysWeatherUri = WeatherContract.WeatherEntry
                .buildWeatherUriWithDate(SunshineDateUtils.normalizeDate(System.currentTimeMillis()));
        Cursor todayWeatherCursor = context.getContentResolver().query(
                todaysWeatherUri,
                WEATHER_WEAR_PROJECTION,
                null,
                null,
                null);

        if (todayWeatherCursor.moveToFirst()) {
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

        todayWeatherCursor.close();
    }
}
