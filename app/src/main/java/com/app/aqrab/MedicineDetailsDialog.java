package com.app.aqrab;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.DocumentSnapshot;

public class MedicineDetailsDialog {

    public static void show(Context context, DocumentSnapshot doc) {
        show(context, doc, null);
    }

    public static void show(Context context, DocumentSnapshot doc, String pharmacyId) {
        if (context == null || doc == null) return;

        String name = doc.getString("name");
        String brand = doc.getString("brand");
        String strength = doc.getString("strength");
        String category = doc.getString("category");
        String form = doc.getString("form");
        Object qtyObj = doc.get("quantity");
        String qty = qtyObj != null ? qtyObj.toString() : "0";
        String unit = doc.getString("unit");
        String purchasePrice = doc.getString("purchasePrice");
        String sellingPrice = doc.getString("sellingPrice");
        String batch = doc.getString("batchNumber");
        if (batch == null || batch.isEmpty()) {
            batch = doc.getString("batch");
        }
        String mfgDate = doc.getString("manufactureDate");
        String expDate = doc.getString("expiryDate");
        String alertBefore = doc.getString("alertBefore");

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        // Header Title
        TextView tvTitle = new TextView(context);
        tvTitle.setText(name != null ? name : context.getString(R.string.medicine_details_title));
        tvTitle.setTextSize(20);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor("#2E5A44"));
        tvTitle.setPadding(0, 0, 0, 20);
        layout.addView(tvTitle);

        addDetailRow(context, layout, "💊 اسم الدواء / Medicine Name:", name);
        if (brand != null && !brand.isEmpty()) addDetailRow(context, layout, "🏷️ العلامة التجارية / Brand:", brand);
        if (strength != null && !strength.isEmpty()) addDetailRow(context, layout, "⚡ التركيز / Strength:", strength);
        if (category != null && !category.isEmpty()) addDetailRow(context, layout, "📂 الفئة / Category:", category);
        if (form != null && !form.isEmpty()) addDetailRow(context, layout, "🧪 الشكل الصيدلاني / Form:", form);
        addDetailRow(context, layout, "📦 الكمية بالمخزون / Stock:", qty + " " + (unit != null ? unit : ""));
        if (sellingPrice != null && !sellingPrice.isEmpty()) addDetailRow(context, layout, "💰 سعر البيع / Selling Price:", sellingPrice + " DA");
        if (purchasePrice != null && !purchasePrice.isEmpty()) addDetailRow(context, layout, "💵 سعر الشراء / Purchase Price:", purchasePrice + " DA");
        if (batch != null && !batch.isEmpty()) addDetailRow(context, layout, "🔢 رقم التشغيلة / Batch:", batch);
        if (mfgDate != null && !mfgDate.isEmpty()) addDetailRow(context, layout, "📅 تاريخ التصنيع / Mfg Date:", mfgDate);
        if (expDate != null && !expDate.isEmpty()) addDetailRow(context, layout, "⏰ تاريخ انتهاء الصلاحية / Expiry Date:", expDate);

        scrollView.addView(layout);

        String finalBatch = batch;
        new AlertDialog.Builder(context)
                .setTitle(R.string.medicine_details_title)
                .setView(scrollView)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.edit, (dialog, which) -> {
                    Intent intent = new Intent(context, AddMedicineActivity.class);
                    intent.putExtra("is_edit_mode", true);
                    intent.putExtra("medicine_id", doc.getId());
                    if (pharmacyId != null) {
                        intent.putExtra("pharmacy_id", pharmacyId);
                    }
                    intent.putExtra("name", name);
                    intent.putExtra("brand", brand);
                    intent.putExtra("category", category);
                    intent.putExtra("strength", strength);
                    intent.putExtra("form", form);
                    intent.putExtra("quantity", qty);
                    intent.putExtra("unit", unit);
                    intent.putExtra("purchasePrice", purchasePrice);
                    intent.putExtra("sellingPrice", sellingPrice);
                    intent.putExtra("batchNumber", finalBatch);
                    intent.putExtra("manufactureDate", mfgDate);
                    intent.putExtra("expiryDate", expDate);
                    intent.putExtra("alertBefore", alertBefore);
                    context.startActivity(intent);
                })
                .show();
    }

    private static void addDetailRow(Context context, LinearLayout parent, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 10, 0, 10);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextSize(12);
        tvLabel.setTextColor(Color.parseColor("#888888"));

        TextView tvValue = new TextView(context);
        tvValue.setText(value);
        tvValue.setTextSize(15);
        tvValue.setTextColor(Color.parseColor("#333333"));
        tvValue.setTypeface(null, Typeface.BOLD);

        row.addView(tvLabel);
        row.addView(tvValue);

        // Add line divider
        View line = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(0, 8, 0, 0);
        line.setLayoutParams(params);
        line.setBackgroundColor(Color.parseColor("#F0F0F0"));

        row.addView(line);

        parent.addView(row);
    }
}
