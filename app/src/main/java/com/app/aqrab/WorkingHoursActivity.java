package com.app.aqrab;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class WorkingHoursActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    private EditText etSatOpen, etSatClose, etSunOpen, etSunClose, etMonOpen, etMonClose;
    private EditText etTueOpen, etTueClose, etWedOpen, etWedClose, etThuOpen, etThuClose, etFriOpen, etFriClose;
    private CheckBox cbOpen247;
    private Button btnSave;
    private ImageButton btnBack;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_working_hours);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initViews();
        setupTimePickers();
        loadWorkingHours();

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveWorkingHours());
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save_hours);
        cbOpen247 = findViewById(R.id.cb_open_24_7);

        etSatOpen = findViewById(R.id.et_sat_open);
        etSatClose = findViewById(R.id.et_sat_close);
        etSunOpen = findViewById(R.id.et_sun_open);
        etSunClose = findViewById(R.id.et_sun_close);
        etMonOpen = findViewById(R.id.et_mon_open);
        etMonClose = findViewById(R.id.et_mon_close);
        etTueOpen = findViewById(R.id.et_tue_open);
        etTueClose = findViewById(R.id.et_tue_close);
        etWedOpen = findViewById(R.id.et_wed_open);
        etWedClose = findViewById(R.id.et_wed_close);
        etThuOpen = findViewById(R.id.et_thu_open);
        etThuClose = findViewById(R.id.et_thu_close);
        etFriOpen = findViewById(R.id.et_fri_open);
        etFriClose = findViewById(R.id.et_fri_close);
    }

    private void setupTimePickers() {
        View.OnClickListener timeListener = v -> {
            EditText et = (EditText) v;
            Calendar mcurrentTime = Calendar.getInstance();
            int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mcurrentTime.get(Calendar.MINUTE);
            TimePickerDialog mTimePicker;
            mTimePicker = new TimePickerDialog(WorkingHoursActivity.this, (timePicker, selectedHour, selectedMinute) -> 
                    et.setText(String.format("%02d:%02d", selectedHour, selectedMinute)), hour, minute, true);
            mTimePicker.setTitle("Select Time");
            mTimePicker.show();
        };

        EditText[] editTexts = {etSatOpen, etSatClose, etSunOpen, etSunClose, etMonOpen, etMonClose, 
                               etTueOpen, etTueClose, etWedOpen, etWedClose, etThuOpen, etThuClose, etFriOpen, etFriClose};
        
        for (EditText et : editTexts) {
            et.setOnClickListener(timeListener);
        }
    }

    private void loadWorkingHours() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        Map<String, Object> hours = (Map<String, Object>) queryDocumentSnapshots.getDocuments().get(0).get("workingHours");
                        if (hours != null) {
                            cbOpen247.setChecked(Boolean.TRUE.equals(hours.get("open247")));
                            
                            etSatOpen.setText((String) hours.get("sat_open"));
                            etSatClose.setText((String) hours.get("sat_close"));
                            etSunOpen.setText((String) hours.get("sun_open"));
                            etSunClose.setText((String) hours.get("sun_close"));
                            etMonOpen.setText((String) hours.get("mon_open"));
                            etMonClose.setText((String) hours.get("mon_close"));
                            etTueOpen.setText((String) hours.get("tue_open"));
                            etTueClose.setText((String) hours.get("tue_close"));
                            etWedOpen.setText((String) hours.get("wed_open"));
                            etWedClose.setText((String) hours.get("wed_close"));
                            etThuOpen.setText((String) hours.get("thu_open"));
                            etThuClose.setText((String) hours.get("thu_close"));
                            etFriOpen.setText((String) hours.get("fri_open"));
                            etFriClose.setText((String) hours.get("fri_close"));
                        }
                    }
                });
    }

    private void saveWorkingHours() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        Map<String, Object> hours = new HashMap<>();
        hours.put("open247", cbOpen247.isChecked());
        hours.put("sat_open", etSatOpen.getText().toString());
        hours.put("sat_close", etSatClose.getText().toString());
        hours.put("sun_open", etSunOpen.getText().toString());
        hours.put("sun_close", etSunClose.getText().toString());
        hours.put("mon_open", etMonOpen.getText().toString());
        hours.put("mon_close", etMonClose.getText().toString());
        hours.put("tue_open", etTueOpen.getText().toString());
        hours.put("tue_close", etTueClose.getText().toString());
        hours.put("wed_open", etWedOpen.getText().toString());
        hours.put("wed_close", etWedClose.getText().toString());
        hours.put("thu_open", etThuOpen.getText().toString());
        hours.put("thu_close", etThuClose.getText().toString());
        hours.put("fri_open", etFriOpen.getText().toString());
        hours.put("fri_close", etFriClose.getText().toString());

        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        db.collection("Pharmacies").document(docId)
                                .update("workingHours", hours)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Working hours saved successfully", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
}
