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
        // استدعاء LocaleHelper لضمان تطبيق اللغة الصحيحة (عربي/إنجليزي) المختارة من قبل المستخدم
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // تعريف حقول إدخال النص لأوقات الفتح والإغلاق لكل يوم
    private EditText etSatOpen, etSatClose, etSunOpen, etSunClose, etMonOpen, etMonClose;
    private EditText etTueOpen, etTueClose, etWedOpen, etWedClose, etThuOpen, etThuClose, etFriOpen, etFriClose;
    
    // تعريف العناصر التفاعلية (مربع اختيار 24/7، زر الحفظ، وزر الرجوع)
    private CheckBox cbOpen247;
    private Button btnSave;
    private ImageButton btnBack;
    
    // تعريف كائنات Firebase للتعامل مع قاعدة البيانات (Firestore) والمصادقة (Auth)
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_working_hours);
        // إنشاء مثيلات Firebase للتمكن من قراءة وكتابة البيانات
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // استدعاء دالة ربط عناصر الواجهة بمتغيرات الجافا
        initViews();
        
        // استدعاء دالة تفعيل منتقي الوقت عند النقر على حقول الإدخال
        setupTimePickers();
        
        // استدعاء دالة جلب البيانات المخزنة مسبقاً من قاعدة البيانات
        loadWorkingHours();

        //  زر الرجوع ليعود بالصيدلي للواجهة السابقة عند النقر عليه
        btnBack.setOnClickListener(v -> finish());
        
        //  زر الحفظ للتحقق من البيانات وإرسالها إلى Firestore
        btnSave.setOnClickListener(v -> saveWorkingHours());
    }
    private void initViews() {
        // ربط أزرار التحكم
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save_hours);
        cbOpen247 = findViewById(R.id.cb_open_24_7);

        // ربط حقول الإدخال لكل يوم من أيام الأسبوع
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
        // إنشاء مستمع موحد لجميع الحقول يقوم بفتح ساعة لاختيار الوقت
        View.OnClickListener timeListener = v -> {
            EditText et = (EditText) v;
            // الحصول على الوقت الحالي لضبطه كقيمة افتراضية في الساعة المنبثقة
            Calendar mcurrentTime = Calendar.getInstance();
            int hour = mcurrentTime.get(Calendar.HOUR_OF_DAY);
            int minute = mcurrentTime.get(Calendar.MINUTE);
            
            // إنشاء منتقي الوقت وتحديد الإجراء عند اختيار الوقت
            TimePickerDialog mTimePicker;
            mTimePicker = new TimePickerDialog(WorkingHoursActivity.this, (timePicker, selectedHour, selectedMinute) -> 
                    // تعيين الوقت المختار داخل حقل النص بتنسيق (ساعة:دقيقة)
                    et.setText(String.format("%02d:%02d", selectedHour, selectedMinute)), hour, minute, true);
            mTimePicker.setTitle("Select Time"); // عنوان النافذة المنبثقة
            mTimePicker.show(); // عرض النافذة
        };

        // وضع جميع حقول الإدخال في مصفوفة لتسهيل تطبيق المستمع عليها بدورة واحدة (Loop)
        EditText[] editTexts = {etSatOpen, etSatClose, etSunOpen, etSunClose, etMonOpen, etMonClose, 
                               etTueOpen, etTueClose, etWedOpen, etWedClose, etThuOpen, etThuClose, etFriOpen, etFriClose};
        
        for (EditText et : editTexts) {
            // تفعيل المستمع عند الضغط على أي حقل نصي
            et.setOnClickListener(timeListener);
        }
    }
    private void loadWorkingHours() {
        // التأكد من أن المستخدم مسجل دخوله حالياً
        if (mAuth.getCurrentUser() == null) return;
        
        // الحصول على المعرف الفريد للمستخدم (الصيدلي)
        String userId = mAuth.getCurrentUser().getUid();

        // البحث في مجموعة "Pharmacies" عن الوثيقة التي تطابق ownerId الخاص بالمستخدم
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    // التحقق مما إذا تم العثور على وثيقة صيدلية
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // استخراج خريطة البيانات "workingHours" من الوثيقة الأولى المسترجعة
                        Map<String, Object> hours = (Map<String, Object>) queryDocumentSnapshots.getDocuments().get(0).get("workingHours");
                        if (hours != null) {
                            // تحديث حالة مربع الاختيار 24/7
                            cbOpen247.setChecked(Boolean.TRUE.equals(hours.get("open247")));
                            
                            // تعبئة حقول النص بالقيم المسترجعة من قاعدة البيانات (أوقات الفتح والإغلاق)
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
        // التحقق من حالة تسجيل الدخول
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        // تجميع كافة المدخلات في خريطة (HashMap) ليتم حفظها ككائن واحد في Firestore
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

        // العثور على وثيقة الصيدلية الخاصة بالمستخدم لتحديثها
        db.collection("Pharmacies")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // الحصول على معرف الوثيقة (Document ID)
                        String docId = queryDocumentSnapshots.getDocuments().get(0).getId();
                        
                        // تنفيذ عملية التحديث لحقل "workingHours" فقط
                        db.collection("Pharmacies").document(docId)
                                .update("workingHours", hours)
                                .addOnSuccessListener(aVoid -> {
                                    // إظهار رسالة نجاح وإغلاق النشاط
                                    Toast.makeText(this, "working hours were successfully saved", Toast.LENGTH_SHORT).show();
                                    finish();
                                })
                                .addOnFailureListener(e -> 
                                    // إظهار رسالة خطأ في حال فشل الاتصال بقاعدة البيانات
                                    Toast.makeText(this, "saving error" + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
}
