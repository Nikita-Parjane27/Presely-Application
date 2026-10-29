package com.smartattend.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.HashMap;
import java.util.Map;
import android.provider.Settings;
import java.util.List;

public class StudentScanFragment extends Fragment {

    // Set to false ONLY to test on one device. Keep true for the final demo.
    private static final boolean CHECK_LOCATION = true;
    private static final int INFO = 0, SUCCESS = 1, ERROR = 2;

    TextView tvHello, tvResultTitle, tvResultMsg;
    MaterialCardView cardResult;
    FirebaseFirestore db;
    FusedLocationProviderClient fusedClient;
    String uid;

    ActivityResultLauncher<ScanOptions> scanLauncher = registerForActivityResult(
            new ScanContract(), result -> {
                if (result.getContents() == null) {
                    showResult(INFO, "Scan cancelled", "Tap Scan QR to try again.");
                } else {
                    markAttendance(result.getContents());
                }
            });

    ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) launchScanner();
                else showResult(ERROR, "Location needed",
                        "Allow the location permission to mark attendance.");
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_scan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        fusedClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        tvHello = view.findViewById(R.id.tvHello);
        tvResultTitle = view.findViewById(R.id.tvResultTitle);
        tvResultMsg = view.findViewById(R.id.tvResultMsg);
        cardResult = view.findViewById(R.id.cardResult);

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            String name = doc.getString("name");
            if (name != null && !name.isEmpty()) tvHello.setText("Hi, " + name.split(" ")[0]);
        });

        view.findViewById(R.id.btnScan).setOnClickListener(v -> {
            if (CHECK_LOCATION && ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            } else {
                launchScanner();
            }
        });
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan the QR shown by your teacher");
        options.setBeepEnabled(false);
        options.setOrientationLocked(false);
        scanLauncher.launch(options);
    }

    private void showResult(int type, String title, String msg) {
        if (!isAdded() || getView() == null) return;
        int bg, fg;
        if (type == SUCCESS) { bg = R.color.success_bg; fg = R.color.success; }
        else if (type == ERROR) { bg = R.color.danger_bg; fg = R.color.danger; }
        else { bg = R.color.brand_container; fg = R.color.brand_dark; }

        cardResult.setVisibility(View.VISIBLE);
        cardResult.setCardBackgroundColor(ContextCompat.getColor(requireContext(), bg));
        tvResultTitle.setTextColor(ContextCompat.getColor(requireContext(), fg));
        tvResultMsg.setTextColor(ContextCompat.getColor(requireContext(), fg));
        tvResultTitle.setText(title);
        tvResultMsg.setText(msg);
        if (type == SUCCESS) cardResult.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    @SuppressWarnings("unchecked")
    private void markAttendance(String content) {
        String[] parts = content.split(":");
        if (parts.length != 2) {
            showResult(ERROR, "Invalid QR code", "Scan the QR shown on your teacher's screen.");
            return;
        }
        String sessionId = parts[0];
        String token = parts[1];

        showResult(INFO, "Checking...", "Please wait a moment.");

        db.collection("sessions").document(sessionId).get()
                .addOnSuccessListener(session -> {
                    if (!session.exists()) {
                        showResult(ERROR, "Session not found", "This QR is not from Presley.");
                        return;
                    }
                    Boolean active = session.getBoolean("active");
                    if (active == null || !active) {
                        showResult(ERROR, "Session ended",
                                "Ask your teacher to start a new session.");
                        return;
                    }
                    if (!token.equals(session.getString("currentToken"))) {
                        showResult(ERROR, "QR expired",
                                "The code changes every 30 seconds. Scan the latest one.");
                        return;
                    }
                    String subject = session.getString("subject");
                    String sessionClass = session.getString("classId");

                    if (sessionClass == null) {
                        afterChecks(session, sessionId, subject);
                        return;
                    }
                    db.collection("users").document(uid).get()
                            .addOnSuccessListener(u -> {
                                List<String> ids = (List<String>) u.get("classIds");
                                if (ids == null || !ids.contains(sessionClass)) {
                                    showResult(ERROR, "Not in this class",
                                            "Join the class first: Profile > My classes > Join a class.");
                                    return;
                                }
                                afterChecks(session, sessionId, subject);
                            })
                            .addOnFailureListener(e -> showResult(ERROR, "Something went wrong", e.getMessage()));
                })
                .addOnFailureListener(e -> showResult(ERROR, "Something went wrong", e.getMessage()));
    }

    private void afterChecks(DocumentSnapshot session, String sessionId, String subject) {
        DocumentReference ref = db.collection("attendance").document(sessionId + "_" + uid);
        ref.get().addOnSuccessListener(existing -> {
            if (existing.exists()) {
                showResult(INFO, "Already marked",
                        "Your attendance for " + subject + " is already recorded.");
                return;
            }
            if (CHECK_LOCATION) {
                checkLocationThenSave(ref, session, sessionId, subject);
            } else {
                saveRecord(ref, sessionId, subject, -1);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void checkLocationThenSave(DocumentReference ref, DocumentSnapshot session,
                                       String sessionId, String subject) {
        Double classLat = session.getDouble("lat");
        Double classLng = session.getDouble("lng");
        Double radius = session.getDouble("radius");
        if (classLat == null || classLng == null || radius == null) {
            showResult(ERROR, "No location set", "This session has no classroom location.");
            return;
        }

        showResult(INFO, "Checking your location...", "Please wait a moment.");
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        showResult(ERROR, "Location unavailable",
                                "Turn on GPS and try again.");
                        return;
                    }
                    float[] result = new float[1];
                    Location.distanceBetween(loc.getLatitude(), loc.getLongitude(),
                            classLat, classLng, result);
                    float distance = result[0];

                    if (distance > radius) {
                        showResult(ERROR, "Too far from class",
                                "You are " + Math.round(distance) + " m away. Move inside the classroom (within "
                                        + radius.intValue() + " m).");
                    } else {
                        saveRecord(ref, sessionId, subject, distance);
                    }
                })
                .addOnFailureListener(e -> showResult(ERROR, "Location error", e.getMessage()));
    }

    private void saveRecord(DocumentReference ref, String sessionId, String subject, float distance) {
        db.collection("users").document(uid).get().addOnSuccessListener(user -> {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", sessionId);
            data.put("studentId", uid);
            data.put("name", user.getString("name"));
            data.put("rollNo", user.getString("rollNo"));
            data.put("subject", subject);
            data.put("distanceMeters", Math.round(distance));
            data.put("deviceId", deviceCode());
            data.put("markedAt", FieldValue.serverTimestamp());

            ref.set(data)
                    .addOnSuccessListener(a -> showResult(SUCCESS, "Attendance marked",
                            subject + " - recorded successfully."))
                    .addOnFailureListener(e -> showResult(ERROR, "Could not save", e.getMessage()));
        });
    }
    private String deviceCode() {
        try {
            String id = Settings.Secure.getString(requireContext().getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((id == null ? "" : id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}