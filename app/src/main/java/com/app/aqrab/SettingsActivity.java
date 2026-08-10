package com.app.aqrab;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    // أزرار التبديل لإشعارات النقص وانتهاء الصلاحية
    private SwitchCompat switchLowStock, switchExpiry;
    // الإعدادات المحلية
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "PharmacySettings";
    // حاوية خيارات الصيدلية (تظهر للصيادلة فقط)
    private LinearLayout llPharmacyOptions;
    // نصوص عرض بيانات المستخدم
    private TextView tvName, tvEmail;
    // صورة البروفايل
    private ImageView ivProfile;

    @Override
    protected void attachBaseContext(Context newBase) {
        // تطبيق اللغة المختارة
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تعيين الواجهة
        setContentView(R.layout.activity_settings);

        // تهيئة الإعدادات المحلية
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // تهيئة العناصر، المستمعين، وتحميل بيانات المستخدم
        initViews();
        setupListeners();
        loadUserInfo();
    }

    // دالة ربط العناصر بالكود
    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        switchLowStock = findViewById(R.id.switch_low_stock);
        switchExpiry = findViewById(R.id.switch_expiry);
        llPharmacyOptions = findViewById(R.id.ll_pharmacy_options);
        
        tvName = findViewById(R.id.tv_user_name_settings);
        tvEmail = findViewById(R.id.tv_user_email_settings);
        ivProfile = findViewById(R.id.iv_user_profile_settings);

        // تحميل الحالات المحفوظة لأزرار التنبيهات
        switchLowStock.setChecked(prefs.getBoolean("low_stock_alerts", true));
        switchExpiry.setChecked(prefs.getBoolean("expiry_alerts", true));
    }

    // جلب معلومات المستخدم وصورته من Firestore
    private void loadUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        tvEmail.setText(user.getEmail()); // تعيين الإيميل
        String uid = user.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // التحقق أولاً إذا كان المستخدم صيدلية
        db.collection("Pharmacies").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                // إظهار خيارات التنبيهات الخاصة بالصيدلية
                llPharmacyOptions.setVisibility(View.VISIBLE);
                tvName.setText(doc.getString("pharmacyName"));
                String photoUrl = doc.getString("photoUrl");
                // تحميل صورة الصيدلية إذا وجدت
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    ivProfile.setPadding(0, 0, 0, 0);
                    ivProfile.setColorFilter(null);
                    Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                }
            } else {
                // إذا لم يكن صيدلية، نتحقق من مجموعة المرضى (Users)
                db.collection("Users").document(uid).get().addOnSuccessListener(docU -> {
                    if (docU.exists()) {
                        tvName.setText(docU.getString("fullName"));
                        String photoUrl = docU.getString("photoUrl");
                        // تحميل صورة المريض
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ivProfile.setPadding(0, 0, 0, 0);
                            ivProfile.setColorFilter(null);
                            Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                        }
                    } else {
                        // استخدام البيانات الافتراضية من حساب Firebase
                        tvName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
                        if (user.getPhotoUrl() != null) {
                            ivProfile.setPadding(0, 0, 0, 0);
                            ivProfile.setColorFilter(null);
                            Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(ivProfile);
                        }
                    }
                });
            }
        });
    }

    // إعداد مستمعي الأحداث للأزرار والخيارات
    private void setupListeners() {
        // حفظ حالة تنبيهات المخزون المنخفض
        switchLowStock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("low_stock_alerts", isChecked).apply();
            Toast.makeText(this, isChecked ? R.string.low_stock_enabled : R.string.low_stock_disabled, Toast.LENGTH_SHORT).show();
        });

        // حفظ حالة تنبيهات تاريخ الانتهاء
        switchExpiry.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("expiry_alerts", isChecked).apply();
            Toast.makeText(this, isChecked ? R.string.expiry_enabled : R.string.expiry_disabled, Toast.LENGTH_SHORT).show();
        });

        // خيار تغيير لغة التطبيق
        findViewById(R.id.ll_change_language).setOnClickListener(v -> {
            String[] languages = {getString(R.string.language_english), "العربية", "Français"};
            String[] codes = {"en", "ar", "fr"};
            
            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_language)
                    .setItems(languages, (dialog, which) -> {
                        // تعيين اللغة الجديدة
                        LocaleHelper.setLocale(this, codes[which]);
                        
                        // إبلاغ المستخدم بتغيير اللغة
                        Toast.makeText(this, getString(R.string.lang_changed, languages[which]), Toast.LENGTH_SHORT).show();

                        // إعادة تشغيل التطبيق من الشاشة الرئيسية لتطبيق اللغة على كل شيء
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .show();
        });

        // خيار تغيير كلمة المرور (إرسال رابط استعادة)
        findViewById(R.id.ll_change_password).setOnClickListener(v -> {
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email != null) {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnSuccessListener(aVoid -> Toast.makeText(this, R.string.reset_email_sent, Toast.LENGTH_LONG).show())
                        .addOnFailureListener(e -> Toast.makeText(this, R.string.failed_reset_email, Toast.LENGTH_SHORT).show());
            }
        });

        // خيار طلب حذف الحساب
        findViewById(R.id.ll_delete_account).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_account_title)
                    .setMessage(R.string.delete_account_msg)
                    .setPositiveButton(R.string.delete_btn, (dialog, which) -> {
                        Toast.makeText(this, "Account deletion requested", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });

        // زر تسجيل الخروج
        findViewById(R.id.btn_logout_settings).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.logout_title)
                    .setMessage(R.string.logout_msg)
                    .setPositiveButton(R.string.nav_logout, (dialog, which) -> {
                        // مسح نوع المستخدم المحفوظ محلياً
                        getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().remove("user_role").apply();
                        
                        // تنفيذ تسجيل الخروج من Firebase
                        FirebaseAuth.getInstance().signOut();
                        // العودة لشاشة البداية
                        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}
