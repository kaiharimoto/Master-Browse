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

    /** True = justified true-aspect-ratio tiles; false = square tile grid. */
    var aspectMode: Boolean
        get() = sp.getBoolean("aspectMode", false)
        set(value) {
            sp.edit().putBoolean("aspectMode", value).apply()
        }

    fun folderThumb(folderPath: String): String? = sp.getString("thumb:$folderPath", null)

    fun setFolderThumb(folderPath: String, imagePath: String?) {
        sp.edit().apply {
            if (imagePath == null) remove("thumb:$folderPath") else putString("thumb:$folderPath", imagePath)
        }.apply()
    }
}
