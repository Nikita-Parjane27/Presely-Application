package com.smartattend.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class TeacherHomeFragment extends Fragment {

    Spinner spClass;
    TextView tvNoClass;
    FirebaseFirestore db;
    String uid;
    List<String> classIds = new ArrayList<>();
    List<String> classNames = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_teacher_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        TextView tvHello = view.findViewById(R.id.tvHello);
        TextView tvRadiusInfo = view.findViewById(R.id.tvRadiusInfo);
        EditText etLate = view.findViewById(R.id.etLate);
        spClass = view.findViewById(R.id.spClass);
        tvNoClass = view.findViewById(R.id.tvNoClass);

        int radius = requireContext().getSharedPreferences("smartattend_prefs", Context.MODE_PRIVATE)
                .getInt("radius_m", 100);
        tvRadiusInfo.setText("Students within " + radius + " m of you can mark attendance. "
                + "Change this in Profile.");

        db.collection("users").document(uid).get().addOnSuccessListener(doc -> {
            if (!isAdded()) return;
            String name = doc.getString("name");
            if (name != null && !name.isEmpty()) tvHello.setText("Hello, " + name.split(" ")[0]);
        });

        view.findViewById(R.id.btnManageClasses).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ClassesActivity.class)));

        view.findViewById(R.id.btnStartSession).setOnClickListener(v -> {
            int pos = spClass.getSelectedItemPosition();
            if (classIds.isEmpty() || pos < 0 || pos >= classIds.size()) {
                Toast.makeText(requireContext(), "Create a class first", Toast.LENGTH_SHORT).show();
                return;
            }
            int lateMin = 0;
            String lateText = etLate.getText().toString().trim();
            if (!lateText.isEmpty()) {
                try {
                    lateMin = Integer.parseInt(lateText);
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Late time must be a number",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            if (lateMin < 0 || lateMin > 120) {
                Toast.makeText(requireContext(), "Late time must be from 0 to 120 minutes",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(requireContext(), TeacherSessionActivity.class);
            i.putExtra("subject", classNames.get(pos));
            i.putExtra("classId", classIds.get(pos));
            i.putExtra("lateMin", lateMin);
            startActivity(i);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadClasses();   // refresh after coming back from Manage classes
    }

    private void loadClasses() {
        db.collection("classes").whereEqualTo("teacherId", uid).get()
                .addOnSuccessListener(snap -> {
                    if (!isAdded() || getView() == null) return;
                    classIds.clear();
                    classNames.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        classIds.add(d.getId());
                        classNames.add(d.getString("name") == null ? d.getId() : d.getString("name"));
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_spinner_item, classNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spClass.setAdapter(adapter);

                    boolean none = classIds.isEmpty();
                    spClass.setVisibility(none ? View.GONE : View.VISIBLE);
                    tvNoClass.setVisibility(none ? View.VISIBLE : View.GONE);
                });
    }
}