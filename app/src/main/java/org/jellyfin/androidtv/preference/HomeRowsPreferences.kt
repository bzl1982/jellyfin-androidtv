package org.jellyfin.androidtv.preference

import android.content.Context

/**
 * Controls whether the home screen builds every program-type row at once, or
 * only the first few so the initial load stays light and doesn't stutter on a
 * big library. The rest are turned on manually from Settings -> Home.
 */
object HomeRowsPreferences {
	private const val PREFS_NAME = "lorla_home_rows"
	private const val KEY_LOAD_ALL = "load_all"

	fun getLoadAll(context: Context): Boolean {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		return prefs.getBoolean(KEY_LOAD_ALL, false)
	}

	fun setLoadAll(context: Context, value: Boolean) {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		prefs.edit().putBoolean(KEY_LOAD_ALL, value).apply()
	}
}
