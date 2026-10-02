package com.app.aqrab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    // نصوص عرض بيانات المستخدم
    private TextView tvName, tvEmail;
    // صورة البروفايل
    private ImageView ivProfile;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        setupListeners();
        loadUserInfo();
    }

    // دالة ربط العناصر بالكود
    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        tvName = findViewById(R.id.tv_user_name_settings);
        tvEmail = findViewById(R.id.tv_user_email_settings);
        ivProfile = findViewById(R.id.iv_user_profile_settings);
    }

    // جلب معلومات المستخدم وصورته من Firestore
    private void loadUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        tvEmail.setText(user.getEmail());
        String uid = user.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Pharmacies").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                tvName.setText(doc.getString("pharmacyName"));
                String photoUrl = doc.getString("photoUrl");
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    ivProfile.setPadding(0, 0, 0, 0);
                    ivProfile.setColorFilter(null);
                    Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                }
            } else {
                db.collection("Users").document(uid).get().addOnSuccessListener(docU -> {
                    if (docU.exists()) {
                        tvName.setText(docU.getString("fullName"));
                        String photoUrl = docU.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            ivProfile.setPadding(0, 0, 0, 0);
                            ivProfile.setColorFilter(null);
                            Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                        }
                    } else {
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
        // خيار تغيير لغة التطبيق
        findViewById(R.id.ll_change_language).setOnClickListener(v -> {
            String[] languages = {getString(R.string.language_english), "العربية", "Français"};
            String[] codes = {"en", "ar", "fr"};

            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_language)
                    .setItems(languages, (dialog, which) -> {
                        LocaleHelper.setLocale(this, codes[which]);
                        Toast.makeText(this, getString(R.string.lang_changed, languages[which]), Toast.LENGTH_SHORT).show();

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

        // خيار طلب حذف الحساب مع نافذة التأكيد
        findViewById(R.id.ll_delete_account).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_account_title)
                    .setMessage(R.string.delete_account_msg)
                    .setPositiveButton(R.string.delete_btn, (dialog, which) -> deleteAccount())
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });

        // زر تسجيل الخروج
        findViewById(R.id.btn_logout_settings).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.logout_title)
                    .setMessage(R.string.logout_msg)
                    .setPositiveButton(R.string.nav_logout, (dialog, which) -> {
                        getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().remove("user_role").apply();
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });
    }

    // دالة حذف حساب المستخدم وبياناته نهائياً
    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        ProgressBar progressBar = findViewById(R.id.progress_bar_settings);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // 1. حذف مستند المستخدم من Firestore (سواء مريض أو صيدلية)
        db.collection("Users").document(uid).delete()
                .addOnCompleteListener(task -> {
                    db.collection("Pharmacies").document(uid).delete()
                            .addOnCompleteListener(task2 -> {
                                // 2. حذف الحساب برمجياً من نظام Firebase Authentication
                                user.delete().addOnCompleteListener(task3 -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    if (task3.isSuccessful()) {
                                        getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().clear().apply();
                                        Toast.makeText(SettingsActivity.this, R.string.account_deleted, Toast.LENGTH_LONG).show();

                                        Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    } else {
                                        Toast.makeText(SettingsActivity.this, R.string.delete_reauth_error, Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                });
    }
}
