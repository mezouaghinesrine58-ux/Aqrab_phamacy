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
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // تعريف عناصر واجهة المستخدم: حاوية القائمة ومؤشر التحميل
    private LinearLayout llHistoryList;
    private ProgressBar progressBar;
    
    // تعريف كائنات Firebase للوصول إلى قاعدة البيانات والتحقق من المستخدم
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط متغيرات الواجهة بالمعرفات الموجودة في ملف الـ layout
        llHistoryList = findViewById(R.id.ll_history_list_container);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        // برمجة زر الرجوع لإغلاق الشاشة والعودة للشاشة السابقة
        btnBack.setOnClickListener(v -> finish());

        // البدء بجلب بيانات سجل المبيعات من السحابة
        fetchSalesHistory();
    }
    private void fetchSalesHistory() {
        // التحقق من وجود مستخدم مسجل دخول
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        // إظهار مؤشر التحميل أثناء جلب البيانات
        progressBar.setVisibility(View.VISIBLE);

        // 1. البحث عن الصيدلية التابعة للمستخدم الحالي بناءً على معرف المالك (ownerId)
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // الحصول على معرف الصيدلية (ID)
                        String pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        // 2. الدخول إلى مجموعة سجل المبيعات الفرعية وترتيبها تنازلياً حسب الوقت (الأحدث أولاً)
                        db.collection("Pharmacies").document(pharmacyId)
                                .collection("SalesHistory")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(historySnapshots -> {
                                    // إخفاء مؤشر التحميل وتفريغ القائمة الحالية
                                    progressBar.setVisibility(View.GONE);
                                    llHistoryList.removeAllViews();
                                    
                                    // تجهيز محول الواجهة (LayoutInflater) وتنسيق التاريخ
                                    LayoutInflater inflater = LayoutInflater.from(this);
                                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

                                    // تكرار عرض كل عملية بيع موجودة في السجل
                                    for (QueryDocumentSnapshot doc : historySnapshots) {
                                        // تحويل ملف XML الخاص بالعنصر إلى واجهة مرئية
                                        View itemView = inflater.inflate(R.layout.item_history, llHistoryList, false);
                                        
                                        // ربط نصوص العرض داخل عنصر السجل
                                        TextView tvName = itemView.findViewById(R.id.tv_history_name);
                                        TextView tvDate = itemView.findViewById(R.id.tv_history_date);
                                        TextView tvDetails = itemView.findViewById(R.id.tv_history_details);
                                        TextView tvTotal = itemView.findViewById(R.id.tv_history_total);

                                        // تعيين اسم الدواء المباع
                                        tvName.setText(doc.getString("medicineName"));
                                        
                                        // استخراج وتنسيق وقت العملية
                                        Long timestamp = doc.getLong("timestamp");
                                        if (timestamp != null) {
                                            tvDate.setText(sdf.format(new Date(timestamp)));
                                        }

                                        // استخراج الكمية والسعر لكل وحدة وعرضهما
                                        Object qty = doc.get("quantity");
                                        Object price = doc.get("pricePerUnit");
                                        tvDetails.setText("Qty: " + qty + " | Price: " + price + " DA");
                                        
                                        // عرض السعر الإجمالي للعملية
                                        Object total = doc.get("totalPrice");
                                        tvTotal.setText(total + " DA");

                                        // إضافة العنصر إلى القائمة في الواجهة
                                        llHistoryList.addView(itemView);
                                    }

                                    // تنبيه المستخدم إذا كان السجل فارغاً
                                    if (historySnapshots.isEmpty()) {
                                        Toast.makeText(this, "لا توجد سجلات مبيعات حالياً", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    // التعامل مع فشل جلب السجل
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "خطأ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        // في حال عدم العثور على صيدلية لهذا الحساب
                        progressBar.setVisibility(View.GONE);
                    }
                });
    }
}
