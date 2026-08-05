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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.home.CategoryMenuConfig
import org.jellyfin.androidtv.ui.home.LorlaMenuConfig
import org.jellyfin.androidtv.ui.home.MenuConfigurationStore
import org.jellyfin.androidtv.ui.home.SubRowConfig
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.compose.koinInject

@Composable
fun LorlaMenuEditorScreen() {
	val context = LocalContext.current
	val store = remember { MenuConfigurationStore(context) }
	val navigationRepository = koinInject<NavigationRepository>()

	var config by remember { mutableStateOf(store.load()) }
	var showResetConfirm by remember { mutableStateOf(false) }

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
					modifier = Modifier.padding(bottom = 24.dp),
				) {
					Text(
						"菜单设置",
						color = JellyfinTheme.colorScheme.onBackground,
						style = JellyfinTheme.typography.default.copy(
							fontSize = 38.sp,
							fontWeight = FontWeight.Bold,
						),
					)

					Spacer(modifier = Modifier.width(24.dp))

					EditorButton(
						label = "恢复默认",
						onClick = { showResetConfirm = true },
					)
				}

				Column(
					modifier = Modifier
						.weight(1f)
						.verticalScroll(rememberScrollState()),
					verticalArrangement = Arrangement.spacedBy(14.dp),
				) {
					config.categories.sortedBy { it.order }.forEachIndexed { index, cat ->
						CategoryEditorCard(
							cat = cat,
							canMoveUp = index > 0,
							canMoveDown = index < config.categories.size - 1,
							onUpdate = { updated ->
								config = config.copy(
									categories = config.categories.map { if (it.id == updated.id) updated else it }
								)
								store.save(config)
							},
							onMoveUp = {
								config = config.copy(categories = swapOrder(config.categories, cat.id, -1))
								store.save(config)
							},
							onMoveDown = {
								config = config.copy(categories = swapOrder(config.categories, cat.id, 1))
								store.save(config)
							},
						)
					}
				}
			}
		}
	}

	if (showResetConfirm) {
		ConfirmDialog(
			title = "恢复默认菜单？",
			message = "所有自定义菜单名称、顺序和子分类设置将被重置。",
			onConfirm = {
				store.reset()
				config = store.load()
				showResetConfirm = false
			},
			onDismiss = { showResetConfirm = false },
		)
	}
}

@Composable
private fun CategoryEditorCard(
	cat: CategoryMenuConfig,
	canMoveUp: Boolean,
	canMoveDown: Boolean,
	onUpdate: (CategoryMenuConfig) -> Unit,
	onMoveUp: () -> Unit,
	onMoveDown: () -> Unit,
) {
	var expanded by remember { mutableStateOf(false) }
	var editingLabel by remember { mutableStateOf<String?>(null) }

	Column(
		modifier = Modifier
			.fillMaxWidth()
			.background(
				JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
				RoundedCornerShape(16.dp),
			)
			.border(
				1.dp,
				JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.10f),
				RoundedCornerShape(16.dp),
			)
			.padding(18.dp),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth(),
		) {
			Text(
				cat.label,
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 20.sp,
					fontWeight = FontWeight.Bold,
				),
				modifier = Modifier.weight(1f),
			)

			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				EditorButton(label = "重命名", onClick = { editingLabel = cat.label })
				EditorButton(label = if (cat.visible) "隐藏" else "显示", onClick = {
					onUpdate(cat.copy(visible = !cat.visible))
				})
				EditorButton(label = "▲", enabled = canMoveUp, onClick = onMoveUp)
				EditorButton(label = "▼", enabled = canMoveDown, onClick = onMoveDown)
				EditorButton(label = if (expanded) "收起" else "子分类", onClick = { expanded = !expanded })
			}
		}

		if (expanded) {
			Spacer(modifier = Modifier.height(14.dp))
			cat.subRows.sortedBy { it.order }.forEachIndexed { index, sub ->
				SubRowEditorRow(
					sub = sub,
					canMoveUp = index > 0,
					canMoveDown = index < cat.subRows.size - 1,
					onUpdate = { updated ->
						onUpdate(cat.copy(
							subRows = cat.subRows.map { if (it.id == updated.id) updated else it }
						))
					},
					onMoveUp = {
						onUpdate(cat.copy(subRows = swapSubOrder(cat.subRows, sub.id, -1)))
					},
					onMoveDown = {
						onUpdate(cat.copy(subRows = swapSubOrder(cat.subRows, sub.id, 1)))
					},
				)
			}
		}
	}

	if (editingLabel != null) {
		RenameDialog(
			initial = editingLabel!!,
			onConfirm = { newLabel ->
				onUpdate(cat.copy(label = newLabel))
				editingLabel = null
			},
			onDismiss = { editingLabel = null },
		)
	}
}

