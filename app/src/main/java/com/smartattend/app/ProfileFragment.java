package com.smartattend.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    FirebaseAuth auth;
    FirebaseFirestore db;
    SharedPreferences prefs;

    String uid, role = "student", name = "", roll = "", email = "";
    TextView tvAvatar, tvName, tvRole, tvEmail, tvRoll, tvSettingTitle, tvSettingValue;
    View rowRollInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = requireContext().getSharedPreferences("smartattend_prefs", Context.MODE_PRIVATE);
        uid = auth.getCurrentUser().getUid();
        email = auth.getCurrentUser().getEmail() == null ? "" : auth.getCurrentUser().getEmail();

        tvAvatar = view.findViewById(R.id.tvAvatar);
        tvName = view.findViewById(R.id.tvName);
        tvRole = view.findViewById(R.id.tvRole);
        tvEmail = view.findViewById(R.id.tvEmail);
        tvRoll = view.findViewById(R.id.tvRoll);
        tvSettingTitle = view.findViewById(R.id.tvSettingTitle);
        tvSettingValue = view.findViewById(R.id.tvSettingValue);
        rowRollInfo = view.findViewById(R.id.rowRollInfo);

        view.findViewById(R.id.rowEdit).setOnClickListener(v -> editProfile());
        view.findViewById(R.id.rowPassword).setOnClickListener(v -> changePassword());
        view.findViewById(R.id.rowSetting).setOnClickListener(v -> editSetting());
        view.findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());
        view.findViewById(R.id.rowClasses).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ClassesActivity.class)));
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> confirmLogout());

        tvEmail.setText(email);
        updateUi();
        loadProfile();
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void loadProfile() {
        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            name = doc.getString("name") == null ? "" : doc.getString("name");
            roll = doc.getString("rollNo") == null ? "" : doc.getString("rollNo");
            role = doc.getString("role") == null ? "student" : doc.getString("role");
            updateUi();
        });
    }

    private void updateUi() {
        boolean teacher = role.equals("teacher");
        tvAvatar.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        tvName.setText(name.isEmpty() ? "Loading..." : name);
        tvRole.setText(teacher ? "Teacher" : "Student");
        tvRoll.setText(roll.isEmpty() ? "-" : roll);
        rowRollInfo.setVisibility(teacher ? View.GONE : View.VISIBLE);

        tvSettingTitle.setText(teacher ? "Attendance radius" : "Attendance warning limit");
        tvSettingValue.setText(teacher ? prefs.getInt("radius_m", 100) + " m"
                : prefs.getInt("min_percent", 75) + "%");
    }

    private EditText makeInput(String hint, String text, int inputType) {
        EditText et = new EditText(requireContext());
        et.setHint(hint);
        et.setText(text);
        et.setInputType(inputType);
        return et;
    }

    private LinearLayout wrap(View... views) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        for (View v : views) box.addView(v);
        return box;
    }

    // ---------- Edit profile ----------
    private void editProfile() {
        boolean teacher = role.equals("teacher");
        EditText etName = makeInput("Full name", name,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText etRoll = makeInput("Roll number", roll, InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(requireContext())
                .setTitle("Edit profile")
                .setView(teacher ? wrap(etName) : wrap(etName, etRoll))
                .setPositiveButton("Save", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newRoll = etRoll.getText().toString().trim();
                    if (newName.isEmpty() || (!teacher && newRoll.isEmpty())) {
                        toast("Name" + (teacher ? "" : " and roll number") + " cannot be empty");
                        return;
                    }
                    Map<String, Object> update = new HashMap<>();
                    update.put("name", newName);
                    if (!teacher) update.put("rollNo", newRoll);

                    db.collection("users").document(uid).update(update)
                            .addOnSuccessListener(a -> {
                                name = newName;
                                if (!teacher) roll = newRoll;
                                if (isAdded()) updateUi();
                                toast("Profile updated");
                            })
                            .addOnFailureListener(e -> toast("Could not update: " + e.getMessage()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Change password ----------
    private void changePassword() {
        int pw = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText etCurrent = makeInput("Current password", "", pw);
        EditText etNew = makeInput("New password (min 6 characters)", "", pw);

        new AlertDialog.Builder(requireContext())
                .setTitle("Change password")
                .setView(wrap(etCurrent, etNew))
                .setPositiveButton("Update", (d, w) -> {
                    String current = etCurrent.getText().toString();
                    String fresh = etNew.getText().toString();
                    if (current.isEmpty() || fresh.length() < 6) {
                        toast("Enter your current password and a new one (min 6 characters)");
                        return;
                    }
                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null || user.getEmail() == null) return;

                    AuthCredential cred = EmailAuthProvider.getCredential(user.getEmail(), current);
                    user.reauthenticate(cred)
                            .addOnSuccessListener(a -> user.updatePassword(fresh)
                                    .addOnSuccessListener(b -> toast("Password updated"))
                                    .addOnFailureListener(e -> toast("Could not update: " + e.getMessage())))
                            .addOnFailureListener(e -> toast("Current password is wrong"));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Settings (radius for teachers, warning limit for students) ----------
    private void editSetting() {
        boolean teacher = role.equals("teacher");
        String key = teacher ? "radius_m" : "min_percent";
        int current = prefs.getInt(key, teacher ? 100 : 75);
        int min = teacher ? 20 : 50;
        int max = teacher ? 500 : 95;

        EditText et = makeInput(teacher ? "Metres (" + min + " to " + max + ")"
                        : "Percent (" + min + " to " + max + ")",
                String.valueOf(current), InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(requireContext())
                .setTitle(teacher ? "Attendance radius" : "Attendance warning limit")
                .setMessage(teacher
                        ? "Students must be within this distance of you to mark attendance."
                        : "You are warned when your attendance falls below this percentage.")
                .setView(wrap(et))
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        int value = Integer.parseInt(et.getText().toString().trim());
                        if (value < min || value > max) {
                            toast("Enter a value from " + min + " to " + max);
                            return;
                        }
                        prefs.edit().putInt(key, value).apply();
                        updateUi();
                        toast("Saved");
                    } catch (NumberFormatException e) {
                        toast("Enter a number");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- About ----------
    private void showAbout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Presley")
                .setMessage("Version 1.0\n\n"
                        + "Attendance by QR code and classroom location. The QR changes every 30 seconds, "
                        + "so a forwarded screenshot does not work.\n\n"
                        + "Made with Android Studio, Java, Firebase and SQLite as a Mobile Application "
                        + "Development Lab mini project.")
                .setPositiveButton("OK", null)
                .show();
    }

    // ---------- Logout ----------
    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout?")
                .setMessage("You will need to log in again.")
                .setPositiveButton("Logout", (d, w) -> {
                    auth.signOut();
                    Intent i = new Intent(requireActivity(), LoginActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}