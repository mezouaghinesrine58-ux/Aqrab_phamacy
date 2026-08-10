package com.app.aqrab;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

public class PatientActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private ImageView ivHome, ivMail, ivHistory, ivSettings;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        // تطبيق اللغة المختارة عند بدء النشاط لضمان استمرار اللغة بعد الانتقال
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient);

        viewPager = findViewById(R.id.view_pager);
        ivHome = findViewById(R.id.iv_home);
        ivMail = findViewById(R.id.iv_mail);
        ivHistory = findViewById(R.id.iv_history_bottom);
        ivSettings = findViewById(R.id.iv_settings_bottom);

        // إعداد الـ Adapter للـ ViewPager2
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // تعطيل اللمس للتنقل لضمان أن التنقل يتم بالسحب أو الضغط (اختياري)
        // viewPager.setUserInputEnabled(true); 

        // مستمع لضغطات الأيقونات في الشريط السفلي
        ivHome.setOnClickListener(v -> viewPager.setCurrentItem(0));
        ivMail.setOnClickListener(v -> viewPager.setCurrentItem(1));
        ivHistory.setOnClickListener(v -> viewPager.setCurrentItem(2));
        ivSettings.setOnClickListener(v -> viewPager.setCurrentItem(3));

        // ربط تغيير الصفحات بتحديث ألوان الأيقونات
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateBottomNavIcons(position);
            }
        });
    }

    private void updateBottomNavIcons(int position) {
        // إعادة تعيين الألوان للوضع الافتراضي (رمادي)
        int defaultColor = android.graphics.Color.parseColor("#9E9E9E");
        int activeColor = android.graphics.Color.parseColor("#2E5A44");

        ivHome.setColorFilter(defaultColor);
        ivMail.setColorFilter(defaultColor);
        ivHistory.setColorFilter(defaultColor);
        ivSettings.setColorFilter(defaultColor);

        // تلوين الأيقونة النشطة
        switch (position) {
            case 0: ivHome.setColorFilter(activeColor); break;
            case 1: ivMail.setColorFilter(activeColor); break;
            case 2: ivHistory.setColorFilter(activeColor); break;
            case 3: ivSettings.setColorFilter(activeColor); break;
        }
    }
}