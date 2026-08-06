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
import org.jellyfin.androidtv.preference.SidebarMode
import org.jellyfin.androidtv.preference.SidebarModePreferences
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
			// Left-rail display mode: hidden / icons-only / icons+labels. When the
			// rail expands the whole page shifts right, so it never covers content.
			Column(
				modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
				verticalArrangement = Arrangement.spacedBy(2.dp),
			) {
				FuseSectionHeader(text = "左侧菜单显示方式")
				SidebarMode.entries.forEach { mode ->
					FuseRadioOption(
						title = mode.label,
						subtitle = sidebarSubtitleFor(mode),
						selected = SidebarModePreferences.get(context) == mode,
						onClick = { SidebarModePreferences.set(context, mode) },
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
	HeroLayoutMode.SHOWCASE_COLLAGE -> "右侧演员头像拼贴，FUSE 招牌风格"
	HeroLayoutMode.FANART_ONLY -> "纯背景大图，仅右上小标题+底部控制"
	HeroLayoutMode.MINI_STAGE -> "迷你舞台：小海报+标题+按钮一条横排"
	HeroLayoutMode.NO_STAGE -> "无大舞台，直接显示海报行"
	HeroLayoutMode.SLIDE_STAGE -> "半高舞台，向下浏览时自然滑走"
}

private fun sidebarSubtitleFor(mode: SidebarMode): String = when (mode) {
	SidebarMode.HIDDEN -> "完全隐藏左侧菜单，内容占满全屏"
	SidebarMode.ICONS_ONLY -> "只显示图标（64dp 窄栏，不展开文字）"
	SidebarMode.ICONS_AND_LABELS -> "默认：展开时显示图标+文字，整页右移"
}