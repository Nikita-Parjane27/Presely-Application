package com.smartattend.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClassesActivity extends AppCompatActivity {

    static class Item {
        String id, name;
    }

    TextView tvSub, tvEmpty;
    ListView lv;
    Button btnAdd;
    FirebaseFirestore db;
    String uid;
    boolean teacher = false;
    List<Item> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classes);
        Ui.applyTopInset(this);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        tvSub = findViewById(R.id.tvSub);
        tvEmpty = findViewById(R.id.tvEmpty);
        lv = findViewById(R.id.lvClasses);
        btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setEnabled(false);

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    teacher = "teacher".equals(doc.getString("role"));
                    btnAdd.setText(teacher ? "Create new class" : "Join a class");
                    tvSub.setText(teacher ? "Tap a class to share its code" : "Tap a class to leave it");
                    btnAdd.setEnabled(true);
                    btnAdd.setOnClickListener(v -> {
                        if (teacher) createClassDialog(); else joinClassDialog();
                    });
                    load();
                })
                .addOnFailureListener(e -> tvEmpty.setText("Error: " + e.getMessage()));

        lv.setOnItemClickListener((p, v, pos, id) -> {
            Item it = items.get(pos);
            if (teacher) shareCode(it); else leaveDialog(it);
        });
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private EditText input(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        return et;
    }

    private LinearLayout wrap(View v) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    // ---------- Load and show ----------

    @SuppressWarnings("unchecked")
    private void load() {
        if (teacher) {
            db.collection("classes").whereEqualTo("teacherId", uid).get()
                    .addOnSuccessListener(snap -> {
                        items.clear();
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Item it = new Item();
                            it.id = d.getId();
                            it.name = d.getString("name") == null ? d.getId() : d.getString("name");
                            items.add(it);
                        }
                        show();
                    })
                    .addOnFailureListener(e -> tvEmpty.setText("Error: " + e.getMessage()));
        } else {
            db.collection("users").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        items.clear();
                        List<String> ids = (List<String>) doc.get("classIds");
                        Map<String, Object> names = (Map<String, Object>) doc.get("classNames");
                        if (ids != null) {
                            for (String id : ids) {
                                Item it = new Item();
                                it.id = id;
                                Object n = names == null ? null : names.get(id);
                                it.name = n == null ? id : n.toString();
                                items.add(it);
                            }
                        }
                        show();
                    })
                    .addOnFailureListener(e -> tvEmpty.setText("Error: " + e.getMessage()));
        }
    }

    private void show() {
        if (items.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(teacher
                    ? "No classes yet.\nTap Create new class."
                    : "You have not joined any class yet.\nTap Join a class and enter the code.");
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
        lv.setAdapter(new ArrayAdapter<Item>(this, R.layout.item_session, items) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_session, parent, false);
                }
                Item it = getItem(position);
                ((TextView) convertView.findViewById(R.id.tvSubject)).setText(it.name);
                ((TextView) convertView.findViewById(R.id.tvDate)).setText("Code: " + it.id);
                return convertView;
            }
        });
    }

    // ---------- Teacher: create and share ----------

    private String makeCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private void createClassDialog() {
        EditText et = input("Class name (e.g. Final Year C3 - MAD Lab)");
        new AlertDialog.Builder(this)
                .setTitle("New class")
                .setView(wrap(et))
                .setPositiveButton("Create", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        toast("Enter a class name");
                        return;
                    }
                    String code = makeCode();
                    Map<String, Object> data = new HashMap<>();
                    data.put("name", name);
                    data.put("teacherId", uid);
                    data.put("code", code);
                    data.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("classes").document(code).set(data)
                            .addOnSuccessListener(a -> {
                                toast("Class created. Code: " + code);
                                load();
                            })
                            .addOnFailureListener(e -> toast("Could not create, try again: " + e.getMessage()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareCode(Item it) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, "Join my class \"" + it.name + "\" on Presley.\n"
                + "Open Presley > Profile > My classes > Join a class, and enter the code: " + it.id);
        startActivity(Intent.createChooser(send, "Share class code"));
    }

    // ---------- Student: join and leave ----------

    private void joinClassDialog() {
        EditText et = input("Class code (6 letters or digits)");
        new AlertDialog.Builder(this)
                .setTitle("Join a class")
                .setView(wrap(et))
                .setPositiveButton("Join", (d, w) -> {
                    String code = et.getText().toString().trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
                    if (code.isEmpty()) {
                        toast("Enter the class code");
                        return;
                    }
                    db.collection("classes").document(code).get()
                            .addOnSuccessListener(doc -> {
                                if (!doc.exists()) {
                                    toast("No class found with that code");
                                    return;
                                }
                                String name = doc.getString("name") == null ? code : doc.getString("name");
                                db.collection("users").document(uid).update(
                                                "classIds", FieldValue.arrayUnion(code),
                                                "classNames." + code, name)
                                        .addOnSuccessListener(a -> {
                                            toast("Joined " + name);
                                            load();
                                        })
                                        .addOnFailureListener(e -> toast("Could not join: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> toast("Error: " + e.getMessage()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void leaveDialog(Item it) {
        new AlertDialog.Builder(this)
                .setTitle("Leave " + it.name + "?")
                .setMessage("It will disappear from your History. You can join again with the code.")
                .setPositiveButton("Leave", (d, w) ->
                        db.collection("users").document(uid).update(
                                        "classIds", FieldValue.arrayRemove(it.id),
                                        "classNames." + it.id, FieldValue.delete())
                                .addOnSuccessListener(a -> load())
                                .addOnFailureListener(e -> toast("Could not leave: " + e.getMessage())))
                .setNegativeButton("Cancel", null)
                .show();
    }
}