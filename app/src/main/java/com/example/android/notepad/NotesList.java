/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      `http://www.apache.org/licenses/LICENSE-2.0`
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import androidx.appcompat.widget.SearchView;
import android.view.KeyEvent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesList extends ListActivity {

    private static final String TAG = "NotesList";

    // 搜索相关变量
    private String mCurrentSearchQuery = "";
    private boolean mIsSearchMode = false;
    private TextView mSearchStatusView;

    private SearchHistoryManager mSearchHistoryManager;

    // 实时搜索防抖变量
    private Handler mSearchHandler = new Handler();
    private static final int SEARCH_DELAY_MS = 300; // 300毫秒防抖延迟
    private Runnable mSearchRunnable;

    // 高级搜索选项
    private boolean mSearchInTitle = true;
    private boolean mSearchInContent = true;
    private boolean mCaseSensitive = false;
    private boolean mWholeWord = false;
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
            NotePad.Notes.COLUMN_NAME_CATEGORY, // 3
            NotePad.Notes.COLUMN_NAME_NOTE, // 4 - 新增，用于搜索和显示
    };

    private static final int COLUMN_INDEX_TITLE = 1;
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 2;
    private static final int COLUMN_INDEX_CATEGORY = 3;
    private static final int COLUMN_INDEX_NOTE = 4;

    // 分类颜色映射
    private static final Map<String, Integer> CATEGORY_COLORS = new HashMap<String, Integer>();
    static {
        CATEGORY_COLORS.put("默认分类", 0xFF2196F3);
        CATEGORY_COLORS.put("工作", 0xFF4CAF50);
        CATEGORY_COLORS.put("学习", 0xFFFF9800);
        CATEGORY_COLORS.put("生活", 0xFF9C27B0);
        CATEGORY_COLORS.put("想法", 0xFF607D8B);
        CATEGORY_COLORS.put("购物清单", 0xFFFF5722);
    }

    private static final int[] COLOR_OPTIONS = {
            0xFFF44336, 0xFFE91E63, 0xFF9C27B0, 0xFF673AB7, 0xFF3F51B5,
            0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688, 0xFF4CAF50,
            0xFF8BC34A, 0xFFCDDC39, 0xFFFFEB3B, 0xFFFFC107, 0xFFFF9800,
            0xFFFF5722, 0xFF795548, 0xFF9E9E9E, 0xFF607D8B
    };

    // 分类筛选状态
    private String mCurrentFilterCategory = null;
    private NotesAdapter mAdapter;
    private int selectedColor = 0xFF2196F3;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化Handler
        mSearchHandler = new Handler();

        // 设置布局
        setContentView(R.layout.notes_list);

        // 初始化搜索状态视图
        mSearchStatusView = findViewById(R.id.search_status_text);

        // 初始化搜索历史管理器
        mSearchHistoryManager = new SearchHistoryManager(this);

        // 处理搜索意图
        handleSearchIntent(getIntent());

        // 设置列表样式
        getListView().setBackgroundColor(getResources().getColor(R.color.background_light));
        getListView().setDivider(null);
        getListView().setDividerHeight(0);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        getListView().setOnCreateContextMenuListener(this);

        // 初始化EditText搜索功能
        initEditTextSearch();

        // 初始化列表
        initializeList();
    }
    /**
     * 初始化列表数据
     */
    private void initializeList() {
        Cursor cursor = managedQuery(
                getIntent().getData(),
                PROJECTION,
                null,
                null,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 使用自定义的NotesAdapter
        mAdapter = new NotesAdapter(this, cursor);

        // 设置列表适配器
        setListAdapter(mAdapter);
    }

    /**
     * 初始化EditText搜索功能
     */
    private void initEditTextSearch() {
        final EditText searchEditText = findViewById(R.id.search_edit_text);
        final TextView clearButton = findViewById(R.id.search_clear_button);

        if (searchEditText == null) {
            Log.e(TAG, "找不到search_edit_text！检查布局文件");
            return;
        }

        Log.d(TAG, "初始化EditText搜索框成功");

        // 设置搜索按钮点击监听（键盘上的搜索键）
        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getAction() == KeyEvent.ACTION_DOWN &&
                                event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    String query = v.getText().toString().trim();

                    // 保存搜索历史
                    if (!TextUtils.isEmpty(query)) {
                        // 这里需要获取搜索结果数量，暂时设为0，稍后更新
                        mSearchHistoryManager.saveSearchQuery(query, 0);
                    }

                    performRealTimeSearch(query, true);

                    // 隐藏键盘
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        // 设置文本变化监听（实时搜索）
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();

                // 显示/隐藏清除按钮
                if (clearButton != null) {
                    clearButton.setVisibility(TextUtils.isEmpty(query) ? View.GONE : View.VISIBLE);
                }

                // 执行实时搜索
                if (!TextUtils.isEmpty(query)) {
                    performRealTimeSearch(query, false);
                } else {
                    clearSearch();
                }
            }
        });

        // 清除按钮点击事件
        if (clearButton != null) {
            clearButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchEditText.setText("");
                    clearSearch();
                    searchEditText.requestFocus();
                }
            });
        }

        // 恢复之前的搜索词
        if (!TextUtils.isEmpty(mCurrentSearchQuery)) {
            searchEditText.setText(mCurrentSearchQuery);
            if (clearButton != null) {
                clearButton.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSearchIntent(intent);
    }

    private void handleSearchIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            performSearch(query);
        }
    }

    private void performSearch(String query) {
        mCurrentSearchQuery = query;
        mIsSearchMode = !query.isEmpty();

        // 更新搜索状态显示
        updateSearchStatus();

        // 执行实际搜索并刷新列表
        refreshNotesList();
    }

    private void updateSearchStatus() {
        if (mIsSearchMode) {
            String statusText = "搜索: \"" + mCurrentSearchQuery + "\"";
            mSearchStatusView.setText(statusText);
            mSearchStatusView.setVisibility(View.VISIBLE);

            // 显示搜索相关的提示
            showSearchHint();
        } else {
            mSearchStatusView.setVisibility(View.GONE);
        }
    }

    private void showSearchHint() {
        // 用Toast替代Snackbar，避免依赖问题
        Toast.makeText(this, "提示: 点击笔记查看详细信息，长按可进行更多操作",
                Toast.LENGTH_LONG).show();
    }

    // 在 Adapter 的 getView 方法中处理搜索高亮
    private class NotesAdapter extends CursorAdapter {
        public NotesAdapter(Context context, Cursor c) {
            super(context, c);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.noteslist_item, parent, false);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView titleView = view.findViewById(android.R.id.text1);
            TextView timestampView = view.findViewById(R.id.text2);
            TextView categoryView = view.findViewById(R.id.category_label);
            TextView searchIndicator = view.findViewById(R.id.search_match_indicator);

            // 使用正确的列索引获取数据
            String title = cursor.getString(COLUMN_INDEX_TITLE);
            String note = cursor.getString(COLUMN_INDEX_NOTE);
            String category = cursor.getString(COLUMN_INDEX_CATEGORY);
            long timestamp = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);

            // 设置基本内容
            timestampView.setText(formatTimestamp(timestamp));
            categoryView.setText(category);

            // 处理搜索高亮
            if (mIsSearchMode && !TextUtils.isEmpty(mCurrentSearchQuery)) {
                // 高级高亮：检查标题、内容和分类中的匹配
                boolean titleMatch = containsSearchTerm(title, mCurrentSearchQuery);
                boolean contentMatch = containsSearchTerm(note, mCurrentSearchQuery);
                boolean categoryMatch = containsSearchTerm(category, mCurrentSearchQuery);

                if (titleMatch || contentMatch || categoryMatch) {
                    // 显示搜索匹配指示器
                    searchIndicator.setVisibility(View.VISIBLE);

                    // 设置指示器文本和颜色
                    if (titleMatch && contentMatch) {
                        searchIndicator.setText("🔍 标题和内容");
                        searchIndicator.setBackgroundColor(0xFF4CAF50); // 绿色
                    } else if (titleMatch) {
                        searchIndicator.setText("🔍 标题");
                        searchIndicator.setBackgroundColor(0xFF2196F3); // 蓝色
                    } else if (contentMatch) {
                        searchIndicator.setText("🔍 内容");
                        searchIndicator.setBackgroundColor(0xFFFF9800); // 橙色
                    } else if (categoryMatch) {
                        searchIndicator.setText("🔍 分类");
                        searchIndicator.setBackgroundColor(0xFF9C27B0); // 紫色
                    }

                    // 应用高级高亮
                    if (titleMatch) {
                        highlightSearchText(titleView, title, mCurrentSearchQuery, true);
                    } else {
                        titleView.setText(title);
                    }
                } else {
                    titleView.setText(title);
                    searchIndicator.setVisibility(View.GONE);
                }
            } else {
                // 非搜索模式
                titleView.setText(title);
                searchIndicator.setVisibility(View.GONE);
            }
        }

        /**
         * 检查文本是否包含搜索词（支持大小写不敏感）
         */
        private boolean containsSearchTerm(String text, String searchQuery) {
            if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchQuery)) {
                return false;
            }

            if (mCaseSensitive) {
                return text.contains(searchQuery);
            } else {
                return text.toLowerCase().contains(searchQuery.toLowerCase());
            }
        }

        /**
         * 高亮搜索文本（支持多关键词）
         */
        private void highlightSearchText(TextView textView, String text, String searchQuery, boolean isTitle) {
            if (TextUtils.isEmpty(text) || TextUtils.isEmpty(searchQuery)) {
                textView.setText(text);
                return;
            }

            SpannableString spannable = new SpannableString(text);
            String lowerText = mCaseSensitive ? text : text.toLowerCase();
            String lowerQuery = mCaseSensitive ? searchQuery : searchQuery.toLowerCase();

            // 分割搜索词（支持多个关键词，用空格分隔）
            String[] keywords = searchQuery.split("\\s+");

            for (String keyword : keywords) {
                if (TextUtils.isEmpty(keyword.trim())) {
                    continue;
                }

                String lowerKeyword = mCaseSensitive ? keyword : keyword.toLowerCase();
                int startIndex = 0;

                while ((startIndex = lowerText.indexOf(lowerKeyword, startIndex)) != -1) {
                    int endIndex = startIndex + keyword.length();

                    // 根据匹配位置设置不同的高亮颜色
                    int highlightColor;
                    if (isTitle) {
                        highlightColor = 0x80FFEB3B; // 标题高亮：黄色，半透明
                    } else {
                        highlightColor = 0x8003A9F4; // 内容高亮：蓝色，半透明
                    }

                    spannable.setSpan(
                            new BackgroundColorSpan(highlightColor),
                            startIndex,
                            endIndex,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    // 加粗匹配的文本
                    spannable.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            startIndex,
                            endIndex,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    startIndex = endIndex;
                }
            }

            textView.setText(spannable);
        }
    }

    /**
     * 清除搜索
     */
    public void clearSearch() {
        mCurrentSearchQuery = "";
        mIsSearchMode = false;

        // 清除EditText中的文本
        EditText searchEditText = findViewById(R.id.search_edit_text);
        if (searchEditText != null) {
            searchEditText.setText("");
        }

        // 更新搜索状态
        updateSearchStatus();

        // 刷新列表（显示所有笔记）
        refreshNotesList();

        // 恢复标题
        setTitle("笔记");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 加载菜单资源
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 设置搜索历史菜单项点击监听器
        MenuItem historyItem = menu.findItem(R.id.menu_search_history);
        if (historyItem != null) {
            historyItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    showSearchHistoryDialog();
                    return true;
                }
            });
        }

        // 设置高级搜索菜单项点击监听器
        MenuItem advancedItem = menu.findItem(R.id.menu_advanced_search);
        if (advancedItem != null) {
            advancedItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    showAdvancedSearchOptions();
                    return true;
                }
            });
        }

        // 如果正在搜索，显示清除搜索的选项
        if (mIsSearchMode && !TextUtils.isEmpty(mCurrentSearchQuery)) {
            menu.add(0, Menu.FIRST + 100, 0, "清除搜索")
                    .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        // 生成其他可执行的操作
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }



    /**
     * 执行实时搜索（带防抖机制）
     * @param query 搜索关键词
     * @param immediate 是否立即执行（用户提交时）
     */
    private void performRealTimeSearch(String query, boolean immediate) {
        Log.d(TAG, "performRealTimeSearch called, query: " + query + ", immediate: " + immediate);

        // 取消之前的搜索任务
        if (mSearchRunnable != null) {
            mSearchHandler.removeCallbacks(mSearchRunnable);
        }

        // 如果查询为空，立即清除搜索
        if (TextUtils.isEmpty(query)) {
            Log.d(TAG, "Query is empty, clearing search");
            clearSearch();
            return;
        }

        final String searchQuery = query.trim();
        Log.d(TAG, "Search query trimmed: " + searchQuery);

        // 创建新的搜索任务
        mSearchRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Executing search runnable with query: " + searchQuery);
                // 执行实际搜索
                executeSearch(searchQuery);
            }
        };

        // 根据情况设置延迟或立即执行
        if (immediate) {
            // 用户提交，立即执行
            Log.d(TAG, "Immediate search execution");
            mSearchHandler.post(mSearchRunnable);
        } else {
            // 实时输入，延迟执行防抖
            Log.d(TAG, "Delayed search execution (debounce)");
            mSearchHandler.postDelayed(mSearchRunnable, SEARCH_DELAY_MS);
        }
    }

    /**
     * 执行实际搜索
     */
    private void executeSearch(String query) {
        Log.d(TAG, "executeSearch starting, query: " + query);

        // 更新当前搜索查询
        mCurrentSearchQuery = query;
        mIsSearchMode = true;

        // 更新搜索状态显示
        updateSearchStatus();

        // 执行搜索并刷新列表
        refreshNotesList();

        Log.d(TAG, "executeSearch completed, mIsSearchMode: " + mIsSearchMode);
    }



    /**
     * 显示搜索统计
     */
    private void showSearchStats() {
        // 获取搜索结果数量
        int resultCount = 0;
        if (mAdapter != null) {
            resultCount = mAdapter.getCount();
        }

        // 在标题栏显示结果数量
        String title = "笔记";
        if (resultCount > 0) {
            title += " (" + resultCount + " 个结果)";
        }
        setTitle(title);

        // 如果有搜索结果，保存到历史记录
        if (resultCount > 0 && !TextUtils.isEmpty(mCurrentSearchQuery)) {
            mSearchHistoryManager.saveSearchQuery(mCurrentSearchQuery, resultCount);
        }
    }

    /**
     * 显示搜索历史对话框
     */
    private void showSearchHistoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.search_history_dialog, null);
        builder.setView(dialogView);

        // 初始化对话框组件
        final ListView historyListView = dialogView.findViewById(R.id.search_history_list);
        final TextView emptyTextView = dialogView.findViewById(R.id.empty_history_text);
        Button clearButton = dialogView.findViewById(R.id.clear_history_button);
        Button closeButton = dialogView.findViewById(R.id.close_button);

        // 加载搜索历史
        final List<SearchHistoryManager.SearchHistoryItem> historyList =
                mSearchHistoryManager.getSearchHistory();

        if (historyList.isEmpty()) {
            emptyTextView.setVisibility(View.VISIBLE);
            historyListView.setVisibility(View.GONE);
            clearButton.setEnabled(false);
        } else {
            emptyTextView.setVisibility(View.GONE);
            historyListView.setVisibility(View.VISIBLE);
            clearButton.setEnabled(true);

            // 创建适配器
            HistoryAdapter adapter = new HistoryAdapter(historyList);
            historyListView.setAdapter(adapter);

            // 设置点击事件
            historyListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    SearchHistoryManager.SearchHistoryItem item = historyList.get(position);

                    // 设置搜索框文本并执行搜索
                    EditText searchEditText = findViewById(R.id.search_edit_text);
                    if (searchEditText != null) {
                        searchEditText.setText(item.query);
                        // 请求焦点并执行搜索
                        searchEditText.requestFocus();
                        performRealTimeSearch(item.query, true);
                    }

                    // 关闭对话框
                    ((AlertDialog) view.getTag()).dismiss();
                }
            });

            // 设置长按删除事件
            historyListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    SearchHistoryManager.SearchHistoryItem item = historyList.get(position);

                    new AlertDialog.Builder(NotesList.this)
                            .setTitle("删除搜索记录")
                            .setMessage("确定要删除搜索记录 \"" + item.query + "\" 吗？")
                            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mSearchHistoryManager.deleteHistoryItem(item.id);
                                    showSearchHistoryDialog(); // 刷新对话框
                                    Toast.makeText(NotesList.this, "搜索记录已删除", Toast.LENGTH_SHORT).show();
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();

                    return true;
                }
            });
        }

        // 设置清除历史按钮
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(NotesList.this)
                        .setTitle("确认清除")
                        .setMessage("确定要清除所有搜索历史吗？")
                        .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mSearchHistoryManager.clearAllHistory();
                                showSearchHistoryDialog(); // 刷新对话框
                                Toast.makeText(NotesList.this, "搜索历史已清除", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        });

        // 设置关闭按钮
        final AlertDialog dialog = builder.create();
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();

        // 将对话框保存到视图的tag中，方便在点击事件中关闭
        dialogView.setTag(dialog);
    }

    /**
     * 搜索历史适配器
     */
    private class HistoryAdapter extends BaseAdapter {
        private List<SearchHistoryManager.SearchHistoryItem> mHistoryList;

        public HistoryAdapter(List<SearchHistoryManager.SearchHistoryItem> historyList) {
            mHistoryList = historyList;
        }

        @Override
        public int getCount() {
            return mHistoryList.size();
        }

        @Override
        public SearchHistoryManager.SearchHistoryItem getItem(int position) {
            return mHistoryList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.search_history_item, parent, false);
                holder = new ViewHolder();
                holder.queryView = convertView.findViewById(R.id.history_query);
                holder.countView = convertView.findViewById(R.id.result_count);
                holder.timeView = convertView.findViewById(R.id.search_time);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            SearchHistoryManager.SearchHistoryItem item = getItem(position);

            // 设置查询文本
            holder.queryView.setText(item.query);

            // 设置结果数量
            String resultText = getResources().getQuantityString(
                    R.plurals.search_results_count,
                    item.resultCount,
                    item.resultCount
            );
            holder.countView.setText(resultText);

            // 设置时间
            holder.timeView.setText(formatHistoryTime(item.timestamp));

            return convertView;
        }

        class ViewHolder {
            TextView queryView;
            TextView countView;
            TextView timeView;
        }

        /**
         * 格式化历史时间
         */
        private String formatHistoryTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            // 转换为秒
            long diffSeconds = diff / 1000;

            if (diffSeconds < 60) {
                return "刚刚";
            } else if (diffSeconds < 3600) {
                return (diffSeconds / 60) + "分钟前";
            } else if (diffSeconds < 86400) {
                return (diffSeconds / 3600) + "小时前";
            } else if (diffSeconds < 2592000) { // 30天
                return (diffSeconds / 86400) + "天前";
            } else {
                // 超过30天显示具体日期
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                return sdf.format(new java.util.Date(timestamp));
            }
        }
    }

    // 合并后的 onOptionsItemSelected 方法
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // 处理清除搜索
        if (id == Menu.FIRST + 100) {
            clearSearch();
            return true;
        } else if (id == R.id.menu_add) {
            // 启动新的Activity创建笔记
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_paste) {
            // 启动新的Activity粘贴笔记
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_categories) {
            // 显示分类管理对话框
            showCategoryManagementDialog();
            return true;
        } else if (id == R.id.menu_filter) {
            // 显示分类筛选对话框
            if (mCurrentFilterCategory != null && !mCurrentFilterCategory.equals("所有分类")) {
                // 如果已经有筛选，点击则取消筛选
                clearFilter();
            } else {
                showCategoryFilterDialog();
            }
            return true;
        }
        // 其他菜单项已在onCreateOptionsMenu中设置监听器
        return super.onOptionsItemSelected(item);
    }


    /**
     * 格式化时间戳为易读的日期时间字符串
     */
    private String formatTimestamp(long timestamp) {
        if (timestamp == 0) {
            return "Unknown time";
        }

        Date date = new Date(timestamp);
        java.text.DateFormat dateFormat = DateFormat.getDateFormat(this);
        java.text.DateFormat timeFormat = DateFormat.getTimeFormat(this);

        return dateFormat.format(date) + " " + timeFormat.format(date);
    }



    /**
     * 显示搜索对话框
     */
    private void showSearchDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("搜索笔记");

        // 创建输入框
        final EditText input = new EditText(this);
        input.setHint("输入标题或内容关键词");
        if (mCurrentSearchQuery != null && !mCurrentSearchQuery.isEmpty()) {
            input.setText(mCurrentSearchQuery);
        }

        // 设置布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);
        builder.setView(layout);

        // 设置按钮
        builder.setPositiveButton("搜索", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String query = input.getText().toString().trim();
                if (!query.isEmpty()) {
                    mCurrentSearchQuery = query;
                    refreshNotesList();
                    Toast.makeText(NotesList.this, "正在搜索: " + query, Toast.LENGTH_SHORT).show();
                } else {
                    // 如果搜索框为空，清除搜索
                    mCurrentSearchQuery = null;
                    refreshNotesList();
                }
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // 如果有当前搜索，添加清除搜索按钮
        if (mCurrentSearchQuery != null && !mCurrentSearchQuery.isEmpty()) {
            builder.setNeutralButton("清除搜索", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mCurrentSearchQuery = null;
                    refreshNotesList();
                    Toast.makeText(NotesList.this, "已清除搜索", Toast.LENGTH_SHORT).show();
                }
            });
        }

        builder.show();

        // 自动弹出键盘
        input.requestFocus();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // 如果剪贴板中有数据，则启用粘贴菜单项
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        if (clipboard != null && clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            mPasteItem.setEnabled(false);
        }

        // 更新筛选菜单项状态
        MenuItem filterItem = menu.findItem(R.id.menu_filter);
        if (mCurrentFilterCategory != null && !mCurrentFilterCategory.equals("所有分类")) {
            filterItem.setTitle("取消筛选 (" + mCurrentFilterCategory + ")");
        } else {
            filterItem.setTitle("分类筛选");
        }

        // 获取当前显示的笔记数量
        final boolean haveItems = getListAdapter().getCount() > 0;

        // 如果列表中有笔记，则生成替代行动
        if (haveItems) {
            // 获取选中项的URI
            long selectedItemId = getSelectedItemId();
            if (selectedItemId != ListView.INVALID_ROW_ID) {
                Uri uri = ContentUris.withAppendedId(getIntent().getData(), selectedItemId);

                // 创建Intent数组
                Intent[] specifics = new Intent[1];

                // 设置Intent为编辑操作
                specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

                // 创建菜单项数组
                MenuItem[] items = new MenuItem[1];

                // 创建Intent
                Intent intent = new Intent(null, uri);
                intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

                // 添加替代选项到菜单
                menu.addIntentOptions(
                        Menu.CATEGORY_ALTERNATIVE,
                        Menu.NONE,
                        Menu.NONE,
                        null,
                        specifics,
                        intent,
                        Menu.NONE,
                        items
                );

                if (items[0] != null) {
                    items[0].setShortcut('1', 'e');
                }
            }
        } else {
            // 如果列表为空，移除所有替代行动
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        return true;
    }



    /**
     * 显示分类筛选对话框
     */
    private void showCategoryFilterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择分类筛选");

        // 加载分类列表
        final List<String> categories = loadCategories();
        categories.add(0, "所有分类"); // 添加"所有分类"选项

        final String[] categoryArray = categories.toArray(new String[0]);

        builder.setSingleChoiceItems(categoryArray,
                categories.indexOf(mCurrentFilterCategory != null ? mCurrentFilterCategory : "所有分类"),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String selectedCategory = categoryArray[which];
                        if (selectedCategory.equals("所有分类")) {
                            clearFilter();
                        } else {
                            applyFilter(selectedCategory);
                        }
                        dialog.dismiss();
                    }
                });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    /**
     * 应用分类筛选
     */
    private void applyFilter(String category) {
        mCurrentFilterCategory = category;
        refreshNotesList();
        Toast.makeText(this, "已筛选分类: " + category, Toast.LENGTH_SHORT).show();
    }

    /**
     * 清除筛选
     */
    private void clearFilter() {
        mCurrentFilterCategory = null;
        mCurrentSearchQuery = null;

        // 清除搜索框文本
        EditText searchEditText = findViewById(R.id.search_edit_text);
        if (searchEditText != null) {
            searchEditText.setText("");
            searchEditText.clearFocus();
        }

        refreshNotesList();
        Toast.makeText(this, "已清除筛选", Toast.LENGTH_SHORT).show();
    }

    /**
     * 从数据库加载分类列表
     */
    private List<String> loadCategories() {
        List<String> categories = new ArrayList<>();

        Cursor cursor = getContentResolver().query(
                NotePad.Categories.CONTENT_URI,
                new String[] { NotePad.Categories.COLUMN_NAME_NAME },
                null, null, NotePad.Categories.DEFAULT_SORT_ORDER
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String categoryName = cursor.getString(0);
                categories.add(categoryName);
            }
            cursor.close();
        }

        // 确保至少有一个默认分类
        if (categories.isEmpty()) {
            categories.add("默认分类");
        }

        return categories;
    }

    /**
     * 显示分类管理对话框
     */
    private void showCategoryManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.category_dialog, null);
        builder.setView(dialogView);

        // 初始化对话框组件
        final EditText categoryNameInput = dialogView.findViewById(R.id.category_name_input);
        Button addCategoryButton = dialogView.findViewById(R.id.add_category_button);
        final ListView categoriesList = dialogView.findViewById(R.id.categories_list);
        Button cancelButton = dialogView.findViewById(R.id.cancel_button);
        Button confirmButton = dialogView.findViewById(R.id.confirm_button);

        // 加载分类列表
        final CategoryAdapter categoryAdapter = new CategoryAdapter();
        categoriesList.setAdapter(categoryAdapter);

        // 添加分类按钮点击事件
        addCategoryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String categoryName = categoryNameInput.getText().toString().trim();
                if (!TextUtils.isEmpty(categoryName)) {
                    addCategory(categoryName);
                    categoryNameInput.setText("");
                    categoryAdapter.refreshData();
                } else {
                    Toast.makeText(NotesList.this, "请输入分类名称", Toast.LENGTH_SHORT).show();
                }
            }
        });

        final AlertDialog dialog = builder.create();

        // 设置取消按钮关闭对话框
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // 设置确定按钮关闭对话框并刷新列表
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshNotesList();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * 添加新分类
     */
    private void addCategory(String categoryName) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Categories.COLUMN_NAME_NAME, categoryName);
        values.put(NotePad.Categories.COLUMN_NAME_COLOR, 0xFF2196F3); // 默认蓝色

        try {
            getContentResolver().insert(NotePad.Categories.CONTENT_URI, values);
            CATEGORY_COLORS.put(categoryName, 0xFF2196F3); // 添加到颜色映射
            Toast.makeText(this, "分类添加成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "分类添加失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to add category", e);
        }
    }

    /**
     * 编辑分类
     */
    private void editCategory(final String oldName, final int categoryId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.edit_category_dialog, null);
        builder.setView(dialogView);

        final EditText categoryNameEdit = dialogView.findViewById(R.id.edit_category_name);
        final LinearLayout colorPalette = dialogView.findViewById(R.id.color_palette);
        Button cancelButton = dialogView.findViewById(R.id.edit_cancel_button);
        Button saveButton = dialogView.findViewById(R.id.edit_save_button);

        categoryNameEdit.setText(oldName);

        // 创建颜色选择器
        createColorPalette(colorPalette, CATEGORY_COLORS.get(oldName));

        final AlertDialog dialog = builder.create();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newName = categoryNameEdit.getText().toString().trim();
                if (!TextUtils.isEmpty(newName)) {
                    updateCategory(categoryId, oldName, newName, selectedColor);
                    dialog.dismiss();
                } else {
                    Toast.makeText(NotesList.this, "分类名称不能为空", Toast.LENGTH_SHORT).show();
                }
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * 创建颜色选择面板
     */
    private void createColorPalette(LinearLayout palette, int currentColor) {
        selectedColor = currentColor;
        palette.removeAllViews();

        for (final int color : COLOR_OPTIONS) {
            // 创建外层容器
            LinearLayout colorContainer = new LinearLayout(this);
            int size = getResources().getDimensionPixelSize(R.dimen.color_button_size);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(size, size);
            containerParams.setMargins(4, 4, 4, 4);
            colorContainer.setLayoutParams(containerParams);
            colorContainer.setOrientation(LinearLayout.VERTICAL);
            colorContainer.setGravity(android.view.Gravity.CENTER);

            // 创建颜色视图
            View colorView = new View(this);
            int innerSize = getResources().getDimensionPixelSize(R.dimen.color_inner_size);
            LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(innerSize, innerSize);
            colorView.setLayoutParams(colorParams);
            colorView.setBackgroundColor(color);
            colorView.setTag(color);

            // 为当前选中的颜色添加边框
            if (color == currentColor) {
                colorContainer.setBackgroundResource(R.drawable.color_selected_border);
            } else {
                colorContainer.setBackgroundColor(Color.TRANSPARENT);
            }

            colorContainer.addView(colorView);
            colorContainer.setTag(color);

            colorContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedColor = color;
                    // 更新所有颜色视图的选中状态
                    for (int i = 0; i < palette.getChildCount(); i++) {
                        View child = palette.getChildAt(i);
                        int childColor = (Integer) child.getTag();
                        if (childColor == color) {
                            child.setBackgroundResource(R.drawable.color_selected_border);
                        } else {
                            child.setBackgroundColor(Color.TRANSPARENT);
                        }
                    }
                }
            });

            palette.addView(colorContainer);
        }
    }

    /**
     * 更新分类
     */
    private void updateCategory(int categoryId, String oldName, String newName, int color) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Categories.COLUMN_NAME_NAME, newName);
        values.put(NotePad.Categories.COLUMN_NAME_COLOR, color);

        Uri categoryUri = ContentUris.withAppendedId(NotePad.Categories.CONTENT_URI, categoryId);

        try {
            getContentResolver().update(categoryUri, values, null, null);

            // 更新颜色映射
            CATEGORY_COLORS.remove(oldName);
            CATEGORY_COLORS.put(newName, color);

            // 更新所有使用该分类的笔记
            updateNotesCategory(oldName, newName);

            Toast.makeText(this, "分类更新成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "分类更新失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Failed to update category", e);
        }
    }

    /**
     * 更新笔记的分类
     */
    private void updateNotesCategory(String oldCategory, String newCategory) {
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, newCategory);

        getContentResolver().update(
                NotePad.Notes.CONTENT_URI,
                values,
                NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?",
                new String[] { oldCategory }
        );
    }

    /**
     * 删除分类
     */
    private void deleteCategory(final int categoryId, final String categoryName) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除分类 \"" + categoryName + "\" 吗？所有属于该分类的笔记将被移动到\"默认分类\"。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 先将该分类下的笔记移动到默认分类
                        updateNotesCategory(categoryName, "默认分类");

                        // 然后删除分类
                        Uri categoryUri = ContentUris.withAppendedId(NotePad.Categories.CONTENT_URI, categoryId);
                        getContentResolver().delete(categoryUri, null, null);

                        // 从颜色映射中移除
                        CATEGORY_COLORS.remove(categoryName);

                        Toast.makeText(NotesList.this, "分类已删除", Toast.LENGTH_SHORT).show();
                        refreshNotesList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 刷新笔记列表（支持高级搜索）
     */
    private void refreshNotesList() {
        // 构建查询条件
        String selection = null;
        String[] selectionArgs = null;
        List<String> selectionArgsList = new ArrayList<>();

        // 处理分类筛选
        if (mCurrentFilterCategory != null && !mCurrentFilterCategory.equals("所有分类")) {
            selection = NotePad.Notes.COLUMN_NAME_CATEGORY + " = ?";
            selectionArgsList.add(mCurrentFilterCategory);
        }

        // 处理搜索查询
        if (!TextUtils.isEmpty(mCurrentSearchQuery)) {
            // 构建高级搜索条件
            String searchCondition = buildAdvancedSearchCondition();

            if (selection == null) {
                selection = searchCondition;
                // 添加搜索参数
                String[] searchArgs = getSearchArgs(mCurrentSearchQuery);
                for (String arg : searchArgs) {
                    selectionArgsList.add(arg);
                }
            } else {
                selection += " AND " + searchCondition;
                // 添加搜索参数
                String[] searchArgs = getSearchArgs(mCurrentSearchQuery);
                for (String arg : searchArgs) {
                    selectionArgsList.add(arg);
                }
            }
        }

        // 转换 selectionArgsList 为数组
        if (!selectionArgsList.isEmpty()) {
            selectionArgs = selectionArgsList.toArray(new String[0]);
        }

        Log.d(TAG, "查询条件 - selection: " + selection + ", args: " + Arrays.toString(selectionArgs));

        // 执行查询
        Cursor newCursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // 更新适配器的游标
        if (mAdapter != null) {
            Cursor oldCursor = mAdapter.swapCursor(newCursor);
            if (oldCursor != null) {
                oldCursor.close();
            }

            // 如果有搜索词，保存搜索历史
            if (!TextUtils.isEmpty(mCurrentSearchQuery) && newCursor != null) {
                int resultCount = newCursor.getCount();
                mSearchHistoryManager.saveSearchQuery(mCurrentSearchQuery, resultCount);
            }
        }

        // 更新界面状态
        updateUIState();
    }

    /**
     * 构建高级搜索条件
     */
    private String buildAdvancedSearchCondition() {
        List<String> conditions = new ArrayList<>();
        String searchPattern = mCurrentSearchQuery;

        // 处理大小写敏感
        if (!mCaseSensitive) {
            // 如果不区分大小写，使用LOWER函数
            searchPattern = searchPattern.toLowerCase();
        }

        // 处理全词匹配
        if (mWholeWord) {
            searchPattern = " " + searchPattern + " ";
        }

        // 构建搜索模式
        String likePattern = "%" + searchPattern + "%";

        // 添加标题搜索条件
        if (mSearchInTitle) {
            if (mCaseSensitive) {
                conditions.add(NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?");
            } else {
                conditions.add("LOWER(" + NotePad.Notes.COLUMN_NAME_TITLE + ") LIKE ?");
            }
        }

        // 添加内容搜索条件
        if (mSearchInContent) {
            if (mCaseSensitive) {
                conditions.add(NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?");
            } else {
                conditions.add("LOWER(" + NotePad.Notes.COLUMN_NAME_NOTE + ") LIKE ?");
            }
        }

        // 添加分类搜索条件
        if (mSearchInTitle && mSearchInContent) {
            if (mCaseSensitive) {
                conditions.add(NotePad.Notes.COLUMN_NAME_CATEGORY + " LIKE ?");
            } else {
                conditions.add("LOWER(" + NotePad.Notes.COLUMN_NAME_CATEGORY + ") LIKE ?");
            }
        }

        // 如果没有选择任何搜索范围，默认搜索标题和内容
        if (conditions.isEmpty()) {
            if (mCaseSensitive) {
                conditions.add(NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ?");
                conditions.add(NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?");
            } else {
                conditions.add("LOWER(" + NotePad.Notes.COLUMN_NAME_TITLE + ") LIKE ?");
                conditions.add("LOWER(" + NotePad.Notes.COLUMN_NAME_NOTE + ") LIKE ?");
            }
        }

        // 组合条件
        if (conditions.size() == 1) {
            return conditions.get(0);
        } else {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sb.append(" OR ");
                }
                sb.append(conditions.get(i));
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * 获取搜索参数
     */
    private String[] getSearchArgs(String query) {
        List<String> args = new ArrayList<>();
        String searchPattern = query;

        if (!mCaseSensitive) {
            searchPattern = searchPattern.toLowerCase();
        }

        if (mWholeWord) {
            searchPattern = " " + searchPattern + " ";
        }

        String likePattern = "%" + searchPattern + "%";

        // 根据搜索条件添加参数
        if (mSearchInTitle) {
            args.add(likePattern);
        }

        if (mSearchInContent) {
            args.add(likePattern);
        }

        // 如果同时搜索标题和内容，也搜索分类
        if (mSearchInTitle && mSearchInContent) {
            args.add(likePattern);
        }

        // 如果没有选择任何搜索范围，默认搜索标题和内容
        if (args.isEmpty()) {
            args.add(likePattern);
            args.add(likePattern);
        }

        return args.toArray(new String[0]);
    }

    /**
     * 显示高级搜索选项对话框
     */
    private void showAdvancedSearchOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("高级搜索选项");

        // 创建自定义布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

// 标题搜索复选框
        final CheckBox titleCheckBox = new CheckBox(this);
        titleCheckBox.setText("搜索标题");
        titleCheckBox.setChecked(mSearchInTitle);
        layout.addView(titleCheckBox);

// 内容搜索复选框
        final CheckBox contentCheckBox = new CheckBox(this);
        contentCheckBox.setText("搜索内容");
        contentCheckBox.setChecked(mSearchInContent);
        layout.addView(contentCheckBox);

// 大小写敏感复选框
        final CheckBox caseCheckBox = new CheckBox(this);
        caseCheckBox.setText("区分大小写");
        caseCheckBox.setChecked(mCaseSensitive);
        layout.addView(caseCheckBox);

// 全词匹配复选框
        final CheckBox wordCheckBox = new CheckBox(this);
        wordCheckBox.setText("全词匹配");
        wordCheckBox.setChecked(mWholeWord);
        layout.addView(wordCheckBox);

        builder.setView(layout);

        // 设置按钮
        builder.setPositiveButton("应用", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 保存设置
                mSearchInTitle = titleCheckBox.isChecked();
                mSearchInContent = contentCheckBox.isChecked();
                mCaseSensitive = caseCheckBox.isChecked();
                mWholeWord = wordCheckBox.isChecked();

                // 如果当前有搜索，重新执行搜索
                if (!TextUtils.isEmpty(mCurrentSearchQuery)) {
                    executeSearch(mCurrentSearchQuery);
                }

                Toast.makeText(NotesList.this, "搜索选项已更新", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);

        // 添加重置按钮
        builder.setNeutralButton("重置", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 重置为默认设置
                mSearchInTitle = true;
                mSearchInContent = true;
                mCaseSensitive = false;
                mWholeWord = false;

                Toast.makeText(NotesList.this, "搜索选项已重置", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    /**
     * 更新界面状态显示
     */
    private void updateUIState() {
        // 更新标题显示搜索和筛选状态
        StringBuilder titleBuilder = new StringBuilder("笔记");

        if (mCurrentSearchQuery != null && !mCurrentSearchQuery.isEmpty()) {
            titleBuilder.append(" - 搜索: ").append(mCurrentSearchQuery);
        }

        if (mCurrentFilterCategory != null && !mCurrentFilterCategory.equals("所有分类")) {
            if (mCurrentSearchQuery != null && !mCurrentSearchQuery.isEmpty()) {
                titleBuilder.append(" (").append(mCurrentFilterCategory).append(")");
            } else {
                titleBuilder.append(" - ").append(mCurrentFilterCategory);
            }
        }

        setTitle(titleBuilder.toString());

        // 显示空列表提示
        View emptyView = findViewById(android.R.id.empty);
        if (emptyView instanceof TextView) {
            TextView emptyTextView = (TextView) emptyView;
            if (mAdapter == null || mAdapter.getCount() == 0) {
                if (mCurrentSearchQuery != null && !mCurrentSearchQuery.isEmpty()) {
                    if (mCurrentFilterCategory != null && !mCurrentFilterCategory.equals("所有分类")) {
                        emptyTextView.setText("在分类 \"" + mCurrentFilterCategory + "\" 中没有找到包含 \"" +
                                mCurrentSearchQuery + "\" 的笔记");
                    } else {
                        emptyTextView.setText("没有找到包含 \"" + mCurrentSearchQuery + "\" 的笔记");
                    }
                } else if (mCurrentFilterCategory != null && !mCurrentFilterCategory.equals("所有分类")) {
                    emptyTextView.setText("该分类下没有笔记");
                } else {
                    emptyTextView.setText("还没有笔记，点击菜单按钮创建新笔记");
                }
                getListView().setEmptyView(emptyTextView);
            }
        }
    }

    /**
     * 分类列表适配器
     */
    private class CategoryAdapter extends BaseAdapter {
        private List<Category> categories = new ArrayList<>();

        public CategoryAdapter() {
            refreshData();
        }

        public void refreshData() {
            categories.clear();
            Cursor cursor = getContentResolver().query(
                    NotePad.Categories.CONTENT_URI,
                    new String[] {
                            NotePad.Categories._ID,
                            NotePad.Categories.COLUMN_NAME_NAME,
                            NotePad.Categories.COLUMN_NAME_COLOR
                    },
                    null, null, NotePad.Categories.DEFAULT_SORT_ORDER
            );

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Category category = new Category();
                    category.id = cursor.getInt(0);
                    category.name = cursor.getString(1);
                    category.color = cursor.getInt(2);
                    categories.add(category);

                    // 更新颜色映射
                    CATEGORY_COLORS.put(category.name, category.color);
                }
                cursor.close();
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return categories.size();
        }

        @Override
        public Category getItem(int position) {
            return categories.get(position);
        }

        @Override
        public long getItemId(int position) {
            return categories.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.category_item, parent, false);
                holder = new ViewHolder();
                holder.colorView = convertView.findViewById(R.id.category_color);
                holder.nameView = convertView.findViewById(R.id.category_name);
                holder.editButton = convertView.findViewById(R.id.edit_button);
                holder.deleteButton = convertView.findViewById(R.id.delete_button);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            final Category category = getItem(position);
            holder.nameView.setText(category.name);
            holder.colorView.setBackgroundColor(category.color);

            // 编辑按钮点击事件
            holder.editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editCategory(category.name, category.id);
                }
            });

            // 删除按钮点击事件
            holder.deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteCategory(category.id, category.name);
                }
            });

            return convertView;
        }

        class ViewHolder {
            View colorView;
            TextView nameView;
            ImageButton editButton;
            ImageButton deleteButton;
        }
    }

    /**
     * 分类数据模型
     */
    private static class Category {
        int id;
        String name;
        int color;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // 尝试获取长按项在ListView中的位置
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        // 获取选中项的数据
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        if (cursor == null) {
            return;
        }

        // 加载上下文菜单资源
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // 设置菜单标题为选中笔记的标题
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // 添加其他Activity可以处理的操作
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // 获取菜单项的额外信息
        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        // 构建选中笔记的URI
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        // 获取菜单项的ID并比较
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // 打开笔记进行查看/编辑
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            // 复制笔记URI到剪贴板
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(),
                    "Note",
                    noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            // 从提供者中删除笔记
            getContentResolver().delete(
                    noteUri,
                    null,
                    null
            );
            return true;
        } else if (id == R.id.context_create_widget) {
            // 创建便签小部件
            createNoteWidget(info.id);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // 构建新的URI
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // 获取传入的操作
        String action = getIntent().getAction();

        // 处理数据请求
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            // 启动编辑Activity
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }

    /**
     * 当从其他Activity返回时刷新列表
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 刷新列表以显示可能的更改
        refreshNotesList();

    }

    /**
     * 创建笔记便签小部件
     */
    private void createNoteWidget(long noteId) {
        // 获取笔记标题用于显示
        Cursor cursor = getContentResolver().query(
                ContentUris.withAppendedId(getIntent().getData(), noteId),
                new String[]{NotePad.Notes.COLUMN_NAME_TITLE},
                null, null, null);

        String noteTitle = "笔记";
        if (cursor != null && cursor.moveToFirst()) {
            noteTitle = cursor.getString(0);
            cursor.close();
        }

        // 显示更友好的提示信息
        Toast.makeText(this, "便签功能已就绪！\n如需新便签，请从桌面添加", Toast.LENGTH_LONG).show();

        // 可选：提供直接打开小部件配置的选项
        showWidgetCreationDialog(noteId, noteTitle);
    }

    /**
     * 显示便签创建对话框
     */
    private void showWidgetCreationDialog(final long noteId, final String noteTitle) {
        new AlertDialog.Builder(this)
                .setTitle("创建便签")
                .setMessage("您想为笔记《" + noteTitle + "》创建便签吗？\n\n" +
                        "• 如需新便签：请从桌面添加小部件\n" +
                        "• 如需更新现有便签：长按桌面便签重新配置")
                .setPositiveButton("从桌面添加", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 指导用户如何添加
                        Toast.makeText(NotesList.this,
                                "请长按桌面 → 选择小部件 → 找到\"笔记便签\"",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }


}