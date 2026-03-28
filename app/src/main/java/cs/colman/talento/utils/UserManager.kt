package cs.colman.talento.utils

import android.content.Context

object UserManager {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"

    fun saveUserId(context: Context, userId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getUserId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, null)
    }

    fun clearUserId(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_USER_ID).apply()
    }

}
