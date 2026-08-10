package com.app.aqrab;

import android.annotation.SuppressLint; 
import android.app.Activity; 
import android.content.Context;
import android.content.Intent; 
import android.os.Bundle; 
import android.text.SpannableString; 
import android.text.TextUtils; 
import android.text.method.HideReturnsTransformationMethod; 
import android.text.method.PasswordTransformationMethod; 
import android.text.style.UnderlineSpan; 
import android.view.View; 
import android.widget.Button; 
import android.widget.CheckBox;
import android.widget.EditText; 
import android.widget.ImageButton;
import android.widget.ImageView; 
import android.widget.TextView; 
import android.widget.Toast; 
import androidx.activity.result.ActivityResultLauncher; 
import androidx.activity.result.contract.ActivityResultContracts; 
import androidx.appcompat.app.AppCompatActivity; 

import com.google.android.gms.auth.api.signin.GoogleSignIn; 
import com.google.android.gms.auth.api.signin.GoogleSignInAccount; 
import com.google.android.gms.auth.api.signin.GoogleSignInClient; 
import com.google.android.gms.auth.api.signin.GoogleSignInOptions; 
import com.google.android.gms.common.api.ApiException; 
import com.google.android.gms.tasks.Task; 
import com.google.firebase.auth.AuthCredential; 
import com.google.firebase.auth.FirebaseAuth; 
import com.google.firebase.auth.FirebaseUser; 
import com.google.firebase.auth.GoogleAuthProvider; 
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    // كائنات Firebase للمصادقة وقاعدة البيانات
    private FirebaseAuth mAuth; 
    private FirebaseFirestore db;
    // عميل تسجيل جوجل
    private GoogleSignInClient mGoogleSignInClient; 
    // مشغل استلام نتيجة اختيار حساب جوجل
    private ActivityResultLauncher<Intent> googleSignUpLauncher; 

    @Override
    protected void attachBaseContext(Context newBase) {
        // تطبيق اللغة المختارة
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // تعيين واجهة إنشاء حساب
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance(); 
        db = FirebaseFirestore.getInstance();

        // زر العودة للشاشة السابقة
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // إعدادات التسجيل السريع عبر جوجل
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) 
                .requestEmail() 
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso); 

        // معالجة نتيجة تسجيل جوجل
        googleSignUpLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) { 
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken()); 
                            }
                        } catch (ApiException e) {
                            Toast.makeText(SignUpActivity.this, "Google connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // تفعيل زر التسجيل عبر جوجل
        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) View googleSignUpButton = findViewById(R.id.btn_google);
        if (googleSignUpButton != null) {
            googleSignUpButton.setOnClickListener(v -> signUpWithGoogle());
        }

        // ربط حقول الإدخال (الاسم، الإيميل، الهاتف، كلمة السر)
        EditText etFullName = findViewById(R.id.et_full_name);
        EditText etEmail = findViewById(R.id.et_email);
        EditText etPhone = findViewById(R.id.et_phone);
        EditText etPassword = findViewById(R.id.et_password);
        CheckBox cbTerms = findViewById(R.id.checkbox_terms);
        ImageView ivShowPassword = findViewById(R.id.iv_show_password);
        // تفعيل إمكانية إظهار كلمة السر
        setupPasswordVisibilityToggle(etPassword, ivShowPassword); 

        EditText etConfirmPassword = findViewById(R.id.et_confirm_password);
        ImageView ivShowConfirmPassword = findViewById(R.id.iv_show_confirm_password);
        // تفعيل إمكانية إظهار تأكيد كلمة السر
        setupPasswordVisibilityToggle(etConfirmPassword, ivShowConfirmPassword); 

        // رابط للذهاب لصفحة تسجيل الدخول إذا كان لديك حساب بالفعل
        TextView tvGoToLogin = findViewById(R.id.tv_go_to_login);
        if (tvGoToLogin != null) {
            SpannableString content = new SpannableString(tvGoToLogin.getText().toString());
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
            tvGoToLogin.setText(content);
            tvGoToLogin.setOnClickListener(v -> {
                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class); 
                intent.putExtra("user_role", getIntent().getStringExtra("user_role")); 
                startActivity(intent);
            });
        }

        // معالجة الضغط على زر "Create Account"
        Button btnCreateAccount = findViewById(R.id.btn_create_account);
        btnCreateAccount.setOnClickListener(v -> {
            String role = getIntent().getStringExtra("user_role"); // الدور المختار (مريض / صيدلية)
            String name = etFullName.getText().toString().trim(); 
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            // التحقق من صحة المدخلات
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(SignUpActivity.this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Toast.makeText(this, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show();
                return;
            }

            if (cbTerms != null && !cbTerms.isChecked()) {
                Toast.makeText(this, R.string.agree_terms, Toast.LENGTH_SHORT).show();
                return;
            }

            btnCreateAccount.setEnabled(false);
            btnCreateAccount.setText(R.string.creating_account);

            // إنشاء الحساب في Firebase Auth
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                // حفظ بيانات المستخدم الإضافية في Firestore
                                saveUserToFirestore(user.getUid(), name, email, phone, role);
                            }
                        } else {
                            btnCreateAccount.setEnabled(true);
                            btnCreateAccount.setText("Create account");
                            String error = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                            Toast.makeText(SignUpActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    // دالة حفظ بيانات المستخدم في Firestore وحفظ نوع الدور محلياً
    private void saveUserToFirestore(String uid, String name, String email, String phone, String role) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", name);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("role", role);
        userData.put("createdAt", System.currentTimeMillis());

        if ("Pharmacy".equals(role)) {
            userData.put("ownerId", uid);
            userData.put("pharmacyName", name); 
            // حفظ الدور والاسم محلياً
            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                    .putString("user_role", "Pharmacy")
                    .putString("user_name", name)
                    .apply();
            db.collection("Pharmacies").document(uid).set(userData)
                    .addOnSuccessListener(aVoid -> navigateToNext(role, name))
                    .addOnFailureListener(e -> handleError(e.getMessage()));
        } else {
            // حفظ الدور والاسم محلياً
            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                    .putString("user_role", "Patient")
                    .putString("user_name", name)
                    .apply();
            db.collection("Users").document(uid).set(userData)
                    .addOnSuccessListener(aVoid -> navigateToNext(role, name))
                    .addOnFailureListener(e -> handleError(e.getMessage()));
        }
    }

    // دالة التوجيه للشاشة التالية بناءً على الدور
    private void navigateToNext(String role, String name) {
        Intent intent;
        if ("Pharmacy".equals(role)) {
            intent = new Intent(SignUpActivity.this, PharmacyActivity.class);
            intent.putExtra("PHARMACY_NAME", name);
        } else {
            intent = new Intent(SignUpActivity.this, PatientActivity.class);
            intent.putExtra("is_new_user", true);
            intent.putExtra("user_name", name);
        }
        startActivity(intent);
        finish();
    }

    // معالجة أخطاء الحفظ
    private void handleError(String message) {
        findViewById(R.id.btn_create_account).setEnabled(true);
        ((Button)findViewById(R.id.btn_create_account)).setText(R.string.create_account);
        Toast.makeText(this, "Error: " + message, Toast.LENGTH_LONG).show();
    }

    // فتح واجهة اختيار حساب جوجل
    private void signUpWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignUpLauncher.launch(signInIntent);
    }

    // ربط حساب جوجل بـ Firebase وحفظ الدور المختار
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) { 
                        FirebaseUser user = mAuth.getCurrentUser();
                        String displayName = (user != null && user.getDisplayName() != null) ? user.getDisplayName() : getString(R.string.role_patient);
                        String role = getIntent().getStringExtra("user_role");
                        
                        if ("Pharmacy".equals(role)) {
                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().putString("user_role", "Pharmacy").apply();
                            Intent intent = new Intent(SignUpActivity.this, PharmacyActivity.class);
                            intent.putExtra("PHARMACY_NAME", displayName);
                            startActivity(intent);
                        } else {
                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().putString("user_role", "Patient").apply();
                            Intent intent = new Intent(SignUpActivity.this, PatientActivity.class);
                            intent.putExtra("is_new_user", true);
                            intent.putExtra("user_name", displayName);
                            startActivity(intent);
                        }
                        finish();
                    } else {
                        Toast.makeText(SignUpActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // دالة مساعدة لتبديل رؤية كلمة السر (إظهار/إخفاء)
    private void setupPasswordVisibilityToggle(EditText editText, ImageView imageView) {
        imageView.setOnClickListener(new View.OnClickListener() {
            private boolean isVisible = false; 

            @Override
            public void onClick(View v) {
                if (isVisible) {
                    editText.setTransformationMethod(PasswordTransformationMethod.getInstance()); 
                } else {
                    editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance()); 
                }
                isVisible = !isVisible; 
                editText.setSelection(editText.getText().length());
            }
        });
    }
}
