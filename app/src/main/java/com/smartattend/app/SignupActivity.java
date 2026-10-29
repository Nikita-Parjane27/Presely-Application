package com.smartattend.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import android.view.View;

public class SignupActivity extends AppCompatActivity {

    EditText etName, etEmail, etPassword, etRoll;
    RadioButton rbTeacher;
    FirebaseAuth auth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        Ui.applyTopInset(this);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etRoll = findViewById(R.id.etRoll);
        rbTeacher = findViewById(R.id.rbTeacher);
        View tilRoll = findViewById(R.id.tilRoll);
        ((RadioGroup) findViewById(R.id.rgRole)).setOnCheckedChangeListener((group, checkedId) ->
                tilRoll.setVisibility(checkedId == R.id.rbTeacher ? View.GONE : View.VISIBLE));

        findViewById(R.id.btnSignup).setOnClickListener(v -> signup());
        findViewById(R.id.tvGoLogin).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

    private void signup() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String roll = etRoll.getText().toString().trim();
        String role = rbTeacher.isChecked() ? "teacher" : "student";

        if (name.isEmpty() || email.isEmpty() || pass.length() < 6) {
            Toast.makeText(this, "Fill all fields (password min 6 chars)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (role.equals("student") && roll.isEmpty()) {
            Toast.makeText(this, "Roll number is required for students", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser().getUid();
                    Map<String, Object> user = new HashMap<>();
                    user.put("name", name);
                    user.put("email", email);
                    user.put("role", role);
                    user.put("rollNo", roll);

                    db.collection("users").document(uid).set(user)
                            .addOnSuccessListener(a -> openHome(role))
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Signup failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void openHome(String role) {
        Class<?> target = role.equals("teacher") ? TeacherHomeActivity.class : StudentHomeActivity.class;
        Intent i = new Intent(this, target);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
    }
}