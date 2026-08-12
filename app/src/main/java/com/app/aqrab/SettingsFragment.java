package com.app.aqrab;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class SettingsFragment extends Fragment {

    // أزرار التبديل لتنبيهات المخزون المنخفض وتاريخ الانتهاء
    private SwitchCompat switchLowStock, switchExpiry;
    // كائن الإعدادات المحفوظة محلياً
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "PharmacySettings";
    // حاوية خيارات الصيدلية (تظهر فقط للصيادلة)
    private LinearLayout llPharmacyOptions;
    // نصوص عرض الاسم والبريد الإلكتروني
    private TextView tvName, tvEmail;
    // صورة البروفايل ومؤشر التحميل
    private ImageView ivProfile;
    private ProgressBar progressBar;

    // مشغل لاختيار صورة من معرض الصور بالهاتف
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    // إذا اختار المستخدم صورة، نبدأ عملية الرفع لـ Firebase
                    uploadImageToFirebase(uri);
                }
            }
    );
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_settings, container, false);
        // الحصول على الإعدادات المحفوظة محلياً
        prefs = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // تهيئة العناصر، ربط المستمعين، وتحميل بيانات المستخدم الحالي
        initViews(view);
        setupListeners(view);
        loadUserInfo();

        return view;
    }

    // دالة ربط عناصر الواجهة بمتغيرات الجافا
    private void initViews(View view) {
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setVisibility(View.GONE); // إخفاء زر العودة لأنه يعرض كـ Fragment الآن

        switchLowStock = view.findViewById(R.id.switch_low_stock);
        switchExpiry = view.findViewById(R.id.switch_expiry);
        llPharmacyOptions = view.findViewById(R.id.ll_pharmacy_options);
        
        tvName = view.findViewById(R.id.tv_user_name_settings);
        tvEmail = view.findViewById(R.id.tv_user_email_settings);
        ivProfile = view.findViewById(R.id.iv_user_profile_settings);
        progressBar = view.findViewById(R.id.progress_bar_settings);

        // ضبط حالة أزرار التبديل بناءً على القيم المحفوظة سابقاً
        switchLowStock.setChecked(prefs.getBoolean("low_stock_alerts", true));
        switchExpiry.setChecked(prefs.getBoolean("expiry_alerts", true));
    }

    // دالة جلب بيانات المستخدم (صيدلية أو مريض) من Firestore
    private void loadUserInfo() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        tvEmail.setText(user.getEmail()); // عرض البريد الإلكتروني
        String uid = user.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        
        // أولاً: البحث في مجموعة الصيدليات
        db.collection("Pharmacies").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                llPharmacyOptions.setVisibility(View.VISIBLE); // إظهار خيارات الصيدلية
                tvName.setText(doc.getString("pharmacyName"));
                String photoUrl = doc.getString("photoUrl");
                if (photoUrl != null && !photoUrl.isEmpty() && isAdded()) {
                    // تحميل الصورة باستخدام مكتبة Glide
                    ivProfile.setPadding(0, 0, 0, 0);
                    ivProfile.setColorFilter(null);
                    Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                }
            } else {
                // ثانياً: إذا لم يكن صيدلية، البحث في مجموعة المستخدمين (المرضى)
                db.collection("Users").document(uid).get().addOnSuccessListener(docU -> {
                    if (docU.exists()) {
                        tvName.setText(docU.getString("fullName"));
                        String photoUrl = docU.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty() && isAdded()) {
                            ivProfile.setPadding(0, 0, 0, 0);
                            ivProfile.setColorFilter(null);
                            Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                        }
                    } else {
                        // استخدام الاسم الافتراضي إذا لم يوجد في أي مجموعة
                        tvName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User");
                        if (user.getPhotoUrl() != null && isAdded()) {
                            ivProfile.setPadding(0, 0, 0, 0);
                            ivProfile.setColorFilter(null);
                            Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(ivProfile);
                        }
                    }
                });
            }
        });
    }

    // دالة إعداد مستمعي الأحداث للعناصر القابلة للضغط والتبديل
    private void setupListeners(View view) {
        //  الضغط على الصورة لفتح معرض الصور وتغييرها
        ivProfile.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        // حفظ حالة تنبيهات المخزون المنخفض عند التغيير
        switchLowStock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("low_stock_alerts", isChecked).apply();
            Toast.makeText(getContext(), isChecked ? R.string.low_stock_enabled : R.string.low_stock_disabled, Toast.LENGTH_SHORT).show();
        });

        // حفظ حالة تنبيهات تاريخ الانتهاء عند التغيير
        switchExpiry.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("expiry_alerts", isChecked).apply();
            Toast.makeText(getContext(), isChecked ? R.string.expiry_enabled : R.string.expiry_disabled, Toast.LENGTH_SHORT).show();
        });

        // خيار تغيير لغة التطبيق عبر ديالوج
        view.findViewById(R.id.ll_change_language).setOnClickListener(v -> {
            String[] languages = {getString(R.string.language_english), "العربية", "Français"};
            String[] codes = {"en", "ar", "fr"};
            
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.select_language)
                    .setItems(languages, (dialog, which) -> {
                        // تعيين اللغة وإعادة تشغيل التطبيق لتفعيل التغيير
                        LocaleHelper.setLocale(getContext(), codes[which]);
                        
                        // إبلاغ المستخدم بتغيير اللغة
                        Toast.makeText(getContext(), getString(R.string.lang_changed, languages[which]), Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(getActivity(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish();
                    })
                    .show();
        });

        // خيار تغيير كلمة المرور عبر إرسال بريد إلكتروني
        view.findViewById(R.id.ll_change_password).setOnClickListener(v -> {
            String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
            if (email != null) {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), R.string.reset_email_sent, Toast.LENGTH_LONG).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), R.string.failed_reset_email, Toast.LENGTH_SHORT).show());
            }
        });

        //  خيار حذف الحساب نهائياً
        view.findViewById(R.id.ll_delete_account).setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.delete_account_title)
                    .setMessage(R.string.delete_account_msg)
                    .setPositiveButton(R.string.delete_btn, (dialog, which) -> deleteAccount())
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });

        // خيار تسجيل الخروج من التطبيق
        view.findViewById(R.id.btn_logout_settings).setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.logout_title)
                    .setMessage(R.string.logout_msg)
                    .setPositiveButton(R.string.nav_logout, (dialog, which) -> {
                        // مسح نوع الدور المسجل محلياً وتسجيل الخروج من Firebase
                        getActivity().getSharedPreferences("AqrabPrefs", Context.MODE_PRIVATE).edit().remove("user_role").apply();
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(getActivity(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish();
                    })
                    .setNegativeButton(R.string.cancel_btn, null)
                    .show();
        });
    }

    // دالة رفع الصورة المختارة إلى Firebase Storage
    private void uploadImageToFirebase(Uri imageUri) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE); // إظهار مؤشر التحميل
        ivProfile.setEnabled(false); // تعطيل الضغط أثناء التحميل

        String uid = user.getUid();
        // تحديد مسار تخزين الصورة في Firebase Storage
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + uid + ".jpg");

        // البدء في رفع الملف
        storageRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
            // بعد نجاح الرفع، الحصول على رابط الصورة المباشر
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                // تحديث الرابط في قاعدة بيانات Firestore
                updateUserPhotoUrl(downloadUrl);
            });
        }).addOnFailureListener(e -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            ivProfile.setEnabled(true);
            Toast.makeText(getContext(), getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        });
    }

    // دالة تحديث رابط الصورة في مستند المستخدم بـ Firestore
    private void updateUserPhotoUrl(String url) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // فحص ما إذا كان المستخدم صيدلية أو مريض لتحديث المكان الصحيح
        db.collection("Pharmacies").document(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                db.collection("Pharmacies").document(uid).update("photoUrl", url)
                        .addOnSuccessListener(aVoid -> onPhotoUpdateSuccess(url));
            } else {
                db.collection("Users").document(uid).update("photoUrl", url)
                        .addOnSuccessListener(aVoid -> onPhotoUpdateSuccess(url));
            }
        });
    }

    // دالة يتم استدعاؤها عند نجاح تحديث الصورة بالكامل
    private void onPhotoUpdateSuccess(String url) {
        if (progressBar != null) progressBar.setVisibility(View.GONE); // إخفاء مؤشر التحميل
        ivProfile.setEnabled(true); // إعادة تفعيل الضغط
        ivProfile.setPadding(0, 0, 0, 0);
        ivProfile.setColorFilter(null);
        // عرض الصورة الجديدة فوراً
        Glide.with(this).load(url).circleCrop().into(ivProfile);
        Toast.makeText(getContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
    }

    // دالة حذف حساب المستخدم وبياناته نهائياً
    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

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
                                        Toast.makeText(getContext(), R.string.account_deleted, Toast.LENGTH_LONG).show();
                                        // العودة لشاشة البداية بعد الحذف
                                        Intent intent = new Intent(getActivity(), MainActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        getActivity().finish();
                                    } else {
                                        // في حال فشل الحذف لسبب أمني (يتطلب تسجيل دخول حديث)
                                        Toast.makeText(getContext(), R.string.delete_reauth_error, Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                });
    }
}
