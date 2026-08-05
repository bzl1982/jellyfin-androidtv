package org.jellyfin.androidtv.ui.settings.lorla

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focus.focusRestorer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.design.Tokens

/**
 * FUSE 风格设置组件包（统一 + 简单）。
 *
 * 设计基调（与首页 8 卡一致，避免 Kodi 式复杂）：
 *  - 单列清晰层级：标题 → 分区头 → 设置行，绝不嵌套标签页/折叠树。
 *  - 统一焦点语言：缩放 1.03 + 2dp 强调色描边 + 轻微高亮渐变（和首页卡片同款）。
 *  - 颜色全部取自 JellyfinTheme.colorScheme，不硬编码新色。
 */

private val FuseRowShape = RoundedCornerShape(14.dp)
private val FuseTrackShape = RoundedCornerShape(12.dp)
private val FuseIconSize = 22.dp
private val FuseIconCircle = 40.dp

/**
 * 统一的"焦点容器"：所有可点击设置项共用同一套焦点视觉，保证整包风格一致。
 * 焦点时 = 轻微放大 + 强调色描边 + 高亮渐变；非焦点 = 低存在感表面。
 */
@Composable
private fun FuseFocusContainer(
	onClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
	content: @Composable BoxScope.() -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(
		targetValue = if (focused) 1.03f else 1f,
		animationSpec = tween(160),
		label = "fuse-row-scale",
	)

	Box(
		modifier = modifier
			.scale(scale)
			.background(
				brush = if (focused) {
					Brush.linearGradient(
						0.00f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.22f),
						1.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.50f),
					)
				} else {
					Brush.linearGradient(
						0.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
						1.00f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.35f),
					)
				},
				shape = FuseRowShape,
			)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) {
					JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.85f)
				} else {
					JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.08f)
				},
				shape = FuseRowShape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
			.focusGroup()
			.padding(horizontal = 18.dp, vertical = 14.dp),
		content = content,
	)
}

/** 左侧图标圆：焦点时实心强调色 + 反白图标（与首页卡片同语言）。 */
@Composable
private fun FuseLeadingIcon(icon: Int, focused: Boolean) {
	Box(
		modifier = Modifier
			.size(FuseIconCircle)
			.background(
				color = if (focused) {
					JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.90f)
				} else {
					JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.18f)
				},
				shape = CircleShape,
			),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			painter = painterResource(icon),
			contentDescription = null,
			tint = if (focused) {
				JellyfinTheme.colorScheme.background
			} else {
				JellyfinTheme.colorScheme.buttonFocused
			},
			modifier = Modifier.size(FuseIconSize),
		)
	}
}

/**
 * 设置页外壳：整屏背景 + 顶部标题/副标题 + 可滚动单列内容。
 * 直接作为每个设置页的根，替代零散的 SettingsLayout/SettingsColumn 用法，保证一致性。
 */
@Composable
fun FuseSettingsScaffold(
	title: String,
	subtitle: String? = null,
	modifier: Modifier = Modifier,
	content: @Composable ColumnScope.() -> Unit,
) {
	Box(
		modifier = modifier
			.fillMaxSize()
			.background(JellyfinTheme.colorScheme.background),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = 64.dp, top = 48.dp, end = 64.dp, bottom = 32.dp)
				.verticalScroll(rememberScrollState())
				.focusRestorer(),
		) {
			Text(
				text = title,
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 34.sp,
					fontWeight = FontWeight.ExtraBold,
					letterSpacing = 0.5.sp,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (subtitle != null) {
				Text(
					text = subtitle,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
					style = JellyfinTheme.typography.default.copy(
						fontSize = 14.sp,
						letterSpacing = 0.3.sp,
					),
					modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
			} else {
				Spacer(Modifier.height(24.dp))
			}

			Column(
				verticalArrangement = Arrangement.spacedBy(Tokens.Space.spaceXs),
				content = content,
			)
		}
	}
}

/** 分区标题：大写 + 强调色，给长列表分块，但不搞折叠/标签页那套复杂东西。 */
@Composable
fun FuseSectionHeader(
	text: String,
	modifier: Modifier = Modifier,
) {
	Text(
		text = text.uppercase(),
		color = JellyfinTheme.colorScheme.buttonFocused,
		style = JellyfinTheme.typography.default.copy(
			fontSize = 13.sp,
			fontWeight = FontWeight.Bold,
			letterSpacing = 1.sp,
		),
		modifier = modifier
			.padding(top = Tokens.Space.spaceLg, bottom = Tokens.Space.space2xs, start = Tokens.Space.spaceSm),
		maxLines = 1,
	)
}

/**
 * 主设置行：左图标 + 主标题（+可选副标题）+ 右侧值/箭头。
 * 点击进入下一级（onClick 非空时显示箭头；传了 value 则显示值不再显示箭头）。
 */
