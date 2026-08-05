package org.jellyfin.androidtv.ui.settings.screen.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.UserPreferences
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.focus.focusKey
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.compat.rememberPreference
import org.jellyfin.androidtv.ui.settings.lorla.FuseIcons
import org.jellyfin.androidtv.ui.settings.lorla.FuseSectionHeader
import org.jellyfin.androidtv.ui.settings.lorla.FuseSettingRow
import org.jellyfin.androidtv.ui.settings.lorla.FuseSettingsScaffold
import org.jellyfin.androidtv.ui.settings.lorla.FuseSwitchRow
import org.koin.compose.koinInject

@Composable
fun SettingsCustomizationScreen() {
	val router = LocalRouter.current
	val userPreferences = koinInject<UserPreferences>()

	FuseSettingsScaffold(
		title = stringResource(R.string.pref_customization),
		subtitle = "主题、时钟、已看标记与背景",
	) {
		var appTheme by rememberPreference(userPreferences, UserPreferences.appTheme)
		FuseSettingRow(
			title = stringResource(R.string.pref_app_theme),
			value = stringResource(appTheme.nameRes),
			onClick = { router.push(Routes.CUSTOMIZATION_THEME) },
			modifier = Modifier.focusKey(Routes.CUSTOMIZATION_THEME),
		)

		var clockBehavior by rememberPreference(userPreferences, UserPreferences.clockBehavior)
		FuseSettingRow(
			title = stringResource(R.string.pref_clock_display),
			value = stringResource(clockBehavior.nameRes),
			onClick = { router.push(Routes.CUSTOMIZATION_CLOCK) },
			modifier = Modifier.focusKey(Routes.CUSTOMIZATION_CLOCK),
		)

		var watchedIndicatorBehavior by rememberPreference(userPreferences, UserPreferences.watchedIndicatorBehavior)
		FuseSettingRow(
			title = stringResource(R.string.pref_watched_indicator),
			value = stringResource(watchedIndicatorBehavior.nameRes),
			onClick = { router.push(Routes.CUSTOMIZATION_WATCHED_INDICATOR) },
			modifier = Modifier.focusKey(Routes.CUSTOMIZATION_WATCHED_INDICATOR),
		)

		var backdropBehavior by rememberPreference(userPreferences, UserPreferences.backdropBehavior)
		FuseSettingRow(
			title = stringResource(R.string.lbl_show_backdrop),
			value = stringResource(backdropBehavior.nameRes),
			onClick = { router.push(Routes.CUSTOMIZATION_BACKDROP) },
			modifier = Modifier.focusKey(Routes.CUSTOMIZATION_BACKDROP),
		)

		var seriesThumbnailsEnabled by rememberPreference(userPreferences, UserPreferences.seriesThumbnailsEnabled)
		FuseSwitchRow(
			title = stringResource(R.string.lbl_use_series_thumbnails),
			subtitle = stringResource(R.string.lbl_use_series_thumbnails_description),
			checked = seriesThumbnailsEnabled,
			onCheckedChange = { seriesThumbnailsEnabled = it },
			modifier = Modifier.focusKey("series_thumbnails_enabled"),
		)

		FuseSectionHeader(text = stringResource(R.string.pref_browsing))

		FuseSettingRow(
			title = stringResource(R.string.pref_libraries),
			icon = FuseIcons.grid,
			onClick = { router.push(Routes.LIBRARIES) },
			modifier = Modifier.focusKey(Routes.LIBRARIES),
		)

		FuseSettingRow(
			title = stringResource(R.string.home_prefs),
			icon = FuseIcons.house,
			onClick = { router.push(Routes.HOME) },
			modifier = Modifier.focusKey(Routes.HOME),
		)
	}
}
