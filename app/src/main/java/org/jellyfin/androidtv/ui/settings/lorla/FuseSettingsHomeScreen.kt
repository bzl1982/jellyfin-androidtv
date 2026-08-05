package org.jellyfin.androidtv.ui.settings.lorla

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
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
	val accent: Color,
	val title: String,
	val subtitle: String,
	val route: String? = null,
)

@Composable
fun FuseSettingsHomeScreen() {
	val router = LocalRouter.current
	val navigationRepository = koinInject<NavigationRepository>()

	val accent1 = JellyfinTheme.colorScheme.buttonFocused
	val accent2 = Color(0xFFFFA94D)
	val accent3 = Color(0xFF4DA6FF)
	val accent4 = Color(0xFF7ED957)
	val accent5 = Color(0xFFFF5C8A)
	val accent6 = Color(0xFFB97CFF)
	val accent7 = Color(0xFFFFD24D)
	val accent8 = Color(0xFF8FE6E0)

	val cards = remember(accent1) {
		listOf(
			SettingsCard(R.drawable.ic_user, accent1, "账号与服务器", "切换用户、服务器、自动登录", Routes.AUTHENTICATION),
			SettingsCard(R.drawable.ic_play, accent3, "播放", "播放器、下一集、码率、刷新率", Routes.PLAYBACK),
			SettingsCard(R.drawable.ic_subtitles, accent7, "字幕", "字幕样式、颜色、描边", Routes.CUSTOMIZATION_SUBTITLES),
			SettingsCard(R.drawable.ic_masks, accent4, "界面", "主题、时钟、watched 标记、屏保", Routes.CUSTOMIZATION),
			SettingsCard(R.drawable.ic_heart, accent5, "皮肤", "LORLA / 药片 / 药片II 切换", Routes.FUSE_SETTINGS_SKIN),
			SettingsCard(R.drawable.ic_movie, accent2, "媒体库", "库显示、海报尺寸、图片类型", Routes.LIBRARIES),
			SettingsCard(R.drawable.ic_house, accent6, "首页", "首页分区、排序、左侧菜单", Routes.HOME),
			SettingsCard(R.drawable.ic_info, accent8, "关于", "版本、开源许可、LORLA 项目", Routes.ABOUT),
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
					.padding(start = 24.dp, top = 56.dp, end = 64.dp, bottom = 40.dp),
			) {
				Text(
					"设置",
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 42.sp,
						fontWeight = FontWeight.ExtraBold,
						letterSpacing = 0.5.sp,
					),
					modifier = Modifier.padding(bottom = 6.dp),
				)
				Text(
					"个性化你的 LORLA 观影体验",
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
					style = JellyfinTheme.typography.default.copy(
						fontSize = 15.sp,
						letterSpacing = 0.3.sp,
					),
					modifier = Modifier.padding(bottom = 38.dp),
				)

				Column(
					modifier = Modifier
						.weight(1f)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(22.dp),
				) {
					cards.chunked(4).forEach { rowCards ->
						Row(
							horizontalArrangement = Arrangement.spacedBy(22.dp),
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
							// Fill empty slots in the last row so layout stays balanced.
							repeat(4 - rowCards.size) {
								Box(modifier = Modifier.weight(1f))
							}
						}
					}
				}

				// Secondary tools row — kept out of the main 8 to keep the home tidy,
				// but still reachable for advanced users.
				Row(
					horizontalArrangement = Arrangement.spacedBy(14.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(top = 20.dp),
				) {
					SecondaryLink(
						icon = R.drawable.ic_grid,
						label = "左侧菜单设置",
						subtitle = "自定义 20 个分类与子行",
						onClick = { router.push(Routes.FUSE_SETTINGS_MENU) },
					)
				}
			}
		}
	}
}

@Composable
private fun SecondaryLink(
	icon: Int,
	label: String,
	subtitle: String,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(14.dp)
	val bgBrush = if (focused) {
		Brush.linearGradient(
			0.00f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.30f),
			1.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
		)
	} else {
		Brush.linearGradient(
			0.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.60f),
			1.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.40f),
		)
	}

	Row(
		modifier = Modifier
			.background(bgBrush, shape)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.80f)
					else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.10f),
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(horizontal = 18.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(icon),
			contentDescription = label,
			tint = if (focused) JellyfinTheme.colorScheme.buttonFocused
				else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
			modifier = Modifier.size(20.dp),
		)
		Spacer(modifier = Modifier.width(12.dp))
		Column {
			Text(
				label,
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 15.sp,
					fontWeight = FontWeight.SemiBold,
				),
				maxLines = 1,
			)
			Text(
				subtitle,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.50f),
				style = JellyfinTheme.typography.default.copy(fontSize = 12.sp),
				maxLines = 1,
			)
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
	val shape = RoundedCornerShape(20.dp)

	val scale by animateFloatAsState(
		targetValue = if (focused) 1.04f else 1f,
		animationSpec = androidx.compose.animation.core.tween(180),
		label = "card-scale",
	)

	val bgBrush = if (focused) {
		Brush.linearGradient(
			0.00f to card.accent.copy(alpha = 0.35f),
			0.55f to card.accent.copy(alpha = 0.18f),
			1.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
		)
	} else {
		Brush.linearGradient(
			0.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.78f),
			0.55f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
			1.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.38f),
		)
	}

	val borderColor = if (focused) {
		card.accent.copy(alpha = 0.85f)
	} else {
		JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.10f)
	}

	Column(
		modifier = modifier
			.scale(scale)
			.height(196.dp)
			.background(bgBrush, shape)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = borderColor,
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(horizontal = 22.dp, vertical = 22.dp)
			.focusGroup(),
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.Top,
		) {
			// Icon in colored circle.
			Box(
				modifier = Modifier
					.size(54.dp)
					.background(
						if (focused) card.accent.copy(alpha = 0.90f)
						else card.accent.copy(alpha = 0.22f),
						CircleShape,
					),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					imageVector = ImageVector.vectorResource(card.icon),
					contentDescription = card.title,
					tint = if (focused) JellyfinTheme.colorScheme.background
						else card.accent,
					modifier = Modifier.size(28.dp),
				)
			}
			// Chevron hint on focus.
			Box(
				modifier = Modifier
					.size(24.dp)
					.background(
						if (focused) card.accent.copy(alpha = 0.20f) else Color.Transparent,
						CircleShape,
					),
				contentAlignment = Alignment.Center,
			) {
				Text(
					"›",
					color = if (focused) card.accent else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.20f),
					style = JellyfinTheme.typography.default.copy(
						fontSize = 22.sp,
						fontWeight = FontWeight.Bold,
					),
				)
			}
		}

		Spacer(modifier = Modifier.weight(1f))

		Text(
			card.title,
			color = JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 22.sp,
				fontWeight = FontWeight.Bold,
				letterSpacing = 0.2.sp,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
		Spacer(modifier = Modifier.height(4.dp))
		Text(
			card.subtitle,
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 13.sp,
				lineHeight = 18.sp,
				letterSpacing = 0.2.sp,
			),
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
	}
}
