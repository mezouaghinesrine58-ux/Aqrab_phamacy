package com.app.aqrab;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
public class FavoritesActivity extends AppCompatActivity {

    // كائنات Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    // حاوية عرض قائمة المفضلات
    private LinearLayout llFavoritesList;
    // نص يظهر عند خلو القائمة
    private TextView tvEmpty;
    // موقع المستخدم لحساب المسافات
    private Location userLocation;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط العناصر
        llFavoritesList = findViewById(R.id.ll_favorites_list);
        tvEmpty = findViewById(R.id.tv_empty_favorites);

        // زر الرجوع
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // جلب الموقع الحالي للمستخدم
        getCurrentLocation();
    }

    // دالة جلب الموقع الجغرافي للجهاز
    private void getCurrentLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                userLocation = loc;
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // تحميل المفضلات في كل مرة يتم الرجوع فيها للشاشة
        loadFavorites();
    }

    // جلب قائمة معرفات (IDs) الصيدليات المفضلة من ملف المستخدم في Firestore
    private void loadFavorites() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("Users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // استخراج مصفوفة المفضلات
                        List<String> favoriteIds = (List<String>) documentSnapshot.get("favorites");
                        if (favoriteIds != null && !favoriteIds.isEmpty()) {
                            fetchPharmaciesDetails(favoriteIds); // جلب تفاصيل كل صيدلية
                        } else {
                            showEmptyState(); // عرض رسالة "لا توجد مفضلات"
                        }
                    } else {
                        showEmptyState();
                    }
                });
    }

    // جلب بيانات كل صيدلية موجودة في قائمة المفضلات
    private void fetchPharmaciesDetails(List<String> ids) {
        llFavoritesList.removeAllViews();
        tvEmpty.setVisibility(View.GONE);

        for (String id : ids) {
            db.collection("Pharmacies").document(id)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            addPharmacyToLayout(doc); // إضافة الصيدلية للواجهة
                        }
                    });
        }
    }

    // دالة إنشاء عنصر صيدلية وإضافته للقائمة المعروضة
    private void addPharmacyToLayout(DocumentSnapshot doc) {
        String name = doc.getString("pharmacyName");
        String address = doc.getString("address");
        String photoUrl = doc.getString("photoUrl");
        String phone = doc.getString("phone");
        String description = doc.getString("description");
        Double lat = doc.getDouble("latitude");
        Double lon = doc.getDouble("longitude");
        Map<String, Object> workingHours = (Map<String, Object>) doc.get("workingHours");

        float distance = 0;
        // حساب المسافة
        if (userLocation != null && lat != null && lon != null) {
            float[] results = new float[1];
            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), lat, lon, results);
            distance = results[0] / 1000f;
        }

        boolean isOpen = checkIfOpen(workingHours); // فحص حالة العمل الآن

        LayoutInflater inflater = LayoutInflater.from(this);
        View itemView = inflater.inflate(R.layout.item_nearby_pharmacy, llFavoritesList, false);

        // ربط عناصر تصميم العنصر الواحد
        TextView tvName = itemView.findViewById(R.id.tv_pharmacy_name);
        TextView tvAddress = itemView.findViewById(R.id.tv_pharmacy_address);
        TextView tvDist = itemView.findViewById(R.id.tv_distance);
        TextView tvStatus = itemView.findViewById(R.id.tv_status);
        ImageView ivPhoto = itemView.findViewById(R.id.iv_pharmacy_img);
        ImageView ivNav = itemView.findViewById(R.id.iv_navigation);

        tvName.setText(name);
        tvAddress.setText(address != null ? address : "Address not available");
        tvDist.setText(String.format(Locale.getDefault(), "%.1f km", distance));

        // تعيين حالة الفتح والغلق
        if (isOpen) {
            tvStatus.setText("Open Now");
            tvStatus.setTextColor(Color.parseColor("#4CAF50"));
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
        } else {
            tvStatus.setText("Closed");
            tvStatus.setTextColor(Color.parseColor("#F44336"));
            tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
        }

        // تحميل الصورة
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this).load(photoUrl).placeholder(R.drawable.a_pharmacy).into(ivPhoto);
        } else {
            ivPhoto.setImageResource(R.drawable.a_pharmacy);
        }

        final float finalDist = distance;
        // فتح التفاصيل عند الضغط على الصيدلية
        itemView.setOnClickListener(v -> {
            Intent intent = new Intent(this, PharmacyDetailActivity.class);
            intent.putExtra("PHARMACY_ID", doc.getId());
            intent.putExtra("PHARMACY_NAME", name);
            intent.putExtra("PHARMACY_ADDRESS", address);
            intent.putExtra("PHARMACY_PHOTO", photoUrl);
            intent.putExtra("PHARMACY_PHONE", phone);
            intent.putExtra("PHARMACY_DESC", description);
            intent.putExtra("PHARMACY_LAT", lat != null ? lat : 0);
            intent.putExtra("PHARMACY_LON", lon != null ? lon : 0);
            intent.putExtra("PHARMACY_DIST", finalDist);
            intent.putExtra("PHARMACY_OPEN", isOpen);
            startActivity(intent);
        });

        // تشغيل نظام الملاحة (GPS)
        ivNav.setOnClickListener(v -> {
            if (lat != null && lon != null) {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lon);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Uri webMapUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon);
                    startActivity(new Intent(Intent.ACTION_VIEW, webMapUri));
                }
            }
        });

        llFavoritesList.addView(itemView);
    }

    // دالة فحص ساعات العمل
    private boolean checkIfOpen(Map<String, Object> hours) {
        if (hours == null) return false;
        if (Boolean.TRUE.equals(hours.get("open247"))) return true;

        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int currentTimeInMinutes = currentHour * 60 + currentMinute;

        String openKey, closeKey;
        switch (day) {
            case Calendar.SATURDAY: openKey = "sat_open"; closeKey = "sat_close"; break;
            case Calendar.SUNDAY: openKey = "sun_open"; closeKey = "sun_close"; break;
            case Calendar.MONDAY: openKey = "mon_open"; closeKey = "mon_close"; break;
            case Calendar.TUESDAY: openKey = "tue_open"; closeKey = "tue_close"; break;
            case Calendar.WEDNESDAY: openKey = "wed_open"; closeKey = "wed_close"; break;
            case Calendar.THURSDAY: openKey = "thu_open"; closeKey = "thu_close"; break;
            case Calendar.FRIDAY: openKey = "fri_open"; closeKey = "fri_close"; break;
            default: return false;
        }

        String openTime = (String) hours.get(openKey);
        String closeTime = (String) hours.get(closeKey);

        if (openTime == null || closeTime == null || openTime.isEmpty() || closeTime.isEmpty()) return false;

        try {
            int openTotalMinutes = parseTimeToMinutes(openTime);
            int closeTotalMinutes = parseTimeToMinutes(closeTime);

            if (closeTotalMinutes < openTotalMinutes) {
                return currentTimeInMinutes >= openTotalMinutes || currentTimeInMinutes <= closeTotalMinutes;
            } else {
                return currentTimeInMinutes >= openTotalMinutes && currentTimeInMinutes <= closeTotalMinutes;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // تحويل الوقت لدقائق
    private int parseTimeToMinutes(String time) {
        String[] parts = time.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h * 60 + m;
    }

    // عرض الحالة الفارغة
    private void showEmptyState() {
        llFavoritesList.removeAllViews();
        tvEmpty.setVisibility(View.VISIBLE);
    }
}
