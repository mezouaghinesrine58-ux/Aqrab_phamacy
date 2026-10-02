package com.app.aqrab;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public class SettingsFragment extends Fragment {

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
                    uploadImageToFirebase(uri);
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView (@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_settings, container, false);
        initViews(view);
        setupListeners(view);
        loadUserInfo();

        return view;
    }

    // دالة ربط عناصر الواجهة بمتغيرات الجافا
    private void initViews(View view) {
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setVisibility(View.GONE);

        tvName = view.findViewById(R.id.tv_user_name_settings);
        tvEmail = view.findViewById(R.id.tv_user_email_settings);
        ivProfile = view.findViewById(R.id.iv_user_profile_settings);
        progressBar = view.findViewById(R.id.progress_bar_settings);
    }

    // دالة جلب بيانات المستخدم (صيدلية أو مريض) من Firestore
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
                if (photoUrl != null && !photoUrl.isEmpty() && isAdded()) {
                    ivProfile.setPadding(0, 0, 0, 0);
                    ivProfile.setColorFilter(null);
                    Glide.with(this).load(photoUrl).circleCrop().into(ivProfile);
                }
            } else {
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
        ivProfile.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // خيار تغيير لغة التطبيق عبر ديالوج
        view.findViewById(R.id.ll_change_language).setOnClickListener(v -> {
            String[] languages = {getString(R.string.language_english), "العربية", "Français"};
            String[] codes = {"en", "ar", "fr"};

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.select_language)
                    .setItems(languages, (dialog, which) -> {
                        LocaleHelper.setLocale(getContext(), codes[which]);
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

        // خيار حذف الحساب نهائياً
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

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        ivProfile.setEnabled(false);

        String uid = user.getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("profile_images/" + uid + ".jpg");

        storageRef.putFile(imageUri).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                String downloadUrl = uri.toString();
                updateUserPhotoUrl(downloadUrl);
            });
        }).addOnFailureListener(e -> {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            ivProfile.setEnabled(true);
            Toast.makeText(getContext(), getString(R.string.upload_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        });
    }

    private void updateUserPhotoUrl(String url) {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

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

    private void onPhotoUpdateSuccess(String url) {
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        ivProfile.setEnabled(true);
        ivProfile.setPadding(0, 0, 0, 0);
        ivProfile.setColorFilter(null);
        Glide.with(this).load(url).circleCrop().into(ivProfile);
        Toast.makeText(getContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
    }

    private void deleteAccount() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        db.collection("Users").document(uid).delete()
                .addOnCompleteListener(task -> {
                    db.collection("Pharmacies").document(uid).delete()
                            .addOnCompleteListener(task2 -> {
                                user.delete().addOnCompleteListener(task3 -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    if (task3.isSuccessful()) {
                                        Toast.makeText(getContext(), R.string.account_deleted, Toast.LENGTH_LONG).show();
                                        Intent intent = new Intent(getActivity(), MainActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        getActivity().finish();
                                    } else {
                                        Toast.makeText(getContext(), R.string.delete_reauth_error, Toast.LENGTH_LONG).show();
                                    }
                                });
                            });
                });
    }
}
