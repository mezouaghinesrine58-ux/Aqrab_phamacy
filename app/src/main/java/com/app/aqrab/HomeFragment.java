package com.app.aqrab;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 2;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private LinearLayout llNearbyList;
    private TextView tvNearbyLabel;
    private EditText etSearch;
    private Location userLocation;
    private List<MedicineInfo> lastMedsFound;

    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<Uri> cameraLauncher;
    private Uri cameraImageUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // إعداد مشغلات اختيار الصور من المعرض أو الكاميرا
        galleryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                processPrescriptionImage(result.getData().getData());
            }
        });

        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraImageUri != null) {
                processPrescriptionImage(cameraImageUri);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // تهيئة خدمات Firebase
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // ربط عناصر واجهة المستخدم
        llNearbyList = view.findViewById(R.id.ll_nearby_pharmacies_list);
        tvNearbyLabel = view.findViewById(R.id.tv_nearby_label);
        etSearch = view.findViewById(R.id.et_search);

        // إعداد مستمع للبحث عند الضغط على زر البحث في لوحة المفاتيح
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        // إعداد مستمع لأيقونة البحث
        view.findViewById(R.id.iv_search_icon).setOnClickListener(v -> performSearch());

        // منطق عرض رسالة الترحيب باسم المستخدم أو الصيدلية
        TextView tvWelcome = view.findViewById(R.id.tv_welcome);
        String intentName = getActivity().getIntent().getStringExtra("user_name");
        
        if (intentName != null && !intentName.isEmpty()) {
            tvWelcome.setText(getString(R.string.welcome_back, intentName));
        } else if (mAuth.getCurrentUser() != null) {
            String uid = mAuth.getCurrentUser().getUid();
            db.collection("Users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("fullName");
                    tvWelcome.setText(getString(R.string.welcome_back, (name != null ? name : "User")));
                } else {
                    // التحقق مما إذا كان المستخدم صيدلية
                    db.collection("Pharmacies").document(uid).get().addOnSuccessListener(phDoc -> {
                        if (phDoc.exists()) {
                            tvWelcome.setText(getString(R.string.welcome_back, phDoc.getString("pharmacyName")));
                        }
                    });
                }
            });
        }

        // إعداد مستمعات النقرات للأزرار المختلفة في الشاشة الرئيسية
        view.findViewById(R.id.ll_scan_prescription).setOnClickListener(v -> showImagePickerDialog());

        view.findViewById(R.id.ll_nearby_pharmacies).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), MapsActivity.class));
        });

        view.findViewById(R.id.ll_my_favorites).setOnClickListener(v -> {
            startActivity(new Intent(getContext(), FavoritesActivity.class));
        });

        // عرض كافة الصيدليات القريبة أو نتائج البحث عن وصفة طبية
        view.findViewById(R.id.tv_view_all_nearby).setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), NearbyPharmaciesActivity.class);
            String query = etSearch.getText().toString().trim();
            if (lastMedsFound != null && !lastMedsFound.isEmpty()) {
                ArrayList<String> names = new ArrayList<>();
                ArrayList<String> strengths = new ArrayList<>();
                for (MedicineInfo mi : lastMedsFound) {
                    names.add(mi.name);
                    strengths.add(mi.strength);
                }
                intent.putStringArrayListExtra("MED_NAMES", names);
                intent.putStringArrayListExtra("MED_STRENGTHS", strengths);
            } else if (!query.isEmpty()) {
                intent.putExtra("SEARCH_QUERY", query);
            }
            startActivity(intent);
        });

        view.findViewById(R.id.iv_notifications).setOnClickListener(v -> showNotificationsDialog());

        TextView tvRequest = view.findViewById(R.id.tv_request_medicine);
        if (tvRequest != null) {
            tvRequest.setOnClickListener(v -> showRequestMedicineDialog());
        }

        // التحقق من أذونات الموقع الجغرافي
        checkLocationPermission();

        return view;
    }

    // التحقق من الحصول على إذن الوصول للموقع
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
        } else {
            getCurrentLocation();
        }
    }

    // الحصول على الإحداثيات الحالية للمستخدم
    private void getCurrentLocation() {
        LocationManager lm = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        try {
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) {
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            
            if (loc != null) {
                userLocation = loc;
                Log.d(TAG, "Location found: " + loc.getLatitude() + ", " + loc.getLongitude());
            }
            loadPharmacies(); // تحميل الصيدليات القريبة بناءً على الموقع
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission error", e);
        }
    }

    // معالجة عملية البحث اليدوي
    private void performSearch() {
        String query = etSearch.getText().toString().trim();
        lastMedsFound = null; // إعادة تعيين نتائج الوصفة عند إجراء بحث يدوي
        if (!query.isEmpty()) {
            Log.d(TAG, "Searching for: " + query);
            searchMedicine(query);
        } else {
            tvNearbyLabel.setText(R.string.feature_nearby);
            loadPharmacies();
        }
        
        // إخفاء لوحة المفاتيح
        etSearch.clearFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
    }

    // البحث عن دواء معين في قاعدة البيانات Firestore
    private void searchMedicine(String query) {
        String lowerCaseQuery = query.toLowerCase();
        // تحويل الحرف الأول لكبير للبحث المتوافق
        String capitalized = query.substring(0, 1).toUpperCase() + (query.length() > 1 ? query.substring(1).toLowerCase() : "");

        tvNearbyLabel.setText(getString(R.string.search_results) + query);
        
        // البحث عن الدواء بأسماء تبدأ بالحروف المدخلة (صغيرة وكبيرة)
        Task<com.google.firebase.firestore.QuerySnapshot> task1 = db.collectionGroup("Inventory")
                .whereGreaterThanOrEqualTo("name", lowerCaseQuery)
                .whereLessThanOrEqualTo("name", lowerCaseQuery + "\uf8ff")
                .get();

        Task<com.google.firebase.firestore.QuerySnapshot> task2 = db.collectionGroup("Inventory")
                .whereGreaterThanOrEqualTo("name", capitalized)
                .whereLessThanOrEqualTo("name", capitalized + "\uf8ff")
                .get();

        Tasks.whenAllSuccess(task1, task2).addOnSuccessListener(results -> {
            List<String> foundPhIds = new ArrayList<>();
            for (Object res : results) {
                com.google.firebase.firestore.QuerySnapshot snapshots = (com.google.firebase.firestore.QuerySnapshot) res;
                for (QueryDocumentSnapshot doc : snapshots) {
                    // الحصول على معرف الصيدلية المالكة لهذا الدواء
                    if (doc.getReference().getParent().getParent() != null) {
                        String phId = doc.getReference().getParent().getParent().getId();
                        if (!foundPhIds.contains(phId)) foundPhIds.add(phId);
                    }
                }
            }

            if (foundPhIds.isEmpty()) {
                saveSearchHistory(query, 0);
                showEmptyResults(getString(R.string.no_pharmacies_found) + query);
                return;
            }

            saveSearchHistory(query, foundPhIds.size());
            fetchPharmacyDetails(foundPhIds);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Search failed", e);
            Toast.makeText(getContext(), "Search failed", Toast.LENGTH_SHORT).show();
        });
    }

    // حفظ سجل عمليات البحث للمستخدم
    private void saveSearchHistory(String query, int resultCount) {
        if (mAuth.getCurrentUser() == null) return;
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("patientId", mAuth.getUid());
        data.put("query", query);
        data.put("timestamp", System.currentTimeMillis());
        data.put("resultCount", resultCount);
        data.put("type", "search");
        db.collection("PatientHistory").add(data);
    }

    // جلب تفاصيل الصيدليات بناءً على المعرفات المستخرجة من البحث
    private void fetchPharmacyDetails(List<String> ids) {
        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String id : ids) {
            tasks.add(db.collection("Pharmacies").document(id).get());
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(docs -> {
            List<PharmacyModel> list = new ArrayList<>();
            for (Object obj : docs) {
                DocumentSnapshot doc = (DocumentSnapshot) obj;
                if (doc.exists()) {
                    PharmacyModel model = mapDocToPharmacy(doc);
                    if (model != null) list.add(model);
                }
            }
            // ترتيب الصيدليات حسب المسافة
            Collections.sort(list, (p1, p2) -> Float.compare(p1.distance, p2.distance));
            updateUIList(list);
        });
    }

    // تحميل كافة الصيدليات المسجلة (الحالة الافتراضية)
    private void loadPharmacies() {
        db.collection("Pharmacies").get().addOnSuccessListener(queryDocumentSnapshots -> {
            List<PharmacyModel> list = new ArrayList<>();
            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                PharmacyModel model = mapDocToPharmacy(doc);
                if (model != null) list.add(model);
            }
            Collections.sort(list, (p1, p2) -> Float.compare(p1.distance, p2.distance));
            updateUIList(list);
        }).addOnFailureListener(e -> Log.e(TAG, "Load pharmacies failed", e));
    }

    // تحويل وثيقة الصيدلية إلى كائن برمجي وحساب المسافة عن المستخدم
    private PharmacyModel mapDocToPharmacy(DocumentSnapshot doc) {
        try {
            String name = doc.getString("pharmacyName");
            String address = doc.getString("address");
            String photoUrl = doc.getString("photoUrl");
            Double lat = doc.getDouble("latitude");
            Double lon = doc.getDouble("longitude");
            Map<String, Object> hours = (Map<String, Object>) doc.get("workingHours");

            if (lat == null || lon == null) return null;

            float dist = 0;
            if (userLocation != null) {
                float[] res = new float[1];
                Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), lat, lon, res);
                dist = res[0] / 1000f; // التحويل للكيلومترات
            }

            return new PharmacyModel(
                name, address, photoUrl, dist, checkIfOpen(hours),
                lat, lon, doc.getString("phone"), doc.getString("description"), doc.getId()
            );
        } catch (Exception e) {
            Log.e(TAG, "Mapping error", e);
            return null;
        }
    }

    // عرض رسالة عند عدم وجود نتائج بحث
    private void showEmptyResults(String msg) {
        llNearbyList.removeAllViews();
        TextView tv = new TextView(getContext());
        tv.setText(msg);
        tv.setPadding(50, 50, 50, 50);
        tv.setGravity(android.view.Gravity.CENTER);
        llNearbyList.addView(tv);
    }

    // التحقق مما إذا كانت الصيدلية مفتوحة الآن بناءً على ساعات العمل
    private boolean checkIfOpen(Map<String, Object> hours) {
        if (hours == null) return false;
        if (Boolean.TRUE.equals(hours.get("open247"))) return true;

        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int currentTimeMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        String openKey, closeKey;
        switch (day) {
            case Calendar.SATURDAY: openKey = "sat_open"; closeKey = "sat_close"; break;
            case Calendar.SUNDAY: openKey = "sun_open"; closeKey = "sun_close"; break;
            case Calendar.MONDAY: openKey = "mon_open"; closeKey = "mon_close"; break;
            case Calendar.TUESDAY: openKey = "tue_open"; closeKey = "tue_close"; break;
            case Calendar.WEDNESDAY: openKey = "wed_open"; closeKey = "wed_close"; break;
            case Calendar.THURSDAY: openKey = "thu_open"; closeKey = "thu_close"; break;
            case Calendar.FRIDAY: openKey = "fri_open"; closeKey = "fri_close"; break;
            default: return false;
        }

        String open = (String) hours.get(openKey);
        String close = (String) hours.get(closeKey);

        if (open == null || close == null || open.isEmpty() || close.isEmpty()) return false;

        try {
            int openMins = parseTime(open);
            int closeMins = parseTime(close);

            if (closeMins < openMins) { // حالة الدوام الليلي (بعد منتصف الليل)
                return currentTimeMinutes >= openMins || currentTimeMinutes <= closeMins;
            } else {
                return currentTimeMinutes >= openMins && currentTimeMinutes <= closeMins;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // تحويل الوقت من نص إلى دقائق
    private int parseTime(String time) {
        String[] p = time.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    // تحديث قائمة الصيدليات المعروضة في الواجهة
    private void updateUIList(List<PharmacyModel> list) {
        if (getContext() == null) return;
        llNearbyList.removeAllViews();
        
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int limit = 3; // إظهار أفضل 3 صيدليات فقط على الشاشة الرئيسية
        
        for (int i = 0; i < Math.min(list.size(), limit); i++) {
            PharmacyModel ph = list.get(i);
            View itemView = inflater.inflate(R.layout.item_nearby_pharmacy, llNearbyList, false);
            
            TextView tvName = itemView.findViewById(R.id.tv_pharmacy_name);
            TextView tvAddr = itemView.findViewById(R.id.tv_pharmacy_address);
            TextView tvDist = itemView.findViewById(R.id.tv_distance);
            TextView tvStatus = itemView.findViewById(R.id.tv_status);
            ImageView ivPhoto = itemView.findViewById(R.id.iv_pharmacy_img);
            ImageView ivNav = itemView.findViewById(R.id.iv_navigation);

            tvName.setText(ph.name);
            tvAddr.setText(ph.address != null ? ph.address : "No address info");
            tvDist.setText(String.format(Locale.getDefault(), "%.1f km", ph.distance));

            // تحديد الحالة (مفتوح، مغلق، أو عدد الأدوية المتطابقة مع الوصفة)
            if (ph.totalRequested > 0) {
                String matchText = getString(R.string.matched) + " " + ph.matchedCount + "/" + ph.totalRequested;
                tvStatus.setText(matchText);
                if (ph.matchedCount == ph.totalRequested) {
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                    tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
                } else if (ph.matchedCount >= ph.totalRequested / 2.0) {
                    tvStatus.setTextColor(Color.parseColor("#FFC107"));
                    tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF8E1")));
                } else {
                    tvStatus.setTextColor(Color.parseColor("#F44336"));
                    tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
                }
            } else if (ph.isOpen) {
                tvStatus.setText(R.string.open_now);
                tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            } else {
                tvStatus.setText(R.string.closed);
                tvStatus.setTextColor(Color.parseColor("#F44336"));
                tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
            }

            // تحميل صورة الصيدلية باستخدام Glide
            if (ph.photoUrl != null && !ph.photoUrl.isEmpty()) {
                Glide.with(this).load(ph.photoUrl).placeholder(R.drawable.a_pharmacy).into(ivPhoto);
            } else {
                ivPhoto.setImageResource(R.drawable.a_pharmacy);
            }

            // فتح الملاحة عند الضغط على أيقونة التوجيه
            ivNav.setOnClickListener(v -> openNavigation(ph.latitude, ph.longitude));

            // فتح تفاصيل الصيدلية عند الضغط على العنصر
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), PharmacyDetailActivity.class);
                intent.putExtra("PHARMACY_ID", ph.id);
                intent.putExtra("PHARMACY_NAME", ph.name);
                intent.putExtra("PHARMACY_LAT", ph.latitude);
                intent.putExtra("PHARMACY_LON", ph.longitude);
                startActivity(intent);
            });

            llNearbyList.addView(itemView);
        }
    }

    // فتح تطبيق الخرائط لبدء التوجيه نحو الصيدلية
    private void openNavigation(double lat, double lon) {
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + lat + "," + lon);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getContext().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // حل بديل عند عدم وجود تطبيق الخرائط
            String url = "https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lon;
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    // جلب وعرض الإشعارات الخاصة بالمستخدم
    private void showNotificationsDialog() {
        if (mAuth.getCurrentUser() == null) return;
        
        String uid = mAuth.getCurrentUser().getUid();
        db.collection("Notifications")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(results -> {
                    if (results.isEmpty()) {
                        Toast.makeText(getContext(), R.string.no_notifications, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<DocumentSnapshot> list = results.getDocuments();
                    // ترتيب الإشعارات من الأحدث للأقدم
                    Collections.sort(list, (d1, d2) -> {
                        Long t1 = d1.getLong("timestamp");
                        Long t2 = d2.getLong("timestamp");
                        return Long.compare(t2 != null ? t2 : 0, t1 != null ? t1 : 0);
                    });

                    // إنشاء واجهة عرض الإشعارات داخل نافذة منبثقة
                    LinearLayout container = new LinearLayout(getContext());
                    container.setOrientation(LinearLayout.VERTICAL);
                    container.setPadding(20, 20, 20, 20);

                    LayoutInflater inf = LayoutInflater.from(getContext());
                    for (DocumentSnapshot doc : list) {
                        View v = inf.inflate(R.layout.item_notification, container, false);
                        ((TextView)v.findViewById(R.id.tv_notif_title)).setText(doc.getString("title"));
                        ((TextView)v.findViewById(R.id.tv_notif_message)).setText(doc.getString("message"));
                        
                        String phId = doc.getString("pharmacyId");
                        if (phId != null && !phId.isEmpty()) {
                            setupPharmacyActionInNotif(v, phId);
                        }

                        container.addView(v);
                    }

                    android.widget.ScrollView sv = new android.widget.ScrollView(getContext());
                    sv.addView(container);

                    new androidx.appcompat.app.AlertDialog.Builder(getContext())
                            .setTitle(R.string.your_notifications)
                            .setView(sv)
                            .setPositiveButton(R.string.close, null)
                            .show();
                });
    }

    // إعداد التفاعلات الخاصة بالصيدلية داخل الإشعار (مثل التوجيه)
    private void setupPharmacyActionInNotif(View v, String phId) {
        db.collection("Pharmacies").document(phId).get().addOnSuccessListener(phDoc -> {
            if (phDoc.exists()) {
                LinearLayout llActions = v.findViewById(R.id.ll_pharmacy_actions);
                TextView tvStatus = v.findViewById(R.id.tv_ph_status);
                View btnDirections = v.findViewById(R.id.btn_get_directions);

                llActions.setVisibility(View.VISIBLE);

                // عرض حالة الصيدلية الحالية في الإشعار
                Map<String, Object> hours = (Map<String, Object>) phDoc.get("workingHours");
                boolean isOpen = checkIfOpen(hours);
                if (isOpen) {
                    tvStatus.setText(R.string.open_now);
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                } else {
                    tvStatus.setText(R.string.closed);
                    tvStatus.setTextColor(Color.parseColor("#F44336"));
                }

                // إعداد زر التوجيه
                Double lat = phDoc.getDouble("latitude");
                Double lon = phDoc.getDouble("longitude");
                if (lat != null && lon != null) {
                    btnDirections.setOnClickListener(v1 -> openNavigation(lat, lon));
                } else {
                    btnDirections.setVisibility(View.GONE);
                }
            }
        });
    }

    // عرض نافذة لطلب دواء غير متوفر لإخطار الصيدليات
    private void showRequestMedicineDialog() {
        EditText et = new EditText(getContext());
        et.setHint("e.g. Panadol 500mg");
        
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.request_medicine)
                .setMessage(R.string.notify_stock)
                .setView(et)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) saveRequest(name);
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    // حفظ طلب الدواء في Firestore
    private void saveRequest(String name) {
        if (mAuth.getCurrentUser() == null) return;

        Map<String, Object> req = new java.util.HashMap<>();
        req.put("medicineName", name.toLowerCase());
        req.put("displayName", name);
        req.put("patientId", mAuth.getUid());
        req.put("timestamp", System.currentTimeMillis());
        req.put("status", "pending");

        db.collection("MedicineRequests").add(req).addOnSuccessListener(d -> 
            Toast.makeText(getContext(), R.string.request_sent, Toast.LENGTH_SHORT).show());
    }

    // عرض خيارات اختيار صورة الوصفة (كاميرا أو معرض)
    private void showImagePickerDialog() {
        String[] options = {getString(R.string.take_photo), getString(R.string.choose_gallery)};
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.select_prescription)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else openGallery();
                }).show();
    }

    // فتح معرض الصور
    private void openGallery() {
        Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(i);
    }

    // فتح الكاميرا لالتقاط صورة الوصفة
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        try {
            java.io.File dir = getContext().getExternalFilesDir(null);
            java.io.File file = new java.io.File(dir, "scan_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = androidx.core.content.FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".provider", file);
            cameraLauncher.launch(cameraImageUri);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Camera error", Toast.LENGTH_SHORT).show();
        }
    }

    // معالجة صورة الوصفة واستخراج النصوص منها باستخدام ML Kit
    private void processPrescriptionImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(getContext(), uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            
            Toast.makeText(getContext(), R.string.reading_prescription, Toast.LENGTH_SHORT).show();
            
            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        // استخراج أسماء الأدوية من النص
                        List<MedicineInfo> meds = extractMeds(visionText);
                        if (meds.isEmpty()) {
                            savePrescriptionHistory(uri, new ArrayList<>(), 0);
                            Toast.makeText(getContext(), R.string.ocr_no_meds, Toast.LENGTH_LONG).show();
                        } else {
                            // عرض الأدوية المستخرجة للمستخدم للتأكيد
                            showMedicationSelectionDialog(meds, uri);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "OCR failed", e);
                        Toast.makeText(getContext(), "Failed to read image", Toast.LENGTH_SHORT).show();
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // نافذة لتأكيد الأدوية التي تم التعرف عليها بواسطة الذكاء الاصطناعي
    private void showMedicationSelectionDialog(List<MedicineInfo> meds, Uri uri) {
        if (getContext() == null) return;

        String[] medStrings = new String[meds.size()];
        boolean[] checkedItems = new boolean[meds.size()];
        for (int i = 0; i < meds.size(); i++) {
            medStrings[i] = meds.get(i).name + (meds.get(i).strength.isEmpty() ? "" : " " + meds.get(i).strength);
            checkedItems[i] = true; // اختيار الكل افتراضياً
        }

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(R.string.confirm_medicines)
                .setMultiChoiceItems(medStrings, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton(R.string.search_pharmacies, (dialog, which) -> {
                    List<MedicineInfo> selectedMeds = new ArrayList<>();
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            selectedMeds.add(meds.get(i));
                        }
                    }
                    if (!selectedMeds.isEmpty()) {
                        lastMedsFound = selectedMeds;
                        searchPharmaciesForMeds(selectedMeds, uri);
                    } else {
                        Toast.makeText(getContext(), R.string.no_meds_selected, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    // منطق استخراج أسماء الأدوية وفلترة الكلمات غير الضرورية من الوصفة
    private List<MedicineInfo> extractMeds(Text visionText) {
        List<MedicineInfo> list = new ArrayList<>();
        // قائمة الكلمات التي يجب تجاهلها (معلومات المريض والطبيب)
        String[] ignoreList = {
                "patient", "name", "age", "sex", "gender", "date", "address", "doctor", "dr.", 
                "hospital", "clinic", "diagnosis", "tel", "phone", "mobile", "weight", "height", 
                "signature", "years", "old", "male", "female", "history", "notes", "rx",
                "tablet", "capsule", "syrup", "injection", "daily", "times", "week", "month",
                "morning", "evening", "night", "before", "after", "food", "meal", "dose",
                "qty", "quantity", "refill", "sig", "dispense", "brand", "generic"
        };

        for (Text.TextBlock block : visionText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String originalTxt = line.getText().trim();
                String txt = originalTxt.toLowerCase();
                
                if (txt.length() < 3) continue;
                
                // تخطي الأسطر التي تبدأ بكلمات التجاهل
                boolean skip = false;
                for (String ignore : ignoreList) {
                    if (txt.startsWith(ignore) || txt.equals(ignore)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;

                // تجاهل الأسطر التي يغلب عليها الأرقام (مثل أرقام الهواتف أو التواريخ)
                if (txt.replaceAll("[^0-9]", "").length() > txt.replaceAll("[0-9]", "").length() && !txt.matches(".*[a-zA-Z]{3,}.*")) {
                    continue;
                }
                
                // تنظيف البوادئ الشائعة مثل Rx
                String cleanedTxt = originalTxt.replaceAll("^(?i)(rx|med|medicine)[:\\s]*", "").trim();

                // استخدام Regex متقدم لاستخراج اسم الدواء وتركيزه (مثلاً Panadol 500mg)
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                        "(?i)^([a-z]{3,}(?:\\s+[a-z]+)*)\\s*(\\d+\\s*(?:mg|ml|g|mcg|unit|iu|%|tab|cap|pill)s?)?.*",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                ).matcher(cleanedTxt);
                
                if (m.find()) {
                    String name = m.group(1).trim();
                    String strength = (m.group(2) != null) ? m.group(2).trim() : "";
                    
                    // فلترة إضافية للتأكد من أن الاسم المستخرج ليس كلمة تجاهل
                    boolean nameIsInvalid = false;
                    for (String ignore : ignoreList) {
                        if (name.toLowerCase().equals(ignore)) {
                            nameIsInvalid = true;
                            break;
                        }
                    }
                    
                    if (!nameIsInvalid && name.length() >= 3) {
                        // تجنب التكرار في القائمة
                        boolean exists = false;
                        for(MedicineInfo mi : list) {
                            if(mi.name.equalsIgnoreCase(name)) { exists = true; break; }
                        }
                        if(!exists) list.add(new MedicineInfo(name, strength));
                    }
                }
            }
        }
        return list;
    }

    // البحث عن الصيدليات التي يتوفر لديها قائمة الأدوية المستخرجة من الوصفة
    private void searchPharmaciesForMeds(List<MedicineInfo> meds, Uri imageUri) {
        StringBuilder searchInfo = new StringBuilder("Searching for: ");
        for(MedicineInfo mi : meds) searchInfo.append(mi.name).append(" ").append(mi.strength).append(", ");
        Log.d(TAG, searchInfo.toString());
        
        db.collection("Pharmacies").get().addOnSuccessListener(phDocs -> {
            List<Task<Void>> tasks = new ArrayList<>();
            List<PharmacyModel> results = new ArrayList<>();

            // فحص مخزون كل صيدلية
            for (QueryDocumentSnapshot ph : phDocs) {
                Task<Void> t = ph.getReference().collection("Inventory").get().continueWith(task -> {
                    if (!task.isSuccessful()) return null;
                    List<DocumentSnapshot> inventory = task.getResult().getDocuments();
                    int count = 0;
                    
                    for (MedicineInfo required : meds) {
                        boolean found = false;
                        for (DocumentSnapshot item : inventory) {
                            String dbName = item.getString("name");
                            if (dbName == null) continue;
                            
                            // مطابقة الدواء المطلوب مع قاعدة البيانات
                            if (isMedicineMatch(required.name, dbName)) {
                                found = true;
                                break;
                            }
                        }
                        if (found) count++;
                    }
                    
                    // إذا وجدت تطابق لواحد على الأقل من الأدوية، تضاف الصيدلية للنتائج
                    if (count > 0) {
                        PharmacyModel model = mapDocToPharmacy(ph);
                        if (model != null) {
                            model.matchedCount = count;
                            model.totalRequested = meds.size();
                            synchronized (results) { results.add(model); }
                        }
                    }
                    return null;
                });
                tasks.add(t);
            }

            // ترتيب النتائج بناءً على عدد الأدوية المتوفرة ثم المسافة
            Tasks.whenAllComplete(tasks).addOnSuccessListener(v -> {
                savePrescriptionHistory(imageUri, meds, results.size());
                if (results.isEmpty()) {
                    Toast.makeText(getContext(), R.string.no_ph_found_meds, Toast.LENGTH_LONG).show();
                    loadPharmacies();
                } else {
                    Collections.sort(results, (p1, p2) -> {
                        if (p1.matchedCount != p2.matchedCount) {
                            return Integer.compare(p2.matchedCount, p1.matchedCount);
                        }
                        return Float.compare(p1.distance, p2.distance);
                    });
                    tvNearbyLabel.setText(getString(R.string.prescription_results, results.size()));
                    updateUIList(results);
                    Toast.makeText(getContext(), getString(R.string.found_ph_count, results.size()), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // منطق مطابقة أسماء الأدوية للتعامل مع الفوارق الطفيفة الناتجة عن الـ OCR
    private boolean isMedicineMatch(String req, String db) {
        if (req == null || db == null) return false;
        
        // تنظيف النصوص وحذف الرموز والمسافات
        String r = req.toLowerCase().replaceAll("[^a-z0-9]", "");
        String d = db.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        if (r.isEmpty() || d.isEmpty()) return false;
        
        // المطابقة المباشرة أو الاحتواء
        if (r.contains(d) || d.contains(r)) return true;
        
        // استخدام خوارزمية Levenshtein للأخطاء الإملائية البسيطة
        int threshold = Math.max(1, Math.min(r.length(), d.length()) / 4);
        return getLevenshteinDistance(r, d) <= threshold;
    }

    // حساب المسافة الإملائية (Levenshtein Distance)
    private int getLevenshteinDistance(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) prev[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            int[] curr = new int[s2.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int d = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + d);
            }
            prev = curr;
        }
        return prev[s2.length()];
    }

    // حفظ سجل مسح الوصفات الطبية للمريض
    private void savePrescriptionHistory(Uri uri, List<MedicineInfo> meds, int resultCount) {
        if (mAuth.getCurrentUser() == null) return;
        
        List<String> medNames = new ArrayList<>();
        for (MedicineInfo m : meds) medNames.add(m.name + (m.strength.isEmpty() ? "" : " " + m.strength));

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("patientId", mAuth.getUid());
        data.put("imageUri", uri.toString());
        data.put("medicines", medNames);
        data.put("timestamp", System.currentTimeMillis());
        data.put("resultCount", resultCount);
        data.put("type", "prescription");
        db.collection("PatientHistory").add(data);
    }

    // كلاسات مساعدة لتمثيل البيانات
    private static class MedicineInfo {
        String name, strength;
        MedicineInfo(String n, String s) { name = n; strength = s; }
    }

    private static class PharmacyModel {
        String id, name, address, photoUrl, phone, description;
        float distance;
        boolean isOpen;
        double latitude, longitude;
        int matchedCount = 0;
        int totalRequested = 0;

        PharmacyModel(String n, String a, String p, float dist, boolean open, double lat, double lon, String ph, String desc, String id) {
            this.name = n; this.address = a; this.photoUrl = p; this.distance = dist;
            this.isOpen = open; this.latitude = lat; this.longitude = lon;
            this.phone = ph; this.description = desc; this.id = id;
        }
    }

    // التعامل مع نتائج طلب أذونات المستخدم
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }
}
