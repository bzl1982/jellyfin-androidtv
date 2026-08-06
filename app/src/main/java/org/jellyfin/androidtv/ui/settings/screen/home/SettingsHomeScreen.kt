package org.jellyfin.androidtv.ui.settings.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.itemsIndexed
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.HeroLayoutMode
import org.jellyfin.androidtv.preference.HeroLayoutModePreferences
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.jellyfin.androidtv.ui.settings.lorla.FuseRadioOption
import org.jellyfin.androidtv.ui.settings.lorla.FuseSectionHeader
import org.jellyfin.androidtv.preference.UserSettingPreferences
import org.koin.compose.koinInject

@Composable
fun SettingsHomeScreen() {
	val router = LocalRouter.current
	val userSettingPreferences = koinInject<UserSettingPreferences>()
	val context = LocalContext.current

	SettingsColumn {
		item {
			// FUSE-style hero-layout picker, sitting on top of the regular
			// home-sections list. Six variants, single selection, persisted to
			// its own SharedPreferences file (lorla_hero_layout). The home screen
			// reads it on every (re)entry.
			Column(
				modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				FuseSectionHeader(text = "FUSE 大舞台展示方式")
				HeroLayoutMode.entries.forEach { mode ->
					FuseRadioOption(
						title = mode.label,
						subtitle = subtitleFor(mode),
						selected = HeroLayoutModePreferences.get(context) == mode,
						onClick = { HeroLayoutModePreferences.set(context, mode) },
					)
				}
			}
		}

		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.pref_customization).uppercase()) },
				headingContent = { Text(stringResource(R.string.home_prefs)) },
			)
		}

		itemsIndexed(userSettingPreferences.homesections) { index, section ->
			ListButton(
				headingContent = { Text(stringResource(R.string.home_section_i, index + 1)) },
				captionContent = { Text(stringResource(userSettingPreferences[section].nameRes)) },
				onClick = { router.push(Routes.HOME_SECTION, mapOf("index" to index.toString())) },
				modifier = Modifier.focusKey("home_section_$index")
			)
		}
	}
}

private fun subtitleFor(mode: HeroLayoutMode): String = when (mode) {
	HeroLayoutMode.FULL_BLEED_LEFT_INFO -> "全屏海报背景，左下标题+简介+评分（默认）"
	HeroLayoutMode.FULL_BLEED_CENTER_INFO -> "全屏海报背景，标题居中、底部信息条"
	HeroLayoutMode.POSTER_SHOWCASE -> "左侧大海报 + 右侧剧情信息"
	HeroLayoutMode.LANDSCAPE_SHOWCASE -> "横幅大图 + 下方信息面板"
	HeroLayoutMode.MINIMAL_TITLE -> "极简：只显示大标题，按钮居中"
	HeroLayoutMode.FULL_BLEED_WITH_NAV_PILLS -> "全屏海报 + 底部分类胶囊导航"
}