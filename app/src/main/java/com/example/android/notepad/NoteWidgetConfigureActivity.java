package com.example.android.notepad;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 便签小部件配置界面
 * 用于选择要显示在便签中的笔记
 */
public class NoteWidgetConfigureActivity extends Activity {

    private static final String PREFS_NAME = "com.example.android.notepad.NoteWidgetProvider";
    private static final String PREF_PREFIX_KEY = "appwidget_";
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    ListView mNotesListView;

    public NoteWidgetConfigureActivity() {
        super();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 设置结果为取消（如果用户退出）
        setResult(RESULT_CANCELED);

        setContentView(R.layout.widget_configure);

        mNotesListView = findViewById(R.id.notes_list);

        // 查找小部件ID
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // 如果未找到有效的小部件ID，结束
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        // 加载笔记列表
        loadNotesList();

        // 设置列表点击事件
        mNotesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 保存选择的笔记ID
                saveNoteIdPref(NoteWidgetConfigureActivity.this, mAppWidgetId, (int) id);

                // 更新小部件
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(NoteWidgetConfigureActivity.this);
                NoteWidgetProvider.updateAppWidget(NoteWidgetConfigureActivity.this, appWidgetManager, mAppWidgetId);

                // 设置结果为OK
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                setResult(RESULT_OK, resultValue);

                Toast.makeText(NoteWidgetConfigureActivity.this, "便签已创建", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void loadNotesList() {
        // 查询所有笔记
        Cursor cursor = getContentResolver().query(
                NotePad.Notes.CONTENT_URI,
                new String[] {
                        NotePad.Notes._ID,
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                },
                null, null, NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 创建适配器
        String[] from = new String[] {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };

        int[] to = new int[] {
                android.R.id.text1,
                android.R.id.text2
        };

        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_2,
                cursor,
                from,
                to,
                0
        );

        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (columnIndex == 2) { // 修改日期列
                    TextView textView = (TextView) view;
                    long timestamp = cursor.getLong(columnIndex);
                    String formattedTime = formatTimestamp(timestamp);
                    textView.setText(formattedTime);
                    return true;
                }
                return false;
            }
        });

        mNotesListView.setAdapter(adapter);
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "未知时间";
        }

        java.util.Date date = new java.util.Date(timestamp);
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(this);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);

        return dateFormat.format(date) + " " + timeFormat.format(date);
    }

    // 保存笔记ID到SharedPreferences
    static void saveNoteIdPref(Context context, int appWidgetId, int noteId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putInt(PREF_PREFIX_KEY + appWidgetId, noteId);
        prefs.apply();
    }

    // 从SharedPreferences读取笔记ID
    static int loadNoteIdPref(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(PREF_PREFIX_KEY + appWidgetId, -1);
    }

    // 删除笔记ID配置
    static void deleteNoteIdPref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId);
        prefs.apply();
    }
}