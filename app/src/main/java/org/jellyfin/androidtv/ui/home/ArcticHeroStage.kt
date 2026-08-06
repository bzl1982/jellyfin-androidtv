package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.preference.HeroLayoutMode
import org.jellyfin.androidtv.ui.base.FuseColors
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ItemFields
import org.koin.compose.koinInject

/**
 * The Arctic Fuse 3 "Spotlight" big stage, rebuilt from scratch.
 *
 * Three things this rewrite fixes compared to the old HeroStage:
 *
 *  1. It can no longer disappear. The previous version let the remote cycle the
 *     layout preference, and one of those six values is NO_STAGE - one stray
 *     key sequence permanently wrote "no stage" into SharedPreferences and the
 *     stage was gone for good. Layout is now read-only here; it is chosen in
 *     Settings -> Home, exactly like FUSE handles SkinSettings.
 *  2. Soft transitions. The artwork is feathered with the very curves sampled
 *     from the skin's flixart / combined_flixart masks, so nothing ends on a
 *     visible line - not on the left, not where the stage meets the rows.
 *  3. Lighting. FUSE slowly pushes the fanart (Background_Main_Zoomer,
 *     zoom 100 -> 150) and fades its overlays as focus moves. Here the same
 *     idea is driven by the remote: the backdrop breathes when the stage holds
 *     focus and a soft key light slides horizontally to whichever control is
 *     selected.
 */
@Composable
	fun ArcticHeroStage(
	modifier: Modifier = Modifier,
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	layoutMode: HeroLayoutMode,
	themeColor: Color,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onDown: () -> Boolean,
	onLeftEdge: () -> Unit,
	playFocus: FocusRequester,
	titleFocus: FocusRequester,
	onTitleClick: () -> Unit,
	scrollState: ScrollState,
) {
	if (layoutMode == HeroLayoutMode.NO_STAGE) return

	val scope = rememberCoroutineScope()
	var stageFocused by remember { mutableStateOf(false) }

	// The "key light": 0f = far left of the stage, 1f = far right. Every control
	// reports its own horizontal anchor when it takes focus, so the glow travels
	// with the remote instead of sitting in one place.
	var lightAnchor by remember { mutableFloatStateOf(0.22f) }
	val light by animateFloatAsState(lightAnchor, tween(450), label = "hero-light")
	val glow by animateFloatAsState(if (stageFocused) 1f else 0.35f, tween(420), label = "hero-glow")
	// Background_Main_Zoomer equivalent - a very slow breath, never a jump cut.
	val artScale by animateFloatAsState(if (stageFocused) 1.05f else 1f, tween(1_100), label = "hero-zoom")

	Box(
		modifier = modifier.onFocusChanged { state ->
			stageFocused = state.hasFocus
			// Returning to the stage (UP from the first row) glides the page back
			// to the top so the whole stage is visible again.
			if (state.hasFocus) scope.launch { runCatching { scrollState.animateScrollTo(0) } }
		},
	) {
		HeroArtLayer(
			item = item,
			scale = artScale,
			lightX = light,
			glow = glow,
			themeColor = themeColor,
			// Showcase dims harder so the cast collage reads on top of the art.
			extraDim = if (layoutMode == HeroLayoutMode.SHOWCASE_COLLAGE) 0.22f else 0f,
			// The mini strip is short: a full-height feather would erase the art.
			featherBottom = if (layoutMode == HeroLayoutMode.MINI_STAGE) 0.55f else 1f,
		)

		when (layoutMode) {
			HeroLayoutMode.FULL_BLEED_LEFT_INFO,
			HeroLayoutMode.SLIDE_STAGE,
			-> 			HeroStandard(
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				compact = layoutMode == HeroLayoutMode.SLIDE_STAGE,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onDown = onDown,
				onLeftEdge = onLeftEdge,
				onLight = { lightAnchor = it },
				playFocus = playFocus,
				titleFocus = titleFocus,
				onTitleClick = onTitleClick,
			)

			HeroLayoutMode.SHOWCASE_COLLAGE -> HeroShowcase(
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onDown = onDown,
				onLeftEdge = onLeftEdge,
				onLight = { lightAnchor = it },
				playFocus = playFocus,
				titleFocus = titleFocus,
				onTitleClick = onTitleClick,
			)

			HeroLayoutMode.FANART_ONLY -> HeroFanart(
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onDown = onDown,
				onLeftEdge = onLeftEdge,
				onLight = { lightAnchor = it },
				playFocus = playFocus,
				titleFocus = titleFocus,
				onTitleClick = onTitleClick,
			)

			HeroLayoutMode.MINI_STAGE -> HeroMini(
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onDown = onDown,
				onLeftEdge = onLeftEdge,
				onLight = { lightAnchor = it },
				playFocus = playFocus,
				titleFocus = titleFocus,
				onTitleClick = onTitleClick,
			)

			HeroLayoutMode.NO_STAGE -> Unit
		}
	}
}

