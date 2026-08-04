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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.form.RadioButton
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.compose.koinInject

private sealed interface SkinOption {
	val title: String

	data class Action(override val title: String) : SkinOption
	data class Value(override val title: String, val value: String) : SkinOption
	data class Radio(override val title: String, val checked: Boolean) : SkinOption
	data class Header(val label: String) : SkinOption {
		override val title: String get() = label
	}
}

private data class SkinCategory(
	val title: String,
	val options: List<SkinOption>,
)

@Composable
fun FuseSkinSettingsScreen() {
	val navigationRepository = koinInject<NavigationRepository>()

	val categories = remember {
		listOf(
			SkinCategory(
				"菜单",
				listOf(
					SkinOption.Action("重置菜单设置为默认值"),
					SkinOption.Value("主菜单类型", "垂直"),
					SkinOption.Radio("显示菜单标签", true),
				),
			),
			SkinCategory(
				"用户界面",
				listOf(
					SkinOption.Action("重置 UI 设置为默认值"),
					SkinOption.Value("主题", "深色"),
					SkinOption.Radio("启用动画", true),
				),
			),
			SkinCategory(
				"界面",
				listOf(
					SkinOption.Action("重置界面设置为默认值"),
					SkinOption.Value("字体大小", "中"),
					SkinOption.Radio("显示滚动条", false),
				),
			),
			SkinCategory(
				"导航",
				listOf(
					SkinOption.Action("重置导航设置为默认值"),
					SkinOption.Value("焦点声音", "开启"),
					SkinOption.Radio("循环导航", true),
				),
			),
			SkinCategory(
				"页面装饰",
				listOf(
					SkinOption.Action("重置页面装饰为默认值"),
					SkinOption.Value("背景艺术", "开启"),
					SkinOption.Radio("显示用户头像", true),
				),
			),
			SkinCategory(
				"行为",
				listOf(
					SkinOption.Action("重置行为设置为默认值"),
					SkinOption.Value("自动隐藏延迟", "5 秒"),
					SkinOption.Radio("启用触控模式", false),
				),
			),
			SkinCategory(
				"详细资料",
				listOf(
					SkinOption.Action("重置详细资料为默认值"),
					SkinOption.Value("信息面板位置", "左侧"),
					SkinOption.Radio("显示评级", true),
				),
			),
			SkinCategory(
				"专家",
				listOf(
					SkinOption.Header("专家"),
					SkinOption.Action("Reset skin settings to defaults"),
					SkinOption.Value("屏幕键盘尺寸", "小"),
					SkinOption.Value("Mouse pointer size", "默认"),
					SkinOption.Header("启动"),
					SkinOption.Radio("假日主题", false),
					SkinOption.Radio("在闪屏后隐藏部件初始化", false),
					SkinOption.Radio("个性化皮肤用户部件配置 (伪简介)", true),
					SkinOption.Radio("启动时的皮肤用户登录屏幕", false),
				),
			),
			SkinCategory(
				"感谢",
				listOf(
					SkinOption.Header("感谢"),
					SkinOption.Action("查看贡献者名单"),
					SkinOption.Action("开源许可"),
				),
			),
		)
	}

	var selectedIndex by remember { mutableIntStateOf(7) }

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
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(bottom = 36.dp),
				) {
					Text(
						"皮肤",
						color = JellyfinTheme.colorScheme.onBackground,
						style = JellyfinTheme.typography.default.copy(
							fontSize = 38.sp,
							fontWeight = FontWeight.Bold,
						),
					)

					Spacer(modifier = Modifier.width(12.dp))

					Text(
						"设置",
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
						style = JellyfinTheme.typography.default.copy(
							fontSize = 38.sp,
							fontWeight = FontWeight.Bold,
						),
					)
				}

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f),
					horizontalArrangement = Arrangement.spacedBy(24.dp),
				) {
					// Left category list
					Column(
						modifier = Modifier
							.width(260.dp)
							.fillMaxHeight(),
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						categories.forEachIndexed { index, category ->
							FuseSkinCategoryItem(
								label = category.title,
								selected = index == selectedIndex,
								onClick = { selectedIndex = index },
							)
						}
					}

					// Right options panel
					Column(
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
							.background(
								JellyfinTheme.colorScheme.surface.copy(alpha = 0.45f),
								RoundedCornerShape(16.dp),
							)
							.border(
								1.dp,
								JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.10f),
								RoundedCornerShape(16.dp),
							)
							.padding(horizontal = 8.dp, vertical = 12.dp)
							.verticalScroll(rememberScrollState()),
					) {
						categories[selectedIndex].options.forEach { option ->
							FuseSkinOptionItem(option = option)
						}
					}
				}

				// Footer info
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 24.dp),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column {
						Text(
							"信息",
							color = JellyfinTheme.colorScheme.onBackground,
							style = JellyfinTheme.typography.default.copy(
								fontSize = 15.sp,
								fontWeight = FontWeight.Bold,
							),
						)
						Text(
							"Kodi 21.0 (21.0.0) Git:20240406-60c4500054",
							color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
							style = JellyfinTheme.typography.default.copy(fontSize = 13.sp),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun FuseSkinCategoryItem(
	label: String,
	selected: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(12.dp)

	val backgroundBrush = when {
		selected -> Brush.horizontalGradient(
			0f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.85f),
			1f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.55f),
		)

		focused -> Brush.horizontalGradient(
			0f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.25f),
			1f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.10f),
		)

		else -> Brush.horizontalGradient(
			0f to Color.Transparent,
			1f to Color.Transparent,
		)
	}

	Box(
		contentAlignment = Alignment.CenterStart,
		modifier = modifier
			.fillMaxWidth()
			.height(52.dp)
			.background(backgroundBrush, shape)
			.border(
				width = if (focused && !selected) 1.dp else 0.dp,
				color = JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.5f),
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(horizontal = 18.dp)
			.focusGroup(),
	) {
		Text(
			label,
			color = when {
				selected -> JellyfinTheme.colorScheme.onButtonFocused
				focused -> JellyfinTheme.colorScheme.buttonFocused
				else -> JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.85f)
			},
			style = JellyfinTheme.typography.default.copy(
				fontSize = 17.sp,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun FuseSkinOptionItem(
	option: SkinOption,
	modifier: Modifier = Modifier,
) {
	when (option) {
		is SkinOption.Header -> {
			Text(
				option.label,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.45f),
				style = JellyfinTheme.typography.default.copy(
					fontSize = 12.sp,
					fontWeight = FontWeight.Bold,
				),
				modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
			)
		}

		is SkinOption.Action -> FuseSkinOptionRow(
			title = option.title,
			modifier = modifier,
		)

		is SkinOption.Value -> FuseSkinOptionRow(
			title = option.title,
			trailing = {
				Text(
					option.value,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
					style = JellyfinTheme.typography.default.copy(fontSize = 15.sp),
				)
			},
			modifier = modifier,
		)

		is SkinOption.Radio -> FuseSkinOptionRow(
			title = option.title,
			trailing = {
				RadioButton(
					checked = option.checked,
					containerColor = JellyfinTheme.colorScheme.buttonFocused,
					contentColor = JellyfinTheme.colorScheme.onButtonFocused,
				)
			},
			modifier = modifier,
		)
	}
}

@Composable
private fun FuseSkinOptionRow(
	title: String,
	modifier: Modifier = Modifier,
	trailing: @Composable (() -> Unit)? = null,
) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(10.dp)

	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
		modifier = modifier
			.fillMaxWidth()
			.height(56.dp)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.15f) else Color.Transparent,
				shape,
			)
			.border(
				width = if (focused) 1.dp else 0.dp,
				color = JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.4f),
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = {})
			.padding(horizontal = 16.dp)
			.focusGroup(),
	) {
		Text(
			title,
			color = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(fontSize = 16.sp),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.weight(1f),
		)

		trailing?.invoke()
	}
}
