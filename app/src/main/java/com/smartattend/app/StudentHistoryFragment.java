package com.smartattend.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import java.util.HashMap;

public class StudentHistoryFragment extends Fragment {

    TextView tvOffline, tvOverallPercent, tvOverallDetail, tvEmpty;
    ListView lvSubjects;
    FirebaseFirestore db;
    DBHelper dbHelper;
    String uid;
    int minPercent;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_student_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvOffline = view.findViewById(R.id.tvOffline);
        tvOverallPercent = view.findViewById(R.id.tvOverallPercent);
        tvOverallDetail = view.findViewById(R.id.tvOverallDetail);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        lvSubjects = view.findViewById(R.id.lvSubjects);

        db = FirebaseFirestore.getInstance();
        dbHelper = new DBHelper(requireContext());
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        minPercent = requireContext().getSharedPreferences("smartattend_prefs", Context.MODE_PRIVATE)
                .getInt("min_percent", 75);

        if (isOnline()) loadOnline(); else showOffline();
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @SuppressWarnings("unchecked")
    private void loadOnline() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(u -> {
                    if (!isAdded()) return;
                    List<String> ids = (List<String>) u.get("classIds");
                    if (ids == null || ids.isEmpty()) {
                        tvOffline.setText("");
                        tvEmpty.setText("You have not joined any class yet.\n"
                                + "Go to Profile > My classes and enter your teacher's code.");
                        return;
                    }
                    List<String> use = ids.subList(0, Math.min(ids.size(), 10));
                    db.collection("sessions").whereIn("classId", use).get()
                            .addOnSuccessListener(sessions ->
                                    db.collection("attendance").whereEqualTo("studentId", uid).get()
                                            .addOnSuccessListener(attendance -> buildReport(sessions, attendance))
                                            .addOnFailureListener(e -> showOffline()))
                            .addOnFailureListener(e -> showOffline());
                })
                .addOnFailureListener(e -> showOffline());
    }

    private void buildReport(QuerySnapshot sessions, QuerySnapshot attendance) {
        if (!isAdded()) return;
        Map<String, Integer> total = new LinkedHashMap<>();
        Map<String, Integer> attended = new LinkedHashMap<>();
        Map<String, String> display = new LinkedHashMap<>();
        Map<String, String> classOfSession = new HashMap<>();

        for (DocumentSnapshot s : sessions.getDocuments()) {
            String cid = s.getString("classId");
            if (cid == null) continue;
            classOfSession.put(s.getId(), cid);
            total.merge(cid, 1, Integer::sum);
            String subject = s.getString("subject");
            display.putIfAbsent(cid, subject == null ? cid : subject.trim());
        }
        for (DocumentSnapshot a : attendance.getDocuments()) {
            String cid = classOfSession.get(a.getString("sessionId"));
            if (cid != null) attended.merge(cid, 1, Integer::sum);
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

        dbHelper.saveReport(uid, list);   // keep a copy for offline use
        show(list, false, 0);
    }

    private void showOffline() {
        if (!isAdded()) return;
        List<DBHelper.SubjectStat> list = dbHelper.getReport(uid);
        if (list.isEmpty()) {
            tvEmpty.setText("You are offline and nothing is saved yet.\nOpen this tab once with internet.");
            return;
        }
        show(list, true, dbHelper.getLastUpdated(uid));
    }

    private String advice(int attended, int total, int limit) {
        if (total == 0) return "";
        if (attended * 100 >= limit * total) {
            int canSkip = (attended * 100) / limit - total;
            if (canSkip <= 0) {
                return "Don't skip the next lecture, you would drop below " + limit + "%";
            }
            return "You can skip " + canSkip + (canSkip == 1 ? " lecture" : " lectures")
                    + " and stay at or above " + limit + "%";
        } else {
            int need = (int) Math.ceil((limit * total - 100.0 * attended) / (100 - limit));
            return "Attend the next " + need + (need == 1 ? " lecture" : " lectures")
                    + " in a row to reach " + limit + "%";
        }
    }

    private void show(List<DBHelper.SubjectStat> list, boolean offline, long lastUpdated) {
        if (list.isEmpty()) {
            tvEmpty.setText("No attendance data yet.\nIt appears here after the first lecture.");
            return;
        }
        tvEmpty.setVisibility(View.GONE);

        if (offline) {
            String when = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    .format(new Date(lastUpdated));
            tvOffline.setText("Offline mode - last updated " + when);
        } else {
            tvOffline.setText("Up to date - saved for offline use");
        }

        int allTotal = 0, allAttended = 0;
        for (DBHelper.SubjectStat s : list) {
            allTotal += s.total;
            allAttended += s.attended;
        }
        int overall = allTotal == 0 ? 0 : (allAttended * 100) / allTotal;
        tvOverallPercent.setText(overall + "%");
        tvOverallPercent.setTextColor(Color.parseColor(overall < minPercent ? "#C62828" : "#2E7D32"));
        tvOverallDetail.setText(allAttended + " of " + allTotal + " lectures attended\n"
                + (overall < minPercent ? "Below the " + minPercent + "% limit. Attend more lectures."
                : "You are on track."));

        ArrayAdapter<DBHelper.SubjectStat> adapter = new ArrayAdapter<DBHelper.SubjectStat>(
                requireContext(), R.layout.item_subject, list) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_subject, parent, false);
                }
                DBHelper.SubjectStat s = getItem(position);
                int color = Color.parseColor(s.percent < minPercent ? "#C62828" : "#2E7D32");

                ((TextView) convertView.findViewById(R.id.tvSubject)).setText(s.subject);
                TextView tvPercent = convertView.findViewById(R.id.tvPercent);
                tvPercent.setText(s.percent + "%");
                tvPercent.setTextColor(color);
                ((TextView) convertView.findViewById(R.id.tvStats))
                        .setText(s.attended + " of " + s.total + " lectures");
                ProgressBar pb = convertView.findViewById(R.id.pbPercent);
                pb.setProgress(s.percent);
                pb.setProgressTintList(ColorStateList.valueOf(color));
                TextView tvAdvice = convertView.findViewById(R.id.tvAdvice);
                tvAdvice.setText(advice(s.attended, s.total, minPercent));
                tvAdvice.setTextColor(Color.parseColor(s.percent < minPercent ? "#EF6C00" : "#2E7D32"));
                return convertView;
            }
        };
        lvSubjects.setAdapter(adapter);
    }
}