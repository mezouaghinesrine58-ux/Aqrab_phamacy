package com.app.aqrab;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PharmacyActivity extends AppCompatActivity {

    // نصوص لعرض إحصائيات المخزون
    private TextView tvTotal, tvInStock, tvLowStock, tvExpiring;
    // حاويات لعرض التنبيهات وقائمة الأدوية
    private LinearLayout llAlertsContainer, llMedicineListContainer;
    // أزرار عرض الكل والفلترة
    private TextView tvViewAllAlerts;
    private LinearLayout llFilter;
    // حقل البحث بداخل لوحة التحكم
    private EditText etSearch;
    // زر وحاوية الإشعارات
    private FrameLayout flNotificationsContainer;
    private ImageView ivNotifications;
    private TextView tvNotificationBadge;
    private List<PharmacyNotificationItem> activeNotificationsList = new ArrayList<>();

    // كائنات Firebase للتعامل مع البيانات والمصادقة
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    // قائمة لحفظ المخزون الحالي للبحث السريع
    private List<QueryDocumentSnapshot> inventoryList = new ArrayList<>();
    // القائمة الجانبية (Drawer)
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    public static class PharmacyNotificationItem {
        String title;
        String message;
        String medName;
        String type; // "expiry" or "stock"
        String severity; // "critical", "warning", "info"

        public PharmacyNotificationItem(String title, String message, String medName, String type, String severity) {
            this.title = title;
            this.message = message;
            this.medName = medName;
            this.type = type;
            this.severity = severity;
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pharmacy);

        // تهيئة كائنات قاعدة البيانات والمصادقة من Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // استدعاء دالة ربط عناصر الواجهة برمز الجافا
        initViews();
        // إعداد وظائف الضغط على الأزرار
        setupClickListeners();
        // إعداد وظيفة البحث الفوري في القائمة
        setupSearch();

        // طلب إذن الإشعارات لأندرويد 13+
        requestNotificationPermission();

        // جلب اسم الصيدلية الممرر أو المحفوظ محلياً وعرضه بدلاً من Aqrab
        TextView tvPharmacyName = findViewById(R.id.tv_pharmacy_name);
        String pharmacyName = getIntent().getStringExtra("PHARMACY_NAME");
        if (pharmacyName == null || pharmacyName.isEmpty()) {
            pharmacyName = getSharedPreferences("AqrabPrefs", MODE_PRIVATE).getString("pharmacy_name", "");
        }
        if (pharmacyName == null || pharmacyName.isEmpty()) {
            pharmacyName = getSharedPreferences("AqrabPrefs", MODE_PRIVATE).getString("user_name", "");
        }
        if (pharmacyName != null && !pharmacyName.isEmpty() && tvPharmacyName != null) {
            tvPharmacyName.setText(pharmacyName);
            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                    .putString("pharmacy_name", pharmacyName)
                    .putString("user_name", pharmacyName)
                    .apply();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void initViews() {
        // ربط نصوص الإحصائيات (الإجمالي، الناقص، منتهي الصلاحية)
        tvTotal = findViewById(R.id.tv_total_medicines);
        tvLowStock = findViewById(R.id.tv_low_stock);
        tvExpiring = findViewById(R.id.tv_expiring_soon);

        // ربط الحاويات (التنبيهات وقائمة الأدوية)
        llAlertsContainer = findViewById(R.id.ll_alerts_container);
        llMedicineListContainer = findViewById(R.id.ll_medicine_list_container);
        // ربط الأزرار (عرض الكل والفلترة)
        tvViewAllAlerts = findViewById(R.id.tv_view_all_alerts);
        llFilter = findViewById(R.id.ll_filter);
        // ربط حقل البحث
        etSearch = findViewById(R.id.et_search_dashboard);

        // ربط عنصر الإشعارات
        flNotificationsContainer = findViewById(R.id.fl_notifications_container);
        ivNotifications = findViewById(R.id.iv_notifications);
        tvNotificationBadge = findViewById(R.id.tv_notification_badge);

        // ربط القائمة الجانبية
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
    }

    // إعداد مراقب لتغيير النص في حقل البحث للفلترة الفورية
    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterMedicineList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // دالة تصفية قائمة الأدوية محلياً بناءً على نص البحث
    private void filterMedicineList(String query) {
        llMedicineListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = 0;

        for (QueryDocumentSnapshot doc : inventoryList) {
            String name = doc.getString("name");
            if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                count++;
                if (count <= 20) {
                    addMedicineToContainer(llMedicineListContainer, doc, name, doc.getString("category"),
                            getQuantity(doc), doc.getString("unit"), inflater, false);
                }
            }
        }

        if (count == 0 && !query.isEmpty()) {
            TextView tvNoResults = new TextView(this);
            tvNoResults.setText(R.string.no_medicines_match);
            tvNoResults.setPadding(20, 20, 20, 20);
            llMedicineListContainer.addView(tvNoResults);
        } else if (query.isEmpty()) {
            renderTopMedicines();
        }
    }

    private int getQuantity(QueryDocumentSnapshot doc) {
        Object qtyObj = doc.get("quantity");
        if (qtyObj != null) {
            try {
                return Integer.parseInt(qtyObj.toString());
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private void renderTopMedicines() {
        llMedicineListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int displayCount = 0;
        for (QueryDocumentSnapshot doc : inventoryList) {
            displayCount++;
            if (displayCount <= 10) {
                addMedicineToContainer(llMedicineListContainer, doc, doc.getString("name"),
                        doc.getString("category"), getQuantity(doc), doc.getString("unit"), inflater, false);
            }
        }
    }

    // دالة إعداد جميع مستمعي الضغط في الشاشة
    private void setupClickListeners() {
        // فتح القائمة الجانبية عند الضغط على أيقونة القائمة
        ImageView ivMenu = findViewById(R.id.iv_menu);
        if (ivMenu != null) {
            ivMenu.setOnClickListener(v -> {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        // إعداد الضغط على زر الإشعارات
        if (flNotificationsContainer != null) {
            flNotificationsContainer.setOnClickListener(v -> showNotificationsDialog());
        }

        // إعداد خيارات القائمة الجانبية
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_logout) {
                    getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().remove("user_role").apply();
                    mAuth.signOut();
                    Intent intent = new Intent(PharmacyActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else if (id == R.id.nav_working_hours) {
                    startActivity(new Intent(PharmacyActivity.this, WorkingHoursActivity.class));
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(PharmacyActivity.this, ProfileActivity.class));
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(PharmacyActivity.this, SettingsActivity.class));
                } else if (id == R.id.nav_dashboard) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }

                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return true;
            });

            updateNavHeader();
        }

        // أزرار الإجراءات السريعة
        LinearLayout btnAddMedicine = findViewById(R.id.ll_add_medicine);
        if (btnAddMedicine != null) {
            btnAddMedicine.setOnClickListener(v -> startActivity(new Intent(PharmacyActivity.this, AddMedicineActivity.class)));
        }

        LinearLayout btnStock = findViewById(R.id.ll_stock);
        if (btnStock != null) {
            btnStock.setOnClickListener(v -> startActivity(new Intent(PharmacyActivity.this, StockActivity.class)));
        }

        LinearLayout btnSell = findViewById(R.id.ll_sell);
        if (btnSell != null) {
            btnSell.setOnClickListener(v -> startActivity(new Intent(PharmacyActivity.this, SellActivity.class)));
        }

        LinearLayout btnHistory = findViewById(R.id.ll_history);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> startActivity(new Intent(PharmacyActivity.this, HistoryActivity.class)));
        }

        if (tvViewAllAlerts != null) {
            tvViewAllAlerts.setOnClickListener(v -> {
                Intent intent = new Intent(PharmacyActivity.this, StockActivity.class);
                intent.putExtra("alerts_only", true);
                startActivity(intent);
            });
        }

        if (llFilter != null) {
            llFilter.setOnClickListener(v -> showFilterDialog());
        }
    }

    private void showFilterDialog() {
        String[] options = {getString(R.string.view_all), getString(R.string.in_stock), getString(R.string.low_stock), getString(R.string.expiring)};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.filter_medicines);
        builder.setItems(options, (dialog, which) -> applyFilter(which));
        builder.show();
    }

    private void applyFilter(int filterIndex) {
        llMedicineListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 30);
        Date thirtyDaysFromNow = cal.getTime();

        for (QueryDocumentSnapshot doc : inventoryList) {
            int qty = getQuantity(doc);
            boolean show = false;

            switch (filterIndex) {
                case 0:
                    show = true;
                    break;
                case 1:
                    show = qty > 0;
                    break;
                case 2:
                    show = (qty > 0 && qty < 10);
                    break;
                case 3:
                    String expiryDateStr = doc.getString("expiryDate");
                    if (expiryDateStr != null) {
                        try {
                            Date expiryDate = sdf.parse(expiryDateStr);
                            if (expiryDate != null && expiryDate.before(thirtyDaysFromNow)) {
                                show = true;
                            }
                        } catch (ParseException ignored) {}
                    }
                    break;
            }

            if (show) {
                addMedicineToContainer(llMedicineListContainer, doc, doc.getString("name"),
                        doc.getString("category"), qty, doc.getString("unit"), inflater, false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDashboardData();
    }

    // دالة جلب البيانات الحية من Firestore وتحديث لوحة التحكم والإشعارات
    private void updateDashboardData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String phName = queryDocumentSnapshots.getDocuments().get(0).getString("pharmacyName");
                        if (phName != null && !phName.isEmpty()) {
                            TextView tvPharmacyName = findViewById(R.id.tv_pharmacy_name);
                            if (tvPharmacyName != null) {
                                tvPharmacyName.setText(phName);
                            }
                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                                    .putString("pharmacy_name", phName)
                                    .putString("user_name", phName)
                                    .apply();
                        }

                        String pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();

                        db.collection("Pharmacies").document(pharmacyId)
                                .collection("Inventory")
                                .get()
                                .addOnSuccessListener(inventorySnapshots -> {
                                    int total = 0;
                                    int inStockCount = 0;
                                    int lowStockCount = 0;
                                    int expiringCount = 0;

                                    llAlertsContainer.removeAllViews();
                                    llMedicineListContainer.removeAllViews();
                                    inventoryList.clear();
                                    LayoutInflater inflater = LayoutInflater.from(this);

                                    SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                                    Calendar cal = Calendar.getInstance();
                                    cal.add(Calendar.DAY_OF_YEAR, 30);
                                    Date thirtyDaysFromNow = cal.getTime();

                                    for (QueryDocumentSnapshot doc : inventorySnapshots) {
                                        inventoryList.add(doc);

                                        total++;
                                        String name = doc.getString("name");
                                        String category = doc.getString("category");
                                        String unit = doc.getString("unit");

                                        int qty = getQuantity(doc);

                                        if (qty > 0) inStockCount++;

                                        boolean isLowStock = (qty > 0 && qty < 10);
                                        if (isLowStock) lowStockCount++;

                                        boolean isExpiring = false;
                                        String expiryDateStr = doc.getString("expiryDate");
                                        if (expiryDateStr != null) {
                                            try {
                                                Date expiryDate = sdf.parse(expiryDateStr);
                                                if (expiryDate != null && expiryDate.before(thirtyDaysFromNow)) {
                                                    isExpiring = true;
                                                    expiringCount++;
                                                }
                                            } catch (ParseException ignored) {}
                                        }

                                        if (total <= 10) {
                                            addMedicineToContainer(llMedicineListContainer, doc, name, category, qty, unit, inflater, false);
                                        }

                                        if (isLowStock || isExpiring) {
                                            addMedicineToContainer(llAlertsContainer, doc, name, (isExpiring ? "Expiring Soon!" : "Low Stock!"), qty, unit, inflater, true);
                                        }
                                    }

                                    if (tvTotal != null) tvTotal.setText(String.valueOf(total));
                                    if (tvInStock != null) tvInStock.setText(String.valueOf(inStockCount));
                                    if (tvLowStock != null) tvLowStock.setText(String.valueOf(lowStockCount));
                                    if (tvExpiring != null) tvExpiring.setText(String.valueOf(expiringCount));

                                    if (llAlertsContainer.getChildCount() == 0) {
                                        TextView tvNoAlerts = new TextView(this);
                                        tvNoAlerts.setText(R.string.no_active_alerts);
                                        tvNoAlerts.setPadding(0, 10, 0, 10);
                                        llAlertsContainer.addView(tvNoAlerts);
                                    }

                                    // فحص بناء وتوليد الإشعارات للانتهاء والمخزون
                                    checkAndBuildNotifications(inventorySnapshots);
                                });
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating dashboard", Toast.LENGTH_SHORT).show());
    }

    // فحص وبناء قائمة الإشعارات الخاصة بالصلاحية والمخزون
    private void checkAndBuildNotifications(com.google.firebase.firestore.QuerySnapshot inventorySnapshots) {
        activeNotificationsList.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
        android.content.SharedPreferences sentNotifPrefs = getSharedPreferences("PharmacySentNotifs", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sentNotifPrefs.edit();

        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);

        int notifIdCounter = 1000;

        for (QueryDocumentSnapshot doc : inventorySnapshots) {
            String docId = doc.getId();
            String name = doc.getString("name");
            if (name == null || name.isEmpty()) name = "Medicine";
            int qty = getQuantity(doc);
            String expiryDateStr = doc.getString("expiryDate");

            // 1. فحص تاريخ انتهاء الصلاحية (3 أشهر، شهر، أسبوعان، أسبوع، منتهي)
            if (expiryDateStr != null && !expiryDateStr.isEmpty()) {
                try {
                    Date expiryDate = sdf.parse(expiryDateStr);
                    if (expiryDate != null) {
                        Calendar expiryCal = Calendar.getInstance();
                        expiryCal.setTime(expiryDate);
                        expiryCal.set(Calendar.HOUR_OF_DAY, 0);
                        expiryCal.set(Calendar.MINUTE, 0);
                        expiryCal.set(Calendar.SECOND, 0);
                        expiryCal.set(Calendar.MILLISECOND, 0);

                        long diffMillis = expiryCal.getTimeInMillis() - todayCal.getTimeInMillis();
                        long daysRemaining = diffMillis / (1000 * 60 * 60 * 24);

                        if (daysRemaining <= 0) {
                            String title = getString(R.string.expired);
                            String msg = getString(R.string.expired) + ": " + name + " (" + expiryDateStr + ")";
                            activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "expiry", "critical"));

                            String key = "exp_0_" + docId;
                            if (!sentNotifPrefs.getBoolean(key, false)) {
                                sendSystemNotification(title, msg, notifIdCounter++);
                                editor.putBoolean(key, true);
                                editor.putBoolean("notifs_seen", false);
                            }
                        } else if (daysRemaining <= 7) { // أسبوع واحد
                            String title = getString(R.string.expiring_1_week);
                            String msg = getString(R.string.expiring_1_week) + ": " + name + " (" + expiryDateStr + ")";
                            activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "expiry", "critical"));

                            String key = "exp_7_" + docId;
                            if (!sentNotifPrefs.getBoolean(key, false)) {
                                sendSystemNotification(title, msg, notifIdCounter++);
                                editor.putBoolean(key, true);
                                editor.putBoolean("notifs_seen", false);
                            }
                        } else if (daysRemaining <= 14) { // أسبوعان
                            String title = getString(R.string.expiring_2_weeks);
                            String msg = getString(R.string.expiring_2_weeks) + ": " + name + " (" + expiryDateStr + ")";
                            activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "expiry", "warning"));

                            String key = "exp_14_" + docId;
                            if (!sentNotifPrefs.getBoolean(key, false)) {
                                sendSystemNotification(title, msg, notifIdCounter++);
                                editor.putBoolean(key, true);
                                editor.putBoolean("notifs_seen", false);
                            }
                        } else if (daysRemaining <= 30) { // شهر واحد
                            String title = getString(R.string.expiring_1_month);
                            String msg = getString(R.string.expiring_1_month) + ": " + name + " (" + expiryDateStr + ")";
                            activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "expiry", "warning"));

                            String key = "exp_30_" + docId;
                            if (!sentNotifPrefs.getBoolean(key, false)) {
                                sendSystemNotification(title, msg, notifIdCounter++);
                                editor.putBoolean(key, true);
                                editor.putBoolean("notifs_seen", false);
                            }
                        } else if (daysRemaining <= 90) { // 3 أشهر
                            String title = getString(R.string.expiring_3_months);
                            String msg = getString(R.string.expiring_3_months) + ": " + name + " (" + expiryDateStr + ")";
                            activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "expiry", "info"));

                            String key = "exp_90_" + docId;
                            if (!sentNotifPrefs.getBoolean(key, false)) {
                                sendSystemNotification(title, msg, notifIdCounter++);
                                editor.putBoolean(key, true);
                                editor.putBoolean("notifs_seen", false);
                            }
                        }
                    }
                } catch (ParseException ignored) {}
            }

            // 2. فحص مستوى المخزون وإلغاء العلامات السابقة إذا تم ملأ المخزون
            if (qty > 10) {
                editor.remove("stock_10_" + docId);
                editor.remove("stock_3_" + docId);
                editor.remove("stock_0_" + docId);
            } else if (qty > 3) {
                editor.remove("stock_3_" + docId);
                editor.remove("stock_0_" + docId);

                String title = getString(R.string.low_stock_10);
                String msg = getString(R.string.low_stock_10) + ": " + name + " (" + qty + " " + getString(R.string.total) + ")";
                activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "stock", "warning"));

                String key = "stock_10_" + docId;
                if (!sentNotifPrefs.getBoolean(key, false)) {
                    sendSystemNotification(title, msg, notifIdCounter++);
                    editor.putBoolean(key, true);
                    editor.putBoolean("notifs_seen", false);
                }
            } else if (qty > 0) {
                editor.remove("stock_0_" + docId);

                String title = getString(R.string.critical_stock_3);
                String msg = getString(R.string.critical_stock_3) + ": " + name + " (" + qty + " " + getString(R.string.total) + ")";
                activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "stock", "critical"));

                String key = "stock_3_" + docId;
                if (!sentNotifPrefs.getBoolean(key, false)) {
                    sendSystemNotification(title, msg, notifIdCounter++);
                    editor.putBoolean(key, true);
                    editor.putBoolean("notifs_seen", false);
                }
            } else { // qty == 0
                String title = getString(R.string.out_of_stock);
                String msg = getString(R.string.out_of_stock) + ": " + name;
                activeNotificationsList.add(new PharmacyNotificationItem(title, msg, name, "stock", "critical"));

                String key = "stock_0_" + docId;
                if (!sentNotifPrefs.getBoolean(key, false)) {
                    sendSystemNotification(title, msg, notifIdCounter++);
                    editor.putBoolean(key, true);
                    editor.putBoolean("notifs_seen", false);
                }
            }
        }
        editor.apply();

        // تحديث شارة زر الإشعارات (تظهر فقط إذا لم تُشاهد بعد)
        boolean isNotifSeen = sentNotifPrefs.getBoolean("notifs_seen", false);
        if (tvNotificationBadge != null) {
            if (!activeNotificationsList.isEmpty() && !isNotifSeen) {
                tvNotificationBadge.setText(String.valueOf(activeNotificationsList.size()));
                tvNotificationBadge.setVisibility(View.VISIBLE);
            } else {
                tvNotificationBadge.setVisibility(View.GONE);
            }
        }
    }

    // عرض نافذة قائمة الإشعارات للمستخدم
    private void showNotificationsDialog() {
        // عند الدخول إلى القائمة يتم مسح شارة الرقم وتسجيل مشاهدة الإشعارات
        if (tvNotificationBadge != null) {
            tvNotificationBadge.setVisibility(View.GONE);
        }
        getSharedPreferences("PharmacySentNotifs", MODE_PRIVATE).edit().putBoolean("notifs_seen", true).apply();

        if (activeNotificationsList.isEmpty()) {
            Toast.makeText(this, R.string.no_notifications_present, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(20, 20, 20, 20);

        LayoutInflater inflater = LayoutInflater.from(this);
        SimpleDateFormat timeSdf = new SimpleDateFormat("HH:mm - d/M/yyyy", Locale.getDefault());
        String currentTime = timeSdf.format(new Date());

        for (PharmacyNotificationItem item : activeNotificationsList) {
            View v = inflater.inflate(R.layout.item_notification, container, false);
            TextView tvTitle = v.findViewById(R.id.tv_notif_title);
            TextView tvMessage = v.findViewById(R.id.tv_notif_message);
            TextView tvTime = v.findViewById(R.id.tv_notif_time);
            ImageView ivIcon = v.findViewById(R.id.iv_notif_icon);

            if (tvTitle != null) tvTitle.setText(item.title);
            if (tvMessage != null) tvMessage.setText(item.message);
            if (tvTime != null) tvTime.setText(currentTime);

            if ("critical".equals(item.severity)) {
                if (tvTitle != null) tvTitle.setTextColor(android.graphics.Color.RED);
                if (ivIcon != null) ivIcon.setColorFilter(android.graphics.Color.RED);
            } else if ("warning".equals(item.severity)) {
                if (tvTitle != null) tvTitle.setTextColor(android.graphics.Color.parseColor("#FF9800"));
                if (ivIcon != null) ivIcon.setColorFilter(android.graphics.Color.parseColor("#FF9800"));
            }

            // عند النقر على الإشعار يتم فتح شاشة المخزون
            v.setOnClickListener(view -> startActivity(new Intent(PharmacyActivity.this, StockActivity.class)));

            container.addView(v);
        }

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(container);

        new AlertDialog.Builder(this)
                .setTitle(R.string.pharmacy_notifications)
                .setView(scrollView)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    // إرسال إشعار للنظام في جهاز أندرويد
    private void sendSystemNotification(String title, String message, int notifId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "pharmacy_inventory_alerts";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Pharmacy Inventory Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for expiring medicines and low stock levels");
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify(notifId, builder.build());
        }
    }

    private void addMedicineToContainer(LinearLayout container, com.google.firebase.firestore.DocumentSnapshot doc, String name, String category, int qty, String unit, LayoutInflater inflater, boolean isAlert) {
        View itemView = inflater.inflate(R.layout.item_dashboard_medicine, container, false);
        TextView tvName = itemView.findViewById(R.id.tv_med_name);
        TextView tvCategory = itemView.findViewById(R.id.tv_med_category);
        TextView tvQty = itemView.findViewById(R.id.tv_med_qty);
        ImageView ivIcon = itemView.findViewById(R.id.iv_med_icon);

        tvName.setText(name);
        tvCategory.setText(category);
        tvQty.setText(qty + " " + (unit != null ? unit : getString(R.string.total)));

        if (isAlert) {
            tvCategory.setTextColor(android.graphics.Color.RED);
            ivIcon.setColorFilter(android.graphics.Color.RED);
        }

        if (doc != null) {
            itemView.setOnClickListener(v -> MedicineDetailsDialog.show(PharmacyActivity.this, doc));
        }

        container.addView(itemView);
    }

    private void addMedicineToContainer(LinearLayout container, String name, String category, int qty, String unit, LayoutInflater inflater, boolean isAlert) {
        addMedicineToContainer(container, null, name, category, qty, unit, inflater, isAlert);
    }

    private void updateNavHeader() {
        if (navigationView == null || mAuth.getCurrentUser() == null) return;

        View headerView = navigationView.getHeaderView(0);
        TextView tvHeaderName = headerView.findViewById(R.id.tv_header_pharmacy_name);
        TextView tvHeaderEmail = headerView.findViewById(R.id.tv_header_pharmacy_email);

        String userId = mAuth.getCurrentUser().getUid();
        tvHeaderEmail.setText(mAuth.getCurrentUser().getEmail());

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String pharmacyName = queryDocumentSnapshots.getDocuments().get(0).getString("pharmacyName");
                        if (pharmacyName != null) {
                            tvHeaderName.setText(pharmacyName);
                        }
                    }
                });
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
