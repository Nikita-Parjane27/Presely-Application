package com.smartattend.app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class StudentHomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_home);
        Ui.applyTopInset(this);

        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.inflateMenu(R.menu.menu_student_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f;
            if (id == R.id.nav_history) f = new StudentHistoryFragment();
            else if (id == R.id.nav_profile) f = new ProfileFragment();
            else f = new StudentScanFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, f).commit();
            return true;
        });
        if (savedInstanceState == null) nav.setSelectedItemId(R.id.nav_scan);
    }
}