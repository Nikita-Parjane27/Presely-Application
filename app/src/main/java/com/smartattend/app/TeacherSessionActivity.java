package com.smartattend.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import android.view.WindowManager;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;

public class TeacherSessionActivity extends AppCompatActivity {

    private static final int REFRESH_SECONDS = 30;
    private double radiusMeters = 100.0;
    ImageView ivQr;
    TextView tvSubject, tvStatus, tvTimer, tvCount;
    FirebaseFirestore db;
    FusedLocationProviderClient fusedClient;
    ListenerRegistration countListener;

    String subject;
    String sessionId = null;
    String currentToken = null;
    int secondsLeft = REFRESH_SECONDS;
    boolean ended = false;

    Handler handler = new Handler(Looper.getMainLooper());

    Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (ended || sessionId == null) return;
            secondsLeft--;
            if (secondsLeft <= 0) {
                refreshToken();
                secondsLeft = REFRESH_SECONDS;
            }
            tvTimer.setText("New code in " + secondsLeft + " s");
            handler.postDelayed(this, 1000);
        }
    };

    ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    fetchLocationAndStart();
                } else {
                    Toast.makeText(this, "Location permission is required to start a session",
                            Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_session);
        Ui.applyTopInset(this);

        db = FirebaseFirestore.getInstance();
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        ivQr = findViewById(R.id.ivQr);
        tvSubject = findViewById(R.id.tvSubject);
        tvStatus = findViewById(R.id.tvStatus);
        tvTimer = findViewById(R.id.tvTimer);
        tvCount = findViewById(R.id.tvCount);

        subject = getIntent().getStringExtra("subject");
        tvSubject.setText(subject);
        tvStatus.setText("Getting your location...");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        radiusMeters = getSharedPreferences("smartattend_prefs", MODE_PRIVATE).getInt("radius_m", 100);

        findViewById(R.id.btnEnd).setOnClickListener(v -> confirmEnd());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { confirmEnd(); }
        });
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndStart();
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocationAndStart() {
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        Toast.makeText(this, "Could not get location. Turn on GPS and try again",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    createSession(loc);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private String newToken() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void createSession(Location loc) {
        currentToken = newToken();
        Map<String, Object> data = new HashMap<>();
        data.put("teacherId", FirebaseAuth.getInstance().getCurrentUser().getUid());
        data.put("subject", subject);
        data.put("classId", getIntent().getStringExtra("classId"));
        data.put("active", true);
        data.put("currentToken", currentToken);
        data.put("lat", loc.getLatitude());
        data.put("lng", loc.getLongitude());
        data.put("radius", radiusMeters);
        data.put("lateAfterMinutes", getIntent().getIntExtra("lateMin", 0));
        data.put("tokenUpdatedAt", FieldValue.serverTimestamp());
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection("sessions").add(data)
                .addOnSuccessListener(ref -> {
                    sessionId = ref.getId();
                    tvStatus.setText("Session is LIVE. Students within "
                            + (int) radiusMeters + " m can scan.");
                    showQr();
                    listenCount();
                    handler.postDelayed(ticker, 1000);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void refreshToken() {
        currentToken = newToken();
        Map<String, Object> update = new HashMap<>();
        update.put("currentToken", currentToken);
        update.put("tokenUpdatedAt", FieldValue.serverTimestamp());
        db.collection("sessions").document(sessionId).update(update);
        showQr();
    }

    private void showQr() {
        try {
            String content = sessionId + ":" + currentToken;
            Bitmap bmp = new BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600);
            ivQr.setImageBitmap(bmp);
        } catch (Exception e) {
            Toast.makeText(this, "QR error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void listenCount() {
        countListener = db.collection("attendance")
                .whereEqualTo("sessionId", sessionId)
                .addSnapshotListener((snap, e) -> {
                    if (snap != null) tvCount.setText("Present: " + snap.size());
                });
    }

    private void endSession() {
        if (ended) return;
        ended = true;
        handler.removeCallbacks(ticker);
        if (sessionId != null) {
            db.collection("sessions").document(sessionId).update("active", false);
        }
        Toast.makeText(this, "Session ended", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!ended && sessionId != null) {
            db.collection("sessions").document(sessionId).update("active", false);
        }
        handler.removeCallbacks(ticker);
        if (countListener != null) countListener.remove();
    }
    private void confirmEnd() {
        new AlertDialog.Builder(this)
                .setTitle("End session?")
                .setMessage("Students will no longer be able to mark attendance.")
                .setPositiveButton("End session", (d, w) -> endSession())
                .setNegativeButton("Keep running", null)
                .show();
    }
}