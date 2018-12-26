package com.example.rache.dailypath;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Location mCurrentLocation;
    private Location mLastLocation;
    private double latitude;
    private double longitude;
    private SQLiteDatabase dbW;
    private SQLiteDatabase dbR;
    private MarkerOptions markerOptions;
    private LatLng mCurrLatLng;
    private LatLng mLastLatLng;
    private String mTime;
    private String mName;
    private String full_address;
    private List<Address> addresses;
    private Geocoder geocoder;
    private String title;
    List<Marker> markerList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @SuppressLint("WrongConstant")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        dbW = openOrCreateDatabase("loc.db",MODE_APPEND,null);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);
        mMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                mMap.moveCamera(CameraUpdateFactory.zoomBy(3));
                return false;
            }
        });

        mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (mCurrentLocation == null) {
            // fall back to network if GPS is not available
            mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (mCurrentLocation != null) {
            //Toast.makeText(this, "Location Changed", Toast.LENGTH_SHORT).show();
            latitude = mCurrentLocation.getLatitude();
            longitude = mCurrentLocation.getLongitude();
        }

        Cursor cursor = dbW.query("location", null, null, null, null, null, null, null);
        while (cursor.moveToNext()){
            Marker marker =mMap.addMarker(new MarkerOptions().position(new LatLng(cursor.getDouble(2), cursor.getDouble(1))).title(cursor.getString(4)));
            markerList.add(marker);
            if (getDistance(latitude,longitude,cursor.getDouble(2),cursor.getDouble(1))<30){
                marker.showInfoWindow();
            }
        }

        mCurrLatLng = new LatLng(latitude, longitude);
        //mMap.addMarker(new MarkerOptions().position(mCurrLatLng).title("Current Location"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mCurrLatLng,15));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                markerOptions = new MarkerOptions();
                mLastLatLng = latLng;
                latitude = mLastLatLng.latitude;
                longitude = mLastLatLng.longitude;
                markerOptions.position(latLng);
                markerOptions.draggable(true);
                //mMap.addMarker(markerOptions.position(latLng).draggable(true));
                checkIn(MapsActivity.this);
            }
        });

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new android.location.LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateLocation();
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }
            @Override
            public void onProviderEnabled(String provider) {
            }
            @Override
            public void onProviderDisabled(String provider) {
            }
        });
    }

    private double getDistance(double lat1, double log1, double lat2, double log2) {
        double EARTH_RADIUS = 6378137.0;
        double radLat1 = (lat1 * Math.PI / 180.0);
        double radLat2 = (lat2 * Math.PI / 180.0);
        double a = radLat1 - radLat2;
        double b = (log1 - log2) * Math.PI / 180.0;
        double dis = 2 * Math.asin(Math.sqrt(Math.pow(Math.sin(a / 2), 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.pow(Math.sin(b / 2), 2)));
        dis = dis * EARTH_RADIUS;
        dis = Math.round(dis * 10000) / 10000;
        return dis;
    }

    public void updateLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (mCurrentLocation == null) {
            // fall back to network if GPS is not available
            mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (mCurrentLocation != null) {
            //Toast.makeText(this, "Location Changed", Toast.LENGTH_SHORT).show();
            latitude = mCurrentLocation.getLatitude();
            longitude = mCurrentLocation.getLongitude();

            for (int i=0;i<markerList.size();i++){

                if (getDistance(latitude, longitude, markerList.get(i).getPosition().latitude,markerList.get(i).getPosition().longitude ) < 30) {
                    markerList.get(i).showInfoWindow();
                }
                if(getDistance(latitude, longitude, markerList.get(i).getPosition().latitude,markerList.get(i).getPosition().longitude ) >= 30){
                    markerList.get(i).hideInfoWindow();
                }
            }

            LatLng mNewLatLng = new LatLng(latitude, longitude);
            //mMap.addMarker(new MarkerOptions().position(mNewLatLng).title("Current Location"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mNewLatLng,15));
        }
    }

    private void checkIn(Context context){
        geocoder = new Geocoder(this, Locale.getDefault());
        try {
            addresses = geocoder.getFromLocation(latitude,longitude,1);
            String address = addresses.get(0).getAddressLine(0);
/*           String city = addresses.get(0).getLocality();
            String state = addresses.get(0).getAdminArea();
            String country = addresses.get(0).getCountryName();
            String postalCode = addresses.get(0).getPostalCode();*/
            full_address = address;
        } catch (IOException e) {
            e.printStackTrace();
        }
        SimpleDateFormat simpleDataFormat = new SimpleDateFormat();
        Date curDate = new Date(System.currentTimeMillis());
        mTime = simpleDataFormat.format(curDate);
        LayoutInflater inflater = LayoutInflater.from(this);
        final View textEntryView = inflater.inflate(
                R.layout.dialoge, null);
        final EditText edtInput = (EditText) textEntryView.findViewById(R.id.edtInput);
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setTitle("Enter Name:");
        builder.setView(textEntryView);
        builder.setPositiveButton("Check in",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        title = (edtInput.getText()).toString();
                        markerOptions.title(title);
                        mMap.addMarker(markerOptions).showInfoWindow();
                        mName = title;
                        storeData();
                        UpdateListView();
                    }
                });
        builder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setTitle("");
                    }
                });
        builder.show();
    }

    private void UpdateListView(){
        Intent intent = new Intent();
        intent.setAction("UPDATE-LIST");
        intent.putExtra("log", longitude+"");
        intent.putExtra("lat", latitude+"");
        intent.putExtra("time",mTime);
        intent.putExtra("name",mName);
        intent.putExtra("address", full_address);
        sendBroadcast(intent);
    }

    private void storeData(){
        ContentValues cv = new ContentValues();
        cv.put("longitude", longitude);
        cv.put("latitude", latitude);
        cv.put("time", String.valueOf(mTime));
        cv.put("name", mName);
        cv.put("address", full_address);
        dbW.insert("location", null, cv);
    }
}
