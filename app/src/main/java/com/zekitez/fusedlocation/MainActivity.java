package com.zekitez.fusedlocation;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.zekitez.fusedlocation.databinding.ActivityMainBinding;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

/**
 * Retrieve current location using Google Play Services Location API
 * Based on Yukari Sakurai "https://github.com/sakurabird/Android-Fused-location-provider-example"
 * "https://developers.google.com/android/guides/setup"
 * "https://developer.android.com/training/location/request-updates"
 */

public class MainActivity extends AppCompatActivity {

    protected static final String TAG = "location-updates-sample";

    // Update location information every second. In reality, it may be slightly more frequent.
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;

    // Fastest update interval. It is not updated more frequently than this value.
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private final static String REQUESTING_LOCATION_UPDATES_KEY = "requesting-location-updates-key";
    private final static String LOCATION_KEY = "location-key";
    private final static String LAST_UPDATED_TIME_STRING_KEY = "last-updated-time-string-key";

    private static final int PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    private static final int REQUEST_CHECK_SETTINGS = 10;
    private static final int REQUEST_CHECK_GOOGLE_SETTINGS = 500;

    private ActivityMainBinding binding;

    private Location currentLocation;
    private Boolean requestingLocationUpdates = false;
    private String lastUpdateTime;
    private String latitudeLabel;
    private String longitudeLabel;
    private String accuracyLabel;
    private String addressLabel;
    private String lastUpdateTimeLabel;
    private boolean isPhoneOnline = false;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private LocationRequest locationRequest;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Log.d(TAG, "onLocationResult : " + (locationResult == null ? 0 : locationResult.getLocations().size()));
            if (locationResult == null) {
                return;
            }
            for (Location location : locationResult.getLocations()) {
                // lastUpdateTime = DateFormat.getTimeInstance().format(location.getTime());
                lastUpdateTime = sdf.format(location.getTime());

                // Update UI with location data
                updateUI(location);
            }
        }
    };

    private AtomicBoolean downloadingAddress = new AtomicBoolean(false);
    private ConnectivityManager connectivityManager = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        latitudeLabel = getResources().getString(R.string.latitude_label);
        longitudeLabel = getResources().getString(R.string.longitude_label);
        accuracyLabel = getResources().getString(R.string.accuracy_label);
        addressLabel = getResources().getString(R.string.address_label);
        lastUpdateTimeLabel = getResources().getString(R.string.last_update_time_label);
        lastUpdateTime = "";
        isPhoneOnline = isPhoneOnline();
        createLocationRequest();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        updateValuesFromBundle(savedInstanceState);
    }

    private void updateValuesFromBundle(Bundle savedInstanceState) {
        Log.i(TAG, "Updating values from bundle");
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY)) {
                requestingLocationUpdates = savedInstanceState.getBoolean(REQUESTING_LOCATION_UPDATES_KEY);
                setButtonsEnabledState();
            }
            if (savedInstanceState.keySet().contains(LOCATION_KEY)) {
                currentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY)) {
                lastUpdateTime = savedInstanceState.getString(LAST_UPDATED_TIME_STRING_KEY);
            }
            updateUI(currentLocation);
        }
    }

    public void startUpdatesButtonHandler(View view) {
        clearUI();
        if (!isPlayServicesAvailable(this)) return;
        if (!requestingLocationUpdates) {
            requestingLocationUpdates = true;
        } else {
            return;
        }

        if (Build.VERSION.SDK_INT < 23) {
            setButtonsEnabledState();
            startLocationUpdates();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            setButtonsEnabledState();
            startLocationUpdates();
        } else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                showRationaleDialog();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        }
    }

    public void stopUpdatesButtonHandler(View view) {
        if (requestingLocationUpdates) {
            requestingLocationUpdates = false;
            setButtonsEnabledState();
            stopLocationUpdates();
        }
    }

    protected void createLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_IN_MILLISECONDS)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(UPDATE_INTERVAL_IN_MILLISECONDS)
                .setMaxUpdateDelayMillis(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
                .build();

        // From https://gist.github.com/kedarmp/26b5697f257d5d0d9f8f2cefe9944ddc
        //This checks whether the GPS mode (high accuracy,battery saving, device only) is set appropriately for "locationRequest". If the current settings cannot fulfil
        //the request(the Google Fused Location Provider determines these automatically), then we listen for failures and show a dialog box for the user to easily
        //change these settings.
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());
        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    // Location settings are not satisfied, but this can be fixed by showing the user a dialog.
                    Log.e(TAG,e.toString());
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MainActivity.this, REQUEST_CHECK_GOOGLE_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });

    }

    private void startLocationUpdates() {
        Log.i(TAG, "startLocationUpdates");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest,
                locationCallback,
                Looper.getMainLooper());
    }

    private void stopLocationUpdates() {
        Log.i(TAG, "stopLocationUpdates");
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void setButtonsEnabledState() {
        if (requestingLocationUpdates) {
            binding.startUpdatesButton.setEnabled(false);
            binding.stopUpdatesButton.setEnabled(true);
        } else {
            binding.startUpdatesButton.setEnabled(true);
            binding.stopUpdatesButton.setEnabled(false);
        }
    }

    private void clearUI() {
        binding.latitudeText.setText("");
        binding.longitudeText.setText("");
        binding.accuracyText.setText("");
        binding.addressText.setText("");
        binding.lastUpdateTimeText.setText("");
    }

    private void updateUI(Location currentLocation) {
        if (currentLocation == null) {
            return;
        }
        binding.latitudeText.setText(String.format("%s: %f", latitudeLabel, currentLocation.getLatitude()));
        binding.longitudeText.setText(String.format("%s: %f", longitudeLabel, currentLocation.getLongitude()));
        downloadAddress(currentLocation);
        binding.accuracyText.setText(String.format("%s: %.2f", accuracyLabel,
                (currentLocation.hasAccuracy() ? currentLocation.getAccuracy() : 999999.99)));
        binding.lastUpdateTimeText.setText(String.format("%s: %s", lastUpdateTimeLabel, lastUpdateTime));
    }


    private void downloadAddress(Location currentLocation) {
        if (downloadingAddress.get() || !isPhoneOnline) return;
        downloadingAddress.set(true);
        // Only when connected with the internet and
        new Thread(new Runnable() {
            boolean resultOk = false;
            String txt = getResources().getString(R.string.address_label);

            @Override
            public void run() {
                List<Address> addresses;
                try {
                    Geocoder gc = new Geocoder(getApplicationContext(), Locale.getDefault());
                    addresses = gc.getFromLocation(currentLocation.getLatitude(), currentLocation.getLongitude(), 1);
                    if (addresses != null && addresses.size() > 0) {
                        Address adres = addresses.get(0);
                        int i = adres.getMaxAddressLineIndex();
                        txt = adres.getAddressLine(0);
                        if (i > 0) {
                            int j = 1;
                            while (j <= i) {
                                txt = txt.concat("," + adres.getAddressLine(j));
                                j++;
                            }
                        }
                        resultOk = true;
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                runOnUiThread(() -> {
                    if (!resultOk) {
                        binding.addressText.setText(addressLabel + ", no internet connection...");
                        binding.addressText.setBackgroundColor(Color.RED);
                    } else {
                        binding.addressText.setText(txt);
                        binding.addressText.setBackgroundColor(Color.WHITE);
                    }
                });
                downloadingAddress.set(false);
            }
        }).start();
    }


    public boolean isPhoneOnline() { // do we have an internet connection ?
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnected());
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setButtonsEnabledState();
                } else {
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                        requestingLocationUpdates = false;
                        Toast.makeText(MainActivity.this, "To enable the function of this app, please enable the location information permission of the app from the terminal setting screen.", Toast.LENGTH_SHORT).show();
                    } else {
                        showRationaleDialog();
                    }
                }
                break;
            }
        }
    }

    private void showRationaleDialog() {
        new AlertDialog.Builder(this)
                .setPositiveButton("to approve", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
                    }
                })
                .setNegativeButton("do not", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "Geolocation permission was not granted.", Toast.LENGTH_SHORT).show();
                        requestingLocationUpdates = false;
                    }
                })
                .setCancelable(false)
                .setMessage("This app needs to allow the use of location information.")
                .show();
    }

    public static boolean isPlayServicesAvailable(Context context) {
        // Google Play Service APK, check if is valid
        int resultCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
            GoogleApiAvailability.getInstance().getErrorDialog((Activity) context, resultCode, 2).show();
            return false;
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG,"onActivityResult request:" + requestCode + " result:" + resultCode);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                }
                break;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isPlayServicesAvailable(this)) return;
        if (requestingLocationUpdates) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, requestingLocationUpdates);
        savedInstanceState.putParcelable(LOCATION_KEY, currentLocation);
        savedInstanceState.putString(LAST_UPDATED_TIME_STRING_KEY, lastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }
}

