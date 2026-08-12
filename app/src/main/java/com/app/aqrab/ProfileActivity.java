package com.app.aqrab;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // تعريف متغيرات واجهة المستخدم (حقول الإدخال، الأزرار، والصورة)
    private EditText etPharmacyName, etOwnerName, etEmail, etPhone, etAddress, etDescription;
    private ImageView ivPharmacyPhoto;
    private Button btnSave;
    private ImageButton btnBack;
    
    // تعريف متغيرات Firebase لقاعدة البيانات، المصادقة، والتخزين
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private String pharmacyDocId; // لتخزين معرف وثيقة الصيدلية في Firestore
    private Uri imageUri; // لتخزين مسار الصورة المختارة محلياً

    // مشغل نتيجة النشاط (Launcher) للتعامل مع اختيار صورة من المعرض
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    ivPharmacyPhoto.setImageURI(imageUri); // عرض الصورة المختارة في الواجهة
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // الحصول على مثيلات خدمات Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();

        // ربط العناصر وتحميل البيانات الحالية
        initViews();
        loadProfileData();

        // إغلاق النشاط عند الضغط على زر الرجوع
        btnBack.setOnClickListener(v -> finish());

        // تنفيذ عملية الحفظ عند الضغط على زر "حفظ"
        btnSave.setOnClickListener(v -> {
            if (imageUri != null) {
                // إذا تم اختيار صورة جديدة، يتم رفعها أولاً ثم حفظ البيانات
                uploadImageAndSaveProfile();
            } else {
                // إذا لم يتم اختيار صورة، يتم تحديث البيانات النصية فقط
                saveProfileData(null);
            }
        });

        // فتح معرض الصور عند الضغط على زر تعديل الصورة
        findViewById(R.id.fab_edit_photo).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });
    }

    // ربط عناصر واجهة المستخدم بمتغيرات الكود وتعريف القيم الأولية
    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save_profile);
        ivPharmacyPhoto = findViewById(R.id.iv_profile_pharmacy);
        
        etPharmacyName = findViewById(R.id.et_profile_pharmacy_name);
        etOwnerName = findViewById(R.id.et_profile_owner_name);
        etEmail = findViewById(R.id.et_profile_email);
        etPhone = findViewById(R.id.et_profile_phone);
        etAddress = findViewById(R.id.et_profile_address);
        etDescription = findViewById(R.id.et_profile_description);
        
        // عرض بريد المستخدم الحالي إذا كان متاحاً
        if (mAuth.getCurrentUser() != null) {
            etEmail.setText(mAuth.getCurrentUser().getEmail());
        }
    }

    // جلب بيانات الملف الشخصي للصيدلية من Firestore بناءً على معرف المستخدم الحالي
    private void loadProfileData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // الحصول على المعرف والبيانات لأول وثيقة مطابقة
                        pharmacyDocId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        Map<String, Object> data = queryDocumentSnapshots.getDocuments().get(0).getData();
                        
                        if (data != null) {
                            // تعبئة حقول الواجهة بالبيانات المسترجعة
                            etPharmacyName.setText((String) data.get("pharmacyName"));
                            etOwnerName.setText((String) data.get("ownerName"));
                            etPhone.setText((String) data.get("phone"));
                            etAddress.setText((String) data.get("address"));
                            etDescription.setText((String) data.get("description"));

                            // تحميل صورة الصيدلية باستخدام مكتبة Glide إذا وجد الرابط
                            String photoUrl = (String) data.get("photoUrl");
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(this).load(photoUrl).into(ivPharmacyPhoto);
                            }
                        }
                    }
                });
    }

    // رفع الصورة المختارة إلى Firebase Storage
    private void uploadImageAndSaveProfile() {
        btnSave.setEnabled(false);
        btnSave.setText("Uploading Image...");

        String fileName = "pharmacies/" + mAuth.getCurrentUser().getUid() + ".jpg";
        StorageReference ref = storage.getReference().child(fileName);

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(uri -> {
                    // بعد الرفع بنجاح، نحصل على الرابط ونقوم بحفظ بيانات الملف الشخصي
                    saveProfileData(uri.toString());
                }))
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Update Profile");
                    Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // حفظ أو تحديث بيانات الملف الشخصي في Firestore
    private void saveProfileData(String photoUrl) {
        String name = etPharmacyName.getText().toString().trim();
        String owner = etOwnerName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        // التحقق من إدخال اسم الصيدلية كشرط أساسي
        if (TextUtils.isEmpty(name)) {
            etPharmacyName.setError("Required");
            btnSave.setEnabled(true);
            btnSave.setText("Update Profile");
            return;
        }

        // تحضير الخريطة (Map) بالبيانات المراد تحديثها
        Map<String, Object> updates = new HashMap<>();
        updates.put("pharmacyName", name);
        updates.put("ownerName", owner);
        updates.put("phone", phone);
        updates.put("address", address);
        updates.put("description", description);
        if (photoUrl != null) {
            updates.put("photoUrl", photoUrl);
        }

        if (pharmacyDocId != null) {
            // تحديث الوثيقة الموجودة مسبقاً
            db.collection("Pharmacies").document(pharmacyDocId)
                    .update(updates)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSave.setEnabled(true);
                        btnSave.setText("Update Profile");
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // في حال عدم وجود وثيقة (حالة استثنائية)، يتم إنشاء وثيقة جديدة
            String userId = mAuth.getCurrentUser().getUid();
            updates.put("ownerId", userId);
            db.collection("Pharmacies").add(updates)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(this, "Profile created successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        }
    }
}