// region Artwork + lighting

@Composable
private fun HeroArtLayer(
	item: BaseItemDto?,
	scale: Float,
	lightX: Float,
	glow: Float,
	themeColor: Color,
	extraDim: Float,
	featherBottom: Float,
) {
	val api = koinInject<ApiClient>()
	val background = JellyfinTheme.colorScheme.background
	val backdrop: JellyfinImage? = item?.itemBackdropImages?.firstOrNull()
		?: item?.itemImages?.values?.firstOrNull()

	// 大舞台底色用当前海报主题色，使整页随选中项染色（FUSE 主题色行为）。
	Box(Modifier.fillMaxSize().background(themeColor.copy(alpha = 0.85f))) {
		if (backdrop != null) {
			AsyncImage(
				url = backdrop.getUrl(api, maxWidth = 1920),
				blurHash = backdrop.blurHash,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier
					.fillMaxSize()
					.graphicsLayer {
						scaleX = scale
						scaleY = scale
					},
			)
		}

		// 0. 主题色整体渐变蒙版：大舞台随选中海报主题色变化。
		Box(Modifier.fillMaxSize().background(
			Brush.verticalGradient(
				0.00f to themeColor.copy(alpha = 0.32f),
				0.55f to themeColor.copy(alpha = 0.12f),
				1.00f to Color.Transparent,
			),
		))
		// 左侧详情蒙版渐变：左侧加蒙版，保证片名/简介可读且呼应主题色。
		Box(Modifier.fillMaxSize().background(
			Brush.horizontalGradient(
				0.00f to themeColor.copy(alpha = 0.55f),
				0.35f to themeColor.copy(alpha = 0.22f),
				0.62f to Color.Transparent,
			),
		))

		// 1. flixart feather - the artwork itself dissolves into the page.
		Box(Modifier.fillMaxSize().background(FuseColors.artFeatherLeft(background, 0.90f)))
		Box(Modifier.fillMaxSize().background(FuseColors.artFeatherBottom(background, featherBottom)))

		// 2. combined_flixart vignette - contrast for text and controls, no band.
		Box(Modifier.fillMaxSize().background(FuseColors.vignetteLeft(0.58f)))
		Box(Modifier.fillMaxSize().background(FuseColors.vignetteBottom(0.55f)))

		// 3. the key light, tracking whichever control the remote is on.
		Box(
			Modifier
				.fillMaxSize()
				.drawBehind {
					drawRect(
						Brush.radialGradient(
							0.00f to Color.White.copy(alpha = 0.105f * glow),
							0.40f to Color.White.copy(alpha = 0.050f * glow),
							1.00f to Color.Transparent,
							center = Offset(size.width * lightX, size.height * 0.66f),
							radius = size.minDimension * 1.20f,
						),
					)
				},
		)

		if (extraDim > 0f) {
			Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = extraDim)))
		}
	}
}

// endregion

// region Variant 1 + 6 : Standard / Slide

@Composable
private fun HeroStandard(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	compact: Boolean,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onDown: () -> Boolean,
	onLeftEdge: () -> Unit,
	onLight: (Float) -> Unit,
	playFocus: FocusRequester,
	titleFocus: FocusRequester,
	onTitleClick: () -> Unit,
) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(
				start = 40.dp,
				// 文字整体上移：顶部 padding 由 110.dp → 56.dp，让标题与简介
				// 不再被压到屏幕底部 (背景图下半区)。同时去掉了原先撑到底部的
				// `Arrangement.Bottom + Spacer(weight(1f))` 结构——内容由顶部向下
				// 自然铺，播放控制条也在文本下方固定位置，整体观感重心上移。
				top = if (compact) 40.dp else 56.dp,
				end = 48.dp,
				bottom = if (compact) 32.dp else 64.dp,
			),
	) {
		Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
			HeroTitle(item, if (compact) 32.sp else 46.sp, titleFocus, onTitleClick, playFocus)
			HeroMetaLine(item)
			if (!compact) {
				item?.overview?.let { overview ->
					Text(
						overview,
						color = FuseColors.mainFg75,
						style = JellyfinTheme.typography.default.copy(fontSize = 15.sp, lineHeight = 22.sp),
						maxLines = 3,
						overflow = TextOverflow.Ellipsis,
						// 简介宽度严格控制在父容器宽度的 50% 以内：
						// 与评分/类型行（HeroMetaLine）保持「左半文字 + 右半留白」布局，
						// 这样既不和评分/控制按钮横向打架，下方播放按钮区域也更纯净。
						modifier = Modifier.fillMaxWidth(0.5f),
					)
				}
			}
		}

		HeroControlBar(
			modifier = Modifier.padding(top = if (compact) 14.dp else 26.dp),
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			compact = compact,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onDown = onDown,
			onLeftEdge = onLeftEdge,
			onLight = onLight,
			playFocus = playFocus,
			titleFocus = titleFocus,
		)
	}
}

