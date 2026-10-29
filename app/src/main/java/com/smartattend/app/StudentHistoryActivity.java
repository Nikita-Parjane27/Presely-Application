package com.smartattend.app;

import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StudentHistoryActivity extends AppCompatActivity {

    private static final int MIN_PERCENT = 75;   // shortage limit

    TextView tvSummary, tvEmpty, tvOffline;
    ListView lvSubjects;
    FirebaseFirestore db;
    DBHelper dbHelper;
    String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_history);

        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvOffline = findViewById(R.id.tvOffline);
        lvSubjects = findViewById(R.id.lvSubjects);
        db = FirebaseFirestore.getInstance();
        dbHelper = new DBHelper(this);
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (isOnline()) {
            loadOnline();
        } else {
            showOffline();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void loadOnline() {
        db.collection("sessions").get().addOnSuccessListener(sessions ->
                db.collection("attendance").whereEqualTo("studentId", uid).get()
                        .addOnSuccessListener(attendance -> buildReport(sessions, attendance))
                        .addOnFailureListener(e -> showOffline())
        ).addOnFailureListener(e -> showOffline());
    }

    private void buildReport(QuerySnapshot sessions, QuerySnapshot attendance) {
        Map<String, Integer> total = new LinkedHashMap<>();
        Map<String, Integer> attended = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();

        for (DocumentSnapshot s : sessions.getDocuments()) {
            String subject = s.getString("subject");
            if (subject == null) continue;
            String key = subject.trim().toLowerCase();
            total.merge(key, 1, Integer::sum);
            display.putIfAbsent(key, subject.trim());
        }
        for (DocumentSnapshot a : attendance.getDocuments()) {
            String subject = a.getString("subject");
            if (subject == null) continue;
            attended.merge(subject.trim().toLowerCase(), 1, Integer::sum);
        }

        List<DBHelper.SubjectStat> list = new ArrayList<>();
        for (String key : total.keySet()) {
            DBHelper.SubjectStat st = new DBHelper.SubjectStat();
            st.subject = display.get(key);
            st.total = total.get(key);
            st.attended = attended.getOrDefault(key, 0);
            st.percent = (st.attended * 100) / st.total;
            list.add(st);
        }

        // Save a fresh copy in SQLite for offline use
        dbHelper.saveReport(uid, list);
        show(list, false, 0);
    }

    private void showOffline() {
        List<DBHelper.SubjectStat> list = dbHelper.getReport(uid);
        if (list.isEmpty()) {
            tvEmpty.setText("You are offline and nothing is saved yet.\n"
                    + "Open this screen once with internet.");
            return;
        }
        show(list, true, dbHelper.getLastUpdated(uid));
    }

    private void show(List<DBHelper.SubjectStat> list, boolean offline, long lastUpdated) {
        if (list.isEmpty()) {
            tvEmpty.setText("No attendance data yet.");
            tvSummary.setText("");
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        if (offline) {
            String when = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    .format(new Date(lastUpdated));
            tvOffline.setText("Offline mode - last updated " + when);
            tvOffline.setTextColor(Color.parseColor("#EF6C00"));
        } else {
            tvOffline.setText("Up to date (saved for offline use)");
            tvOffline.setTextColor(Color.parseColor("#2E7D32"));
        }

        int allTotal = 0, allAttended = 0;
        for (DBHelper.SubjectStat s : list) {
            allTotal += s.total;
            allAttended += s.attended;
        }
        int overall = allTotal == 0 ? 0 : (allAttended * 100) / allTotal;
        tvSummary.setText("Overall: " + allAttended + " of " + allTotal + " lectures (" + overall + "%)");

        ArrayAdapter<DBHelper.SubjectStat> adapter = new ArrayAdapter<DBHelper.SubjectStat>(
                this, android.R.layout.simple_list_item_1, list) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                DBHelper.SubjectStat s = getItem(position);
                tv.setText(s.subject + "\n" + s.attended + " of " + s.total + " lectures  |  "
                        + s.percent + "%"
                        + (s.percent < MIN_PERCENT ? "  (below " + MIN_PERCENT + "%)" : ""));
                tv.setTextColor(s.percent < MIN_PERCENT ? Color.parseColor("#C62828")
                        : Color.parseColor("#2E7D32"));
                tv.setPadding(16, 24, 16, 24);
                return tv;
            }
        };
        lvSubjects.setAdapter(adapter);
    }
}