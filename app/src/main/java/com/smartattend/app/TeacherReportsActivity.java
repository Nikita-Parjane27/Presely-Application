package com.smartattend.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TeacherReportsActivity extends AppCompatActivity {

    static class Item {
        String id, subject, dateText;
        Date date;
        @Override public String toString() { return subject + "\n" + dateText; }
    }

    TextView tvEmpty;
    ListView lvSessions;
    FirebaseFirestore db;
    List<Item> items = new ArrayList<>();
    String pendingCsv = "";

    // Lets the teacher choose where to save the CSV (no storage permission needed)
    ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri == null) return;
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                } catch (IOException | NullPointerException e) {
                    Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_reports);

        db = FirebaseFirestore.getInstance();
        tvEmpty = findViewById(R.id.tvEmpty);
        lvSessions = findViewById(R.id.lvSessions);

        findViewById(R.id.btnSubjectReport).setOnClickListener(v -> showSubjectPicker());

        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        db.collection("sessions")
                .whereEqualTo("teacherId", uid)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Item it = new Item();
                        it.id = d.getId();
                        it.subject = d.getString("subject") == null ? "" : d.getString("subject");
                        Timestamp ts = d.getTimestamp("createdAt");
                        it.date = ts != null ? ts.toDate() : new Date();
                        it.dateText = fmt.format(it.date);
                        items.add(it);
                    }
                    // newest first
                    Collections.sort(items, (a, b) -> b.date.compareTo(a.date));

                    if (items.isEmpty()) {
                        tvEmpty.setText("No sessions yet. Start one from Teacher Home.");
                        return;
                    }
                    tvEmpty.setVisibility(View.GONE);

                    ArrayAdapter<Item> adapter = new ArrayAdapter<Item>(this,
                            android.R.layout.simple_list_item_1, items) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            TextView tv = (TextView) super.getView(position, convertView, parent);
                            tv.setPadding(16, 24, 16, 24);
                            return tv;
                        }
                    };
                    lvSessions.setAdapter(adapter);

                    lvSessions.setOnItemClickListener((parent, view, position, id) -> {
                        Item it = items.get(position);
                        Intent i = new Intent(this, SessionDetailActivity.class);
                        i.putExtra("sessionId", it.id);
                        i.putExtra("subject", it.subject);
                        i.putExtra("dateText", it.dateText);
                        startActivity(i);
                    });
                })
                .addOnFailureListener(e -> tvEmpty.setText("Error: " + e.getMessage()));
    }

    // ---------- Whole-subject report ----------

    private void showSubjectPicker() {
        if (items.isEmpty()) {
            Toast.makeText(this, "No sessions to report yet", Toast.LENGTH_SHORT).show();
            return;
        }
        // Distinct subjects (same name in any letter case counts as one)
        Map<String, String> subjects = new LinkedHashMap<>();
        for (Item it : items) {
            subjects.putIfAbsent(it.subject.trim().toLowerCase(), it.subject.trim());
        }
        List<String> keys = new ArrayList<>(subjects.keySet());
        String[] names = subjects.values().toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Choose subject")
                .setItems(names, (dialog, which) -> exportSubject(keys.get(which), names[which]))
                .show();
    }

    private void exportSubject(String key, String displayName) {
        // Lectures of this subject, oldest first (they become the columns)
        List<Item> lectures = new ArrayList<>();
        for (Item it : items) {
            if (it.subject.trim().toLowerCase().equals(key)) lectures.add(it);
        }
        Collections.sort(lectures, (a, b) -> a.date.compareTo(b.date));

        Toast.makeText(this, "Preparing report...", Toast.LENGTH_SHORT).show();

        db.collection("users").whereEqualTo("role", "student").get()
                .addOnSuccessListener(students ->
                        db.collection("attendance").get()
                                .addOnSuccessListener(att -> {
                                    // Attendance document ids look like sessionId_studentUid
                                    Set<String> present = new HashSet<>();
                                    for (DocumentSnapshot a : att.getDocuments()) present.add(a.getId());

                                    List<DocumentSnapshot> studs = new ArrayList<>(students.getDocuments());
                                    Collections.sort(studs, (a, b) ->
                                            safe(a.getString("rollNo")).compareTo(safe(b.getString("rollNo"))));

                                    pendingCsv = buildSubjectCsv(displayName, lectures, studs, present);
                                    String fileName = displayName.replaceAll("[^a-zA-Z0-9]", "_")
                                            + "_subject_report.csv";
                                    createCsv.launch(fileName);
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private String buildSubjectCsv(String subject, List<Item> lectures,
                                   List<DocumentSnapshot> studs, Set<String> present) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject,").append(csv(subject)).append("\n");
        sb.append("Total lectures,").append(lectures.size()).append("\n\n");

        // Header row
        sb.append("Roll No,Name");
        for (Item l : lectures) sb.append(",").append(csv(l.dateText));
        sb.append(",Total,Percent\n");

        // One row per student
        for (DocumentSnapshot s : studs) {
            sb.append(csv(safe(s.getString("rollNo")))).append(",")
                    .append(csv(safe(s.getString("name"))));
            int count = 0;
            for (Item l : lectures) {
                boolean p = present.contains(l.id + "_" + s.getId());
                if (p) count++;
                sb.append(",").append(p ? "P" : "A");
            }
            int pct = lectures.isEmpty() ? 0 : (count * 100) / lectures.size();
            sb.append(",").append(count).append("/").append(lectures.size())
                    .append(",").append(pct).append("%\n");
        }
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}