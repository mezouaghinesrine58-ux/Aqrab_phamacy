package com.app.aqrab;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
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
                    // عرض ملخص عام للمسح بدلاً من النص الكامل المكتشف
                    if (meds != null && !meds.isEmpty()) {
                        tvDetails.setText(getString(R.string.prescription_scan_desc, meds.size()));
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

                // عند الضغط على أية عملية في السجل يتم عرض نافذة التفاصيل كاملة
                itemView.setOnClickListener(v -> showHistoryDetailsDialog(doc));

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

    private void showHistoryDetailsDialog(DocumentSnapshot doc) {
        if (getContext() == null || doc == null) return;

        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_history_details, null);
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvDate = dialogView.findViewById(R.id.tv_dialog_date);
        ImageView btnClose = dialogView.findViewById(R.id.btn_close_dialog);
        CardView cardImage = dialogView.findViewById(R.id.card_prescription_image);
        ImageView ivImage = dialogView.findViewById(R.id.iv_prescription_image);
        LinearLayout llContent = dialogView.findViewById(R.id.ll_dialog_content);
        Button btnAction = dialogView.findViewById(R.id.btn_dialog_action);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        Long ts = doc.getLong("timestamp");
        if (ts != null) {
            tvDate.setText(sdf.format(new Date(ts)));
        }

        String type = doc.getString("type");

        if ("prescription".equals(type)) {
            tvTitle.setText(R.string.prescription_details_title);

            // 1. عرض صورة الوصفة الطبية إذا وجدت
            String imageUri = doc.getString("imageUri");
            if (imageUri != null && !imageUri.trim().isEmpty()) {
                cardImage.setVisibility(View.VISIBLE);
                Glide.with(this)
                        .load(imageUri)
                        .placeholder(R.drawable.ic_scan)
                        .into(ivImage);
            } else {
                cardImage.setVisibility(View.GONE);
            }

            // 2. قائمة الأدوية المكتشفة في الوصفة
            List<String> medicines = (List<String>) doc.get("medicines");
            int medCount = (medicines != null) ? medicines.size() : 0;

            addDetailSectionHeader(llContent, getString(R.string.detected_medicines_label, medCount));

            if (medicines != null && !medicines.isEmpty()) {
                for (String med : medicines) {
                    addMedicineItemRow(llContent, med);
                }
            } else {
                addDetailValueRow(llContent, getString(R.string.no_meds_detected));
            }

            // 3. عدد الصيدليات المطابقة التي تم العثور عليها
            Long count = doc.getLong("resultCount");
            int c = (count != null) ? count.intValue() : 0;
            addDetailValueRow(llContent, getString(R.string.pharmacies_found_label, c));

            // 4. زر إجراء البحث عن الصيدليات المطابقة
            if (medicines != null && !medicines.isEmpty()) {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(R.string.search_pharmacies);
                btnAction.setOnClickListener(v -> {
                    dialog.dismiss();
                    Intent intent = new Intent(getContext(), NearbyPharmaciesActivity.class);
                    intent.putStringArrayListExtra("MED_NAMES", new ArrayList<>(medicines));
                    startActivity(intent);
                });
            }

        } else if ("search".equals(type)) {
            tvTitle.setText(R.string.search_details_title);
            cardImage.setVisibility(View.GONE);

            String query = doc.getString("query");
            Long count = doc.getLong("resultCount");
            int c = (count != null) ? count.intValue() : 0;

            if (query != null && !query.isEmpty()) {
                addDetailValueRow(llContent, getString(R.string.search_query_label, query));
            }
            addDetailValueRow(llContent, getString(R.string.pharmacies_found_label, c));

            if (query != null && !query.isEmpty()) {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(R.string.search_again);
                btnAction.setOnClickListener(v -> {
                    dialog.dismiss();
                    Intent intent = new Intent(getContext(), NearbyPharmaciesActivity.class);
                    intent.putExtra("SEARCH_QUERY", query);
                    startActivity(intent);
                });
            }

        } else {
            // طلب دواء (MedicineRequest)
            tvTitle.setText(R.string.request_details_title);
            cardImage.setVisibility(View.GONE);

            String medName = doc.getString("displayName");
            if (medName == null) medName = doc.getString("medicineName");
            if (medName == null) medName = getString(R.string.medicine_request_title);

            String status = doc.getString("status");
            if (status == null) status = "Pending";

            addDetailValueRow(llContent, getString(R.string.requested_medicine_label, medName));
            addDetailValueRow(llContent, getString(R.string.request_status_label, status.toUpperCase()));

            String finalMedName = medName;
            btnAction.setVisibility(View.VISIBLE);
            btnAction.setText(R.string.search_pharmacies);
            btnAction.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(getContext(), NearbyPharmaciesActivity.class);
                intent.putExtra("SEARCH_QUERY", finalMedName);
                startActivity(intent);
            });
        }

        dialog.show();
    }

    private void addDetailSectionHeader(LinearLayout parent, String title) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextSize(14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#2E5A44"));
        tv.setPadding(0, 12, 0, 8);
        parent.addView(tv);
    }

    private void addMedicineItemRow(LinearLayout parent, String medicineName) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setText(String.format(Locale.getDefault(), "• %s", medicineName));
        tv.setTextSize(13);
        tv.setTextColor(Color.parseColor("#424242"));
        tv.setPadding(12, 4, 12, 4);
        parent.addView(tv);
    }

    private void addDetailValueRow(LinearLayout parent, String text) {
        if (getContext() == null) return;
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setPadding(0, 6, 0, 6);
        parent.addView(tv);
    }

    private void showEmptyState() {
        if (getContext() == null) return;
        TextView tvEmpty = new TextView(getContext());
        tvEmpty.setText(R.string.no_history_found);
        tvEmpty.setPadding(0, 100, 0, 0);
        tvEmpty.setGravity(android.view.Gravity.CENTER);
        tvEmpty.setTextColor(Color.GRAY);
        llHistoryList.addView(tvEmpty);
    }
}