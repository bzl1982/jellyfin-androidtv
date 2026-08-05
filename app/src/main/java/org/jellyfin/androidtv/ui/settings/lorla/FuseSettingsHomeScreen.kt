package org.jellyfin.androidtv.ui.settings.lorla

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.settings.Routes
import org.koin.compose.koinInject

private data class SettingsCard(
	val icon: Int,
	val title: String,
	val subtitle: String,
	val route: String? = null,
)

@Composable
fun FuseSettingsHomeScreen() {
	val router = LocalRouter.current
	val navigationRepository = koinInject<NavigationRepository>()

	val cards = remember {
		listOf(
			SettingsCard(R.drawable.ic_user, "账号与服务器", "切换用户、服务器、自动登录", Routes.AUTHENTICATION),
			SettingsCard(R.drawable.ic_play, "播放", "播放器、下一集、码率、刷新率", Routes.PLAYBACK),
			SettingsCard(R.drawable.ic_subtitles, "字幕", "字幕样式、颜色、描边", Routes.CUSTOMIZATION_SUBTITLES),
			SettingsCard(R.drawable.ic_masks, "界面", "主题、时钟、 watched 标记", Routes.CUSTOMIZATION),
			SettingsCard(R.drawable.ic_heart, "皮肤", "LORLA 皮肤切换", Routes.FUSE_SETTINGS_SKIN),
			SettingsCard(R.drawable.ic_movie, "媒体库", "库显示、海报尺寸、图片类型", Routes.LIBRARIES),
			SettingsCard(R.drawable.ic_house, "首页", "首页分区、排序", Routes.HOME),
			SettingsCard(R.drawable.ic_tv_timer, "直播电视", "节目单、选项、频道排序", Routes.LIVETV_GUIDE_OPTIONS),
			SettingsCard(R.drawable.ic_time, "屏保", "屏保超时、年龄分级", Routes.CUSTOMIZATION_SCREENSAVER),
			SettingsCard(R.drawable.ic_grid, "菜单设置", "调整左侧菜单名称、顺序、子分类", Routes.FUSE_SETTINGS_MENU),
			SettingsCard(R.drawable.ic_info, "关于", "版本、开源许可", Routes.ABOUT),
		)
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(JellyfinTheme.colorScheme.background),
	) {
		Row(modifier = Modifier.fillMaxSize()) {
			FuseSettingsEdgeSidebar(
				onClose = { navigationRepository.goBack() },
				modifier = Modifier.fillMaxHeight(),
			)

			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(top = 48.dp, bottom = 32.dp, end = 64.dp),
			) {
				Text(
					"设置",
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 38.sp,
						fontWeight = FontWeight.Bold,
					),
					modifier = Modifier.padding(bottom = 36.dp),
				)

				Column(
					modifier = Modifier
						.weight(1f)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(18.dp),
				) {
					cards.chunked(4).forEach { rowCards ->
						Row(
							horizontalArrangement = Arrangement.spacedBy(18.dp),
							modifier = Modifier.fillMaxWidth(),
						) {
							rowCards.forEach { card ->
								FuseSettingsCard(
									card = card,
									onClick = {
										card.route?.let { router.push(it) }
									},
									modifier = Modifier.weight(1f),
								)
							}
						}
					}
				}
			}
		}
	}
}

@Composable
private fun FuseSettingsCard(
	card: SettingsCard,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(16.dp)

	val backgroundBrush = when {
		focused -> Brush.horizontalGradient(
			0f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.85f),
			1f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.55f),
		)

		else -> Brush.horizontalGradient(
			0f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.72f),
			1f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.45f),
		)
	}

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
		modifier = modifier
			.height(160.dp)
			.background(backgroundBrush, shape)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = if (focused) Color.Transparent else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.12f),
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(16.dp)
			.focusGroup(),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(card.icon),
			contentDescription = card.title,
			tint = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			modifier = Modifier.size(38.dp),
		)

		Spacer(modifier = Modifier.height(14.dp))

		Text(
			card.title,
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 20.sp,
				fontWeight = FontWeight.Bold,
				textAlign = TextAlign.Center,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)

		Spacer(modifier = Modifier.height(4.dp))

		Text(
			card.subtitle,
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused.copy(alpha = 0.85f) else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 13.sp,
				textAlign = TextAlign.Center,
			),
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
	}
}
