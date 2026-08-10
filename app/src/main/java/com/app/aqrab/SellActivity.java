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

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        etSearch = findViewById(R.id.et_search_medicine);
        llResults = findViewById(R.id.ll_sell_results_container);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        findPharmacyAndLoadData();

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

    private void findPharmacyAndLoadData() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        
        progressBar.setVisibility(View.VISIBLE);
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        pharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        loadAllInventory();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "No pharmacy linked", Toast.LENGTH_SHORT).show();
                    }
                });
    }

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

    private void displayResults(List<QueryDocumentSnapshot> list) {
        llResults.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (QueryDocumentSnapshot doc : list) {
            View itemView = inflater.inflate(R.layout.item_sell_medicine, llResults, false);
            
            TextView tvName = itemView.findViewById(R.id.tv_medicine_name);
            TextView tvPrice = itemView.findViewById(R.id.tv_medicine_price);
            TextView tvStock = itemView.findViewById(R.id.tv_stock_left);
            Button btnSell = itemView.findViewById(R.id.btn_sell_action);

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
            btnSell.setOnClickListener(v -> showSellDialog(doc.getId(), doc.getString("name"), finalCurrentQty, sellingPrice));

            llResults.addView(itemView);
        }
    }

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

    private void processSale(String medicineId, String name, int soldQty, int newQty, String price) {
        progressBar.setVisibility(View.VISIBLE);
        
        // 1. Update Inventory
        db.collection("Pharmacies").document(pharmacyId)
                .collection("Inventory").document(medicineId)
                .update("quantity", String.valueOf(newQty))
                .addOnSuccessListener(aVoid -> {
                    // 2. Record in History
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
