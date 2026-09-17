package com.kai.masterbrowse

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("master_browse", Context.MODE_PRIVATE)
    }

    var homeFolder: String?
        get() = sp.getString("home", null)
        set(value) {
            sp.edit().putString("home", value).apply()
        }

    var tileSizeDp: Float
        get() = sp.getFloat("tileSize", 150f)
        set(value) {
            sp.edit().putFloat("tileSize", value).apply()
        }

    /** How the current folder's entries are ordered. */
    var sortMode: SortMode
        get() = runCatching { SortMode.valueOf(sp.getString("sortMode", null) ?: SortMode.NAME.name) }
            .getOrDefault(SortMode.NAME)
        set(value) {
            sp.edit().putString("sortMode", value.name).apply()
        }

    /** Descending order when true. */
    var sortDesc: Boolean
        get() = sp.getBoolean("sortDesc", false)
        set(value) {
            sp.edit().putBoolean("sortDesc", value).apply()
        }

    /** Show the filename bar on tiles. */
    var showNames: Boolean
        get() = sp.getBoolean("showNames", true)
        set(value) {
            sp.edit().putBoolean("showNames", value).apply()
        }

    /** True = justified true-aspect-ratio tiles; false = square tile grid. */
    var aspectMode: Boolean
        get() = sp.getBoolean("aspectMode", false)
        set(value) {
            sp.edit().putBoolean("aspectMode", value).apply()
        }

    /**
     * Pinned folder paths in pin order. Stored newline-joined (getStringSet's
     * returned set must never be mutated and loses order; paths with a literal
     * newline aren't realistic for media folders).
     */
    var pinnedFolders: List<String>
        get() = sp.getString("pins", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) {
            sp.edit().putString("pins", value.joinToString("\n")).apply()
        }

    /** Tile size inside the floating window; kept separate so pinching there doesn't
     *  shrink the fullscreen grid. */
    var floatTileSizeDp: Float
        get() = sp.getFloat("floatTileSize", 110f)
        set(value) {
            sp.edit().putFloat("floatTileSize", value).apply()
        }

    /** Last position/size of the floating window, in px. -1 means "not set yet". */
    var floatX: Int
        get() = sp.getInt("floatX", -1)
        set(value) {
            sp.edit().putInt("floatX", value).apply()
        }

    var floatY: Int
        get() = sp.getInt("floatY", -1)
        set(value) {
            sp.edit().putInt("floatY", value).apply()
        }

    var floatW: Int
        get() = sp.getInt("floatW", -1)
        set(value) {
            sp.edit().putInt("floatW", value).apply()
        }

    var floatH: Int
        get() = sp.getInt("floatH", -1)
        set(value) {
            sp.edit().putInt("floatH", value).apply()
        }

    /**
     * How much room the floating window's media area should take up, in px². Kept apart
     * from floatW/floatH so that auto-fitting to each item's shape never redefines the
     * size the user actually dragged the window to. -1 means "not set yet".
     */
    var floatContentArea: Long
        get() = sp.getLong("floatContentArea", -1L)
        set(value) {
            sp.edit().putLong("floatContentArea", value).apply()

        }

    fun folderThumb(folderPath: String): String? = sp.getString("thumb:$folderPath", null)

    fun setFolderThumb(folderPath: String, imagePath: String?) {
        sp.edit().apply {
            if (imagePath == null) remove("thumb:$folderPath") else putString("thumb:$folderPath", imagePath)
        }.apply()
    }
}
