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

    fun folderThumb(folderPath: String): String? = sp.getString("thumb:$folderPath", null)

    fun setFolderThumb(folderPath: String, imagePath: String?) {
        sp.edit().apply {
            if (imagePath == null) remove("thumb:$folderPath") else putString("thumb:$folderPath", imagePath)
        }.apply()
    }
}
