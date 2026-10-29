package com.smartattend.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.firebase.firestore.Query;

public class SessionDetailActivity extends AppCompatActivity {

    static class Row {
        String uid, roll, name, device;
        boolean present, flagged, late, manual;
    }

    TextView tvTitle, tvInfo, tvWarning;
    ListView lvStudents;
    FirebaseFirestore db;

    String sessionId, subject, dateText;
    long createdMs = 0, lateMin = 0;
    List<Row> rows = new ArrayList<>();

    ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri == null) return;
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    os.write(buildCsv().getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                } catch (IOException | NullPointerException e) {
                    Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_detail);
        Ui.applyTopInset(this);

        db = FirebaseFirestore.getInstance();
        tvTitle = findViewById(R.id.tvTitle);
        tvInfo = findViewById(R.id.tvInfo);
        tvWarning = findViewById(R.id.tvWarning);
        lvStudents = findViewById(R.id.lvStudents);

        sessionId = getIntent().getStringExtra("sessionId");
        subject = getIntent().getStringExtra("subject");
        dateText = getIntent().getStringExtra("dateText");
        tvTitle.setText(subject + "\n" + dateText);

        findViewById(R.id.btnExport).setOnClickListener(v -> {
            if (rows.isEmpty()) {
                Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show();
                return;
            }
            createCsv.launch(subject.replaceAll("[^a-zA-Z0-9]", "_") + "_attendance.csv");
        });

        lvStudents.setOnItemClickListener((p, v, position, id) -> onRowClick(rows.get(position)));

        loadData();
    }

    // ---------- Loading ----------

    private void loadData() {
        db.collection("sessions").document(sessionId).get()
                .addOnSuccessListener(sess -> {
                    Timestamp created = sess.getTimestamp("createdAt");
                    createdMs = created == null ? 0 : created.toDate().getTime();
                    Long lm = sess.getLong("lateAfterMinutes");
                    lateMin = lm == null ? 0 : lm;

                    String classId = sess.getString("classId");
                    Query studentsQuery = classId == null
                            ? db.collection("users").whereEqualTo("role", "student")
                            : db.collection("users").whereArrayContains("classIds", classId);

                    studentsQuery.get()
                            .addOnSuccessListener(students ->
                                    db.collection("attendance").whereEqualTo("sessionId", sessionId).get()
                                            .addOnSuccessListener(att -> build(students, att))
                                            .addOnFailureListener(e -> tvInfo.setText("Error: " + e.getMessage())))
                            .addOnFailureListener(e -> tvInfo.setText("Error: " + e.getMessage()));
                })
                .addOnFailureListener(e -> tvInfo.setText("Error: " + e.getMessage()));
    }

    private void build(QuerySnapshot students, QuerySnapshot att) {
        rows.clear();
        tvWarning.setVisibility(View.GONE);

        Map<String, DocumentSnapshot> attOf = new HashMap<>();
        Map<String, Integer> deviceCount = new HashMap<>();
        for (DocumentSnapshot a : att.getDocuments()) {
            String sid = a.getString("studentId");
            if (sid == null) continue;
            attOf.put(sid, a);
            if (!Boolean.TRUE.equals(a.getBoolean("manual"))) {
                String dev = a.getString("deviceId");
                if (dev != null && !dev.equals("unknown")) deviceCount.merge(dev, 1, Integer::sum);
            }
        }

        int presentCount = 0, lateCount = 0;
        for (DocumentSnapshot s : students.getDocuments()) {
            Row r = new Row();
            r.uid = s.getId();
            r.roll = s.getString("rollNo") == null ? "" : s.getString("rollNo");
            r.name = s.getString("name") == null ? "" : s.getString("name");

            DocumentSnapshot a = attOf.get(s.getId());
            r.present = a != null;
            if (r.present) {
                r.manual = Boolean.TRUE.equals(a.getBoolean("manual"));
                if (!r.manual) {
                    r.device = a.getString("deviceId");
                    Timestamp marked = a.getTimestamp("markedAt");
                    r.late = lateMin > 0 && createdMs > 0 && marked != null
                            && (marked.toDate().getTime() - createdMs) > lateMin * 60000L;
                    r.flagged = r.device != null && deviceCount.containsKey(r.device)
                            && deviceCount.get(r.device) > 1;
                }
                presentCount++;
                if (r.late) lateCount++;
            }
            rows.add(r);
        }
        Collections.sort(rows, (x, y) -> x.roll.compareTo(y.roll));

        tvInfo.setText("Present: " + presentCount + " of " + rows.size()
                + (lateCount > 0 ? " (" + lateCount + " late)" : "")
                + "\nTap a student to change attendance");

        // Proxy warning: group flagged students by device
        Map<String, List<String>> groups = new HashMap<>();
        for (Row r : rows) {
            if (r.flagged) groups.computeIfAbsent(r.device, k -> new ArrayList<>()).add(r.name);
        }
        if (!groups.isEmpty()) {
            StringBuilder sb = new StringBuilder("Possible proxy attendance:");
            for (List<String> names : groups.values()) {
                sb.append("\n").append(TextUtils.join(", ", names)).append(" used the same phone");
            }
            tvWarning.setText(sb.toString());
            tvWarning.setVisibility(View.VISIBLE);
        }

        List<String> display = new ArrayList<>();
        for (Row r : rows) {
            display.add(r.roll + "   " + r.name + "   -   " + status(r));
        }
        lvStudents.setAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, display) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(colorOf(rows.get(position)));
                tv.setPadding(16, 20, 16, 20);
                return tv;
            }
        });
    }

    private String status(Row r) {
        if (!r.present) return "Absent";
        String s = r.late ? "Late" : (r.manual ? "Present (manual)" : "Present");
        if (r.flagged) s += " - same phone";
        return s;
    }

    private int colorOf(Row r) {
        if (!r.present) return Color.parseColor("#C62828");
        if (r.flagged) return Color.parseColor("#6A1B9A");
        if (r.late) return Color.parseColor("#B26A00");
        return Color.parseColor("#2E7D32");
    }

    // ---------- Manual override ----------

    private void onRowClick(Row r) {
        if (r.present) {
            new AlertDialog.Builder(this)
                    .setTitle("Remove attendance?")
                    .setMessage("Mark " + r.name + " as absent for this lecture?")
                    .setPositiveButton("Mark absent", (d, w) -> removeAttendance(r))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            EditText etReason = new EditText(this);
            etReason.setHint("Reason (e.g. phone battery dead)");
            LinearLayout box = new LinearLayout(this);
            int pad = (int) (24 * getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad / 3, pad, 0);
            box.addView(etReason, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            new AlertDialog.Builder(this)
                    .setTitle("Mark " + r.name + " present?")
                    .setView(box)
                    .setPositiveButton("Mark present",
                            (d, w) -> addManual(r, etReason.getText().toString().trim()))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void addManual(Row r, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("studentId", r.uid);
        data.put("name", r.name);
        data.put("rollNo", r.roll);
        data.put("subject", subject);
        data.put("manual", true);
        data.put("reason", reason);
        data.put("markedBy", FirebaseAuth.getInstance().getCurrentUser().getUid());
        data.put("markedAt", FieldValue.serverTimestamp());

        db.collection("attendance").document(sessionId + "_" + r.uid).set(data)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, r.name + " marked present", Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void removeAttendance(Row r) {
        db.collection("attendance").document(sessionId + "_" + r.uid).delete()
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, r.name + " marked absent", Toast.LENGTH_SHORT).show();
                    loadData();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Could not remove: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // ---------- CSV ----------

    private String csv(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String buildCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject,").append(csv(subject)).append("\n");
        sb.append("Date,").append(csv(dateText)).append("\n\n");
        sb.append("Roll No,Name,Status\n");
        for (Row r : rows) {
            sb.append(csv(r.roll)).append(",")
                    .append(csv(r.name)).append(",")
                    .append(status(r)).append("\n");
        }
        return sb.toString();
    }
}