// endregion

// region Variant 2 : Showcase collage

@Composable
private fun HeroShowcase(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onDown: () -> Boolean,
	onLeftEdge: () -> Unit,
	onLight: (Float) -> Unit,
	playFocus: FocusRequester,
	titleFocus: FocusRequester,
	onTitleClick: () -> Unit,
) {
	val api = koinInject<ApiClient>()

	// Cast head shots are fetched ONE ITEM AT A TIME. Never add ItemFields.PEOPLE
	// to the home list request - it bloats the response and hangs the screen.
	var people by remember(item?.id) { mutableStateOf<List<BaseItemPerson>>(emptyList()) }
	LaunchedEffect(item?.id) {
		people = emptyList()
		val id = item?.id ?: return@LaunchedEffect
		val fetched = runCatching {
			api.itemsApi.getItems(
				ids = setOf(id),
				fields = setOf(ItemFields.PEOPLE),
				recursive = true,
			).content.items.orEmpty().firstOrNull()?.people.orEmpty()
		}.getOrElse { emptyList() }
		people = fetched.filter { it.primaryImage != null }.take(9)
	}

	Box(Modifier.fillMaxSize()) {
		if (people.isNotEmpty()) {
			Column(
				modifier = Modifier
					.fillMaxHeight()
					.align(Alignment.CenterEnd)
					.padding(end = 56.dp),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.End,
			) {
				people.chunked(3).forEach { rowPeople ->
					Row(
						horizontalArrangement = Arrangement.spacedBy(16.dp),
						modifier = Modifier.padding(vertical = 8.dp),
					) {
						rowPeople.forEach { person ->
							val head = person.primaryImage
							Box(
								Modifier
									.size(92.dp)
									.clip(CircleShape)
									.background(FuseColors.mainSoft)
									.border(2.dp, FuseColors.overlayHard, CircleShape),
							) {
								if (head != null) {
									AsyncImage(
										url = head.getUrl(api, maxWidth = 220),
										scaleType = ImageView.ScaleType.CENTER_CROP,
										modifier = Modifier.fillMaxSize(),
									)
								}
							}
						}
					}
				}
			}
		}

		HeroStandard(
			item = item,
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			compact = false,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onDown = onDown,
			onLeftEdge = onLeftEdge,
			onLight = onLight,
			playFocus = playFocus,
			titleFocus = titleFocus,
			onTitleClick = onTitleClick,
		)
	}
}

// endregion

// region Variant 3 : Fanart only

@Composable
private fun HeroFanart(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onDown: () -> Boolean,
	onLeftEdge: () -> Unit,
	onLight: (Float) -> Unit,
	playFocus: FocusRequester,
	titleFocus: FocusRequester,
	onTitleClick: () -> Unit,
) {
	Box(Modifier.fillMaxSize()) {
		var fanartTitleFocused by remember { mutableStateOf(false) }
		Box(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 44.dp, end = 56.dp)
				.focusRequester(titleFocus)
				.onFocusChanged { fanartTitleFocused = it.hasFocus }
				.clickable(onClick = onTitleClick)
				.onPreviewKeyEvent { event ->
					if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
						playFocus.requestFocus()
						true
					} else {
						false
					}
				}
				.background(
					if (fanartTitleFocused) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.18f)
					else Color.Transparent,
					RoundedCornerShape(6.dp),
				)
				.padding(horizontal = 8.dp, vertical = 4.dp),
		) {
			Text(
				item?.name.orEmpty(),
				color = if (fanartTitleFocused) JellyfinTheme.colorScheme.buttonFocused else FuseColors.mainFg90,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 24.sp,
					fontWeight = FontWeight.SemiBold,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		HeroControlBar(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.padding(bottom = 64.dp),
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			compact = false,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onDown = onDown,
			onLeftEdge = onLeftEdge,
			onLight = onLight,
			playFocus = playFocus,
			titleFocus = titleFocus,
		)
	}
}

