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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NearbyPharmaciesActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        // تطبيق اللغة المختارة لضمان استمرارها في هذه الشاشة
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    // كائن Firestore
    private FirebaseFirestore db;
    // حاوية القائمة
    private LinearLayout llList;
    // نص الحالة الفارغة
    private TextView tvEmpty;
    // موقع المستخدم
    private Location userLocation;

    private String searchQuery;
    private ArrayList<String> medNames;
    private ArrayList<String> medStrengths;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_pharmacies);

        db = FirebaseFirestore.getInstance();
        llList = findViewById(R.id.ll_pharmacies_list);
        tvEmpty = findViewById(R.id.tv_empty_pharmacies);

        searchQuery = getIntent().getStringExtra("SEARCH_QUERY");
        medNames = getIntent().getStringArrayListExtra("MED_NAMES");
        medStrengths = getIntent().getStringArrayListExtra("MED_STRENGTHS");

        TextView tvTitle = findViewById(R.id.tv_title);
        if (searchQuery != null) {
            tvTitle.setText("Results for: " + searchQuery);
        } else if (medNames != null) {
            tvTitle.setText("Prescription Results");
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        getCurrentLocation();
        
        if (searchQuery != null) {
            performMedicineSearch(searchQuery);
        } else if (medNames != null) {
            performPrescriptionSearch(medNames, medStrengths);
        } else {
            loadAllPharmacies();
        }
    }

    private void performMedicineSearch(String query) {
        String lowerCaseQuery = query.toLowerCase();
        String capitalized = query.substring(0, 1).toUpperCase() + (query.length() > 1 ? query.substring(1).toLowerCase() : "");

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> t1 = db.collectionGroup("Inventory")
                .whereGreaterThanOrEqualTo("name", lowerCaseQuery)
                .whereLessThanOrEqualTo("name", lowerCaseQuery + "\uf8ff")
                .get();

        com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot> t2 = db.collectionGroup("Inventory")
                .whereGreaterThanOrEqualTo("name", capitalized)
                .whereLessThanOrEqualTo("name", capitalized + "\uf8ff")
                .get();

        com.google.android.gms.tasks.Tasks.whenAllSuccess(t1, t2).addOnSuccessListener(results -> {
            List<String> foundIds = new ArrayList<>();
            for (Object res : results) {
                for (QueryDocumentSnapshot doc : (com.google.firebase.firestore.QuerySnapshot) res) {
                    if (doc.getReference().getParent().getParent() != null) {
                        String id = doc.getReference().getParent().getParent().getId();
                        if (!foundIds.contains(id)) foundIds.add(id);
                    }
                }
            }
            fetchSpecificPharmacies(foundIds, 0, 0);
        });
    }

    private void performPrescriptionSearch(List<String> names, List<String> strengths) {
        db.collection("Pharmacies").get().addOnSuccessListener(phDocs -> {
            List<com.google.android.gms.tasks.Task<Void>> tasks = new ArrayList<>();
            List<PharmacyModel> results = new ArrayList<>();

            for (QueryDocumentSnapshot ph : phDocs) {
                tasks.add(ph.getReference().collection("Inventory").get().continueWith(task -> {
                    if (!task.isSuccessful()) return null;
                    List<DocumentSnapshot> inventory = task.getResult().getDocuments();
                    int count = 0;
                    for (int i = 0; i < names.size(); i++) {
                        String req = names.get(i);
                        for (DocumentSnapshot item : inventory) {
                            String dbName = item.getString("name");
                            if (dbName != null && isMedicineMatch(req, dbName)) {
                                count++;
                                break;
                            }
                        }
                    }
                    if (count > 0) {
                        PharmacyModel m = mapDocToPharmacy(ph);
                        if (m != null) {
                            m.matchedCount = count;
                            m.totalRequested = names.size();
                            synchronized (results) { results.add(m); }
                        }
                    }
                    return null;
                }));
            }
            com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnSuccessListener(v -> {
                Collections.sort(results, (p1, p2) -> {
                    if (p1.matchedCount != p2.matchedCount) return Integer.compare(p2.matchedCount, p1.matchedCount);
                    return Float.compare(p1.distance, p2.distance);
                });
                renderList(results);
            });
        });
    }

    private void fetchSpecificPharmacies(List<String> ids, int matched, int total) {
        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String id : ids) tasks.add(db.collection("Pharmacies").document(id).get());

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks).addOnSuccessListener(docs -> {
            List<PharmacyModel> list = new ArrayList<>();
            for (Object obj : docs) {
                DocumentSnapshot doc = (DocumentSnapshot) obj;
                PharmacyModel m = mapDocToPharmacy(doc);
                if (m != null) {
                    m.matchedCount = matched;
                    m.totalRequested = total;
                    list.add(m);
                }
            }
            Collections.sort(list, (p1, p2) -> Float.compare(p1.distance, p2.distance));
            renderList(list);
        });
    }

    private PharmacyModel mapDocToPharmacy(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        String name = doc.getString("pharmacyName");
        Double lat = doc.getDouble("latitude");
        Double lon = doc.getDouble("longitude");
        if (lat == null || lon == null) return null;

        float dist = 0;
        if (userLocation != null) {
            float[] res = new float[1];
            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), lat, lon, res);
            dist = res[0] / 1000f;
        }

        return new PharmacyModel(name, doc.getString("address"), doc.getString("photoUrl"), dist,
                checkIfOpen((Map<String, Object>) doc.get("workingHours")), lat, lon,
                doc.getString("phone"), doc.getString("description"), doc.getId());
    }

    private boolean isMedicineMatch(String req, String db) {
        String r = req.toLowerCase().replaceAll("[^a-z0-9]", "");
        String d = db.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (r.isEmpty() || d.isEmpty()) return false;
        if (r.contains(d) || d.contains(r)) return true;
        return getLevenshteinDistance(r, d) <= Math.max(1, Math.min(r.length(), d.length()) / 4);
    }

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

    // جلب موقع المستخدم الحالي
    private void getCurrentLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc == null) loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                userLocation = loc;
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    // جلب كافة الصيدليات من Firestore وحساب مسافاتها وترتيبها
    private void loadAllPharmacies() {
        db.collection("Pharmacies")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<PharmacyModel> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString("pharmacyName");
                        String address = doc.getString("address");
                        String photoUrl = doc.getString("photoUrl");
                        String phone = doc.getString("phone");
                        String description = doc.getString("description");
                        Double lat = doc.getDouble("latitude");
                        Double lon = doc.getDouble("longitude");
                        Map<String, Object> workingHours = (Map<String, Object>) doc.get("workingHours");

                        float distance = 0;
                        if (userLocation != null && lat != null && lon != null) {
                            float[] results = new float[1];
                            Location.distanceBetween(userLocation.getLatitude(), userLocation.getLongitude(), lat, lon, results);
                            distance = results[0] / 1000f;
                        }

                        boolean isOpen = checkIfOpen(workingHours);
                        list.add(new PharmacyModel(name, address, photoUrl, distance, isOpen, lat, lon, phone, description, doc.getId()));
                    }

                    // الترتيب حسب المسافة من الأقرب للأبعد
                    Collections.sort(list, (p1, p2) -> Float.compare(p1.distance, p2.distance));

                    renderList(list); // عرض القائمة النهائية
                });
    }

    // دالة عرض الصيدليات في الواجهة
    private void renderList(List<PharmacyModel> list) {
        llList.removeAllViews();
        if (list.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (PharmacyModel pharmacy : list) {
            View itemView = inflater.inflate(R.layout.item_nearby_pharmacy, llList, false);

            TextView tvName = itemView.findViewById(R.id.tv_pharmacy_name);
            TextView tvAddress = itemView.findViewById(R.id.tv_pharmacy_address);
            TextView tvDist = itemView.findViewById(R.id.tv_distance);
            TextView tvStatus = itemView.findViewById(R.id.tv_status);
            ImageView ivPhoto = itemView.findViewById(R.id.iv_pharmacy_img);
            ImageView ivNav = itemView.findViewById(R.id.iv_navigation);

            tvName.setText(pharmacy.name);
            tvAddress.setText(pharmacy.address != null ? pharmacy.address : "Address not available");
            tvDist.setText(String.format(Locale.getDefault(), "%.1f km", pharmacy.distance));

            if (pharmacy.totalRequested > 0) {
                String matchText = "Matched " + pharmacy.matchedCount + "/" + pharmacy.totalRequested;
                tvStatus.setText(matchText);
                if (pharmacy.matchedCount == pharmacy.totalRequested) {
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                    tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
                } else if (pharmacy.matchedCount >= pharmacy.totalRequested / 2.0) {
                    tvStatus.setTextColor(Color.parseColor("#FFC107"));
                    tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFF8E1")));
                } else {
                    tvStatus.setTextColor(Color.parseColor("#F44336"));
                    tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
                }
            } else if (pharmacy.isOpen) {
                tvStatus.setText("Open Now");
                tvStatus.setTextColor(Color.parseColor("#4CAF50"));
                tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E8F5E9")));
            } else {
                tvStatus.setText("Closed");
                tvStatus.setTextColor(Color.parseColor("#F44336"));
                tvStatus.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
            }

            if (pharmacy.photoUrl != null && !pharmacy.photoUrl.isEmpty()) {
                Glide.with(this).load(pharmacy.photoUrl).placeholder(R.drawable.a_pharmacy).into(ivPhoto);
            } else {
                ivPhoto.setImageResource(R.drawable.a_pharmacy);
            }

            // فتح تفاصيل الصيدلية عند الضغط
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(this, PharmacyDetailActivity.class);
                intent.putExtra("PHARMACY_ID", pharmacy.id);
                intent.putExtra("PHARMACY_NAME", pharmacy.name);
                intent.putExtra("PHARMACY_ADDRESS", pharmacy.address);
                intent.putExtra("PHARMACY_PHOTO", pharmacy.photoUrl);
                intent.putExtra("PHARMACY_PHONE", pharmacy.phone);
                intent.putExtra("PHARMACY_DESC", pharmacy.description);
                intent.putExtra("PHARMACY_LAT", pharmacy.latitude);
                intent.putExtra("PHARMACY_LON", pharmacy.longitude);
                intent.putExtra("PHARMACY_DIST", pharmacy.distance);
                intent.putExtra("PHARMACY_OPEN", pharmacy.isOpen);
                startActivity(intent);
            });

            // فتح الملاحة
            ivNav.setOnClickListener(v -> {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + pharmacy.latitude + "," + pharmacy.longitude);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Uri webMapUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + pharmacy.latitude + "," + pharmacy.longitude);
                    startActivity(new Intent(Intent.ACTION_VIEW, webMapUri));
                }
            });

            llList.addView(itemView);
        }
    }

    // دالة التحقق من ساعات العمل
    private boolean checkIfOpen(Map<String, Object> hours) {
        if (hours == null) return false;
        if (Boolean.TRUE.equals(hours.get("open247"))) return true;

        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);
        int currentTimeInMinutes = currentHour * 60 + currentMinute;

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

        String openTime = (String) hours.get(openKey);
        String closeTime = (String) hours.get(closeKey);

        if (openTime == null || closeTime == null || openTime.isEmpty() || closeTime.isEmpty()) return false;

        try {
            int openTotalMinutes = parseTimeToMinutes(openTime);
            int closeTotalMinutes = parseTimeToMinutes(closeTime);
            if (closeTotalMinutes < openTotalMinutes) {
                return currentTimeInMinutes >= openTotalMinutes || currentTimeInMinutes <= closeTotalMinutes;
            } else {
                return currentTimeInMinutes >= openTotalMinutes && currentTimeInMinutes <= closeTotalMinutes;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int parseTimeToMinutes(String time) {
        String[] parts = time.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h * 60 + m;
    }

    // كلاس تمثيل بيانات الصيدلية
    private static class PharmacyModel {
        String id, name, address, photoUrl, phone, description;
        float distance;
        boolean isOpen;
        double latitude, longitude;
        int matchedCount = 0;
        int totalRequested = 0;

        PharmacyModel(String name, String address, String photoUrl, float distance, boolean isOpen, double latitude, double longitude, String phone, String description, String id) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.photoUrl = photoUrl;
            this.distance = distance;
            this.isOpen = isOpen;
            this.latitude = latitude;
            this.longitude = longitude;
            this.phone = phone;
            this.description = description;
        }
    }
}
