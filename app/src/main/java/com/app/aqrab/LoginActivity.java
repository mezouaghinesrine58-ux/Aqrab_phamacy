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
import android.widget.EditText; 
import android.widget.ImageButton;
import android.widget.ImageView; 
import android.widget.TextView; 
import android.widget.Toast; 
import androidx.activity.result.ActivityResultLauncher; 
import androidx.activity.result.contract.ActivityResultContracts; 
import androidx.annotation.NonNull; 
import androidx.appcompat.app.AppCompatActivity; 

import com.google.android.gms.auth.api.signin.GoogleSignIn; 
import com.google.android.gms.auth.api.signin.GoogleSignInAccount; 
import com.google.android.gms.auth.api.signin.GoogleSignInClient; 
import com.google.android.gms.auth.api.signin.GoogleSignInOptions; 
import com.google.android.gms.common.api.ApiException; 
import com.google.android.gms.tasks.OnCompleteListener; 
import com.google.android.gms.tasks.Task; 
import com.google.firebase.auth.AuthCredential; 
import com.google.firebase.auth.AuthResult; 
import com.google.firebase.auth.FirebaseAuth; 
import com.google.firebase.auth.FirebaseUser; 
import com.google.firebase.auth.GoogleAuthProvider; 
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
public class LoginActivity extends AppCompatActivity {

