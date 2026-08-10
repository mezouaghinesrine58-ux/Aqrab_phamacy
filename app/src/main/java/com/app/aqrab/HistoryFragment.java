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
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llHistoryList = view.findViewById(R.id.ll_history_list_container);
        progressBar = view.findViewById(R.id.progress_bar);

        fetchPatientHistory();

        return view;
    }

    private void fetchPatientHistory() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        Task<QuerySnapshot> task1 = db.collection("MedicineRequests")
                .whereEqualTo("patientId", userId)
                .get();

        Task<QuerySnapshot> task2 = db.collection("PatientHistory")
                .whereEqualTo("patientId", userId)
                .get();

        Tasks.whenAllSuccess(task1, task2).addOnSuccessListener(results -> {
            if (!isAdded()) return;
            progressBar.setVisibility(View.GONE);
            llHistoryList.removeAllViews();
            
            List<DocumentSnapshot> allItems = new ArrayList<>();
            for (Object res : results) {
                allItems.addAll(((QuerySnapshot) res).getDocuments());
            }

            // Sort by timestamp DESC
            Collections.sort(allItems, (d1, d2) -> {
                Long t1 = d1.getLong("timestamp");
                Long t2 = d2.getLong("timestamp");
                return Long.compare(t2 != null ? t2 : 0L, t1 != null ? t1 : 0L);
            });

            LayoutInflater inflater = LayoutInflater.from(getContext());
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

            for (DocumentSnapshot doc : allItems) {
                View itemView = inflater.inflate(R.layout.item_history, llHistoryList, false);

                TextView tvTitle = itemView.findViewById(R.id.tv_history_name);
                TextView tvDate = itemView.findViewById(R.id.tv_history_date);
                TextView tvDetails = itemView.findViewById(R.id.tv_history_details);
                TextView tvStatus = itemView.findViewById(R.id.tv_history_total);
                ImageView ivIcon = itemView.findViewById(R.id.iv_history_icon);

                Long ts = doc.getLong("timestamp");
                if (ts != null) tvDate.setText(sdf.format(new Date(ts)));

                String type = doc.getString("type");
                if (type == null) {
                    // MedicineRequest
                    String medName = doc.getString("displayName");
                    if (medName == null) medName = doc.getString("medicineName");
                    tvTitle.setText(medName != null ? medName : getString(R.string.medicine_request_title));
                    tvDetails.setText(R.string.stock_request_type);
                    ivIcon.setImageResource(R.drawable.ic_history);
                    ivIcon.setColorFilter(Color.parseColor("#FFC107"));
                    
                    String status = doc.getString("status");
                    if (status != null) {
                        tvStatus.setText(status.toUpperCase());
                        tvStatus.setTextColor(status.equalsIgnoreCase("fulfilled") ? Color.parseColor("#4CAF50") : Color.parseColor("#FF9800"));
                    }
                } else if (type.equals("search")) {
                    tvTitle.setText(getString(R.string.search_history_title, doc.getString("query")));
                    Long count = doc.getLong("resultCount");
                    tvDetails.setText(getString(R.string.found_ph_count_history, (count != null ? count.intValue() : 0)));
                    tvStatus.setText(R.string.quick_search);
                    tvStatus.setTextColor(Color.GRAY);
                    ivIcon.setImageResource(R.drawable.ic_search);
                    ivIcon.setColorFilter(Color.parseColor("#2E5A44"));
                } else if (type.equals("prescription")) {
                    List<String> meds = (List<String>) doc.get("medicines");
                    tvTitle.setText(R.string.prescription_scan_title);
                    if (meds != null && !meds.isEmpty()) {
                        tvDetails.setText(String.join(", ", meds));
                    } else {
                        tvDetails.setText(R.string.no_meds_detected);
                    }
                    
                    Long count = doc.getLong("resultCount");
                    int c = (count != null) ? count.intValue() : 0;
                    tvStatus.setText(c > 0 ? getString(R.string.found_status) : getString(R.string.no_results_status));
                    tvStatus.setTextColor(c > 0 ? Color.parseColor("#4CAF50") : Color.RED);
                    
                    ivIcon.setImageResource(R.drawable.ic_scan);
                    ivIcon.setColorFilter(Color.parseColor("#2E5A44"));
                }

                llHistoryList.addView(itemView);
            }

            if (allItems.isEmpty()) {
                showEmptyState();
            }
        }).addOnFailureListener(e -> {
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