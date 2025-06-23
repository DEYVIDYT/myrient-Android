package com.example.myrientandroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class DownloadDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "DownloadDbHelper";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "downloads.db";

    public static final String TABLE_DOWNLOADS = "downloads";
    public static final String COLUMN_ID = "id"; // Primary key, could be the download URL or a generated UUID
    public static final String COLUMN_ORIGINAL_URL = "original_url";
    public static final String COLUMN_FILE_NAME = "file_name";
    public static final String COLUMN_LOCAL_FILE_PATH = "local_file_path";
    public static final String COLUMN_STATUS = "status"; // String representation of DownloadProgressInfo.DownloadStatus
    public static final String COLUMN_PROGRESS = "progress"; // int 0-100
    public static final String COLUMN_BYTES_DOWNLOADED = "bytes_downloaded"; // long
    public static final String COLUMN_TOTAL_BYTES = "total_bytes"; // long
    public static final String COLUMN_DOWNLOAD_SPEED = "download_speed"; // String
    public static final String COLUMN_FAILURE_REASON = "failure_reason"; // String
    public static final String COLUMN_CREATED_AT = "created_at"; // long, timestamp for sorting

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_DOWNLOADS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_ORIGINAL_URL + " TEXT, " +
                    COLUMN_FILE_NAME + " TEXT, " +
                    COLUMN_LOCAL_FILE_PATH + " TEXT, " +
                    COLUMN_STATUS + " TEXT, " +
                    COLUMN_PROGRESS + " INTEGER, " +
                    COLUMN_BYTES_DOWNLOADED + " INTEGER, " +
                    COLUMN_TOTAL_BYTES + " INTEGER, " +
                    COLUMN_DOWNLOAD_SPEED + " TEXT, " +
                    COLUMN_FAILURE_REASON + " TEXT, " +
                    COLUMN_CREATED_AT + " INTEGER" +
                    ");";

    public DownloadDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
        Log.i(TAG, "Database table " + TABLE_DOWNLOADS + " created.");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DOWNLOADS);
        onCreate(db);
        Log.i(TAG, "Database upgraded from version " + oldVersion + " to " + newVersion);
    }

    public boolean addDownload(DownloadProgressInfo item) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, item.getId());
        values.put(COLUMN_ORIGINAL_URL, item.getOriginalUrl());
        values.put(COLUMN_FILE_NAME, item.getFileName());
        values.put(COLUMN_LOCAL_FILE_PATH, item.getLocalFilePath());
        values.put(COLUMN_STATUS, item.getStatus().name());
        values.put(COLUMN_PROGRESS, item.getProgress());
        values.put(COLUMN_BYTES_DOWNLOADED, item.getBytesDownloaded());
        values.put(COLUMN_TOTAL_BYTES, item.getTotalBytes());
        values.put(COLUMN_DOWNLOAD_SPEED, item.getDownloadSpeed());
        values.put(COLUMN_FAILURE_REASON, item.getFailureReason());
        values.put(COLUMN_CREATED_AT, System.currentTimeMillis());

        long result = db.insert(TABLE_DOWNLOADS, null, values);
        // db.close(); // Do not close db here if you expect more operations soon
        return result != -1;
    }

    public DownloadProgressInfo getDownload(String id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_DOWNLOADS, null, COLUMN_ID + "=?",
                new String[]{id}, null, null, null, null);

        DownloadProgressInfo item = null;
        if (cursor != null && cursor.moveToFirst()) {
            item = cursorToDownloadProgressInfo(cursor);
            cursor.close();
        }
        // db.close();
        return item;
    }

    public List<DownloadProgressInfo> getAllDownloadsSortedByDate() {
        List<DownloadProgressInfo> downloadList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        // Sort by COLUMN_CREATED_AT in descending order to get newest first
        Cursor cursor = db.query(TABLE_DOWNLOADS, null, null, null, null, null, COLUMN_CREATED_AT + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                downloadList.add(cursorToDownloadProgressInfo(cursor));
            } while (cursor.moveToNext());
            cursor.close();
        }
        // db.close();
        return downloadList;
    }


    public int updateDownload(DownloadProgressInfo item) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        // ID is the primary key, not updated.
        values.put(COLUMN_ORIGINAL_URL, item.getOriginalUrl());
        values.put(COLUMN_FILE_NAME, item.getFileName());
        values.put(COLUMN_LOCAL_FILE_PATH, item.getLocalFilePath());
        values.put(COLUMN_STATUS, item.getStatus().name());
        values.put(COLUMN_PROGRESS, item.getProgress());
        values.put(COLUMN_BYTES_DOWNLOADED, item.getBytesDownloaded());
        values.put(COLUMN_TOTAL_BYTES, item.getTotalBytes());
        values.put(COLUMN_DOWNLOAD_SPEED, item.getDownloadSpeed());
        values.put(COLUMN_FAILURE_REASON, item.getFailureReason());
        // COLUMN_CREATED_AT is not updated.

        int rowsAffected = db.update(TABLE_DOWNLOADS, values, COLUMN_ID + "=?", new String[]{item.getId()});
        // db.close();
        return rowsAffected;
    }

    // Specific update methods can be more efficient
    public int updateDownloadStatus(String id, DownloadProgressInfo.DownloadStatus status, String failureReason) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status.name());
        if (failureReason != null) {
            values.put(COLUMN_FAILURE_REASON, failureReason);
        }
        int rowsAffected = db.update(TABLE_DOWNLOADS, values, COLUMN_ID + "=?", new String[]{id});
        return rowsAffected;
    }

    public int updateDownloadProgress(String id, long bytesDownloaded, long totalBytes, int progress, String speed) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_BYTES_DOWNLOADED, bytesDownloaded);
        values.put(COLUMN_TOTAL_BYTES, totalBytes);
        values.put(COLUMN_PROGRESS, progress);
        values.put(COLUMN_DOWNLOAD_SPEED, speed);
        values.put(COLUMN_STATUS, DownloadProgressInfo.DownloadStatus.DOWNLOADING.name()); // Assume it's downloading if progress is updated

        int rowsAffected = db.update(TABLE_DOWNLOADS, values, COLUMN_ID + "=?", new String[]{id});
        return rowsAffected;
    }


    public void deleteDownload(String id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_DOWNLOADS, COLUMN_ID + "=?", new String[]{id});
        // db.close();
    }

    private DownloadProgressInfo cursorToDownloadProgressInfo(Cursor cursor) {
        String id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID));
        String originalUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ORIGINAL_URL));
        String fileName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME));

        DownloadProgressInfo item = new DownloadProgressInfo(id, originalUrl, fileName);
        item.setLocalFilePath(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCAL_FILE_PATH)));
        try {
            item.setStatus(DownloadProgressInfo.DownloadStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS))));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid status in DB: " + cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS)));
            item.setStatus(DownloadProgressInfo.DownloadStatus.FAILED); // Default to FAILED if status is unknown
        }
        item.setProgress(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_PROGRESS)));
        item.setBytesDownloaded(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_BYTES_DOWNLOADED)));
        item.setTotalBytes(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TOTAL_BYTES)));
        item.setDownloadSpeed(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DOWNLOAD_SPEED)));
        item.setFailureReason(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FAILURE_REASON)));
        item.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
        return item;
    }
}
