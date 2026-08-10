package com.app.aqrab;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import android.content.SharedPreferences;

public class PharmacyDetailActivity extends AppCompatActivity {

    // متغيرات لحفظ بيانات الصيدلية المستلمة
    private String id, name, address, photoUrl, phone, description;
    private double latitude, longitude;
    private float distance;
    private boolean isOpen, isFavorite = false;
    // كائنات Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    // عناصر الواجهة
    private ImageView ivFavorite;
    private MapView map;
    private TextView tvHoursSummary;
    private RatingBar rbUserRating;
    private EditText etReviewComment;
    private LinearLayout llReviewsList;
    // خارطة لحفظ ساعات العمل
    private Map<String, Object> workingHoursMap;

    @Override
    protected void attachBaseContext(Context newBase) {
        // تطبيق اللغة
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // تهيئة مكتبة OSMDroid للخرائط
        Context ctx = getApplicationContext();
        Configuration.getInstance().setUserAgentValue(getPackageName());
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        
        // تعيين الواجهة
        setContentView(R.layout.activity_pharmacy_detail);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // استلام البيانات المرسلة عبر الـ Intent من الشاشة السابقة
        id = getIntent().getStringExtra("PHARMACY_ID");
        name = getIntent().getStringExtra("PHARMACY_NAME");
        address = getIntent().getStringExtra("PHARMACY_ADDRESS");
        photoUrl = getIntent().getStringExtra("PHARMACY_PHOTO");
        phone = getIntent().getStringExtra("PHARMACY_PHONE");
        description = getIntent().getStringExtra("PHARMACY_DESC");
        latitude = getIntent().getDoubleExtra("PHARMACY_LAT", 0);
        longitude = getIntent().getDoubleExtra("PHARMACY_LON", 0);
        distance = getIntent().getFloatExtra("PHARMACY_DIST", 0);
        isOpen = getIntent().getBooleanExtra("PHARMACY_OPEN", false);

        // تهيئة العناصر، الخريطة، والبيانات
        initViews();
        initMap();
        fetchPharmacyDetails(); // جلب تفاصيل إضافية من Firestore
        loadReviews(); // تحميل التقييمات
        checkIfFavorite(); // التحقق من حالة المفضلة
    }