// endregion

// region Variant 4 : Mini strip

@Composable
private fun HeroMini(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onDown: () -> Boolean,
	onLeftEdge: () -> Unit,
	onLight: (Float) -> Unit,
	playFocus: FocusRequester,
	titleFocus: FocusRequester,
	onTitleClick: () -> Unit,
) {
	val api = koinInject<ApiClient>()
	val poster = item?.itemImages?.values?.firstOrNull() ?: item?.itemBackdropImages?.firstOrNull()

	Row(
		modifier = Modifier
			.fillMaxSize()
			.padding(start = 40.dp, end = 48.dp, top = 28.dp, bottom = 28.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(24.dp),
	) {
		Box(
			Modifier
				.size(width = 86.dp, height = 128.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(FuseColors.mainSoft),
		) {
			if (poster != null) {
				AsyncImage(
					url = poster.getUrl(api, maxWidth = 240),
					blurHash = poster.blurHash,
					scaleType = ImageView.ScaleType.FIT_CENTER,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			HeroTitle(item, 26.sp, titleFocus, onTitleClick, playFocus)
			HeroMetaLine(item)
		}

		HeroControlBar(
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			compact = true,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onDown = onDown,
			onLeftEdge = onLeftEdge,
			onLight = onLight,
			playFocus = playFocus,
			titleFocus = titleFocus,
		)
	}
}

// endregion

// region Shared pieces

@Composable
private fun HeroTitle(
	item: BaseItemDto?,
	size: androidx.compose.ui.unit.TextUnit,
	titleFocus: FocusRequester,
	onTitleClick: () -> Unit,
	playFocus: FocusRequester,
) {
	var focused by remember { mutableStateOf(false) }
	Box(
		modifier = Modifier
			.focusRequester(titleFocus)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onTitleClick)
			.onPreviewKeyEvent { event ->
				if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
					playFocus.requestFocus()
					true
				} else {
					false
				}
			}
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.16f)
				else Color.Transparent,
				RoundedCornerShape(6.dp),
			)
			.padding(horizontal = 8.dp, vertical = 4.dp),
	) {
		Text(
			item?.name.orEmpty(),
			color = if (focused) JellyfinTheme.colorScheme.buttonFocused else FuseColors.mainFg100,
			style = JellyfinTheme.typography.default.copy(
				fontSize = size,
				fontWeight = FontWeight.Bold,
			),
			maxLines = 2,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

@Composable
private fun HeroMetaLine(item: BaseItemDto?) {
	Row(
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		item?.communityRating?.let { rating ->
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				Text(
					"★",
					color = FuseColors.yellowStar,
					style = JellyfinTheme.typography.default.copy(fontSize = 15.sp),
				)
				Text(
					"%.1f".format(rating),
					color = FuseColors.mainFg90,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 15.sp,
						fontWeight = FontWeight.SemiBold,
					),
				)
			}
		}

		val meta = buildString {
			item?.productionYear?.let { append(it) }
			val genres = item?.genres?.take(3)?.joinToString(" / ")
			if (!genres.isNullOrBlank()) {
				if (isNotEmpty()) append("  ·  ")
				append(genres)
			}
		}
		if (meta.isNotBlank()) {
			Text(
				meta,
				color = FuseColors.mainFg75,
				style = JellyfinTheme.typography.default.copy(fontSize = 15.sp),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

/**
 * FUSE spotlight control group (skin ids 310/311/312/314/315):
 *   [<]  ( play )  ( i )  [>]   • • • • • • •
 *
 * Everything here is a normal focusable button and the row relies on plain
 * Compose focus traversal. The only key that is intercepted is DOWN, which
 * hands focus to the first poster row. Nothing counts key presses, nothing
 * mutates a preference - that is what used to make the stage vanish.
 */
@Composable
private fun HeroControlBar(
	modifier: Modifier = Modifier,
	featuredCount: Int,
	heroIndex: Int,
	compact: Boolean,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onDown: () -> Boolean,
	onLeftEdge: () -> Unit,
	onLight: (Float) -> Unit,
	playFocus: FocusRequester,
	titleFocus: FocusRequester,
) {
	val showArrows = featuredCount > 1
	val playSize = if (compact) 52.dp else 68.dp
	val smallSize = if (compact) 38.dp else 46.dp

	// Focus always comes back to the play button - that is what the home screen
	// and the back key both aim at.
	LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

	Row(
		modifier = modifier
			.focusGroup()
			.onPreviewKeyEvent { event ->
				if (event.type == KeyEventType.KeyDown) {
					when (event.key) {
					Key.DirectionDown -> {
						// Try the explicit anchor (first-row poster). If it isn't
						// reachable yet (still composing / empty first row), DON'T
						// consume the key — let the system's natural focus search
						// step down so the remote never traps inside the bar.
						onDown()
					}
						// Pressing UP from any control parks on the (now focusable)
						// title above the control bar - that is where the remote can
						// step up to see the full poster via the detail page.
						Key.DirectionUp -> { titleFocus.requestFocus(); true }
						else -> false
					}
				} else {
					false
				}
			},
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp),
	) {
		if (showArrows) {
			FuseCircleButton(
				iconRes = R.drawable.ic_previous,
				size = smallSize,
				primary = false,
				lightAnchor = 0.10f,
				onLight = onLight,
				onClick = onPreviousFeatured,
				onLeftEdge = onLeftEdge,
			)
		}

		FuseCircleButton(
			iconRes = R.drawable.ic_play,
			size = playSize,
			primary = true,
			lightAnchor = if (showArrows) 0.19f else 0.12f,
			onLight = onLight,
			onClick = onPlay,
			onLeftEdge = if (showArrows) null else onLeftEdge,
			focusRequester = playFocus,
		)

		FuseCircleButton(
			iconRes = R.drawable.ic_info,
			size = smallSize,
			primary = false,
			lightAnchor = 0.28f,
			onLight = onLight,
			onClick = onInfo,
		)

		if (showArrows) {
			FuseCircleButton(
				iconRes = R.drawable.ic_next,
				size = smallSize,
				primary = false,
				lightAnchor = 0.36f,
				onLight = onLight,
				onClick = onNextFeatured,
			)
		}

		if (featuredCount in 2..24) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier.padding(start = 14.dp),
			) {
				repeat(featuredCount) { index ->
					Box(
						Modifier
							.size(
								width = if (index == heroIndex) 22.dp else 6.dp,
								height = 6.dp,
							)
							.background(
								if (index == heroIndex) FuseColors.mainFg100 else FuseColors.mainFg25,
								RoundedCornerShape(3.dp),
							),
					)
				}
			}
		}
	}
}

/**
 * circle_120.png / circle_60.png equivalent: a soft round button that lights up
 * white on focus (ColorHighlight) and sits on main_soft when idle.
 */
@Composable
private fun FuseCircleButton(
	iconRes: Int,
	size: Dp,
	primary: Boolean,
	lightAnchor: Float,
	onLight: (Float) -> Unit,
	onClick: () -> Unit,
	onLeftEdge: (() -> Unit)? = null,
	focusRequester: FocusRequester? = null,
) {
	var focused by remember { mutableStateOf(false) }
	val scale by animateFloatAsState(if (focused) 1.10f else 1f, tween(180), label = "fuse-circle")

	val fill = when {
		focused -> FuseColors.focusHighlight
		primary -> FuseColors.mainFg100.copy(alpha = 0.88f)
		else -> FuseColors.mainSoft
	}
	val content = when {
		focused -> FuseColors.focusSelected
		primary -> FuseColors.focusSelected
		else -> FuseColors.mainFg90
	}

	Box(
		modifier = Modifier
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.size(size)
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.clip(CircleShape)
			.background(fill)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = if (focused) Color.Transparent else FuseColors.overlayHard,
				shape = CircleShape,
			)
			.onFocusChanged { state ->
				focused = state.isFocused
				if (state.isFocused) onLight(lightAnchor)
			}
			.onPreviewKeyEvent { event ->
				if (
					event.type == KeyEventType.KeyDown &&
					event.key == Key.DirectionLeft &&
					onLeftEdge != null
				) {
					onLeftEdge()
					true
				} else {
					false
				}
			}
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(iconRes),
			contentDescription = null,
			tint = content,
			modifier = Modifier.size(size * 0.42f),
		)
	}
}

// endregion
