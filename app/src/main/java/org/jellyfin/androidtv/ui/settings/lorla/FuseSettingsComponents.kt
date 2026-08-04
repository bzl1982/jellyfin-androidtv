package org.jellyfin.androidtv.ui.settings.lorla

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text

@Composable
fun FuseSettingsEdgeSidebar(
	onClose: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier
			.width(80.dp)
			.fillMaxHeight()
			.padding(vertical = 48.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.Top),
	) {
		FuseEdgeButton(
			icon = R.drawable.ic_logout,
			label = "关闭",
			onClick = onClose,
		)

		FuseEdgeButton(
			icon = R.drawable.ic_grid,
			label = "菜单",
			onClick = {},
		)
	}
}

@Composable
private fun FuseEdgeButton(
	icon: Int,
	label: String,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
		modifier = modifier
			.width(64.dp)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(vertical = 8.dp)
			.focusGroup(),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(icon),
			contentDescription = label,
			tint = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			modifier = Modifier.size(24.dp),
		)

		Text(
			label,
			color = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			style = JellyfinTheme.typography.default.copy(fontSize = 11.sp),
			maxLines = 1,
		)
	}
}
