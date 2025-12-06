package com.example.android.notepad;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * 笔记便签桌面小部件提供器
 * 支持显示单个笔记的便签小部件
 */
public class NoteWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "NoteWidgetProvider";
    public static final String ACTION_WIDGET_UPDATE = "com.example.android.notepad.ACTION_WIDGET_UPDATE";
    public static final String EXTRA_NOTE_ID = "note_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate 被调用, 小部件数量: " + appWidgetIds.length);

        // 更新所有小部件实例
        for (int appWidgetId : appWidgetIds) {
            Log.d(TAG, "更新小部件: " + appWidgetId);
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_WIDGET_UPDATE.equals(intent.getAction())) {
            // 收到更新请求，更新所有小部件
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, NoteWidgetProvider.class));
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.d(TAG, "updateAppWidget 被调用, ID: " + appWidgetId);

        try {
            // 获取小部件配置中存储的笔记ID
            int noteId = NoteWidgetConfigureActivity.loadNoteIdPref(context, appWidgetId);

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            if (noteId == -1) {
                // 未配置笔记，显示提示
                Log.d(TAG, "小部件未配置，显示提示");
                views.setTextViewText(R.id.widget_title, "点击配置便签");
                views.setTextViewText(R.id.widget_content, "选择要显示的笔记");
                views.setTextViewText(R.id.widget_category, "未配置");
                views.setTextViewText(R.id.widget_date, "");

                // 设置点击打开配置界面
                Intent configIntent = new Intent(context, NoteWidgetConfigureActivity.class);
                configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent configPendingIntent = PendingIntent.getActivity(
                        context, appWidgetId, configIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent);

            } else {
                // 查询笔记数据 - 修复查询方式
                Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, noteId);
                Cursor cursor = null;

                try {
                    // 尝试直接查询，如果失败则尝试不带条件查询
                    cursor = context.getContentResolver().query(
                            NotePad.Notes.CONTENT_URI,
                            new String[] {
                                    NotePad.Notes._ID,
                                    NotePad.Notes.COLUMN_NAME_TITLE,
                                    NotePad.Notes.COLUMN_NAME_NOTE,
                                    NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                                    NotePad.Notes.COLUMN_NAME_CATEGORY
                            },
                            NotePad.Notes._ID + " = ?",
                            new String[] { String.valueOf(noteId) },
                            null
                    );

                    // 如果查询失败，尝试另一种查询方式
                    if (cursor == null || cursor.getCount() == 0) {
                        Log.w(TAG, "直接查询失败，尝试URI查询");
                        if (cursor != null) {
                            cursor.close();
                        }

                        // 使用URI查询
                        cursor = context.getContentResolver().query(
                                noteUri,
                                new String[] {
                                        NotePad.Notes.COLUMN_NAME_TITLE,
                                        NotePad.Notes.COLUMN_NAME_NOTE,
                                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                                        NotePad.Notes.COLUMN_NAME_CATEGORY
                                },
                                null, null, null
                        );
                    }

                    if (cursor != null && cursor.moveToFirst()) {
                        // 获取笔记数据
                        String title = cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_TITLE));
                        String content = cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_NOTE));
                        long modificationDate = cursor.getLong(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE));
                        String category = cursor.getString(cursor.getColumnIndexOrThrow(NotePad.Notes.COLUMN_NAME_CATEGORY));

                        // 格式化内容（限制长度）
                        String displayContent = content;
                        if (displayContent != null && displayContent.length() > 100) {
                            displayContent = displayContent.substring(0, 100) + "...";
                        } else if (displayContent == null) {
                            displayContent = "无内容";
                        }

                        // 处理标题和分类为空的情况
                        if (title == null || title.isEmpty()) {
                            title = "无标题";
                        }
                        if (category == null || category.isEmpty()) {
                            category = "默认分类";
                        }

                        // 格式化日期
                        String formattedDate = formatTimestamp(context, modificationDate);

                        // 设置小部件显示内容
                        views.setTextViewText(R.id.widget_title, title);
                        views.setTextViewText(R.id.widget_content, displayContent);
                        views.setTextViewText(R.id.widget_category, category);
                        views.setTextViewText(R.id.widget_date, formattedDate);

                        Log.d(TAG, "小部件内容更新成功: " + title);

                    } else {
                        // 笔记不存在
                        Log.w(TAG, "笔记不存在或无法访问，ID: " + noteId);
                        views.setTextViewText(R.id.widget_title, "笔记不存在");
                        views.setTextViewText(R.id.widget_content, "该笔记可能已被删除或无法访问");
                        views.setTextViewText(R.id.widget_category, "错误");
                        views.setTextViewText(R.id.widget_date, "");

                        // 设置点击重新配置
                        Intent configIntent = new Intent(context, NoteWidgetConfigureActivity.class);
                        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                        configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        PendingIntent configPendingIntent = PendingIntent.getActivity(
                                context, appWidgetId, configIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "查询笔记数据失败: " + e.getMessage(), e);
                    views.setTextViewText(R.id.widget_title, "数据加载失败");
                    views.setTextViewText(R.id.widget_content, "请重新配置便签");
                    views.setTextViewText(R.id.widget_category, "错误");
                    views.setTextViewText(R.id.widget_date, "");

                    // 设置点击重新配置
                    Intent configIntent = new Intent(context, NoteWidgetConfigureActivity.class);
                    configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                    configIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    PendingIntent configPendingIntent = PendingIntent.getActivity(
                            context, appWidgetId, configIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent);
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                // 设置点击打开笔记编辑界面
                Intent intent = new Intent(context, NoteEditor.class);
                intent.setAction(Intent.ACTION_EDIT);
                intent.setData(ContentUris.withAppendedId(NotePad.Notes.CONTENT_URI, noteId));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context, appWidgetId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
            }

            // 更新小部件
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "小部件更新完成: " + appWidgetId);

        } catch (Exception e) {
            Log.e(TAG, "更新小部件时发生错误: " + e.getMessage(), e);
        }
    }

    private static String formatTimestamp(Context context, long timestamp) {
        if (timestamp == 0) {
            return "未知时间";
        }

        java.util.Date date = new java.util.Date(timestamp);

        try {
            java.text.DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(context);
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(context);

            return dateFormat.format(date) + " " + timeFormat.format(date);
        } catch (Exception e) {
            Log.e(TAG, "格式化日期失败: " + e.getMessage());
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            return sdf.format(date);
        }
    }
}