    // كائن المصادقة من Firebase
    private FirebaseAuth mAuth; 
    // عميل تسجيل الدخول عبر جوجل
    private GoogleSignInClient mGoogleSignInClient; 
    // مشغل استلام نتيجة اختيار حساب جوجل
    private ActivityResultLauncher<Intent> googleSignInLauncher; 

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_in);

        // تهيئة Firebase Auth
        mAuth = FirebaseAuth.getInstance(); 

        // زر العودة للشاشة السابقة
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // إعدادات تسجيل الدخول بجوجل
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) 
                .requestEmail() 
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso); 

        // تعريف معالج نتيجة تسجيل جوجل
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) { 
                        Intent data = result.getData(); 
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data); 
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class); 
                            if (account != null) {
                                firebaseAuthWithGoogle(account.getIdToken()); // ربط توكن جوجل بـ Firebase
                            }
                        } catch (ApiException e) { 
                            Toast.makeText(LoginActivity.this, "Google login failed " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // تفعيل زر جوجل
        View googleButton = findViewById(R.id.google_button_id); 
        if (googleButton != null) {
            googleButton.setOnClickListener(v -> signInWithGoogle()); 
        }

        // ربط عناصر الواجهة (إيميل، كلمة مرور، نسيان كلمة السر)
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password); 
        EditText etEmail = findViewById(R.id.et_email); 
        EditText etPassword = findViewById(R.id.et_password); 
        ImageView ivShowPassword = findViewById(R.id.iv_show_password); 

        // تبديل رؤية كلمة المرور عند ضغط أيقونة العين
        ivShowPassword.setOnClickListener(new View.OnClickListener() {
            private boolean isPasswordVisible = false; 

            @Override
            public void onClick(View v) {
                if (isPasswordVisible) {
                    etPassword.setTransformationMethod(PasswordTransformationMethod.getInstance()); 
                } else {
                    etPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance()); 
                }
                isPasswordVisible = !isPasswordVisible; 
                etPassword.setSelection(etPassword.getText().length()); 
            }
        });

        // وظيفة استعادة كلمة المرور عبر البريد
        tvForgotPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim(); 
            if (TextUtils.isEmpty(email)) { 
                Toast.makeText(LoginActivity.this, R.string.enter_email_reset, Toast.LENGTH_SHORT).show();
                return;
            }
            mAuth.sendPasswordResetEmail(email) 
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this, R.string.reset_email_sent, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(LoginActivity.this, R.string.failed_reset_email, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // تفعيل رابط "Sign up" للذهاب لصفحة التسجيل
        TextView tvSignUpLink = findViewById(R.id.tv_sign_up_link);
        if (tvSignUpLink != null) {
            SpannableString content = new SpannableString(tvSignUpLink.getText().toString()); 
            content.setSpan(new UnderlineSpan(), 0, content.length(), 0); 
            tvSignUpLink.setText(content); 
            tvSignUpLink.setOnClickListener(v -> {
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class); 
                intent.putExtra("user_role", getIntent().getStringExtra("user_role")); 
                startActivity(intent);
            });
        }

        // وظيفة زر تسجيل الدخول الأساسي
        Button btnLogin = findViewById(R.id.btn_login_submit);
        btnLogin.setOnClickListener(v -> {
            String role = getIntent().getStringExtra("user_role"); // الدور المختار (مريض أو صيدلية)
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            // التحقق من الحقول
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(LoginActivity.this, R.string.fill_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText(R.string.logging_in);

            // محاولة تسجيل الدخول بـ Firebase
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Login");

                        if (task.isSuccessful()) {
                            FirebaseUser user = mAuth.getCurrentUser();
                            String userId = user.getUid();
                            
                            // توجيه المستخدم بناءً على الدور المختار وحفظه محلياً
                            if ("Pharmacy".equals(role)) {
                                FirebaseFirestore.getInstance().collection("Pharmacies")
                                        .document(userId).get().addOnSuccessListener(documentSnapshot -> {
                                            String name = documentSnapshot.getString("pharmacyName");
                                            if (name == null) name = email.split("@")[0];
                                            
                                            // حفظ الدور والاسم محلياً
                                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                                                    .putString("user_role", "Pharmacy")
                                                    .putString("user_name", name)
                                                    .apply();
                                            
                                            Intent intent = new Intent(LoginActivity.this, PharmacyActivity.class);
                                            intent.putExtra("PHARMACY_NAME", name);
                                            startActivity(intent);
                                            finish();
                                        }).addOnFailureListener(e -> {
                                            String name = email.split("@")[0];
                                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                                                    .putString("user_role", "Pharmacy")
                                                    .putString("user_name", name)
                                                    .apply();
                                            Intent intent = new Intent(LoginActivity.this, PharmacyActivity.class);
                                            intent.putExtra("PHARMACY_NAME", name);
                                            startActivity(intent);
                                            finish();
                                        });
                            } else {
                                // حفظ الدور كمريض
                                FirebaseFirestore.getInstance().collection("Users")
                                        .document(userId).get().addOnSuccessListener(doc -> {
                                            String name = doc.getString("fullName");
                                            if (name == null) name = email.split("@")[0];
                                            
                                            // حفظ الدور والاسم محلياً
                                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                                                    .putString("user_role", "Patient")
                                                    .putString("user_name", name)
                                                    .apply();
                                            
                                            Intent intent = new Intent(LoginActivity.this, PatientActivity.class);
                                            intent.putExtra("user_name", name);
                                            startActivity(intent);
                                            finish();
                                        }).addOnFailureListener(e -> {
                                            String name = email.split("@")[0];
                                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit()
                                                    .putString("user_role", "Patient")
                                                    .putString("user_name", name)
                                                    .apply();
                                            Intent intent = new Intent(LoginActivity.this, PatientActivity.class);
                                            intent.putExtra("user_name", name);
                                            startActivity(intent);
                                            finish();
                                        });
                            }
                        } else {
                            String error = task.getException() != null ? task.getException().getMessage() : "Auth Failed";
                            Toast.makeText(LoginActivity.this, "Login Error: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    } 

    // دالة فتح واجهة اختيار حساب جوجل
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent(); 
        googleSignInLauncher.launch(signInIntent); 
    }

    // دالة ربط بيانات جوجل بـ Firebase Auth والتوجيه التلقائي
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null); 
        mAuth.signInWithCredential(credential) 
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) { 
                        FirebaseUser user = mAuth.getCurrentUser(); 
                        String displayName = (user != null && user.getDisplayName() != null) ? user.getDisplayName() : "User";
                        String role = getIntent().getStringExtra("user_role"); 
                        
                        if ("Pharmacy".equals(role)) {
                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().putString("user_role", "Pharmacy").apply();
                            Intent intent = new Intent(LoginActivity.this, PharmacyActivity.class);
                            intent.putExtra("PHARMACY_NAME", displayName);
                            startActivity(intent);
                        } else {
                            getSharedPreferences("AqrabPrefs", MODE_PRIVATE).edit().putString("user_role", "Patient").apply();
                            Intent intent = new Intent(LoginActivity.this, PatientActivity.class);
                            intent.putExtra("user_name", displayName);
                            startActivity(intent);
                        }
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