    // دالة تحميل التقييمات والتعليقات من Firestore
    private void loadReviews() {
        if (id == null) return;
        db.collection("Pharmacies").document(id).collection("Reviews")
                .orderBy("timestamp", Query.Direction.DESCENDING) // ترتيب من الأحدث
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    llReviewsList.removeAllViews();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        addReviewToLayout(doc); // إضافة كل تعليق للواجهة
                    }
                });
    }

    // دالة إنشاء عنصر تعليق وإضافته للقائمة
    private void addReviewToLayout(QueryDocumentSnapshot doc) {
        View itemView = getLayoutInflater().inflate(R.layout.item_review, llReviewsList, false);
        TextView tvName = itemView.findViewById(R.id.tv_reviewer_name);
        TextView tvDate = itemView.findViewById(R.id.tv_review_date);
        TextView tvComment = itemView.findViewById(R.id.tv_review_comment);
        RatingBar rb = itemView.findViewById(R.id.rb_item_rating);

        tvName.setText(doc.getString("userName"));
        tvComment.setText(doc.getString("comment"));
        Double rating = doc.getDouble("rating");
        rb.setRating(rating != null ? rating.floatValue() : 0);

        // تنسيق التاريخ
        Object timestamp = doc.get("timestamp");
        if (timestamp instanceof com.google.firebase.Timestamp) {
            java.util.Date date = ((com.google.firebase.Timestamp) timestamp).toDate();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault());
            tvDate.setText(sdf.format(date));
        }

        llReviewsList.addView(itemView);
    }

    // دالة إرسال تقييم جديد
    private void submitReview() {
        FirebaseUser user = mAuth.getCurrentUser();
        // التحقق من تسجيل الدخول
        if (user == null) {
            Toast.makeText(this, R.string.login_to_review, Toast.LENGTH_SHORT).show();
            return;
        }

        float rating = rbUserRating.getRating();
        String comment = etReviewComment.getText().toString().trim();

        if (rating == 0) {
            Toast.makeText(this, R.string.select_rating, Toast.LENGTH_SHORT).show();
            return;
        }

        // محاولة جلب الاسم من SharedPreferences أولاً لتسريع العملية
        android.content.SharedPreferences prefs = getSharedPreferences("AqrabPrefs", MODE_PRIVATE);
        String cachedName = prefs.getString("user_name", null);
        
        if (cachedName != null) {
            saveReviewToFirestore(user.getUid(), cachedName, rating, comment);
            return;
        }

        // إذا لم يوجد في التفضيلات، نجلب اسم المستخدم من Firestore قبل الحفظ
        String uid = user.getUid();
        db.collection("Users").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String name = doc.getString("fullName");
                // حفظ الاسم محلياً للمرات القادمة لتجنب جلب البيانات من Firestore مرة أخرى
                prefs.edit().putString("user_name", name).apply();
                saveReviewToFirestore(uid, name, rating, comment);
            } else {
                // البحث في مجموعة الصيدليات إذا لم يكن مريضاً
                db.collection("Pharmacies").document(uid).get().addOnSuccessListener(docP -> {
                    String name = "User";
                    if (docP.exists()) {
                        name = docP.getString("pharmacyName");
                        prefs.edit().putString("user_name", name).apply();
                    } else if (user.getDisplayName() != null) {
                        name = user.getDisplayName();
                    }
                    saveReviewToFirestore(uid, name, rating, comment);
                }).addOnFailureListener(e -> {
                    String name = user.getDisplayName() != null ? user.getDisplayName() : "User";
                    saveReviewToFirestore(uid, name, rating, comment);
                });
            }
        }).addOnFailureListener(e -> {
            String name = user.getDisplayName() != null ? user.getDisplayName() : "User";
            saveReviewToFirestore(uid, name, rating, comment);
        });
    }

    // دالة حفظ التقييم في Firestore بعد جلب الاسم
    private void saveReviewToFirestore(String userId, String userName, float rating, String comment) {
        // تحضير بيانات التقييم
        Map<String, Object> review = new HashMap<>();
        review.put("userId", userId);
        review.put("userName", userName != null ? userName : "User");
        review.put("rating", rating);
        review.put("comment", comment);
        review.put("timestamp", FieldValue.serverTimestamp());

        // الحفظ في Firestore
        db.collection("Pharmacies").document(id).collection("Reviews")
                .add(review)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, R.string.review_posted, Toast.LENGTH_SHORT).show();
                    rbUserRating.setRating(0); // تصفير التقييم
                    etReviewComment.setText(""); // مسح النص
                    loadReviews(); // تحديث القائمة
                })
                .addOnFailureListener(e -> Toast.makeText(this, R.string.failed_review, Toast.LENGTH_SHORT).show());
    }

    // عرض نافذة منبثقة بساعات العمل كاملة
    private void showHoursDialog() {
        if (workingHoursMap == null) {
            Toast.makeText(this, R.string.hours_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_working_hours, null);
        LinearLayout container = view.findViewById(R.id.ll_hours_container);
        
        // التحقق إذا كانت تعمل 24/7
        if (Boolean.TRUE.equals(workingHoursMap.get("open247"))) {
            addDayRow(container, getString(R.string.all_days), getString(R.string.open_24_7), true);
        } else {
            String[] days = {getString(R.string.monday), getString(R.string.tuesday), getString(R.string.wednesday), getString(R.string.thursday), getString(R.string.friday), getString(R.string.saturday), getString(R.string.sunday)};
            String[] keys = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
            
            Calendar cal = Calendar.getInstance();
            int today = cal.get(Calendar.DAY_OF_WEEK);
            int todayIdx = (today + 5) % 7; // ضبط الفهرس ليوافق مصفوفة الأيام

            for (int i = 0; i < days.length; i++) {
                String open = (String) workingHoursMap.get(keys[i] + "_open");
                String close = (String) workingHoursMap.get(keys[i] + "_close");
                String timeText = (open == null || open.isEmpty()) ? "Closed" : open + " - " + close;
                addDayRow(container, days[i], timeText, i == todayIdx);
            }
        }

        AlertDialog dialog = builder.setView(view).create();
        // جعل خلفية النافذة شفافة للسماح بظهور الحواف المنحنية
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            dialog.getWindow().getDecorView().setPadding(0, 0, 0, 0);
        }
        
        view.findViewById(R.id.btn_close_hours).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // دالة مساعدة لإضافة صف يوم واحد في نافذة ساعات العمل
    private void addDayRow(LinearLayout container, String day, String hours, boolean isToday) {
        View row = getLayoutInflater().inflate(R.layout.item_hour_row, container, false);
        TextView tvDay = row.findViewById(R.id.tv_hour_day);
        TextView tvHours = row.findViewById(R.id.tv_hour_time);

        tvDay.setText(day);
        if (isToday) {
            tvDay.setTextColor(android.graphics.Color.parseColor("#2E5A44"));
            tvDay.setText(day + " (" + getString(R.string.today_label) + ")");
        }

        tvHours.setText(hours);
        if (isToday) {
            tvHours.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvHours.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        
        container.addView(row);
    }

    // تهيئة الخريطة وتحديد موقع الصيدلية
    private void initMap() {
        map = findViewById(R.id.mv_pharmacy_location);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        if (latitude != 0 && longitude != 0) {
            GeoPoint pharmacyPoint = new GeoPoint(latitude, longitude);
            map.getController().setCenter(pharmacyPoint);

            // إضافة دبوس (Marker) لموقع الصيدلية
            Marker marker = new Marker(map);
            marker.setPosition(pharmacyPoint);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_red_pin));
            marker.setTitle(name);
            map.getOverlays().add(marker);
        }
        map.invalidate(); // تحديث الخريطة
    }

    // جلب بيانات إضافية للصيدلية (ساعات العمل والعنوان الدقيق)
    private void fetchPharmacyDetails() {
        if (id == null) return;
        db.collection("Pharmacies").document(id)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        workingHoursMap = (Map<String, Object>) documentSnapshot.get("workingHours");
                        String hoursStr = getTodayHours(workingHoursMap);
                        tvHoursSummary.setText(hoursStr); // عرض ساعات اليوم
                        
                        String dbAddress = documentSnapshot.getString("address");
                        if (dbAddress != null) {
                            TextView tvFullAddress = findViewById(R.id.tv_detail_full_address);
                            tvFullAddress.setText(dbAddress);
                        }
                    }
                });
    }

    // دالة استخراج ساعات العمل لليوم الحالي فقط
    private String getTodayHours(Map<String, Object> hours) {
        if (hours == null) return getString(R.string.hours_not_available);
        if (Boolean.TRUE.equals(hours.get("open247"))) return getString(R.string.open_24_7);

        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        String openKey, closeKey;
        switch (day) {
            case Calendar.SATURDAY: openKey = "sat_open"; closeKey = "sat_close"; break;
            case Calendar.SUNDAY: openKey = "sun_open"; closeKey = "sun_close"; break;
            case Calendar.MONDAY: openKey = "mon_open"; closeKey = "mon_close"; break;
            case Calendar.TUESDAY: openKey = "tue_open"; closeKey = "tue_close"; break;
            case Calendar.WEDNESDAY: openKey = "wed_open"; closeKey = "wed_close"; break;
            case Calendar.THURSDAY: openKey = "thu_open"; closeKey = "thu_close"; break;
            case Calendar.FRIDAY: openKey = "fri_open"; closeKey = "fri_close"; break;
            default: return getString(R.string.closed_today);
        }

        String openTime = (String) hours.get(openKey);
        String closeTime = (String) hours.get(closeKey);
        
        if (openTime == null || closeTime == null || openTime.isEmpty() || closeTime.isEmpty()) {
            return getString(R.string.closed_today);
        }
        
        return openTime + " - " + closeTime;
    }

    // ربط العناصر وتعيين البيانات الأولية والوظائف
    private void initViews() {
        TextView tvName = findViewById(R.id.tv_detail_name);
        TextView tvStatus = findViewById(R.id.tv_detail_status);
        TextView tvDistance = findViewById(R.id.tv_detail_distance);
        TextView tvDesc = findViewById(R.id.tv_detail_description);
        TextView tvFullAddress = findViewById(R.id.tv_detail_full_address);
        tvHoursSummary = findViewById(R.id.tv_detail_hours_summary);
        ImageView ivPhoto = findViewById(R.id.iv_pharmacy_detail_img);
        ivFavorite = findViewById(R.id.iv_favorite);

        rbUserRating = findViewById(R.id.rb_user_rating);
        etReviewComment = findViewById(R.id.et_review_comment);
        llReviewsList = findViewById(R.id.ll_reviews_list);
        Button btnSubmitReview = findViewById(R.id.btn_submit_review);

        btnSubmitReview.setOnClickListener(v -> submitReview());

        // تعيين البيانات
        tvName.setText(name);
        tvDesc.setText(description != null && !description.isEmpty() ? description : getString(R.string.default_pharmacy_desc));
        tvDistance.setText(String.format(java.util.Locale.getDefault(), "%.1f km", distance));
        tvFullAddress.setText(address != null ? address : getString(R.string.address));

        if (isOpen) {
            tvStatus.setText(R.string.open_now);
            tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else {
            tvStatus.setText(R.string.closed);
            tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"));
            tvStatus.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFEBEE")));
        }

        // تحميل صورة الصيدلية
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this).load(photoUrl).placeholder(R.drawable.a_pharmacy).into(ivPhoto);
        } else {
            ivPhoto.setImageResource(R.drawable.a_pharmacy);
        }

        // إعداد أزرار العمل السريع (اتصال، ملاحة، ساعات، رسائل)
        findViewById(R.id.ll_action_call).setOnClickListener(v -> {
            if (phone != null && !phone.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + phone));
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.no_phone_number, Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.ll_action_directions).setOnClickListener(v -> {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + latitude + "," + longitude);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Uri webMapUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + latitude + "," + longitude);
                startActivity(new Intent(Intent.ACTION_VIEW, webMapUri));
            }
        });

        findViewById(R.id.ll_action_hours).setOnClickListener(v -> showHoursDialog());

        // وظيفة زر المفضلة
        ivFavorite.setOnClickListener(v -> toggleFavorite());

        // زر الرجوع في التولبار
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // التحقق من حالة المفضلة عند فتح الشاشة (سحابياً ومحلياً)
    private void checkIfFavorite() {
        if (id == null || mAuth.getCurrentUser() == null) return;
        
        String userId = mAuth.getCurrentUser().getUid();
        // التحقق من Firestore
        db.collection("Users").document(userId).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                List<String> favorites = (List<String>) documentSnapshot.get("favorites");
                if (favorites != null && favorites.contains(id)) {
                    isFavorite = true;
                    updateFavoriteIcon();
                }
            }
        });

        // التحقق محلياً للسرعة أو في حال عدم وجود إنترنت
        SharedPreferences prefs = getSharedPreferences("AqrabPrefs", Context.MODE_PRIVATE);
        Set<String> localFavorites = prefs.getStringSet("favorites", new HashSet<>());
        if (localFavorites.contains(id)) {
            isFavorite = true;
            updateFavoriteIcon();
        }
    }

    // دالة إضافة أو حذف الصيدلية من المفضلة
    private void toggleFavorite() {
        if (id == null) return;
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, R.string.login_to_favorite, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String userId = user.getUid();
        isFavorite = !isFavorite;
        
        if (isFavorite) {
            // إضافة للمفضلة في Firestore
            db.collection("Users").document(userId)
                    .update("favorites", FieldValue.arrayUnion(id))
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.added_to_favorites, Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> {
                        // إنشاء الحقل إذا لم يكن موجوداً
                        Map<String, Object> data = new HashMap<>();
                        data.put("favorites", FieldValue.arrayUnion(id));
                        db.collection("Users").document(userId).set(data, com.google.firebase.firestore.SetOptions.merge());
                        Toast.makeText(this, R.string.added_to_favorites, Toast.LENGTH_SHORT).show();
                    });
            
            // تحديث محلي
            SharedPreferences prefs = getSharedPreferences("AqrabPrefs", Context.MODE_PRIVATE);
            Set<String> localFavs = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
            localFavs.add(id);
            prefs.edit().putStringSet("favorites", localFavs).apply();
            
        } else {
            // حذف من المفضلة في Firestore
            db.collection("Users").document(userId)
                    .update("favorites", FieldValue.arrayRemove(id))
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show());

            // تحديث محلي
            SharedPreferences prefs = getSharedPreferences("AqrabPrefs", Context.MODE_PRIVATE);
            Set<String> localFavs = new HashSet<>(prefs.getStringSet("favorites", new HashSet<>()));
            localFavs.remove(id);
            prefs.edit().putStringSet("favorites", localFavs).apply();
        }
        
        updateFavoriteIcon(); // تحديث لون القلب
    }

    // تحديث لون أيقونة القلب (أحمر للمفضلة)
    private void updateFavoriteIcon() {
        if (isFavorite) {
            ivFavorite.setColorFilter(android.graphics.Color.RED);
        } else {
            ivFavorite.setColorFilter(android.graphics.Color.parseColor("#2E5A44"));
        }
    }

    // إدارة دورة حياة الخريطة
    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) map.onPause();
    }
}
