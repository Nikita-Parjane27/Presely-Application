package com.smartattend.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "smartattend.db";
    private static final int DB_VERSION = 1;

    // One subject row of the report
    public static class SubjectStat {
        public String subject;
        public int attended, total, percent;
    }

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE report ("
                + "uid TEXT, subject TEXT, attended INTEGER, total INTEGER, "
                + "percent INTEGER, updated INTEGER, "
                + "PRIMARY KEY (uid, subject))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS report");
        onCreate(db);
    }

    // Replace this student's saved report with the fresh one
    public void saveReport(String uid, List<SubjectStat> list) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("report", "uid = ?", new String[]{uid});
            long now = System.currentTimeMillis();
            for (SubjectStat s : list) {
                ContentValues cv = new ContentValues();
                cv.put("uid", uid);
                cv.put("subject", s.subject);
                cv.put("attended", s.attended);
                cv.put("total", s.total);
                cv.put("percent", s.percent);
                cv.put("updated", now);
                db.insert("report", null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<SubjectStat> getReport(String uid) {
        List<SubjectStat> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query("report",
                new String[]{"subject", "attended", "total", "percent"},
                "uid = ?", new String[]{uid}, null, null, "subject ASC");
        while (c.moveToNext()) {
            SubjectStat s = new SubjectStat();
            s.subject = c.getString(0);
            s.attended = c.getInt(1);
            s.total = c.getInt(2);
            s.percent = c.getInt(3);
            list.add(s);
        }
        c.close();
        return list;
    }

    public long getLastUpdated(String uid) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT MAX(updated) FROM report WHERE uid = ?", new String[]{uid});
        long t = 0;
        if (c.moveToFirst()) t = c.getLong(0);
        c.close();
        return t;
    }
}