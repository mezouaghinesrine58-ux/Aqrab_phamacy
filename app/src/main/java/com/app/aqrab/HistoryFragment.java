package com.app.aqrab;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
public class HistoryFragment extends Fragment {
    private LinearLayout llHistoryList;
    private ProgressBar progressBar;
    
    // تعريف مثيلات Firebase لقاعدة البيانات والمصادقة
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // تحويل ملف XML الخاص بالواجهة إلى كائن View
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        // تهيئة مثيلات Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط عناصر الواجهة بالمعرفات (IDs)
        llHistoryList = view.findViewById(R.id.ll_history_list_container);
        progressBar = view.findViewById(R.id.progress_bar);

        // البدء بجلب سجل المريض فور إنشاء الواجهة
        fetchPatientHistory();

        return view;
    }
    private void fetchPatientHistory() {
        // التأكد من تسجيل دخول المريض
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        // إظهار مؤشر التحميل
        progressBar.setVisibility(View.VISIBLE);

        // إنشاء مهمة لجلب طلبات الأدوية الخاصة بالمريض
        Task<QuerySnapshot> task1 = db.collection("MedicineRequests")
                .whereEqualTo("patientId", userId)
                .get();

        // إنشاء مهمة لجلب سجل العمليات الأخرى (بحث، مسح روشتات)
        Task<QuerySnapshot> task2 = db.collection("PatientHistory")
                .whereEqualTo("patientId", userId)
                .get();

        // تنفيذ كافه المهام معاً والانتظار حتى نجاحها جميعاً
        Tasks.whenAllSuccess(task1, task2).addOnSuccessListener(results -> {
            // التحقق من أن الـ Fragment لا يزال مرتبطاً بالنشاط لتجنب أخطاء الواجهة
            if (!isAdded()) return;
            
            // إخفاء مؤشر التحميل وتفريغ القائمة القديمة
            progressBar.setVisibility(View.GONE);
            llHistoryList.removeAllViews();
            
            // تجميع كافة المستندات المسترجعة في قائمة واحدة
            List<DocumentSnapshot> allItems = new ArrayList<>();
            for (Object res : results) {
                allItems.addAll(((QuerySnapshot) res).getDocuments());
            }

            // ترتيب العناصر حسب الطابع الزمني (timestamp) تنازلياً (الأحدث أولاً)
            Collections.sort(allItems, (d1, d2) -> {
                Long t1 = d1.getLong("timestamp");
                Long t2 = d2.getLong("timestamp");
                return Long.compare(t2 != null ? t2 : 0L, t1 != null ? t1 : 0L);
            });

            // تجهيز محول الواجهة وتنسيق التاريخ
            LayoutInflater inflater = LayoutInflater.from(getContext());
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

            // تكرار عرض كل عنصر في القائمة الموحدة
            for (DocumentSnapshot doc : allItems) {
                // إنشاء واجهة العنصر (Row Item)
                View itemView = inflater.inflate(R.layout.item_history, llHistoryList, false);

                // ربط عناصر العرض داخل الصف
                TextView tvTitle = itemView.findViewById(R.id.tv_history_name);
                TextView tvDate = itemView.findViewById(R.id.tv_history_date);
                TextView tvDetails = itemView.findViewById(R.id.tv_history_details);
                TextView tvStatus = itemView.findViewById(R.id.tv_history_total);
                ImageView ivIcon = itemView.findViewById(R.id.iv_history_icon);

                // تعيين التاريخ المنسق
                Long ts = doc.getLong("timestamp");
                if (ts != null) tvDate.setText(sdf.format(new Date(ts)));

                // تحديد نوع السجل لعرض البيانات المناسبة
                String type = doc.getString("type");
                if (type == null) {
                    // الحالة: طلب دواء (MedicineRequest) - لا يحتوي على حقل "type" عادةً
                    String medName = doc.getString("displayName");
                    if (medName == null) medName = doc.getString("medicineName");
                    tvTitle.setText(medName != null ? medName : getString(R.string.medicine_request_title));
                    tvDetails.setText(R.string.stock_request_type);
                    ivIcon.setImageResource(R.drawable.ic_history);
                    ivIcon.setColorFilter(Color.parseColor("#FFC107")); // لون أصفر للطلبات
                    
                    // تحديد حالة الطلب ولونها
                    String status = doc.getString("status");
                    if (status != null) {
                        tvStatus.setText(status.toUpperCase());
                        tvStatus.setTextColor(status.equalsIgnoreCase("fulfilled") ? Color.parseColor("#4CAF50") : Color.parseColor("#FF9800"));
                    }
                } else if (type.equals("search")) {
                    // الحالة: سجل بحث
                    tvTitle.setText(getString(R.string.search_history_title, doc.getString("query")));
                    Long count = doc.getLong("resultCount");
                    tvDetails.setText(getString(R.string.found_ph_count_history, (count != null ? count.intValue() : 0)));
                    tvStatus.setText(R.string.quick_search);
                    tvStatus.setTextColor(Color.GRAY);
                    ivIcon.setImageResource(R.drawable.ic_search);
                    ivIcon.setColorFilter(Color.parseColor("#2E5A44"));
                } else if (type.equals("prescription")) {
                    // الحالة: سجل مسح روشتة
                    List<String> meds = (List<String>) doc.get("medicines");
                    tvTitle.setText(R.string.prescription_scan_title);
                    // عرض قائمة الأدوية المكتشفة مفصولة بفاصلة
                    if (meds != null && !meds.isEmpty()) {
                        tvDetails.setText(String.join(", ", meds));
                    } else {
                        tvDetails.setText(R.string.no_meds_detected);
                    }
                    
                    // عرض حالة البحث عن الأدوية المكتشفة
                    Long count = doc.getLong("resultCount");
                    int c = (count != null) ? count.intValue() : 0;
                    tvStatus.setText(c > 0 ? getString(R.string.found_status) : getString(R.string.no_results_status));
                    tvStatus.setTextColor(c > 0 ? Color.parseColor("#4CAF50") : Color.RED);
                    
                    ivIcon.setImageResource(R.drawable.ic_scan);
                    ivIcon.setColorFilter(Color.parseColor("#2E5A44"));
                }

                // إضافة الصف المكتمل إلى القائمة الرئيسية
                llHistoryList.addView(itemView);
            }

            // عرض واجهة "لا يوجد سجل" إذا كانت القائمة فارغة
            if (allItems.isEmpty()) {
                showEmptyState();
            }
        }).addOnFailureListener(e -> {
            // التعامل مع أخطاء جلب البيانات
            if (isAdded()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmptyState() {
        TextView tvEmpty = new TextView(getContext());
        tvEmpty.setText(R.string.no_history_found);
        tvEmpty.setPadding(0, 100, 0, 0);
        tvEmpty.setGravity(android.view.Gravity.CENTER);
        tvEmpty.setTextColor(Color.GRAY);
        llHistoryList.addView(tvEmpty);
    }
}
