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

    private LinearLayout llHistoryList;
    private ProgressBar progressBar;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llHistoryList = findViewById(R.id.ll_history_list_container);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        fetchSalesHistory();
    }

    private void fetchSalesHistory() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        progressBar.setVisibility(View.VISIBLE);

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        db.collection("Pharmacies").document(pharmacyId)
                                .collection("SalesHistory")
                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                .get()
                                .addOnSuccessListener(historySnapshots -> {
                                    progressBar.setVisibility(View.GONE);
                                    llHistoryList.removeAllViews();
                                    LayoutInflater inflater = LayoutInflater.from(this);
                                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

                                    for (QueryDocumentSnapshot doc : historySnapshots) {
                                        View itemView = inflater.inflate(R.layout.item_history, llHistoryList, false);
                                        
                                        TextView tvName = itemView.findViewById(R.id.tv_history_name);
                                        TextView tvDate = itemView.findViewById(R.id.tv_history_date);
                                        TextView tvDetails = itemView.findViewById(R.id.tv_history_details);
                                        TextView tvTotal = itemView.findViewById(R.id.tv_history_total);

                                        tvName.setText(doc.getString("medicineName"));
                                        
                                        Long timestamp = doc.getLong("timestamp");
                                        if (timestamp != null) {
                                            tvDate.setText(sdf.format(new Date(timestamp)));
                                        }

                                        Object qty = doc.get("quantity");
                                        Object price = doc.get("pricePerUnit");
                                        tvDetails.setText("Qty: " + qty + " | Price: " + price + " DA");
                                        
                                        Object total = doc.get("totalPrice");
                                        tvTotal.setText(total + " DA");

                                        llHistoryList.addView(itemView);
                                    }

                                    if (historySnapshots.isEmpty()) {
                                        Toast.makeText(this, "No sales records found", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                });
    }
}
