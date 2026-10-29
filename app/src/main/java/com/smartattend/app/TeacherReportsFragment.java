package com.smartattend.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
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
import java.util.HashMap;

public class TeacherReportsFragment extends Fragment {

    static class Item {
        String id, subject, dateText, classId;
        Date date;
        long lateMin;
    }

    TextView tvEmpty;
    ListView lvSessions;
    FirebaseFirestore db;
    List<Item> items = new ArrayList<>();
    String pendingCsv = "";

    ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri == null || !isAdded()) return;
                try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
                    os.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                    toast("Saved");
                } catch (IOException | NullPointerException e) {
                    toast("Could not save: " + e.getMessage());
                }
            });

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_reports, container, false);
    }

    private void showAtRisk(String key, String displayName) {
        List<Item> lectures = new ArrayList<>();
        for (Item it : items) {
            if (key.equals(it.classId)) lectures.add(it);
        }
        if (lectures.isEmpty()) return;
        final int limit = 75;
        final int total = lectures.size();

        db.collection("users").whereArrayContains("classIds", key).get()
                .addOnSuccessListener(students ->
                        db.collection("attendance").get().addOnSuccessListener(att -> {
                            if (!isAdded()) return;
                            Set<String> present = new HashSet<>();
                            for (DocumentSnapshot a : att.getDocuments()) present.add(a.getId());

                            Map<String, Integer> countOf = new HashMap<>();
                            List<DocumentSnapshot> risk = new ArrayList<>();
                            for (DocumentSnapshot s : students.getDocuments()) {
                                int count = 0;
                                for (Item l : lectures) {
                                    if (present.contains(l.id + "_" + s.getId())) count++;
                                }
                                if (count * 100 < limit * total) {
                                    risk.add(s);
                                    countOf.put(s.getId(), count);
                                }
                            }
                            Collections.sort(risk, (a, b) -> countOf.get(a.getId()) - countOf.get(b.getId()));

                            StringBuilder sb = new StringBuilder();
                            for (DocumentSnapshot s : risk) {
                                int c = countOf.get(s.getId());
                                sb.append(safe(s.getString("rollNo"))).append("   ")
                                        .append(safe(s.getString("name"))).append("   -   ")
                                        .append(c).append("/").append(total)
                                        .append(" (").append((c * 100) / total).append("%)\n");
                            }
                            String msg = risk.isEmpty() ? "Everyone is at or above " + limit + "%." : sb.toString();

                            new AlertDialog.Builder(requireContext())
                                    .setTitle(displayName + ": below " + limit + "%")
                                    .setMessage(msg)
                                    .setPositiveButton("OK", null)
                                    .show();
                        }).addOnFailureListener(e -> toast("Error: " + e.getMessage())))
                .addOnFailureListener(e -> toast("Error: " + e.getMessage()));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        tvEmpty = view.findViewById(R.id.tvEmpty);
        lvSessions = view.findViewById(R.id.lvSessions);
        view.findViewById(R.id.btnSubjectReport).setOnClickListener(v -> showSubjectPicker(false));
        view.findViewById(R.id.btnAtRisk).setOnClickListener(v -> showSubjectPicker(true));
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        db.collection("sessions").whereEqualTo("teacherId", uid).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    items.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Item it = new Item();
                        it.id = d.getId();
                        it.subject = d.getString("subject") == null ? "" : d.getString("subject");
                        Timestamp ts = d.getTimestamp("createdAt");
                        it.date = ts != null ? ts.toDate() : new Date();
                        it.dateText = fmt.format(it.date);
                        Long lm = d.getLong("lateAfterMinutes");
                        it.lateMin = lm == null ? 0 : lm;
                        it.classId = d.getString("classId");
                        items.add(it);
                    }
                    Collections.sort(items, (a, b) -> b.date.compareTo(a.date));   // newest first

                    if (items.isEmpty()) {
                        tvEmpty.setText("No sessions yet.\nStart one from the Home tab.");
                        return;
                    }
                    tvEmpty.setVisibility(View.GONE);

                    lvSessions.setAdapter(new ArrayAdapter<Item>(requireContext(),
                            R.layout.item_session, items) {
                        @NonNull
                        @Override
                        public View getView(int position, @Nullable View convertView,
                                            @NonNull ViewGroup parent) {
                            if (convertView == null) {
                                convertView = LayoutInflater.from(getContext())
                                        .inflate(R.layout.item_session, parent, false);
                            }
                            Item it = getItem(position);
                            ((TextView) convertView.findViewById(R.id.tvSubject)).setText(it.subject);
                            ((TextView) convertView.findViewById(R.id.tvDate)).setText(it.dateText);
                            return convertView;
                        }
                    });

                    lvSessions.setOnItemClickListener((parent, v, position, id) -> {
                        Item it = items.get(position);
                        Intent i = new Intent(requireContext(), SessionDetailActivity.class);
                        i.putExtra("sessionId", it.id);
                        i.putExtra("subject", it.subject);
                        i.putExtra("dateText", it.dateText);
                        startActivity(i);
                    });
                })
                .addOnFailureListener(e -> tvEmpty.setText("Error: " + e.getMessage()));
    }

    // ---------- Whole-subject report ----------

    private void showSubjectPicker(boolean atRisk) {
        Map<String, String> classes = new LinkedHashMap<>();
        for (Item it : items) {
            if (it.classId != null) classes.putIfAbsent(it.classId, it.subject);
        }
        if (classes.isEmpty()) {
            toast("No class sessions yet. Create a class and start a session.");
            return;
        }
        List<String> keys = new ArrayList<>(classes.keySet());
        String[] names = classes.values().toArray(new String[0]);

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose class")
                .setItems(names, (dialog, which) -> {
                    if (atRisk) showAtRisk(keys.get(which), names[which]);
                    else exportSubject(keys.get(which), names[which]);
                })
                .show();
    }

    private void exportSubject(String key, String displayName) {
        List<Item> lectures = new ArrayList<>();
        for (Item it : items) {
            if (key.equals(it.classId)) lectures.add(it);
        }
        Collections.sort(lectures, (a, b) -> a.date.compareTo(b.date));   // oldest first

        toast("Preparing report...");

        db.collection("users").whereArrayContains("classIds", key).get()
                .addOnSuccessListener(students ->
                        db.collection("attendance").get()
                                .addOnSuccessListener(att -> {
                                    if (!isAdded()) return;
                                    Map<String, DocumentSnapshot> attOf = new HashMap<>();
                                    for (DocumentSnapshot a : att.getDocuments()) attOf.put(a.getId(), a);

                                    List<DocumentSnapshot> studs = new ArrayList<>(students.getDocuments());
                                    Collections.sort(studs, (a, b) ->
                                            safe(a.getString("rollNo")).compareTo(safe(b.getString("rollNo"))));

                                    pendingCsv = buildSubjectCsv(displayName, lectures, studs, attOf);
                                    createCsv.launch(displayName.replaceAll("[^a-zA-Z0-9]", "_")
                                            + "_subject_report.csv");
                                })
                                .addOnFailureListener(e -> toast("Error: " + e.getMessage())))
                .addOnFailureListener(e -> toast("Error: " + e.getMessage()));
    }

    private String buildSubjectCsv(String subject, List<Item> lectures,
                                   List<DocumentSnapshot> studs, Map<String, DocumentSnapshot> attOf) {
        StringBuilder sb = new StringBuilder();
        sb.append("Subject,").append(csv(subject)).append("\n");
        sb.append("Total lectures,").append(lectures.size()).append("\n");
        sb.append("P = present  L = late (counts as present)  A = absent\n\n");

        sb.append("Roll No,Name");
        for (Item l : lectures) sb.append(",").append(csv(l.dateText));
        sb.append(",Total,Percent\n");

        for (DocumentSnapshot s : studs) {
            sb.append(csv(safe(s.getString("rollNo")))).append(",")
                    .append(csv(safe(s.getString("name"))));
            int count = 0;
            for (Item l : lectures) {
                DocumentSnapshot a = attOf.get(l.id + "_" + s.getId());
                String mark = "A";
                if (a != null) {
                    mark = "P";
                    count++;
                    boolean manual = Boolean.TRUE.equals(a.getBoolean("manual"));
                    Timestamp marked = a.getTimestamp("markedAt");
                    if (!manual && l.lateMin > 0 && marked != null
                            && (marked.toDate().getTime() - l.date.getTime()) > l.lateMin * 60000L) {
                        mark = "L";
                    }
                }
                sb.append(",").append(mark);
            }
            int pct = lectures.isEmpty() ? 0 : (count * 100) / lectures.size();
            sb.append(",").append(count).append("/").append(lectures.size())
                    .append(",").append(pct).append("%\n");
        }
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
}