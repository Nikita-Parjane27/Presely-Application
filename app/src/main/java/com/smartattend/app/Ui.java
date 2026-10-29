package com.smartattend.app;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Ui {
    public static void applyTopInset(Activity a) {
        if (Build.VERSION.SDK_INT < 35 || a.getApplicationInfo().targetSdkVersion < 35) return;
        View content = a.findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(0, top, 0, 0);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }
}