package com.app.aqrab;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    // أزرار عرض الكل والفلترة التي تمت إضافتها اليوم
    private TextView tvViewAllAlerts; // متغير لزر عرض كل التنبيهات
    private LinearLayout llFilter; // متغير لزر تصفية (فلترة) قائمة الأدوية
    // حقل البحث بداخل لوحة التحكم
    private EditText etSearch;
    // كائنات Firebase للتعامل مع البيانات والمصادقة
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    // قائمة لحفظ المخزون الحالي للبحث السريع
    private List<QueryDocumentSnapshot> inventoryList = new ArrayList<>();
    // القائمة الجانبية (Drawer)
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

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
        
        // جلب اسم الصيدلية الممرر من شاشة الدخول وعرضه
        String pharmacyName = getIntent().getStringExtra("PHARMACY_NAME");
        if (pharmacyName != null && !pharmacyName.isEmpty()) {
            TextView tvPharmacyName = findViewById(R.id.tv_pharmacy_name);
            tvPharmacyName.setText(pharmacyName);
        }
    }
    private void initViews() {
        // ربط نصوص الإحصائيات (الإجمالي، المتوفر، الناقص، منتهي الصلاحية)
        tvTotal = findViewById(R.id.tv_total_medicines);
        tvInStock = findViewById(R.id.tv_in_stock);
        tvLowStock = findViewById(R.id.tv_low_stock);
        tvExpiring = findViewById(R.id.tv_expiring_soon);
        
        // ربط الحاويات (التنبيهات وقائمة الأدوية)
        llAlertsContainer = findViewById(R.id.ll_alerts_container);
        llMedicineListContainer = findViewById(R.id.ll_medicine_list_container);
        // ربط الأزرار الجديدة (عرض الكل والفلترة)
        tvViewAllAlerts = findViewById(R.id.tv_view_all_alerts);
        llFilter = findViewById(R.id.ll_filter);
        // ربط حقل البحث
        etSearch = findViewById(R.id.et_search_dashboard);
        
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
                // استدعاء دالة الفلترة عند كل حرف يكتبه المستخدم
                filterMedicineList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // دالة تصفية قائمة الأدوية محلياً بناءً على نص البحث
    private void filterMedicineList(String query) {
        llMedicineListContainer.removeAllViews(); // مسح القائمة الحالية
        LayoutInflater inflater = LayoutInflater.from(this); // محرك بناء الواجهات
        int count = 0; // عداد النتائج

        // تكرار عبر قائمة الأدوية المخزنة محلياً
        for (QueryDocumentSnapshot doc : inventoryList) {
            String name = doc.getString("name");
            // التحقق مما إذا كان الاسم يحتوي على نص البحث
            if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                count++;
                if (count <= 20) { // عرض أول 20 نتيجة فقط للأداء
                    addMedicineToContainer(llMedicineListContainer, name, doc.getString("category"), 
                            getQuantity(doc), doc.getString("unit"), inflater, false);
                }
            }
        }
        
        // عرض رسالة في حال عدم وجود نتائج مطابقة للبحث
        if (count == 0 && !query.isEmpty()) {
            TextView tvNoResults = new TextView(this);
            tvNoResults.setText(R.string.no_medicines_match);
            tvNoResults.setPadding(20, 20, 20, 20);
            llMedicineListContainer.addView(tvNoResults);
        } else if (query.isEmpty()) {
            // إعادة عرض القائمة الافتراضية عند مسح حقل البحث
            renderTopMedicines();
        }
    }

    // دالة آمنة لجلب الكمية من الوثيقة وتحويلها لرقم صحيح
    private int getQuantity(QueryDocumentSnapshot doc) {
        Object qtyObj = doc.get("quantity");
        if (qtyObj != null) {
            try {
                return Integer.parseInt(qtyObj.toString());
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    // عرض قائمة افتراضية تحتوي على أول 10 أدوية
    private void renderTopMedicines() {
        llMedicineListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int displayCount = 0;
        for (QueryDocumentSnapshot doc : inventoryList) {
            displayCount++;
            if (displayCount <= 10) {
                addMedicineToContainer(llMedicineListContainer, doc.getString("name"), 
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

        // إعداد خيارات القائمة الجانبية (تسجيل الخروج، الملف الشخصي، الإعدادات...)
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_logout) {
                    // مسح بيانات الجلسة وتسجيل الخروج والعودة لشاشة البداية
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
                
                // إغلاق القائمة بعد اختيار العنصر
                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return true;
            });

            // تحديث بيانات المستخدم المعروضة في هيدر القائمة الجانبية
            updateNavHeader();
        }

        // زر إضافة دواء جديد للمخزون
        LinearLayout btnAddMedicine = findViewById(R.id.ll_add_medicine);
        if (btnAddMedicine != null) {
            btnAddMedicine.setOnClickListener(v -> {
                Intent intent = new Intent(PharmacyActivity.this, AddMedicineActivity.class);
                startActivity(intent);
            });
        }

        // زر عرض كامل المخزون الحالي
        LinearLayout btnStock = findViewById(R.id.ll_stock);
        if (btnStock != null) {
            btnStock.setOnClickListener(v -> {
                Intent intent = new Intent(PharmacyActivity.this, StockActivity.class);
                startActivity(intent);
            });
        }

        // زر تسجيل عملية بيع جديدة
        LinearLayout btnSell = findViewById(R.id.ll_sell);
        if (btnSell != null) {
            btnSell.setOnClickListener(v -> {
                Intent intent = new Intent(PharmacyActivity.this, SellActivity.class);
                startActivity(intent);
            });
        }

        // زر عرض سجل العمليات السابقة
        LinearLayout btnHistory = findViewById(R.id.ll_history);
        if (btnHistory != null) {
            btnHistory.setOnClickListener(v -> {
                Intent intent = new Intent(PharmacyActivity.this, HistoryActivity.class);
                startActivity(intent);
            });
        }

        //  زر عرض الكل للتنبيهات ينتقل لشاشة المخزون
        if (tvViewAllAlerts != null) {
            tvViewAllAlerts.setOnClickListener(v -> {
                startActivity(new Intent(PharmacyActivity.this, StockActivity.class));
            });
        }

        //  زر التصفية يفتح نافذة خيارات التصفية
        if (llFilter != null) {
            llFilter.setOnClickListener(v -> showFilterDialog());
        }
    }

    // دالة عرض نافذة اختيار نوع التصفية (الكل، المتوفر، الناقص، القريب من الانتهاء)
    private void showFilterDialog() {
        String[] options = {getString(R.string.view_all), getString(R.string.in_stock), getString(R.string.low_stock), getString(R.string.expiring)};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.filter_medicines);
        builder.setItems(options, (dialog, which) -> {
            // تنفيذ التصفية بناءً على الخيار الذي تم نقره
            applyFilter(which);
        });
        builder.show();
    }

    // دالة تطبيق التصفية المختارة على قائمة الأدوية في لوحة التحكم
    private void applyFilter(int filterIndex) {
        llMedicineListContainer.removeAllViews(); // مسح القائمة الحالية
        LayoutInflater inflater = LayoutInflater.from(this);
        
        // إعداد التاريخ لحساب الأدوية التي تنتهي خلال 30 يوماً
        SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 30);
        Date thirtyDaysFromNow = cal.getTime();

        // التكرار عبر المخزون وتطبيق شروط التصفية
        for (QueryDocumentSnapshot doc : inventoryList) {
            int qty = getQuantity(doc);
            boolean show = false;

            switch (filterIndex) {
                case 0: // الخيار: عرض الكل
                    show = true;
                    break;
                case 1: // الخيار: المتوفر فقط
                    show = qty > 0;
                    break;
                case 2: // الخيار: المخزون المنخفض (أقل من 10)
                    show = (qty > 0 && qty < 10);
                    break;
                case 3: // الخيار: قريب من انتهاء الصلاحية
                    String expiryDateStr = doc.getString("expiryDate");
                    if (expiryDateStr != null) {
                        try {
                            Date expiryDate = sdf.parse(expiryDateStr);
                            if (expiryDate != null && expiryDate.before(thirtyDaysFromNow)) {
                                show = true;
                            }
                        } catch (ParseException e) {}
                    }
                    break;
            }

            // إذا انطبق الشرط، يتم إضافة الدواء للقائمة المعروضة
            if (show) {
                addMedicineToContainer(llMedicineListContainer, doc.getString("name"), 
                        doc.getString("category"), qty, doc.getString("unit"), inflater, false);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // تحديث بيانات لوحة التحكم تلقائياً عند العودة للنشاط
        updateDashboardData();
    }

    // دالة جلب البيانات الحية من Firestore وتحديث لوحة التحكم
    private void updateDashboardData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        // جلب وثيقة الصيدلية الخاصة بالمستخدم الحالي
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        // جلب مجموعة الأدوية (Inventory) الخاصة بهذه الصيدلية
                        db.collection("Pharmacies").document(pharmacyId)
                                .collection("Inventory")
                                .get()
                                .addOnSuccessListener(inventorySnapshots -> {
                                    int total = 0; // إجمالي الأنواع
                                    int inStockCount = 0; // المتوفر
                                    int lowStockCount = 0; // الناقص
                                    int expiringCount = 0; // القريب من الانتهاء

                                    llAlertsContainer.removeAllViews();
                                    llMedicineListContainer.removeAllViews();
                                    inventoryList.clear(); // مسح القائمة المحلية لإعادة بنائها
                                    LayoutInflater inflater = LayoutInflater.from(this);

                                    // تحضير تاريخ المقارنة لانتهاء الصلاحية (بعد 30 يوم)
                                    SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy", Locale.getDefault());
                                    Calendar cal = Calendar.getInstance();
                                    cal.add(Calendar.DAY_OF_YEAR, 30);
                                    Date thirtyDaysFromNow = cal.getTime();

                                    // معالجة كل دواء في المخزون
                                    for (QueryDocumentSnapshot doc : inventorySnapshots) {
                                        inventoryList.add(doc); // إضافة للدراسة المحلية
                                        
                                        total++;
                                        String name = doc.getString("name");
                                        String category = doc.getString("category");
                                        String unit = doc.getString("unit");
                                        
                                        int qty = getQuantity(doc);

                                        if (qty > 0) inStockCount++;
                                        
                                        // فحص النقص
                                        boolean isLowStock = (qty > 0 && qty < 10);
                                        if (isLowStock) lowStockCount++;

                                        // فحص انتهاء الصلاحية القريب
                                        boolean isExpiring = false;
                                        String expiryDateStr = doc.getString("expiryDate");
                                        if (expiryDateStr != null) {
                                            try {
                                                Date expiryDate = sdf.parse(expiryDateStr);
                                                if (expiryDate != null && expiryDate.before(thirtyDaysFromNow)) {
                                                    isExpiring = true;
                                                    expiringCount++;
                                                }
                                            } catch (ParseException e) {}
                                        }

                                        // إضافة الدواء للقائمة الرئيسية (أول 10 عناصر فقط)
                                        if (total <= 10) {
                                            addMedicineToContainer(llMedicineListContainer, name, category, qty, unit, inflater, false);
                                        }

                                        // إضافة الدواء لقسم التنبيهات إذا كان ناقصاً أو قارب الانتهاء
                                        if (isLowStock || isExpiring) {
                                            addMedicineToContainer(llAlertsContainer, name, (isExpiring ? "Expiring Soon!" : "Low Stock!"), qty, unit, inflater, true);
                                        }
                                    }

                                    // عرض الأرقام النهائية في واجهة المستخدم
                                    tvTotal.setText(String.valueOf(total));
                                    tvInStock.setText(String.valueOf(inStockCount));
                                    tvLowStock.setText(String.valueOf(lowStockCount));
                                    tvExpiring.setText(String.valueOf(expiringCount));
                                    
                                    // عرض نص افتراضي في حال عدم وجود أي تنبيهات حالية
                                    if (llAlertsContainer.getChildCount() == 0) {
                                        TextView tvNoAlerts = new TextView(this);
                                        tvNoAlerts.setText(R.string.no_active_alerts);
                                        tvNoAlerts.setPadding(0, 10, 0, 10);
                                        llAlertsContainer.addView(tvNoAlerts);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating dashboard", Toast.LENGTH_SHORT).show();
                });
    }

    // دالة مساعدة لإنشاء صف عرض الدواء في القوائم
    private void addMedicineToContainer(LinearLayout container, String name, String category, int qty, String unit, LayoutInflater inflater, boolean isAlert) {
        View itemView = inflater.inflate(R.layout.item_dashboard_medicine, container, false);
        TextView tvName = itemView.findViewById(R.id.tv_med_name);
        TextView tvCategory = itemView.findViewById(R.id.tv_med_category);
        TextView tvQty = itemView.findViewById(R.id.tv_med_qty);
        ImageView ivIcon = itemView.findViewById(R.id.iv_med_icon);

        tvName.setText(name);
        tvCategory.setText(category);
        tvQty.setText(qty + " " + (unit != null ? unit : getString(R.string.total)));

        // إذا كان الدواء ضمن التنبيهات، نغير لونه للأحمر للفت الانتباه
        if (isAlert) {
            tvCategory.setTextColor(android.graphics.Color.RED);
            ivIcon.setColorFilter(android.graphics.Color.RED);
        }

        container.addView(itemView); // إضافة العنصر للحاوية المحددة
    }

    // دالة تحديث بيانات الهيدر في القائمة الجانبية (اسم الصيدلية وبريدها)
    private void updateNavHeader() {
        if (navigationView == null || mAuth.getCurrentUser() == null) return;
        
        View headerView = navigationView.getHeaderView(0);
        TextView tvHeaderName = headerView.findViewById(R.id.tv_header_pharmacy_name);
        TextView tvHeaderEmail = headerView.findViewById(R.id.tv_header_pharmacy_email);
        
        String userId = mAuth.getCurrentUser().getUid();
        tvHeaderEmail.setText(mAuth.getCurrentUser().getEmail());
        
        // جلب اسم الصيدلية من قاعدة البيانات لعرضه في القائمة
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

    // دالة معالجة زر العودة: إغلاق القائمة الجانبية إذا كانت مفتوحة
    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
}
