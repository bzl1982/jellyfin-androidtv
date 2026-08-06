package org.jellyfin.androidtv.preference

import android.content.Context

/**
 * How the left rail is shown. A global preference (Settings -> Home), same as
 * every other view-style knob — never an on-page toggle.
 */
enum class SidebarMode(val label: String) {
	HIDDEN("隐藏菜单"),
	ICONS_ONLY("只显示图标"),
	ICONS_AND_LABELS("图标+文字"),
	;

	companion object {
		fun fromName(name: String?): SidebarMode =
			if (name == null) ICONS_AND_LABELS
			else runCatching { valueOf(name) }.getOrDefault(ICONS_AND_LABELS)
	}
}

object SidebarModePreferences {
	private const val PREFS_NAME = "lorla_sidebar_mode"
	private const val KEY_MODE = "mode"

	fun get(context: Context): SidebarMode {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		return SidebarMode.fromName(prefs.getString(KEY_MODE, null))
	}

	fun set(context: Context, mode: SidebarMode) {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		prefs.edit().putString(KEY_MODE, mode.name).apply()
	}
}