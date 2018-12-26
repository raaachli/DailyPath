package com.example.rache.dailypath;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.provider.SyncStateContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;
    private Location mCurrentLocation;
    private LocationManager locationManager;
    private TextView tvLatitude, tvLongitude, tvAddress,delay_time;
    private Button check_btn;
    private Button map_btn;
    private Button auto_btn;
    private double latitude;
    private double longitude;
    private List<Address> addresses;
    private String full_address;
    private ListView location_list;
    private Geocoder geocoder;
    private DBHelper dbHelper;
    private SQLiteDatabase dbW;
    private SQLiteDatabase dbR;
    private String mTime;
    private String mName;
    //private String delay_time;
    private EditText edt_name;
    private SimpleAdapter mAdapter;
    List<Map<String, String>> locationList = new ArrayList<>();
    private boolean autocheckFlag = false;
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE =101;
    private ListReceiver listReceiver;
    //private boolean netFlag = false;
    private long startTime2;
    private long startTime;
    private long consumingTime2;
    private long consumingTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        tvLatitude = (TextView) findViewById(R.id.latitude);
        tvLongitude = (TextView) findViewById(R.id.longitude);
        tvAddress = (TextView) findViewById(R.id.address);

        //delay_time = (TextView)findViewById(R.id.delay_time);

        check_btn = (Button) findViewById(R.id.check_btn);
        map_btn = (Button)findViewById(R.id.map_btn);
        auto_btn = (Button)findViewById(R.id.auto_btn);
        location_list = (ListView) findViewById(R.id.location_list);
        edt_name = (EditText) findViewById(R.id.edt_name);
        //autocheckFlag = false;

        dbHelper = new DBHelper(this,"loc.db",null,1);
        dbW = dbHelper.getWritableDatabase();
        dbR = dbHelper.getReadableDatabase();

        mAdapter = new SimpleAdapter(this, locationList,R.layout.list_items,new String[]{"log","lat","time","name","address"},new int[]{R.id.item_log,
        R.id.item_lat,R.id.item_time,R.id.item_name,R.id.item_address});
        location_list.setAdapter(mAdapter);
        restoreData();

        setLocation();

        check_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                check_in();
            }
        });
        final Intent intent = new Intent(this,MapsActivity.class);
        map_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               startActivity(intent);
            }
        });
        auto_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                autocheckFlag = true;
                AutoCheckin();
            }
        });

        listReceiver = new ListReceiver();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction("UPDATE-LIST");
        registerReceiver(listReceiver, filter2);
    }

    public void AutoCheckin(){
        final Intent in = new Intent(this,AutoCheckinService.class);
        if(autocheckFlag == true){
            startService(in);
            IntentFilter filter = new IntentFilter();
            filter.addAction("CHECK");
            registerReceiver(new autocheckReceiver(),filter);
        }
        Toast.makeText(this, "Auto Check In", Toast.LENGTH_SHORT).show();
    }

    public class autocheckReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            MainActivity mainActivity =(MainActivity)context;
            mainActivity.autocheck_in();
        }
    }

    private void autocheck_in(){
        mName = "PASSING_BY";
        Cursor cursor = dbW.query("location", null, null, null, null, null, null, null);
        while (cursor.moveToNext()){
            if(getDistance(latitude,longitude,cursor.getDouble(2),cursor.getDouble(1))<30){
                Toast.makeText(this,"passing by",Toast.LENGTH_SHORT).show();
                mName=cursor.getString(4);
                full_address=cursor.getString(5);
                break;
            }
        }
        insertList();
        storeData();
        cursor.close();
    }

    private void check_in(){
        mName = edt_name.getText().toString();
        Cursor cursor = dbW.query("location", null, null, null, null, null, null, null);
        while (cursor.moveToNext()){
            if(getDistance(latitude,longitude,cursor.getDouble(2),cursor.getDouble(1))<30){
                Toast.makeText(this,"check-in in 30m",Toast.LENGTH_SHORT).show();
                if(!cursor.getString(4).equals("PASSING_BY")){
                    mName=cursor.getString(4);
                }
                full_address=cursor.getString(5);
                break;
            }
        }
        insertList();
        edt_name.setText(" ");
        storeData();
        cursor.close();
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

    private void restoreData(){
        Cursor cursor = dbW.query("location", null, null, null, null, null, null, null);
        while (cursor.moveToNext()){
            Map<String, String> map = new HashMap<>();
            map.put("log", "longitude:"+cursor.getDouble(1));
            map.put("lat", "latitude:"+cursor.getDouble(2));
            map.put("time", "time:"+cursor.getString(3));
            map.put("name","name:"+ cursor.getString(4));
            map.put("address", "address:"+cursor.getString(5));
            locationList.add(map);
            mAdapter.notifyDataSetChanged();
        }
    }

    private void insertList(){
        Map<String, String> map = new HashMap<>();
        map.put("log", "longitude:"+longitude);
        map.put("lat", "latitude:"+latitude);
        map.put("time", "time:"+mTime);
        map.put("name", "name:"+ mName);
        map.put("address", "address:"+full_address);
        locationList.add(map);
        mAdapter.notifyDataSetChanged();
    }

    public class ListReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            Map<String, String> map = new HashMap<>();
            map.put("log", "longitude:"+ intent.getStringExtra("log"));
            map.put("lat","latitude:"+ intent.getStringExtra("lat"));
            map.put("time", "time:"+intent.getStringExtra("time"));
            map.put("name","name:"+ intent.getStringExtra("name"));
            map.put("address", "address:"+ intent.getStringExtra("address"));
            locationList.add(map);
            mAdapter.notifyDataSetChanged();
        }
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    public void updateLocation(){
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (mCurrentLocation == null) {
            //Toast.makeText(this, "use network", Toast.LENGTH_SHORT).show();
            // fall back to network if GPS is not available
            mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (mCurrentLocation != null) {
            //Toast.makeText(this, "use gps", Toast.LENGTH_SHORT).show();
            latitude = mCurrentLocation.getLatitude();
            longitude = mCurrentLocation.getLongitude();

            tvLatitude.setText(String.valueOf(latitude));
            tvLongitude.setText(String.valueOf(longitude));

            //get address
            geocoder = new Geocoder(this, Locale.getDefault());
            try {
                addresses = geocoder.getFromLocation(latitude,longitude,1);
                String address = addresses.get(0).getAddressLine(0);
                /*String city = addresses.get(0).getLocality();
                String state = addresses.get(0).getAdminArea();
                String country = addresses.get(0).getCountryName();
                String postalCode = addresses.get(0).getPostalCode();*/
                full_address = address;
                tvAddress.setText(full_address);
            } catch (IOException e) {
                e.printStackTrace();
            }
            SimpleDateFormat simpleDataFormat = new SimpleDateFormat();
            Date curDate = new Date(System.currentTimeMillis());
            mTime = simpleDataFormat.format(curDate);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }


    private boolean checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }else {
            return true;
        }
    }

    private void requestPermissions() {
        requirePermissions();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!checkPermissions()) {
            requestPermissions();
        } else {

            //startTime = System.currentTimeMillis();
            setLocation();
            //consumingTime = System.currentTimeMillis() - startTime;
            //delay_time.setText("delay: "+consumingTime+"");

            autocheckFlag = true;
        }
    }

    private void requirePermissions(){
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        setLocation();

    }

    private void setLocation(){
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, new android.location.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    updateLocation();
                    if(autocheckFlag == true){
                        if(getDistance(latitude,longitude,location.getLatitude(),location.getLongitude())>100){
                            check_in();
                        }
                    }
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
        }else{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, new android.location.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if(autocheckFlag == true){
                        if(getDistance(latitude,longitude,location.getLatitude(),location.getLongitude())>100){
                            updateLocation();
                            autocheck_in();
                        }else {
                            updateLocation();
                        }
                    }else {
                        updateLocation();
                    }
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

        mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);


        if (mCurrentLocation == null) {
            // fall back to network if GPS is not available
            mCurrentLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (mCurrentLocation != null) {
            //Toast.makeText(this, "use gps", Toast.LENGTH_SHORT).show();
            latitude = mCurrentLocation.getLatitude();
            longitude = mCurrentLocation.getLongitude();
            tvLatitude.setText(String.valueOf(latitude));
            tvLongitude.setText(String.valueOf(longitude));
            //get address
            geocoder = new Geocoder(this, Locale.getDefault());
            try {
                addresses = geocoder.getFromLocation(latitude,longitude,1);
                String address = addresses.get(0).getAddressLine(0);
                /*String city = addresses.get(0).getLocality();
                String state = addresses.get(0).getAdminArea();
                String country = addresses.get(0).getCountryName();
                String postalCode = addresses.get(0).getPostalCode();*/
                full_address = address;
                tvAddress.setText(full_address);
            } catch (IOException e) {
                e.printStackTrace();
            }

            SimpleDateFormat simpleDataFormat = new SimpleDateFormat();
            Date curDate = new Date(System.currentTimeMillis());
            mTime = simpleDataFormat.format(curDate);
            //Toast.makeText(this, mTime, Toast.LENGTH_SHORT).show();
        }
    }
}
