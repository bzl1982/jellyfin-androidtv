package org.jellyfin.androidtv.ui.player.video

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.IconButton
import org.jellyfin.androidtv.ui.base.button.IconButtonDefaults
import org.jellyfin.androidtv.ui.base.SeekbarDefaults
import org.jellyfin.androidtv.ui.composable.rememberPlayerPositionInfo
import org.jellyfin.androidtv.ui.player.base.PlayerSeekbar
import org.jellyfin.playback.core.PlaybackManager
import org.jellyfin.playback.core.model.PlayState
import org.jellyfin.playback.core.queue.queue
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Composable
fun VideoPlayerControls(
	playbackManager: PlaybackManager = koinInject(),
	onPlaybackInfoClick: () -> Unit = {},
) {
	val playState by playbackManager.state.playState.collectAsState()
	val accent = JellyfinTheme.colorScheme.rangeControlFill

	Column(
		verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 10.dp)
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(16.dp),
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.focusRestorer()
				.focusGroup()
				.padding(horizontal = 36.dp)
		) {
			PlayPauseButton(playbackManager, playState)
			RewindButton(playbackManager)
			FastForwardButton(playbackManager)

			Spacer(Modifier.weight(1f))

			PreviousEntryButton(playbackManager)
			NextEntryButton(playbackManager)
			PlaybackInfoButton(onClick = onPlaybackInfoClick)
		}

		PlayerSeekbar(
			playbackManager = playbackManager,
			colors = SeekbarDefaults.colors(
				progressColor = accent,
				knobColor = accent,
			),
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 36.dp)
				.height(5.dp)
		)

		Row(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 36.dp, vertical = 4.dp)
		) {
			PositionText(playbackManager, accent)
		}
	}
}

/**
 * FUSE-style OSD button colors:
 * - container stays fully transparent (no focus box, unlike the default ButtonBase)
 * - unfocused icon = dimmed white (FUSE `panel_fg_70`)
 * - focused icon = re-colored to the skin accent (Netflix red / Infuse orange)
 *   (Icon tints automatically from the button's content color via LocalTextStyle)
 */
@Composable
private fun osdButtonColors() = IconButtonDefaults.colors(
	containerColor = Color.Transparent,
	contentColor = Color.White.copy(alpha = 0.6f),
	focusedContainerColor = Color.Transparent,
	focusedContentColor = JellyfinTheme.colorScheme.rangeControlFill,
	disabledContainerColor = Color.Transparent,
	disabledContentColor = Color.White.copy(alpha = 0.25f),
)

@Composable
private fun PlayPauseButton(
	playbackManager: PlaybackManager,
	playState: PlayState,
) {
	val focusRequester = remember { FocusRequester() }
	IconButton(
		onClick = {
			when (playState) {
				PlayState.STOPPED,
				PlayState.ERROR -> playbackManager.state.play()

				PlayState.PLAYING -> playbackManager.state.pause()
				PlayState.PAUSED -> playbackManager.state.unpause()
			}
		},
		colors = osdButtonColors(),
		modifier = Modifier
			.focusRequester(focusRequester)
			.onVisibilityChanged {
				focusRequester.requestFocus()
			}
	) {
		AnimatedContent(playState) { playState ->
			when (playState) {
				PlayState.PLAYING -> {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_pause),
						contentDescription = stringResource(R.string.lbl_pause),
						modifier = Modifier.size(30.dp)
					)
				}

				PlayState.STOPPED,
				PlayState.PAUSED,
				PlayState.ERROR -> {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.ic_play),
						contentDescription = stringResource(R.string.lbl_play),
						modifier = Modifier.size(30.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun RewindButton(
	playbackManager: PlaybackManager,
) = IconButton(
	onClick = { playbackManager.state.rewind() },
	colors = osdButtonColors(),
) {
	Icon(
		imageVector = ImageVector.vectorResource(R.drawable.ic_rewind),
		contentDescription = stringResource(R.string.rewind),
		modifier = Modifier.size(30.dp)
	)
}

@Composable
private fun FastForwardButton(
	playbackManager: PlaybackManager,
) = IconButton(
	onClick = { playbackManager.state.fastForward() },
	colors = osdButtonColors(),
) {
	Icon(
		imageVector = ImageVector.vectorResource(R.drawable.ic_fast_forward),
		contentDescription = stringResource(R.string.fast_forward),
		modifier = Modifier.size(30.dp)
	)
}

@Composable
private fun PreviousEntryButton(
	playbackManager: PlaybackManager,
) {
	val entryIndex by playbackManager.queue.entryIndex.collectAsState()
	val coroutineScope = rememberCoroutineScope()

	IconButton(
		enabled = entryIndex > 0,
		onClick = {
			coroutineScope.launch {
				playbackManager.queue.previous()
			}
		},
		colors = osdButtonColors(),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_previous),
			contentDescription = stringResource(R.string.lbl_prev_item),
			modifier = Modifier.size(30.dp)
		)
	}
}

@Composable
private fun NextEntryButton(
	playbackManager: PlaybackManager,
) {
	val entryIndex by playbackManager.queue.entryIndex.collectAsState()
	val coroutineScope = rememberCoroutineScope()

	IconButton(
		enabled = entryIndex < playbackManager.queue.estimatedSize - 1,
		onClick = {
			coroutineScope.launch {
				playbackManager.queue.next()
			}
		},
		colors = osdButtonColors(),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_next),
			contentDescription = stringResource(R.string.lbl_next_item),
			modifier = Modifier.size(30.dp)
		)
	}
}

private fun Duration.formatted(includeHours: Boolean): String {
	val totalSeconds = toInt(DurationUnit.SECONDS)
	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60

	return if (includeHours) "%02d:%02d:%02d".format(hours, minutes, seconds)
	else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun PositionText(
	playbackManager: PlaybackManager,
	accent: Color,
) {
	val positionInfo by rememberPlayerPositionInfo(playbackManager, precision = 1.seconds)
	if (positionInfo.duration == Duration.ZERO) return

	val formatted by remember {
		derivedStateOf {
			val includeHours = positionInfo.duration.inWholeMinutes >= 60
			positionInfo.active.formatted(includeHours) to positionInfo.duration.formatted(includeHours)
		}
	}
	val activeFormatted = formatted.first
	val durationFormatted = formatted.second

	Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		Text(
			text = activeFormatted,
			style = LocalTextStyle.current.copy(color = accent)
		)
		Text(
			text = "/",
			style = LocalTextStyle.current.copy(color = Color.White.copy(alpha = 0.4f))
		)
		Text(
			text = durationFormatted,
			style = LocalTextStyle.current.copy(color = Color.White.copy(alpha = 0.7f))
		)
	}
}

@Composable
fun PlaybackInfoButton(
	onClick: () -> Unit,
) = IconButton(
	onClick = onClick,
	colors = osdButtonColors(),
) {
	Icon(
		imageVector = ImageVector.vectorResource(R.drawable.ic_info),
		contentDescription = stringResource(R.string.playback_info),
		modifier = Modifier.size(30.dp)
	)
}