@Composable
fun FuseSettingRow(
	title: String,
	modifier: Modifier = Modifier,
	icon: Int? = null,
	subtitle: String? = null,
	value: String? = null,
	onClick: (() -> Unit)? = null,
) {
	val showChevron = onClick != null && value == null

	FuseFocusContainer(
		onClick = onClick,
		modifier = modifier.fillMaxWidth(),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth(),
		) {
			if (icon != null) {
				FuseLeadingIcon(icon = icon, focused = focused)
				Spacer(Modifier.width(Tokens.Space.spaceMd))
			}

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 16.sp,
						fontWeight = FontWeight.SemiBold,
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (subtitle != null) {
					Spacer(Modifier.height(2.dp))
					Text(
						text = subtitle,
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
						style = JellyfinTheme.typography.default.copy(
							fontSize = 12.sp,
							lineHeight = 16.sp,
						),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			if (value != null) {
				Spacer(Modifier.width(Tokens.Space.spaceMd))
				Text(
					text = value,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
					style = JellyfinTheme.typography.default.copy(
						fontSize = 14.sp,
						fontWeight = FontWeight.Medium,
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}

			if (showChevron) {
				Spacer(Modifier.width(Tokens.Space.spaceMd))
				Text(
					text = "›",
					color = JellyfinTheme.colorScheme.buttonFocused,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 22.sp,
						fontWeight = FontWeight.Bold,
					),
				)
			}
		}
	}
}

/** 开关行：点击整行切换，右侧显示统一样式的开关。 */
@Composable
fun FuseSwitchRow(
	title: String,
	modifier: Modifier = Modifier,
	icon: Int? = null,
	subtitle: String? = null,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	FuseFocusContainer(
		onClick = { onCheckedChange(!checked) },
		modifier = modifier.fillMaxWidth(),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth(),
		) {
			if (icon != null) {
				FuseLeadingIcon(icon = icon, focused = false)
				Spacer(Modifier.width(Tokens.Space.spaceMd))
			}

			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 16.sp,
						fontWeight = FontWeight.SemiBold,
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (subtitle != null) {
					Spacer(Modifier.height(2.dp))
					Text(
						text = subtitle,
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
						style = JellyfinTheme.typography.default.copy(
							fontSize = 12.sp,
							lineHeight = 16.sp,
						),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Spacer(Modifier.width(Tokens.Space.spaceMd))
			FuseSwitch(checked = checked)
		}
	}
}

/** 单选行：用于"选项列表"（如主题、码率）。选中显示实心圆点（强调色）。 */
@Composable
fun FuseRadioOption(
	title: String,
	modifier: Modifier = Modifier,
	subtitle: String? = null,
	selected: Boolean,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	FuseFocusContainer(
		onClick = onClick,
		modifier = modifier.fillMaxWidth(),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = title,
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 16.sp,
						fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
				if (subtitle != null) {
					Spacer(Modifier.height(2.dp))
					Text(
						text = subtitle,
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
						style = JellyfinTheme.typography.default.copy(
							fontSize = 12.sp,
							lineHeight = 16.sp,
						),
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Spacer(Modifier.width(Tokens.Space.spaceMd))
			// 单选指示点：选中=实心强调色，否则空心描边。
			Box(
				modifier = Modifier
					.size(20.dp)
					.background(
						color = if (selected) {
							JellyfinTheme.colorScheme.buttonFocused
						} else {
							Color.Transparent
						},
						shape = CircleShape,
					)
					.border(
						width = 2.dp,
						color = JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.7f),
						shape = CircleShape,
					),
				contentAlignment = Alignment.Center,
			) {
				if (selected) {
					Box(
						modifier = Modifier
							.size(8.dp)
							.background(JellyfinTheme.colorScheme.background, CircleShape),
					)
				}
			}
		}
	}
}

/** 统一开关视觉：轨道 + 滑块，开=强调色，关=低透明度表面。 */
@Composable
private fun FuseSwitch(checked: Boolean) {
	val maxOffset = 20.dp
	val offset by animateDpAsState(
		targetValue = if (checked) maxOffset else 0.dp,
		animationSpec = tween(180),
		label = "fuse-switch-knob",
	)

	Box(
		modifier = Modifier
			.size(width = 44.dp, height = 24.dp)
			.background(
				color = if (checked) {
					JellyfinTheme.colorScheme.buttonFocused
				} else {
					JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.22f)
				},
				shape = FuseTrackShape,
			)
			.padding(3.dp),
	) {
		Box(
			modifier = Modifier
				.offset { IntOffset(x = offset.roundToPx(), y = 0) }
				.size(18.dp)
				.background(JellyfinTheme.colorScheme.background, CircleShape),
		)
	}
}

// 预置图标资源（与现有设置页保持一致，方便直接替换）。
object FuseIcons {
	val users = R.drawable.ic_users
	val adjust = R.drawable.ic_adjust
	val photos = R.drawable.ic_photos
	val next = R.drawable.ic_next
	val telemetry = R.drawable.ic_error
	val about = R.drawable.ic_jellyfin
	val play = R.drawable.ic_play
	val subtitles = R.drawable.ic_subtitles
	val masks = R.drawable.ic_masks
	val heart = R.drawable.ic_heart
	val movie = R.drawable.ic_movie
	val house = R.drawable.ic_house
	val info = R.drawable.ic_info
	val grid = R.drawable.ic_grid
}
