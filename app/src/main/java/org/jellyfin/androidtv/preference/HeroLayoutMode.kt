package org.jellyfin.androidtv.preference

import android.content.Context

/**
 * The big stage at the top of the home screen has six visual variants. The
 * selection is a global preference, never something switched on the page
 * itself — it lives in Settings -> Home, same as every other global knob.
 */
enum class HeroLayoutMode(val label: String) {
	FULL_BLEED_LEFT_INFO("全屏海报+左信息"),
	FULL_BLEED_CENTER_INFO("全屏海报+居中信息"),
	POSTER_SHOWCASE("海报展示"),
	LANDSCAPE_SHOWCASE("横幅展示"),
	MINIMAL_TITLE("极简标题"),
	FULL_BLEED_WITH_NAV_PILLS("全屏海报+底部导航"),
	;

	companion object {
		fun fromName(name: String?): HeroLayoutMode =
			if (name == null) FULL_BLEED_LEFT_INFO
			else runCatching { valueOf(name) }.getOrDefault(FULL_BLEED_LEFT_INFO)
	}
}

/**
 * Persistent store for the user's chosen hero layout. Same shape as
 * UserSettingPreferences but scoped to its own SharedPreferences file because
 * the value is read on the home screen on every recomposition.
 */
object HeroLayoutModePreferences {
	private const val PREFS_NAME = "lorla_hero_layout"
	private const val KEY_MODE = "mode"

	fun get(context: Context): HeroLayoutMode {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		return HeroLayoutMode.fromName(prefs.getString(KEY_MODE, null))
	}

	fun set(context: Context, mode: HeroLayoutMode) {
		val prefs = context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		prefs.edit().putString(KEY_MODE, mode.name).apply()
	}
}