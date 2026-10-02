package com.app.aqrab;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StockActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // حاوية عرض قائمة المخزون (LinearLayout) وحقل البحث ومؤشر التحميل
    private LinearLayout llStockList;
    private EditText etSearch;
    private ProgressBar progressBar;
    
    // كائنات Firebase للتعامل مع قاعدة البيانات (Firestore) والتحقق من هوية المستخدم (Auth)
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private boolean alertsOnly = false;
    private String currentPharmacyId;
    private final List<QueryDocumentSnapshot> allStockItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);

        alertsOnly = getIntent().getBooleanExtra("alerts_only", false);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llStockList = findViewById(R.id.ll_stock_list_container);
        etSearch = findViewById(R.id.et_search_stock);
        progressBar = findViewById(R.id.progress_bar);
        ImageButton btnBack = findViewById(R.id.btn_back);

        TextView tvTitle = findViewById(R.id.tv_title);
        if (tvTitle != null && alertsOnly) {
            tvTitle.setText(R.string.pharmacy_alerts);
        }

        btnBack.setOnClickListener(v -> finish());

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterStockList(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchStock();
    }

    private void fetchStock() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        
        progressBar.setVisibility(View.VISIBLE);

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        currentPharmacyId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        db.collection("Pharmacies").document(currentPharmacyId)
                                .collection("Inventory")
                                .get()
                                .addOnSuccessListener(inventorySnapshots -> {
                                    progressBar.setVisibility(View.GONE);
                                    allStockItems.clear();

                                    for (QueryDocumentSnapshot doc : inventorySnapshots) {
                                        Object qtyObj = doc.get("quantity");
                                        int qty = 0;
                                        if (qtyObj != null) {
                                            try {
                                                qty = Integer.parseInt(qtyObj.toString());
                                            } catch (Exception ignored) {}
                                        }

                                        boolean isLowStock = (qty < 10);
                                        boolean isExpiring = isExpiringSoon(doc.getString("expiryDate"));

                                        if (alertsOnly && !isLowStock && !isExpiring) {
                                            continue;
                                        }

                                        allStockItems.add(doc);
                                    }

                                    String query = etSearch != null ? etSearch.getText().toString() : "";
                                    filterStockList(query);
                                })
                                .addOnFailureListener(e -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(this, "error in retrieving inventory " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "no pharmacy is associated with this account", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "database error " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void filterStockList(String query) {
        llStockList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int addedCount = 0;

        String cleanQuery = query != null ? query.trim().toLowerCase() : "";

        for (QueryDocumentSnapshot doc : allStockItems) {
            String name = doc.getString("name");
            String cat = doc.getString("category");
            String brand = doc.getString("brand");

            boolean matches = cleanQuery.isEmpty() ||
                    (name != null && name.toLowerCase().contains(cleanQuery)) ||
                    (cat != null && cat.toLowerCase().contains(cleanQuery)) ||
                    (brand != null && brand.toLowerCase().contains(cleanQuery));

            if (!matches) continue;

            Object qtyObj = doc.get("quantity");
            int qty = 0;
            if (qtyObj != null) {
                try {
                    qty = Integer.parseInt(qtyObj.toString());
                } catch (Exception ignored) {}
            }

            boolean isLowStock = (qty < 10);
            boolean isExpiring = isExpiringSoon(doc.getString("expiryDate"));

            View itemView = inflater.inflate(R.layout.item_stock_medicine, llStockList, false);
            
            TextView tvName = itemView.findViewById(R.id.tv_medicine_name);
            TextView tvCategory = itemView.findViewById(R.id.tv_medicine_category);
            TextView tvQuantity = itemView.findViewById(R.id.tv_stock_quantity);
            TextView tvUnit = itemView.findViewById(R.id.tv_stock_unit);
            ImageButton btnEdit = itemView.findViewById(R.id.btn_edit_medicine);

            tvName.setText(doc.getString("name"));

            if (isExpiring) {
                tvCategory.setText((cat != null ? cat : "") + " • Expiring Soon!");
                tvCategory.setTextColor(android.graphics.Color.RED);
            } else if (isLowStock) {
                tvCategory.setText((cat != null ? cat : "") + " • Low Stock!");
                tvCategory.setTextColor(android.graphics.Color.RED);
            } else {
                tvCategory.setText(cat);
            }

            tvQuantity.setText(String.valueOf(qty));
            tvUnit.setText(doc.getString("unit"));

            itemView.setOnClickListener(v -> MedicineDetailsDialog.show(StockActivity.this, doc, currentPharmacyId));

            if (btnEdit != null) {
                btnEdit.setOnClickListener(v -> openEditMedicine(doc));
            }

            llStockList.addView(itemView);
            addedCount++;
        }

        if (addedCount == 0) {
            if (!cleanQuery.isEmpty()) {
                TextView tvNoResults = new TextView(this);
                tvNoResults.setText(R.string.no_medicines_match);
                tvNoResults.setPadding(20, 20, 20, 20);
                llStockList.addView(tvNoResults);
            } else if (alertsOnly) {
                Toast.makeText(this, R.string.no_active_alerts, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "المخزون فارغ حالياً", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openEditMedicine(QueryDocumentSnapshot doc) {
        Intent intent = new Intent(this, AddMedicineActivity.class);
        intent.putExtra("is_edit_mode", true);
        intent.putExtra("medicine_id", doc.getId());
        if (currentPharmacyId != null) {
            intent.putExtra("pharmacy_id", currentPharmacyId);
        }
        intent.putExtra("name", doc.getString("name"));
        intent.putExtra("brand", doc.getString("brand"));
        intent.putExtra("category", doc.getString("category"));
        intent.putExtra("strength", doc.getString("strength"));
        intent.putExtra("form", doc.getString("form"));
        Object qtyObj = doc.get("quantity");
        intent.putExtra("quantity", qtyObj != null ? qtyObj.toString() : "0");
        intent.putExtra("unit", doc.getString("unit"));
        intent.putExtra("purchasePrice", doc.getString("purchasePrice"));
        intent.putExtra("sellingPrice", doc.getString("sellingPrice"));
        String batch = doc.getString("batchNumber");
        if (batch == null || batch.isEmpty()) batch = doc.getString("batch");
        intent.putExtra("batchNumber", batch);
        intent.putExtra("manufactureDate", doc.getString("manufactureDate"));
        intent.putExtra("expiryDate", doc.getString("expiryDate"));
        intent.putExtra("alertBefore", doc.getString("alertBefore"));
        startActivity(intent);
    }

    private boolean isExpiringSoon(String expiryDateStr) {
        if (expiryDateStr == null || expiryDateStr.trim().isEmpty()) return false;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 30);
        Date thirtyDaysFromNow = cal.getTime();

        String[] formats = {"dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd", "dd-MM-yyyy"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                Date expiryDate = sdf.parse(expiryDateStr.trim());
                if (expiryDate != null && expiryDate.before(thirtyDaysFromNow)) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }
}
