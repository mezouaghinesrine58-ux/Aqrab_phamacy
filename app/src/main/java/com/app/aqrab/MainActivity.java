package com.app.aqrab;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    // متغير لحفظ نوع المستخدم الذي تم اختياره (مريض أو صيدلية)
    String selectedRole = "";

    @Override
    protected void attachBaseContext(Context newBase) {
        // تطبيق اللغة المختارة عند بدء النشاط
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // الحصول على المستخدم الحالي من Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        // إذا كان المستخدم مسجلاً دخوله مسبقاً
        if (currentUser != null) {
            String uid = currentUser.getUid();
            
            // 1. محاولة الدخول المباشر عبر نوع المستخدم المحفوظ محلياً في الإعدادات (أسرع)
            android.content.SharedPreferences prefs = getSharedPreferences("AqrabPrefs", MODE_PRIVATE);
            String savedRole = prefs.getString("user_role", "");
            
            // إذا كان المستخدم مريضاً، توجه لواجهة المريض
            if ("Patient".equals(savedRole)) {
                Intent intent = new Intent(MainActivity.this, PatientActivity.class);
                startActivity(intent);
                finish(); // إغلاق هذه الشاشة
                return;
            } 
            // إذا كان صيدلية، توجه لواجهة الصيدلية
            else if ("Pharmacy".equals(savedRole)) {
                Intent intent = new Intent(MainActivity.this, PharmacyActivity.class);
                startActivity(intent);
                finish(); // إغلاق هذه الشاشة
                return;
            }

            // 2. إذا لم يوجد نوع مستخدم محفوظ محلياً، نتحقق من قاعدة بيانات Firestore
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            // البحث في مجموعة المرضى "Users"
            db.collection("Users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    // حفظ النوع والاسم محلياً لتسريع المرة القادمة
                    String name = doc.getString("fullName");
                    prefs.edit()
                            .putString("user_role", "Patient")
                            .putString("user_name", name)
                            .apply();
                    Intent intent = new Intent(MainActivity.this, PatientActivity.class);
                    intent.putExtra("user_name", name);
                    startActivity(intent);
                    finish();
                } else {
                    // إذا لم يكن مريضاً، ابحث في مجموعة الصيدليات "Pharmacies"
                    db.collection("Pharmacies").document(uid).get().addOnSuccessListener(docP -> {
                        if (docP.exists()) {
                            // حفظ النوع والاسم محلياً
                            String pName = docP.getString("pharmacyName");
                            prefs.edit()
                                    .putString("user_role", "Pharmacy")
                                    .putString("user_name", pName)
                                    .apply();
                            Intent intent = new Intent(MainActivity.this, PharmacyActivity.class);
                            intent.putExtra("PHARMACY_NAME", pName);
                            startActivity(intent);
                            finish();
                        }
                    });
                }
            });
        }
    }

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تفعيل ميزة العرض من الحافة إلى الحافة
        EdgeToEdge.enable(this);
        // تحديد واجهة النشاط
        setContentView(R.layout.activity_main);

        // إعداد هوامش النظام لتجنب التداخل مع أزرار التنقل أو النوتش
        if (findViewById(R.id.main) != null) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // ربط زر "إنشاء حساب" من واجهة XML
        View btnSignUp = findViewById(R.id.btn_sign_up);
        // ضبط وظيفة الضغط على الزر
        btnSignUp.setOnClickListener(v -> {
            // التحقق إذا لم يتم اختيار نوع مستخدم بعد
            if (selectedRole.isEmpty()) {
                Toast.makeText(MainActivity.this, R.string.select_role, Toast.LENGTH_SHORT).show();
            } else {
                // الانتقال لصفحة إنشاء حساب مع تمرير نوع المستخدم
                Intent intent = new Intent(MainActivity.this, SignUpActivity.class);
                intent.putExtra("user_role", selectedRole);
                startActivity(intent);
            }
        });

        // ربط زر "تسجيل الدخول" من واجهة XML
        View btnLogin = findViewById(R.id.btn_log_in);
        // ضبط وظيفة الضغط
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // التحقق من اختيار نوع المستخدم
                if (selectedRole.isEmpty()) {
                    Toast.makeText(MainActivity.this, R.string.select_role, Toast.LENGTH_SHORT).show();
                } else {
                    // الانتقال لصفحة تسجيل الدخول مع تمرير نوع المستخدم
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    intent.putExtra("user_role", selectedRole);
                    startActivity(intent);
                }
            }
        });

        // تعريف أزرار اختيار نوع المستخدم (مريض / صيدلية)
        com.google.android.material.button.MaterialButton btnPatient = findViewById(R.id.btn_patient);
        com.google.android.material.button.MaterialButton btnPharmacy = findViewById(R.id.btn_pharmacy);

        // وظيفة اختيار "مريض"
        btnPatient.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedRole = "Patient"; // تعيين النوع كمريض
                // تغيير شكل الزر ليصبح مختاراً (أبيض مع إطار أخضر)
                btnPatient.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                btnPatient.setTextColor(android.graphics.Color.parseColor("#2E5A44"));
                btnPatient.setStrokeWidth(4);

                // إعادة زر الصيدلية للوضع غير المختار (أخضر بالكامل)
                btnPharmacy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E5A44")));
                btnPharmacy.setTextColor(android.graphics.Color.WHITE);
                btnPharmacy.setStrokeWidth(0);
            }
        });

        // وظيفة اختيار "صيدلية"
        btnPharmacy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedRole = "Pharmacy"; // تعيين النوع كصيدلية
                // تغيير شكل الزر ليصبح مختاراً
                btnPharmacy.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                btnPharmacy.setTextColor(android.graphics.Color.parseColor("#2E5A44"));
                btnPharmacy.setStrokeWidth(4);
                // إعادة زر المريض للوضع غير المختار
                btnPatient.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2E5A44")));
                btnPatient.setTextColor(android.graphics.Color.WHITE);
                btnPatient.setStrokeWidth(0);
            }
        });

        // إعداد زر اختيار اللغة في الوسط بنظام قائمة منسدلة
        com.google.android.material.button.MaterialButton btnLang = findViewById(R.id.btn_language);
        View llOptions = findViewById(R.id.ll_language_options);
        
        updateLanguageButtonText(btnLang);
        
        // تبديل ظهور القائمة عند الضغط على الزر الرئيسي
        btnLang.setOnClickListener(v -> {
            if (llOptions.getVisibility() == View.VISIBLE) {
                llOptions.setVisibility(View.GONE);
            } else {
                llOptions.setVisibility(View.VISIBLE);
            }
        });

        // إعداد خيارات اللغات داخل القائمة المنسدلة
        findViewById(R.id.tv_lang_en).setOnClickListener(v -> changeLanguage("en"));
        findViewById(R.id.tv_lang_ar).setOnClickListener(v -> changeLanguage("ar"));
        findViewById(R.id.tv_lang_fr).setOnClickListener(v -> changeLanguage("fr"));
    }

    // دالة تغيير اللغة وإعادة تشغيل النشاط
    private void changeLanguage(String code) {
        LocaleHelper.setLocale(this, code);
        
        // إعادة تشغيل النشاط بطريقة تضمن تحديث كافة الموارد
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0); // إخفاء حركة الانتقال لجعلها تبدو كتحديث سريع
    }

    // تحديث نص الزر بناءً على اللغة الحالية
    private void updateLanguageButtonText(com.google.android.material.button.MaterialButton btn) {
        String lang = LocaleHelper.getLanguage(this);
        switch (lang) {
            case "ar": btn.setText("العربية"); break;
            case "fr": btn.setText("Français"); break;
            default: btn.setText("English"); break;
        }
    }
}
