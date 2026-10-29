package com.smartattend.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    EditText etEmail, etPassword;
    Button btnLogin;
    ProgressBar progress;
    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        Ui.applyTopInset(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progress = findViewById(R.id.progress);

        btnLogin.setOnClickListener(v -> login());
        findViewById(R.id.tvGoSignup).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));
        findViewById(R.id.tvForgot).setOnClickListener(v -> forgotPassword());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Already logged in? Skip the login screen.
        if (auth.getCurrentUser() != null) {
            setLoading(true);
            routeByRole(auth.getCurrentUser().getUid());
        }
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String friendly(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Wrong email or password";
        }
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection";
        }
        return e.getMessage();
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }
        setLoading(true);
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(r -> routeByRole(r.getUser().getUid()))
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, friendly(e), Toast.LENGTH_LONG).show();
                });
    }

    private void forgotPassword() {
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            Toast.makeText(this, "Type your email above first", Toast.LENGTH_SHORT).show();
            return;
        }
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(a ->
                        Toast.makeText(this, "Reset link sent. Check your email (and spam)",
                                Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        Toast.makeText(this, friendly(e), Toast.LENGTH_LONG).show());
    }

    private void routeByRole(String uid) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    Class<?> target = "teacher".equals(role)
                            ? TeacherHomeActivity.class : StudentHomeActivity.class;
                    Intent i = new Intent(this, target);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Could not load profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }
}