@Composable
private fun SubRowEditorRow(
	sub: SubRowConfig,
	canMoveUp: Boolean,
	canMoveDown: Boolean,
	onUpdate: (SubRowConfig) -> Unit,
	onMoveUp: () -> Unit,
	onMoveDown: () -> Unit,
) {
	var editingLabel by remember { mutableStateOf<String?>(null) }

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
	) {
		Text(
			sub.label,
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.85f),
			style = JellyfinTheme.typography.default.copy(fontSize = 17.sp),
			modifier = Modifier.weight(1f),
		)

		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			EditorButton(label = "重命名", onClick = { editingLabel = sub.label })
			EditorButton(label = if (sub.visible) "隐藏" else "显示", onClick = {
				onUpdate(sub.copy(visible = !sub.visible))
			})
			EditorButton(label = "▲", enabled = canMoveUp, onClick = onMoveUp)
			EditorButton(label = "▼", enabled = canMoveDown, onClick = onMoveDown)
		}
	}

	if (editingLabel != null) {
		RenameDialog(
			initial = editingLabel!!,
			onConfirm = { newLabel ->
				onUpdate(sub.copy(label = newLabel))
				editingLabel = null
			},
			onDismiss = { editingLabel = null },
		)
	}
}

@Composable
private fun EditorButton(
	label: String,
	onClick: () -> Unit,
	enabled: Boolean = true,
) {
	var focused by remember { mutableStateOf(false) }
	val alpha = if (enabled) 1f else 0.35f

	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.height(36.dp)
			.background(
				if (focused && enabled) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.85f)
				else JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
				RoundedCornerShape(8.dp),
			)
			.border(
				1.dp,
				JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.12f),
				RoundedCornerShape(8.dp),
			)
			.onFocusChanged { if (enabled) focused = it.hasFocus }
			.then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
			.padding(horizontal = 14.dp)
			.focusGroup(),
	) {
		Text(
			label,
			color = if (focused && enabled) JellyfinTheme.colorScheme.onButtonFocused
			else JellyfinTheme.colorScheme.onBackground.copy(alpha = alpha),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 14.sp,
				fontWeight = FontWeight.Bold,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun RenameDialog(
	initial: String,
	onConfirm: (String) -> Unit,
	onDismiss: () -> Unit,
) {
	var text by remember { mutableStateOf(initial) }

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.65f))
			.clickable(onClick = onDismiss),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier
				.width(520.dp)
				.background(
					JellyfinTheme.colorScheme.surface.copy(alpha = 0.95f),
					RoundedCornerShape(20.dp),
				)
				.border(
					1.dp,
					JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.15f),
					RoundedCornerShape(20.dp),
				)
				.padding(28.dp),
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			Text(
				"修改名称",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 22.sp,
					fontWeight = FontWeight.Bold,
				),
			)

			BasicTextField(
				value = text,
				onValueChange = { text = it },
				singleLine = true,
				cursorBrush = SolidColor(JellyfinTheme.colorScheme.buttonFocused),
				textStyle = JellyfinTheme.typography.default.copy(
					color = JellyfinTheme.colorScheme.onBackground,
					fontSize = 18.sp,
				),
				modifier = Modifier
					.fillMaxWidth()
					.background(
						JellyfinTheme.colorScheme.background.copy(alpha = 0.60f),
						RoundedCornerShape(10.dp),
					)
					.padding(horizontal = 16.dp, vertical = 14.dp),
			)

			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
				modifier = Modifier.fillMaxWidth(),
			) {
				EditorButton(label = "取消", onClick = onDismiss)
				EditorButton(label = "确定", onClick = { onConfirm(text) })
			}
		}
	}
}

@Composable
private fun ConfirmDialog(
	title: String,
	message: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black.copy(alpha = 0.65f))
			.clickable(onClick = onDismiss),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier
				.width(520.dp)
				.background(
					JellyfinTheme.colorScheme.surface.copy(alpha = 0.95f),
					RoundedCornerShape(20.dp),
				)
				.border(
					1.dp,
					JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.15f),
					RoundedCornerShape(20.dp),
				)
				.padding(28.dp),
			verticalArrangement = Arrangement.spacedBy(18.dp),
		) {
			Text(
				title,
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 22.sp,
					fontWeight = FontWeight.Bold,
				),
			)
			Text(
				message,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
				style = JellyfinTheme.typography.default.copy(fontSize = 16.sp),
			)
			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
				modifier = Modifier.fillMaxWidth(),
			) {
				EditorButton(label = "取消", onClick = onDismiss)
				EditorButton(label = "确定", onClick = onConfirm)
			}
		}
	}
}

private fun swapOrder(list: List<CategoryMenuConfig>, id: String, direction: Int): List<CategoryMenuConfig> {
	val sorted = list.sortedBy { it.order }.toMutableList()
	val index = sorted.indexOfFirst { it.id == id }
	if (index < 0) return list
	val target = index + direction
	if (target < 0 || target >= sorted.size) return list
	val currentOrder = sorted[index].order
	val targetOrder = sorted[target].order
	sorted[index] = sorted[index].copy(order = targetOrder)
	sorted[target] = sorted[target].copy(order = currentOrder)
	return sorted
}

private fun swapSubOrder(list: List<SubRowConfig>, id: String, direction: Int): List<SubRowConfig> {
	val sorted = list.sortedBy { it.order }.toMutableList()
	val index = sorted.indexOfFirst { it.id == id }
	if (index < 0) return list
	val target = index + direction
	if (target < 0 || target >= sorted.size) return list
	val currentOrder = sorted[index].order
	val targetOrder = sorted[target].order
	sorted[index] = sorted[index].copy(order = targetOrder)
	sorted[target] = sorted[target].copy(order = currentOrder)
	return sorted
}
