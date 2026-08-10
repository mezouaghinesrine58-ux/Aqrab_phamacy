package com.app.aqrab;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // عنصر عرض الخريطة من مكتبة OSMDroid
    private MapView map = null; 
    // مدير الموقع للحصول على إحداثيات GPS
    private LocationManager locationManager; 
    // رمز طلب إذن الموقع
    private static final int REQUEST_LOCATION_PERMISSION = 1; 
    // الدبوس الذي يمثل موقع المستخدم على الخريطة
    private Marker userMarker; 
    // كائن قاعدة بيانات Firestore لجلب مواقع الصيدليات
    private FirebaseFirestore db; 
    // قائمة لحفظ دبابيس (Markers) الصيدليات لعرضها
    private List<Marker> pharmacyMarkers = new ArrayList<>(); 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // إعدادات مكتبة OSMDroid الضرورية لتحميل الخريطة بشكل صحيح
        Context ctx = getApplicationContext();
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // تعيين واجهة الخريطة
        setContentView(R.layout.activity_maps);

        // تهيئة Firebase Firestore
        db = FirebaseFirestore.getInstance();

        // تهيئة عنصر الخريطة من الواجهة
        map = findViewById(R.id.mapview);
        map.setTileSource(TileSourceFactory.MAPNIK); // استخدام خرائط Mapnik الافتراضية
        map.setMultiTouchControls(true); // تفعيل اللمس المتعدد (الزووم بالأصابع)
        map.getController().setZoom(12.0); // مستوى التكبير الافتراضي

        // تحديد نقطة البداية (الجزائر العاصمة كمثال افتراضي)
        GeoPoint startPoint = new GeoPoint(36.7538, 3.0588);
        map.getController().setCenter(startPoint);

        // زر العودة للشاشة السابقة
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // التحقق من إذن الموقع وجلب الصيدليات
        checkLocationPermission();
        fetchPharmaciesFromFirestore(); 
    }

    // دالة لجلب مواقع جميع الصيدليات من Firestore وعرضها كدبابيس حمراء
    private void fetchPharmaciesFromFirestore() {
        db.collection("Pharmacies")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // مسح الدبابيس القديمة من الخريطة قبل الإضافة الجديدة
                    for (Marker m : pharmacyMarkers) {
                        map.getOverlays().remove(m);
                    }
                    pharmacyMarkers.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String name = document.getString("pharmacyName");
                        Double lat = document.getDouble("latitude");
                        Double lon = document.getDouble("longitude");

                        if (lat != null && lon != null) {
                            GeoPoint pharmacyPoint = new GeoPoint(lat, lon);
                            Marker marker = new Marker(map);
                            marker.setPosition(pharmacyPoint);
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            
                            // تعيين أيقونة الدبوس الأحمر للصيدلية
                            marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_red_pin));
                            marker.setTitle(name != null ? name : getString(R.string.role_pharmacy));
                            
                            map.getOverlays().add(marker); // إضافة الدبوس للخريطة
                            pharmacyMarkers.add(marker); // حفظه في القائمة
                        }
                    }
                    map.invalidate(); // تحديث عرض الخريطة فوراً
                    if (!pharmacyMarkers.isEmpty()) {
                        Toast.makeText(this, getString(R.string.found_ph_total, pharmacyMarkers.size()), Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading pharmacies: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // دالة التحقق من إذن الموقع وطلب الإذن إذا لزم الأمر
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            startLocationUpdates(); // بدء تتبع موقع المستخدم
        }
    }

    // بدء عملية تتبع موقع المستخدم وتحديثه على الخريطة
    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            // جلب آخر موقع معروف لسرعة العرض
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation == null) lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (lastLocation != null) {
                updateUserLocationOnMap(lastLocation);
            }

            // طلب تحديثات الموقع بشكل مستمر (كل 5 ثواني أو تحرك 5 أمتار)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    updateUserLocationOnMap(location);
                }
            });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    // تحديث مكان الدبوس الأخضر الذي يمثل موقع المستخدم الحالي
    private void updateUserLocationOnMap(Location location) {
        GeoPoint myPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        
        if (userMarker == null) {
            userMarker = new Marker(map);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            // تعيين أيقونة الدبوس الأخضر لموقعك
            userMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_green_pin));
            userMarker.setTitle(getString(R.string.my_position));
            map.getOverlays().add(userMarker);
        }
        
        userMarker.setPosition(myPoint);
        map.invalidate(); // إعادة رسم الخريطة
    }

    // معالجة استجابة المستخدم لطلب إذن الموقع
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            }
        }
    }

    // إدارة دورة حياة الخريطة للحفاظ على الأداء والذاكرة
    @Override
    protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override
    protected void onPause() { super.onPause(); if (map != null) map.onPause(); }
}
