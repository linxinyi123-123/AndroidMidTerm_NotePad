package com.example.android.notepad;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索历史管理类
 * 用于管理搜索历史的增删改查
 */
public class SearchHistoryManager {

    private static final String TAG = "SearchHistoryManager";
    private static final int MAX_HISTORY_ITEMS = 20; // 最大历史记录数量

    private Context mContext;
    private ContentResolver mContentResolver;

    public SearchHistoryManager(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    /**
     * 保存搜索记录
     * @param query 搜索关键词
     * @param resultCount 搜索结果数量
     */
    public void saveSearchQuery(String query, int resultCount) {
        if (TextUtils.isEmpty(query)) {
            return;
        }

        try {
            // 保存搜索记录
            ContentValues values = new ContentValues();
            values.put(NotePad.SearchHistory.COLUMN_NAME_QUERY, query);
            values.put(NotePad.SearchHistory.COLUMN_NAME_RESULT_COUNT, resultCount);
            values.put(NotePad.SearchHistory.COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());

            mContentResolver.insert(NotePad.SearchHistory.CONTENT_URI, values);

            // 清理过期的历史记录（保持最多MAX_HISTORY_ITEMS条）
            cleanupOldHistory();

        } catch (Exception e) {
            Log.e(TAG, "保存搜索历史失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理过期的历史记录
     */
    private void cleanupOldHistory() {
        try {
            // 获取当前所有历史记录，按时间倒序
            Cursor cursor = mContentResolver.query(
                    NotePad.SearchHistory.CONTENT_URI,
                    new String[] { NotePad.SearchHistory._ID },
                    null, null,
                    NotePad.SearchHistory.DEFAULT_SORT_ORDER
            );

            if (cursor != null && cursor.getCount() > MAX_HISTORY_ITEMS) {
                cursor.moveToPosition(MAX_HISTORY_ITEMS);
                List<Integer> idsToDelete = new ArrayList<>();

                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    idsToDelete.add(id);
                }

                // 删除多余的记录
                for (Integer id : idsToDelete) {
                    Uri deleteUri = Uri.withAppendedPath(
                            NotePad.SearchHistory.CONTENT_URI,
                            String.valueOf(id)
                    );
                    mContentResolver.delete(deleteUri, null, null);
                }
            }

            if (cursor != null) {
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "清理搜索历史失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取搜索历史列表
     * @return 搜索历史列表
     */
    public List<SearchHistoryItem> getSearchHistory() {
        List<SearchHistoryItem> historyList = new ArrayList<>();

        try {
            Cursor cursor = mContentResolver.query(
                    NotePad.SearchHistory.CONTENT_URI,
                    new String[] {
                            NotePad.SearchHistory._ID,
                            NotePad.SearchHistory.COLUMN_NAME_QUERY,
                            NotePad.SearchHistory.COLUMN_NAME_TIMESTAMP,
                            NotePad.SearchHistory.COLUMN_NAME_RESULT_COUNT
                    },
                    null, null,
                    NotePad.SearchHistory.DEFAULT_SORT_ORDER
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    SearchHistoryItem item = new SearchHistoryItem();
                    item.id = cursor.getInt(0);
                    item.query = cursor.getString(1);
                    item.timestamp = cursor.getLong(2);
                    item.resultCount = cursor.getInt(3);
                    historyList.add(item);
                }
                cursor.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "获取搜索历史失败: " + e.getMessage(), e);
        }

        return historyList;
    }

    /**
     * 清除所有搜索历史
     */
    public void clearAllHistory() {
        try {
            mContentResolver.delete(
                    NotePad.SearchHistory.CONTENT_URI,
                    null, null
            );
        } catch (Exception e) {
            Log.e(TAG, "清除搜索历史失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除单条搜索历史
     * @param id 历史记录ID
     */
    public void deleteHistoryItem(int id) {
        try {
            Uri deleteUri = Uri.withAppendedPath(
                    NotePad.SearchHistory.CONTENT_URI,
                    String.valueOf(id)
            );
            mContentResolver.delete(deleteUri, null, null);
        } catch (Exception e) {
            Log.e(TAG, "删除搜索历史失败: " + e.getMessage(), e);
        }
    }

    /**
     * 搜索历史数据模型
     */
    public static class SearchHistoryItem {
        public int id;
        public String query;
        public long timestamp;
        public int resultCount;

        @Override
        public String toString() {
            return query + " (" + resultCount + " 结果)";
        }
    }
}