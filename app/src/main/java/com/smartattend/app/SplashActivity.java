package com.smartattend.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(this::route, 1200);
    }

    private void route() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            open(LoginActivity.class);
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> open("teacher".equals(doc.getString("role"))
                        ? TeacherHomeActivity.class : StudentHomeActivity.class))
                .addOnFailureListener(e -> open(LoginActivity.class));
    }

    private void open(Class<?> target) {
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}