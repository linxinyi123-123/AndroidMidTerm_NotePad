/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the Note Pad content provider and its clients. A contract defines the
 * information that a client needs to access the provider as one or more data tables. A contract
 * is a public, non-extendable (final) class that contains constants defining column names and
 * URIs. A well-written client depends only on the constants in the contract.
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    /**
     * The scheme part for this provider's URI
     */
    private static final String SCHEME = "content://";

    // This class cannot be instantiated
    private NotePad() {
    }

    /**
     * Notes table contract
     */
    public static final class Notes implements BaseColumns {

        // This class cannot be instantiated
        private Notes() {}

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "notes";

        /*
         * Path parts for the URIs
         */

        /**
         * Path part for the Notes URI
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * Path part for the Note ID URI
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * 0-relative position of a note ID segment in the path part of a note ID URI
         */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /**
         * Path part for the Live Folder URI
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * The content URI base for a single note. Callers must
         * append a numeric note id to this Uri to retrieve a note
         */
        public static final Uri CONTENT_ID_URI_BASE
                = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * The content URI match pattern for a single note, specified by its ID. Use this to match
         * incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN
                = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         * The content Uri pattern for a notes listing for live folders
         */
        public static final Uri LIVE_FOLDER_URI
                = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME type definitions
         */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * Column definitions
         */

        /**
         * Column name for the title of the note
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * Column name of the note content
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * Column name for the modification timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        /**
         * Column name for the category of the note
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_CATEGORY = "category";
    }

    /**
     * Categories table contract
     */
    public static final class Categories implements BaseColumns {

        // This class cannot be instantiated
        private Categories() {}

        /**
         * The table name for categories
         */
        public static final String TABLE_NAME = "categories";

        /**
         * Path part for the Categories URI
         */
        private static final String PATH_CATEGORIES = "/categories";

        /**
         * The content:// style URL for the categories table
         */
        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_CATEGORIES);

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of categories.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.category";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single category.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.category";

        /*
         * Column definitions
         */

        /**
         * Column name for the category name
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_NAME = "name";

        /**
         * Column name for the category color
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_COLOR = "color";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "name ASC";
    }

    /**
     * 搜索历史表 contract
     */
    public static final class SearchHistory implements BaseColumns {

        // This class cannot be instantiated
        private SearchHistory() {}

        /**
         * The table name for search history
         */
        public static final String TABLE_NAME = "search_history";

        /**
         * Path part for the SearchHistory URI
         */
        private static final String PATH_SEARCH_HISTORY = "/search_history";

        /**
         * The content:// style URL for the search history table
         */
        public static final Uri CONTENT_URI = Uri.parse(SCHEME + AUTHORITY + PATH_SEARCH_HISTORY);

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of search history.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.search_history";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single search history.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.search_history";

        /*
         * Column definitions
         */

        /**
         * Column name for the search query
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_QUERY = "search_query";  // 修改这里：从"query"改为"search_query"

        /**
         * Column name for the search timestamp
         * <P>Type: INTEGER (long from System.currentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_TIMESTAMP = "timestamp";

        /**
         * Column name for the search result count
         * <P>Type: INTEGER</P>
         */
        public static final String COLUMN_NAME_RESULT_COUNT = "result_count";

        /**
         * The default sort order for this table (most recent first)
         */
        public static final String DEFAULT_SORT_ORDER = "timestamp DESC";
    }
}