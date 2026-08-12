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

    // حاوية عرض قائمة المخزون (LinearLayout) حيث سيتم إضافة عناصر الأدوية برمجياً
    private LinearLayout llStockList;
    
    // مؤشر التحميل (ProgressBar) لإعلام المستخدم بوجود عملية جلب بيانات جارية
    private ProgressBar progressBar;
    
    // كائنات Firebase للتعامل مع قاعدة البيانات (Firestore) والتحقق من هوية المستخدم (Auth)
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تعيين واجهة المستخدم الرسومية الخاصة بنشاط المخزون
        setContentView(R.layout.activity_stock);

        // تهيئة مثيلات Firebase للوصول إلى الوظائف السحابية
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط عناصر الواجهة البرمجية بالمعرفات الموجودة في ملف XML
        llStockList = findViewById(R.id.ll_stock_list_container);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        //  زر العودة لإغلاق النشاط الحالي والرجوع للشاشة السابقة
        btnBack.setOnClickListener(v -> finish());

        // استدعاء الدالة المسؤولة عن جلب بيانات المخزون من Firestore
        fetchStock();
    }
    private void fetchStock() {
        // التحقق من تسجيل دخول المستخدم لتجنب الأخطاء عند جلب الـ UID
        if (mAuth.getCurrentUser() == null) return;

        // الحصول على المعرف الفريد للمستخدم الحالي
        String userId = mAuth.getCurrentUser().getUid();
        
        // إظهار مؤشر التحميل قبل البدء بعملية جلب البيانات من السحابة
        progressBar.setVisibility(View.VISIBLE);

        // 1. الخطوة الأولى: العثور على وثيقة الصيدلية التي يملكها المستخدم الحالي (بناءً على ownerId)
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // التحقق مما إذا كانت نتائج البحث تحتوي على صيدلية مرتبطة
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // الحصول على المعرف الخاص بالصيدلية (ID الوثيقة)
                        String pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        // 2. الخطوة الثانية: جلب مجموعة الأدوية (Inventory) الخاصة بهذه الصيدلية حصراً
                        db.collection("Pharmacies").document(pharmacyId)
                                .collection("Inventory")
                                .get()
                                .addOnSuccessListener(inventorySnapshots -> {
                                    // إخفاء مؤشر التحميل بعد استلام البيانات بنجاح
                                    progressBar.setVisibility(View.GONE);
                                    
                                    // مسح أي عناصر قديمة كانت موجودة في القائمة قبل إضافة الجديدة
                                    llStockList.removeAllViews();
                                    
                                    // استخدام LayoutInflater لتحويل ملف الـ XML الخاص بعنصر الدواء إلى كائن View
                                    LayoutInflater inflater = LayoutInflater.from(this);

                                    // تكرار عرض كل دواء تم جلبه من قاعدة البيانات
                                    for (QueryDocumentSnapshot doc : inventorySnapshots) {
                                        // إنشاء واجهة عنصر الدواء (Row Item)
                                        View itemView = inflater.inflate(R.layout.item_stock_medicine, llStockList, false);
                                        
                                        // ربط نصوص العرض داخل العنصر
                                        TextView tvName = itemView.findViewById(R.id.tv_medicine_name);
                                        TextView tvCategory = itemView.findViewById(R.id.tv_medicine_category);
                                        TextView tvQuantity = itemView.findViewById(R.id.tv_stock_quantity);
                                        TextView tvUnit = itemView.findViewById(R.id.tv_stock_unit);

                                        // تعيين بيانات الدواء المستخرجة من Firestore في نصوص الواجهة
                                        tvName.setText(doc.getString("name"));
                                        tvCategory.setText(doc.getString("category"));
                                        // التأكد من معالجة الكمية كقيمة نصية حتى لو كانت رقماً في قاعدة البيانات
                                        tvQuantity.setText(doc.get("quantity") != null ? doc.get("quantity").toString() : "0");
                                        tvUnit.setText(doc.getString("unit"));

                                        // إضافة صف الدواء المكتمل إلى حاوية القائمة الرئيسية
                                        llStockList.addView(itemView);
                                    }

                                    // إظهار رسالة تنبيه للمستخدم في حال كانت الصيدلية لا تحتوي على أي أدوية في المخزون
                                    if (inventorySnapshots.isEmpty()) {
                                        Toast.makeText(this, "المخزون فارغ حالياً", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // معالجة حالة فشل جلب بيانات المخزون
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "error in retrieving inventory " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // في حال لم يتم العثور على صيدلية مسجلة باسم هذا الحساب
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "no pharmacy is associated with this account", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    // معالجة حالة فشل الوصول إلى مجموعة الصيدليات في Firestore
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "database error" + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}
