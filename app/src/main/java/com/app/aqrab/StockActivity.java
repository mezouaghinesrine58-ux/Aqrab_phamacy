package com.app.aqrab;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class StockActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // حاوية عرض قائمة المخزون
    private LinearLayout llStockList;
    // مؤشر التحميل
    private ProgressBar progressBar;
    // كائنات Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تعيين واجهة المخزون
        setContentView(R.layout.activity_stock);

        // تهيئة Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط عناصر الواجهة
        llStockList = findViewById(R.id.ll_stock_list_container);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        // زر العودة
        btnBack.setOnClickListener(v -> finish());

        // جلب قائمة المخزون
        fetchStock();
    }

    // دالة جلب كافة الأدوية المضافة لمخزون الصيدلية الحالية
    private void fetchStock() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        progressBar.setVisibility(View.VISIBLE); // إظهار مؤشر التحميل

        // 1. العثور على الصيدلية المملوكة للمستخدم الحالي
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        // 2. جلب مجموعة الأدوية (Inventory) الخاصة بهذه الصيدلية حصراً
                        db.collection("Pharmacies").document(pharmacyId)
                                .collection("Inventory")
                                .get()
                                .addOnSuccessListener(inventorySnapshots -> {
                                    progressBar.setVisibility(View.GONE); // إخفاء مؤشر التحميل
                                    llStockList.removeAllViews(); // مسح القائمة القديمة
                                    LayoutInflater inflater = LayoutInflater.from(this);

                                    // تكرار عرض كل دواء تم جلبه من قاعدة البيانات
                                    for (QueryDocumentSnapshot doc : inventorySnapshots) {
                                        View itemView = inflater.inflate(R.layout.item_stock_medicine, llStockList, false);
                                        
                                        TextView tvName = itemView.findViewById(R.id.tv_medicine_name);
                                        TextView tvCategory = itemView.findViewById(R.id.tv_medicine_category);
                                        TextView tvQuantity = itemView.findViewById(R.id.tv_stock_quantity);
                                        TextView tvUnit = itemView.findViewById(R.id.tv_stock_unit);

                                        // تعيين بيانات الدواء في التصميم
                                        tvName.setText(doc.getString("name"));
                                        tvCategory.setText(doc.getString("category"));
                                        tvQuantity.setText(doc.get("quantity") != null ? doc.get("quantity").toString() : "0");
                                        tvUnit.setText(doc.getString("unit"));

                                        llStockList.addView(itemView); // إضافة الصف للقائمة
                                    }

                                    // رسالة في حال كان المخزون فارغاً
                                    if (inventorySnapshots.isEmpty()) {
                                        Toast.makeText(this, "Inventory is empty", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Error fetching inventory: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "No pharmacy linked to this account", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
