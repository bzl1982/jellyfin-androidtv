package org.jellyfin.androidtv.preference

import android.content.Context

/**
 * The big stage at the top of the home screen has six visual variants. These
 * map 1:1 to the real Arctic Fuse spotlight/billboard styles: Standard
 * (full-bleed backdrop + left info tower), Showcase (cast collage), Fanart
 * (artwork only, minimal chrome), Mini (compact top strip), Disabled (no
 * stage, rows start immediately) and the slide variant (half-height stage).
 * The selection is a global preference, never something switched on the page
 * itself — it lives in Settings -> Home, same as every other global knob.
 */
enum class HeroLayoutMode(val label: String) {
	FULL_BLEED_LEFT_INFO("全屏海报+左信息"),
	SHOWCASE_COLLAGE("群像拼贴"),
	FANART_ONLY("纯背景极简"),
	MINI_STAGE("迷你舞台"),
	NO_STAGE("无大舞台"),
	SLIDE_STAGE("半高下滑"),
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