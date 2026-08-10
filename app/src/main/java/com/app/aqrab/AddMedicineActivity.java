package com.app.aqrab;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AddMedicineActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // حقول إدخال بيانات الدواء
    private EditText etName, etBrand, etStrength, etQuantity, etPurchasePrice, etSellingPrice, etBatch, etManufactureDate, etExpiryDate;
    // قوائم الاختيار (الفئة، الشكل الدوائي، الوحدة، وقت التنبيه)
    private Spinner spinnerCategory, spinnerForm, spinnerUnit, spinnerAlert;
    // زر الحفظ
    private Button btnSave;
    // كائنات Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تعيين الواجهة
        setContentView(R.layout.activity_add_medicine);

        // تهيئة Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // تهيئة العناصر والوظائف
        initViews();
        setupSpinners();
        setupDatePickers();

        // تفعيل زر الحفظ
        btnSave.setOnClickListener(v -> saveMedicine());
        
        // زر العودة
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    // ربط عناصر XML بالكود
    private void initViews() {
        etName = findViewById(R.id.et_medicine_name);
        etBrand = findViewById(R.id.et_brand);
        etStrength = findViewById(R.id.et_strength);
        etQuantity = findViewById(R.id.et_quantity);
        etPurchasePrice = findViewById(R.id.et_purchase_price);
        etSellingPrice = findViewById(R.id.et_selling_price);
        etBatch = findViewById(R.id.et_batch_number);
        etManufactureDate = findViewById(R.id.et_manufacture_date);
        etExpiryDate = findViewById(R.id.et_expiry_date);
        
        spinnerCategory = findViewById(R.id.spinner_category);
        spinnerForm = findViewById(R.id.spinner_form);
        spinnerUnit = findViewById(R.id.spinner_unit);
        spinnerAlert = findViewById(R.id.spinner_alert);
        
        btnSave = findViewById(R.id.btn_save_medicine);
    }

    // إعداد مستمعي حقول التاريخ لفتح نافذة التقويم
    private void setupDatePickers() {
        etManufactureDate.setOnClickListener(v -> showDatePicker(etManufactureDate));
        etExpiryDate.setOnClickListener(v -> showDatePicker(etExpiryDate));
    }

    // دالة إظهار نافذة اختيار التاريخ
    private void showDatePicker(EditText editText) {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            String date = dayOfMonth + "/" + (month1 + 1) + "/" + year1;
            editText.setText(date);
        }, year, month, day);
        datePickerDialog.show();
    }

    // دالة حفظ بيانات الدواء في قاعدة البيانات
    private void saveMedicine() {
        // جلب البيانات من الحقول
        String name = etName.getText().toString().trim();
        String brand = etBrand.getText().toString().trim();
        String strength = etStrength.getText().toString().trim();
        String quantity = etQuantity.getText().toString().trim();
        String purchasePrice = etPurchasePrice.getText().toString().trim();
        String sellingPrice = etSellingPrice.getText().toString().trim();
        String batch = etBatch.getText().toString().trim();
        String manufactureDate = etManufactureDate.getText().toString().trim();
        String expiryDate = etExpiryDate.getText().toString().trim();

        // التحقق من الحقول الإلزامية
        if (TextUtils.isEmpty(name)) {
            etName.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(quantity)) {
            etQuantity.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(expiryDate)) {
            etExpiryDate.setError("Required");
            return;
        }

        // التأكد من وجود مستخدم مسجل
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();

        // تحضير خريطة البيانات (Map) للحفظ
        Map<String, Object> medicine = new HashMap<>();
        medicine.put("name", name);
        medicine.put("brand", brand);
        medicine.put("category", spinnerCategory.getSelectedItem().toString());
        medicine.put("strength", strength);
        medicine.put("form", spinnerForm.getSelectedItem().toString());
        medicine.put("quantity", quantity);
        medicine.put("unit", spinnerUnit.getSelectedItem().toString());
        medicine.put("purchasePrice", purchasePrice);
        medicine.put("sellingPrice", sellingPrice);
        medicine.put("batchNumber", batch);
        medicine.put("manufactureDate", manufactureDate);
        medicine.put("expiryDate", expiryDate);
        medicine.put("alertBefore", spinnerAlert.getSelectedItem().toString());
        medicine.put("ownerId", userId);
        medicine.put("createdAt", System.currentTimeMillis());

        btnSave.setEnabled(false); // تعطيل الزر لمنع الإضافة المتكررة
        btnSave.setText("Checking Access...");

        // 1. البحث عن الصيدلية التي يمتلكها المستخدم الحالي
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String pharmacyDocId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        String pharmacyName = queryDocumentSnapshots.getDocuments().get(0).getString("pharmacyName");
                        
                        // 2. إضافة الدواء لمجموعة Inventory التابعة لهذه الصيدلية
                        db.collection("Pharmacies").document(pharmacyDocId)
                                .collection("Inventory")
                                .add(medicine)
                                .addOnSuccessListener(documentReference -> {
                                    // التحقق من وجود طلبات لهذا الدواء وتنبيه المستخدمين
                                    checkPendingRequests(name, pharmacyName, pharmacyDocId);
                                    
                                    Toast.makeText(AddMedicineActivity.this, "Successfully added to " + pharmacyName, Toast.LENGTH_LONG).show();
                                    finish(); // العودة للشاشة السابقة بعد النجاح
                                })
                                .addOnFailureListener(e -> {
                                    btnSave.setEnabled(true);
                                    btnSave.setText("Save Medicine");
                                    Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    } else {
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Medicine");
                        Toast.makeText(this, "No matching Pharmacy found.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    btnSave.setText("Save Medicine");
                    Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // إعداد البيانات والقوائم المنسدلة (Spinners)
    private void setupSpinners() {
        String[] categories = {"Antibiotics", "Painkillers", "Vitamins", "Diabetes", "Hypertension", "Other"};
        setupSpinnerAdapter(spinnerCategory, categories);

        String[] forms = {"Tablet", "Capsule", "Syrup", "Injection", "Ointment", "Drops"};
        setupSpinnerAdapter(spinnerForm, forms);

        String[] units = {"Box", "Strip", "Bottle", "Piece"};
        setupSpinnerAdapter(spinnerUnit, units);

        String[] alerts = {"No Alert", "1 Week Before", "1 Month Before", "3 Months Before"};
        setupSpinnerAdapter(spinnerAlert, alerts);
    }

    // دالة مساعدة لربط مصفوفة نصوص بـ Spinner
    private void setupSpinnerAdapter(Spinner spinner, String[] data) {
        if (spinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, data);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }
    }

    // التحقق من وجود طلبات مسبقة لهذا الدواء من قبل المرضى
    private void checkPendingRequests(String medicineName, String pharmacyName, String pharmacyId) {
        db.collection("MedicineRequests")
                .whereEqualTo("medicineName", medicineName.toLowerCase())
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (com.google.firebase.firestore.QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            String patientId = doc.getString("patientId");
                            if (patientId != null) {
                                sendNotificationToPatient(patientId, medicineName, pharmacyName, pharmacyId, doc.getId());
                            }
                        }
                        Toast.makeText(this, "Notification sent to " + queryDocumentSnapshots.size() + " patients!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("Firestore", "Error checking requests: " + e.getMessage());
                });
    }

    // "إرسال" إشعار للمريض (بإضافته لمجموعة الإشعارات في قاعدة البيانات)
    private void sendNotificationToPatient(String patientId, String medName, String phName, String phId, String requestId) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("userId", patientId);
        notification.put("title", "Medicine Available! 💊");
        notification.put("message", "The medicine '" + medName + "' you requested is now available at '" + phName + "'.");
        notification.put("pharmacyId", phId);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("isRead", false);
        notification.put("type", "request_fulfilled");

        // حفظ الإشعار في مجموعة Notifications ليراه المستخدم عند فتح التطبيق
        db.collection("Notifications").add(notification);

        // تحديث حالة الطلب ليصبح مكتمل
        db.collection("MedicineRequests").document(requestId)
                .update("status", "fulfilled");
    }
}
