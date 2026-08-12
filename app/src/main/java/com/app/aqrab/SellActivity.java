package com.app.aqrab;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
public class SellActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // تعريف متغيرات واجهة المستخدم وقاعدة بيانات Firebase
    private EditText etSearch;
    private LinearLayout llResults;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String pharmacyId;
    private List<QueryDocumentSnapshot> allMedicines = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell);

        // تهيئة مثيلات قاعدة البيانات Firestore ونظام المصادقة
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط عناصر واجهة المستخدم بالمتغيرات
        etSearch = findViewById(R.id.et_search_medicine);
        llResults = findViewById(R.id.ll_sell_results_container);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        // إغلاق النشاط عند الضغط على زر الرجوع
        btnBack.setOnClickListener(v -> finish());

        // البدء في البحث عن الصيدلية التابعة للمستخدم الحالي وتحميل بيانات المخزون
        findPharmacyAndLoadData();

        // إضافة مستمع لتغير النص في حقل البحث لتصفية النتائج بشكل فوري
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterResults(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // البحث عن وثيقة الصيدلية المرتبطة بـ ID المستخدم الحالي في Firestore
    private void findPharmacyAndLoadData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        
        progressBar.setVisibility(View.VISIBLE);
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // تخزين معرف الصيدلية وتحميل قائمة الأدوية
                        pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        loadAllInventory();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "No pharmacy linked", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // جلب كافة الأدوية الموجودة في مجموعة Inventory داخل وثيقة الصيدلية
    private void loadAllInventory() {
        db.collection("Pharmacies").document(pharmacyId)
                .collection("Inventory")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    progressBar.setVisibility(View.GONE);
                    allMedicines.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        allMedicines.add(doc);
                    }
                    displayResults(allMedicines);
                });
    }

    // تصفية القائمة المحلية للأدوية بناءً على الاسم المدخل في حقل البحث
    private void filterResults(String query) {
        List<QueryDocumentSnapshot> filtered = new ArrayList<>();
        for (QueryDocumentSnapshot doc : allMedicines) {
            String name = doc.getString("name");
            if (name != null && name.toLowerCase().contains(query.toLowerCase())) {
                filtered.add(doc);
            }
        }
        displayResults(filtered);
    }

    // عرض قائمة الأدوية في واجهة المستخدم عن طريق إضافة طرق عرض (Views) بشكل ديناميكي
    private void displayResults(List<QueryDocumentSnapshot> list) {
        llResults.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (QueryDocumentSnapshot doc : list) {
            View itemView = inflater.inflate(R.layout.item_sell_medicine, llResults, false);
            
            TextView tvName = itemView.findViewById(R.id.tv_medicine_name);
            TextView tvPrice = itemView.findViewById(R.id.tv_medicine_price);
            TextView tvStock = itemView.findViewById(R.id.tv_stock_left);
            Button btnSell = itemView.findViewById(R.id.btn_sell_action);

            // تعيين بيانات الدواء من Firestore إلى عناصر الواجهة
            tvName.setText(doc.getString("name"));
            tvPrice.setText(doc.getString("sellingPrice") + " DA");
            
            Object qtyObj = doc.get("quantity");
            int currentQty = 0;
            if (qtyObj != null) {
                try {
                    currentQty = Integer.parseInt(qtyObj.toString());
                } catch (Exception e) {}
            }
            
            tvStock.setText("In stock: " + currentQty);
            
            int finalCurrentQty = currentQty;
            String sellingPrice = doc.getString("sellingPrice");
            // عند الضغط على زر "بيع"، يتم عرض نافذة لتحديد الكمية
            btnSell.setOnClickListener(v -> showSellDialog(doc.getId(), doc.getString("name"), finalCurrentQty, sellingPrice));

            llResults.addView(itemView);
        }
    }

    // عرض نافذة منبثقة (Dialog) تطلب من الصيدلي إدخال الكمية المراد بيعها
    private void showSellDialog(String medicineId, String name, int currentQty, String price) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sell " + name);
        
        final EditText input = new EditText(this);
        input.setHint("Enter quantity to sell");
        input.setPadding(50, 40, 50, 40);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);

        builder.setPositiveButton("Confirm Sell", (dialog, which) -> {
            String val = input.getText().toString();
            if (!val.isEmpty()) {
                int sellQty = Integer.parseInt(val);
                // التأكد من أن الكمية المطلوبة متوفرة في المخزون وأكبر من الصفر
                if (sellQty > 0 && sellQty <= currentQty) {
                    processSale(medicineId, name, sellQty, currentQty - sellQty, price);
                } else {
                    Toast.makeText(this, "Invalid quantity or not enough stock!", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // تنفيذ عملية البيع: تحديث الكمية المتبقية في المخزون وتسجيل العملية في سجل المبيعات
    private void processSale(String medicineId, String name, int soldQty, int newQty, String price) {
        progressBar.setVisibility(View.VISIBLE);
        
        // 1. تحديث حقل الكمية في وثيقة الدواء داخل Firestore
        db.collection("Pharmacies").document(pharmacyId)
                .collection("Inventory").document(medicineId)
                .update("quantity", String.valueOf(newQty))
                .addOnSuccessListener(aVoid -> {
                    // 2. إنشاء وثيقة جديدة في سجل المبيعات (SalesHistory) لحفظ تفاصيل العملية
                    java.util.Map<String, Object> sale = new java.util.HashMap<>();
                    sale.put("medicineName", name);
                    sale.put("quantity", soldQty);
                    sale.put("pricePerUnit", price);
                    sale.put("totalPrice", (price != null ? (Double.parseDouble(price) * soldQty) : 0));
                    sale.put("timestamp", System.currentTimeMillis());

                    db.collection("Pharmacies").document(pharmacyId)
                            .collection("SalesHistory")
                            .add(sale)
                            .addOnSuccessListener(docRef -> {
                                Toast.makeText(this, "Sale complete & recorded!", Toast.LENGTH_SHORT).show();
                                // إعادة تحميل المخزون لتحديث الكميات المعروضة في الواجهة
                                loadAllInventory();
                            })
                            .addOnFailureListener(e -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(this, "Update success but history failed", Toast.LENGTH_SHORT).show();
                                loadAllInventory();
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Sale failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
