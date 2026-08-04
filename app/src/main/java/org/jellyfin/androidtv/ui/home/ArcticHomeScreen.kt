package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.data.repository.UserViewsRepository
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.androidtv.util.apiclient.getUrl
import org.jellyfin.androidtv.util.apiclient.itemBackdropImages
import org.jellyfin.androidtv.util.apiclient.itemImages
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.compose.koinInject

// region Data model

data class ArcticRow(
	val title: String,
	val items: List<BaseItemDto>,
)

private data class SidebarEntry(
	val icon: Int,
	val label: String,
	val onClick: () -> Unit,
)

/**
 * Six home layout modes translated from the FUSE 2 reference screenshots.
 * Mode 0 is the existing portrait-poster look; modes 1-5 replicate the 5 screenshots.
 */
private enum class HomeLayoutMode {
	PORTRAIT_POSTERS,   // current default
	WIDE_INFO_CARDS,    // 003
	LANDSCAPE_CARDS,    // 004
	CIRCULAR_DISCS,     // 008
	PORTRAIT_WITH_BAR,  // 011
	LARGE_LANDSCAPE,    // 028
}

private val HomeLayoutMode.label: String
	get() = when (this) {
		HomeLayoutMode.PORTRAIT_POSTERS -> "竖版海报"
		HomeLayoutMode.WIDE_INFO_CARDS -> "宽信息卡"
		HomeLayoutMode.LANDSCAPE_CARDS -> "横向海报"
		HomeLayoutMode.CIRCULAR_DISCS -> "圆形光盘"
		HomeLayoutMode.PORTRAIT_WITH_BAR -> "竖版+信息栏"
		HomeLayoutMode.LARGE_LANDSCAPE -> "大海报"
	}

// endregion

@Composable
fun ArcticHomeScreen() {
	val api = koinInject<ApiClient>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val navigationRepository = koinInject<NavigationRepository>()

	var libraries by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var featuredItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var heroIndex by remember { mutableStateOf(0) }
	var rows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var loaded by remember { mutableStateOf(false) }

	// UI toggles (will be persisted through the settings page later).
	var rowsVisible by remember { mutableStateOf(true) }
	var layoutMode by remember { mutableStateOf(HomeLayoutMode.PORTRAIT_POSTERS) }
	var sidebarExpanded by remember { mutableStateOf(false) }

	val sidebarFocus = remember { FocusRequester() }
	val mainContentFocus = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
		runCatching {
			val views = userViewsRepository.views.first()
				.filter { it.collectionType !in setOf(CollectionType.PLAYLISTS, CollectionType.LIVETV) }

			val resume = api.itemsApi.getResumeItems(
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
				limit = 20,
				mediaTypes = listOf(MediaType.VIDEO),
				includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
				excludeActiveSessions = true,
			).content.items.orEmpty()

			val nextUp = api.tvShowsApi.getNextUp(
				imageTypeLimit = 1,
				limit = 20,
				enableResumable = false,
				fields = ItemRepository.browseFields,
			).content.items.orEmpty()

			val recentlyAdded = api.itemsApi.getItems(
				limit = 24,
				recursive = true,
				includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
				sortBy = setOf(ItemSortBy.DATE_CREATED),
				sortOrder = setOf(SortOrder.DESCENDING),
				fields = ItemRepository.browseFields,
				imageTypeLimit = 1,
			).content.items.orEmpty()

			val perLibrary = views.take(6).mapNotNull { view ->
				val items = api.userLibraryApi.getLatestMedia(
					parentId = view.id,
					limit = 20,
					fields = ItemRepository.browseFields,
					imageTypeLimit = 1,
					groupItems = true,
				).content
				if (items.isNullOrEmpty()) null else ArcticRow(view.name ?: "", items)
			}

			val featureCandidates = buildList {
				addAll(resume)
				addAll(nextUp)
				addAll(recentlyAdded)
				perLibrary.forEach { addAll(it.items.take(2)) }
			}.distinctBy { it.id }.take(12)

			val rowList = buildList {
				if (resume.isNotEmpty()) add(ArcticRow("继续观看", resume))
				if (nextUp.isNotEmpty()) add(ArcticRow("接下来播放", nextUp))
				if (recentlyAdded.isNotEmpty()) add(ArcticRow("最近添加", recentlyAdded))
				addAll(perLibrary)
			}

			withContext(Dispatchers.Main) {
				libraries = views
				featuredItems = featureCandidates
				rows = rowList
				loaded = true
			}
		}.onFailure { it.printStackTrace() }
		}
	}

	// Auto-rotate the featured stage every 8s when no one is interacting with it.
	LaunchedEffect(heroIndex, featuredItems.size) {
		if (featuredItems.size <= 1) return@LaunchedEffect
		delay(8_000)
		heroIndex = (heroIndex + 1) % featuredItems.size
	}

	Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background)) {
		// 1) Full-screen fanart background.
		ArcticBackground(
			item = featuredItems.getOrNull(heroIndex),
			modifier = Modifier.fillMaxSize(),
		)

		// 2) Main content (full width) - sits under the sidebar.
		ArcticMainContent(
			modifier = Modifier.fillMaxSize(),
			hero = featuredItems.getOrNull(heroIndex),
			featuredCount = featuredItems.size,
			heroIndex = heroIndex,
			onHeroIndexChange = { heroIndex = it },
			rows = rows,
			loaded = loaded,
			rowsVisible = rowsVisible,
			layoutMode = layoutMode,
			onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
			onHeroPlay = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
			onHeroInfo = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
			onToggleRows = { rowsVisible = !rowsVisible },
			onCycleLayoutMode = { layoutMode = HomeLayoutMode.entries[(layoutMode.ordinal + 1) % HomeLayoutMode.entries.size] },
			initialFocus = mainContentFocus,
		)

		// 3) Floating auto-hide sidebar.
		ArcticSidebar(
			libraries = libraries,
			expanded = sidebarExpanded,
			onExpandedChange = { sidebarExpanded = it },
			initialFocus = sidebarFocus,
			onHome = {
				sidebarExpanded = false
				navigationRepository.navigate(Destinations.home, replace = true)
			},
			onSearch = {
				sidebarExpanded = false
				navigationRepository.navigate(Destinations.search())
			},
			onSettings = {
				sidebarExpanded = false
				navigationRepository.navigate(Destinations.fuseSettings)
			},
			onLibrary = {
				sidebarExpanded = false
				navigationRepository.navigate(Destinations.libraryBrowser(it))
			},
		)
	}

	// Initial focus goes to the main content, not the sidebar, so the sidebar stays collapsed.
	LaunchedEffect(Unit) { runCatching { mainContentFocus.requestFocus() } }
}

// region Background

@Composable
private fun ArcticBackground(
	item: BaseItemDto?,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val backdrop: JellyfinImage? = item?.itemBackdropImages?.firstOrNull()
		?: item?.itemImages?.values?.firstOrNull()

	Box(modifier = modifier) {
		if (backdrop != null) {
			AsyncImage(
				url = backdrop.getUrl(api, maxWidth = 1920),
				blurHash = backdrop.blurHash,
				// Full screen, cover scale. FUSE 2 uses scale so the artwork fills the stage.
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background))
		}

		// Left-to-right scrim for legible text on the left info panel.
		Box(
			Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						0.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.90f),
						0.18f to JellyfinTheme.colorScheme.background.copy(alpha = 0.65f),
						0.40f to JellyfinTheme.colorScheme.background.copy(alpha = 0.28f),
						0.70f to Color.Transparent,
						1.00f to Color.Transparent,
					),
				),
		)

		// Bottom scrim so rows are readable against the lower part of the fanart.
		Box(
			Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						0.00f to Color.Transparent,
						0.40f to Color.Transparent,
						0.75f to JellyfinTheme.colorScheme.background.copy(alpha = 0.78f),
						1.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.95f),
					),
				),
		)

		// Vignette overlay: centre brighter, edges darker (spotlight feel).
		Box(
			Modifier
				.fillMaxSize()
				.background(
					Brush.radialGradient(
						0.00f to Color.Transparent,
						0.55f to Color.Transparent,
						0.88f to JellyfinTheme.colorScheme.background.copy(alpha = 0.40f),
						1.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.70f),
					),
				),
		)
	}
}

// endregion

// region Sidebar

@Composable
private fun ArcticSidebar(
	libraries: List<BaseItemDto>,
	expanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	initialFocus: FocusRequester,
	onHome: () -> Unit,
	onSearch: () -> Unit,
	onSettings: () -> Unit,
	onLibrary: (BaseItemDto) -> Unit,
) {
	val entries = buildList {
		add(SidebarEntry(R.drawable.ic_grid, "首页", onHome))
		add(SidebarEntry(R.drawable.ic_search, "搜索", onSearch))
		libraries.take(4).forEach { view ->
			add(SidebarEntry(collectionIcon(view.collectionType), view.name ?: "", { onLibrary(view) }))
		}
		add(SidebarEntry(R.drawable.ic_settings, "设置", onSettings))
	}

	val targetWidth by animateDpAsState(
		targetValue = if (expanded) 200.dp else 64.dp,
		animationSpec = tween(250),
		label = "sidebar-width",
	)

	// Track focus of each sidebar item; expand if any is focused.
	val focusStates = remember(entries.size) { List(entries.size) { mutableStateOf(false) } }
	val anyFocused = focusStates.any { it.value }
	LaunchedEffect(anyFocused) { onExpandedChange(anyFocused) }

	Column(
		modifier = Modifier
			.fillMaxHeight()
			.width(targetWidth)
			.background(
				Brush.horizontalGradient(
					0.00f to JellyfinTheme.colorScheme.background.copy(alpha = if (expanded) 0.82f else 0.55f),
					0.70f to JellyfinTheme.colorScheme.background.copy(alpha = if (expanded) 0.45f else 0.0f),
					1.00f to Color.Transparent,
				),
			)
			.padding(top = 240.dp, bottom = 240.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
	) {
		entries.forEachIndexed { index, entry ->
			SidebarItem(
				entry = entry,
				expanded = expanded,
				modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
				onFocusChange = { focusStates[index].value = it },
			)
		}
	}
}

private fun collectionIcon(type: CollectionType?): Int = when (type) {
	CollectionType.MOVIES -> R.drawable.ic_movie
	CollectionType.TVSHOWS -> R.drawable.ic_tv
	CollectionType.MUSIC -> R.drawable.ic_star
	CollectionType.PHOTOS -> R.drawable.ic_photo
	CollectionType.LIVETV -> R.drawable.ic_tv
	else -> R.drawable.ic_grid
}

@Composable
private fun SidebarItem(
	entry: SidebarEntry,
	expanded: Boolean,
	onFocusChange: (Boolean) -> Unit = {},
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val selected = focused

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(56.dp)
			.padding(horizontal = if (expanded) 14.dp else 8.dp)
			.background(
				if (selected) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.16f) else Color.Transparent,
				RoundedCornerShape(10.dp),
			)
			.onFocusChanged {
				focused = it.hasFocus
				onFocusChange(it.hasFocus)
			}
			.clickable(onClick = entry.onClick)
			.padding(horizontal = if (expanded) 14.dp else 0.dp)
			.animateContentSize(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = if (expanded) Arrangement.spacedBy(12.dp, Alignment.Start) else Arrangement.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(entry.icon),
			contentDescription = entry.label,
			tint = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			modifier = Modifier.size(22.dp),
		)

		if (expanded) {
			Text(
				entry.label,
				color = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
				style = JellyfinTheme.typography.default.copy(
					fontSize = 14.sp,
					fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

// endregion

// region Main content

@Composable
private fun ArcticMainContent(
	modifier: Modifier = Modifier,
	hero: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onHeroIndexChange: (Int) -> Unit,
	rows: List<ArcticRow>,
	loaded: Boolean,
	rowsVisible: Boolean,
	layoutMode: HomeLayoutMode,
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
	onToggleRows: () -> Unit,
	onCycleLayoutMode: () -> Unit,
	initialFocus: FocusRequester,
) {
	BoxWithConstraints(modifier = modifier) {
		val infoPanelHeight = if (rowsVisible) 440.dp else 520.dp
		val infoPanelBottomPadding = if (rowsVisible) 0.dp else 64.dp

		Column(Modifier.fillMaxSize()) {
			// FUSE 2 info panel sits at left=view_side area, top around 200dp.
			ArcticInfoPanel(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = view_side_dp, top = 200.dp, end = view_pad_dp, bottom = infoPanelBottomPadding)
					.height(infoPanelHeight),
				item = hero,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = { hero?.let(onHeroPlay) },
				onInfo = { hero?.let(onHeroInfo) },
				onNextFeatured = {
					if (featuredCount > 0) onHeroIndexChange((heroIndex + 1) % featuredCount)
				},
				onPreviousFeatured = {
					if (featuredCount > 0) onHeroIndexChange((heroIndex - 1 + featuredCount) % featuredCount)
				},
				onToggleRows = onToggleRows,
				onCycleLayoutMode = onCycleLayoutMode,
				rowsVisible = rowsVisible,
				layoutMode = layoutMode,
				initialFocus = initialFocus,
			)

			if (rowsVisible) {
				val scrollState = rememberScrollState()
				Column(
					modifier = Modifier
						.weight(1f)
						.verticalScroll(scrollState),
				) {
					if (!loaded) {
						Box(
							Modifier
								.fillMaxWidth()
								.padding(top = 28.dp),
							contentAlignment = Alignment.Center,
						) {
							CircularProgressIndicator(
								modifier = Modifier.size(40.dp),
								color = JellyfinTheme.colorScheme.buttonFocused,
							)
						}
					} else {
						rows.forEach { ArcticRowView(it.title, it.items, layoutMode, onItemClick) }
						Spacer(Modifier.height(48.dp))
					}
				}
			}
		}
	}
}

private val view_side_dp = 80.dp
private val view_pad_dp = 80.dp

@Composable
private fun ArcticInfoPanel(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onToggleRows: () -> Unit,
	onCycleLayoutMode: () -> Unit,
	rowsVisible: Boolean,
	layoutMode: HomeLayoutMode,
	initialFocus: FocusRequester,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.SpaceBetween,
	) {
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
			Text(
				"KODI".uppercase(),
				color = JellyfinTheme.colorScheme.buttonFocused,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 13.sp,
					fontWeight = FontWeight.Bold,
				),
			)

			Text(
				item?.name ?: "",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 46.sp,
					fontWeight = FontWeight.Bold,
				),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)

			Row(
				horizontalArrangement = Arrangement.spacedBy(10.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Box(
					Modifier
						.background(
							JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.18f),
							RoundedCornerShape(4.dp),
						)
						.padding(horizontal = 8.dp, vertical = 3.dp),
				) {
					Text(
						"INFO",
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.90f),
						style = JellyfinTheme.typography.default.copy(
							fontSize = 12.sp,
							fontWeight = FontWeight.Bold,
						),
					)
				}
				Text(
					buildHeroMeta(item),
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
					style = JellyfinTheme.typography.default.copy(fontSize = 15.sp),
					maxLines = 1,
				)
			}

			item?.overview?.let { overview ->
				Text(
					overview,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.82f),
					style = JellyfinTheme.typography.default.copy(fontSize = 15.sp, lineHeight = 22.sp),
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.width(680.dp),
				)
			}
		}

		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// Play: pressing LEFT on it cycles to previous featured item when at first item it moves to sidebar.
			HeroPlayButton(
				size = 68.dp,
				iconSize = 24.dp,
				onClick = onPlay,
				onLeft = onPreviousFeatured,
				focusRequester = initialFocus,
			)

			// Info: pressing RIGHT on it cycles to next featured item.
			HeroInfoButton(
				size = 54.dp,
				iconSize = 20.dp,
				onClick = onInfo,
				onRight = onNextFeatured,
			)

			// Dots indicator.
			if (featuredCount > 1) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.padding(start = 18.dp),
				) {
					repeat(featuredCount) { i ->
						Box(
							Modifier
								.size(width = if (i == heroIndex) 20.dp else 6.dp, height = 6.dp)
								.background(
									if (i == heroIndex) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.35f),
									RoundedCornerShape(3.dp),
								),
						)
					}
				}
			}

			Spacer(Modifier.width(8.dp))

			// Temporary toggles until the real settings page is ready.
			HeroSmallButton(
				label = if (rowsVisible) "隐" else "显",
				onClick = onToggleRows,
			)
			HeroSmallButton(
				label = layoutMode.label.take(2),
				onClick = onCycleLayoutMode,
			)
		}
	}
}

@Composable
private fun HeroPlayButton(
	size: androidx.compose.ui.unit.Dp,
	iconSize: androidx.compose.ui.unit.Dp,
	onClick: () -> Unit,
	onLeft: () -> Unit,
	focusRequester: FocusRequester? = null,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.size(size)
			.scale(if (focused) 1.08f else 1f)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = 0.45f),
				CircleShape,
			)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.22f),
				shape = CircleShape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
					onLeft()
					true
				} else {
					false
				}
			}
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_play),
			contentDescription = null,
			tint = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			modifier = Modifier.size(iconSize),
		)
	}
}

@Composable
private fun HeroInfoButton(
	size: androidx.compose.ui.unit.Dp,
	iconSize: androidx.compose.ui.unit.Dp,
	onClick: () -> Unit,
	onRight: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.size(size)
			.scale(if (focused) 1.08f else 1f)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = 0.45f),
				CircleShape,
			)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.22f),
				shape = CircleShape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
					onRight()
					true
				} else {
					false
				}
			}
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(R.drawable.ic_info),
			contentDescription = null,
			tint = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			modifier = Modifier.size(iconSize),
		)
	}
}

@Composable
private fun HeroSmallButton(
	label: String,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.height(34.dp)
			.width(46.dp)
			.scale(if (focused) 1.06f else 1f)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = 0.35f),
				RoundedCornerShape(8.dp),
			)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.20f),
				shape = RoundedCornerShape(8.dp),
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Text(
			label,
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
			),
		)
	}
}

private fun buildHeroMeta(item: BaseItemDto?): String = buildString {
	item ?: return@buildString
	item.productionYear?.let { append(it); append(" · ") }
	item.communityRating?.let { append("★ "); append("%.1f".format(it)); append(" · ") }
	val genres = item.genres?.take(3)?.joinToString(" / ")
	if (!genres.isNullOrBlank()) append(genres)
}

// endregion

// region Rows

@Composable
private fun ArcticRowView(
	title: String,
	items: List<BaseItemDto>,
	layoutMode: HomeLayoutMode,
	onItemClick: (BaseItemDto) -> Unit,
) {
	Column(
		Modifier
			.fillMaxWidth()
			.padding(top = 22.dp, bottom = 6.dp),
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(end = 36.dp, bottom = 12.dp),
		) {
			Box(
				Modifier
					.width(4.dp)
					.height(18.dp)
					.background(JellyfinTheme.colorScheme.buttonFocused, RoundedCornerShape(2.dp)),
			)
			Spacer(Modifier.width(10.dp))
			Text(
				title,
				color = JellyfinTheme.colorScheme.listHeader,
				style = JellyfinTheme.typography.default.copy(
					fontWeight = FontWeight.Bold,
					fontSize = 19.sp,
				),
			)
		}

		LazyRow(
			contentPadding = PaddingValues(end = 36.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			items(items, key = { it.id }) { item ->
				when (layoutMode) {
					HomeLayoutMode.PORTRAIT_POSTERS -> PortraitPosterCard(item = item, onClick = { onItemClick(item) })
					HomeLayoutMode.WIDE_INFO_CARDS -> WideInfoCard(item = item, onClick = { onItemClick(item) })
					HomeLayoutMode.LANDSCAPE_CARDS -> LandscapeCard(item = item, onClick = { onItemClick(item) })
					HomeLayoutMode.CIRCULAR_DISCS -> CircularDiscCard(item = item, onClick = { onItemClick(item) })
					HomeLayoutMode.PORTRAIT_WITH_BAR -> PortraitWithBarCard(item = item, onClick = { onItemClick(item) })
					HomeLayoutMode.LARGE_LANDSCAPE -> LargeLandscapeCard(item = item, onClick = { onItemClick(item) })
				}
			}
		}
	}
}

@Composable
private fun PortraitPosterCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(200.dp)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.width(200.dp)
				.height(294.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f))
				.border(
					width = if (focused) 3.dp else 0.dp,
					color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					shape = RoundedCornerShape(8.dp),
				),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 400),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.FIT_CENTER,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Text(
			item.name ?: "",
			color = JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = FontWeight.Medium,
				fontSize = 14.sp,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(200.dp),
		)
	}
}

@Composable
private fun WideInfoCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Row(
		modifier = modifier
			.width(520.dp)
			.height(180.dp)
			.clip(RoundedCornerShape(12.dp))
			.background(
				Brush.horizontalGradient(
					0.0f to JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.22f),
					0.4f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.45f),
					1.0f to JellyfinTheme.colorScheme.surface.copy(alpha = 0.25f),
				),
			)
			.border(
				width = if (focused) 3.dp else 0.dp,
				color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
				shape = RoundedCornerShape(12.dp),
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(12.dp),
		horizontalArrangement = Arrangement.spacedBy(14.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.width(120.dp)
				.height(180.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f)),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 300),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.FIT_CENTER,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Column(
			modifier = Modifier.weight(1f),
			verticalArrangement = Arrangement.spacedBy(6.dp),
		) {
			Text(
				item.name ?: "",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontWeight = FontWeight.Bold,
					fontSize = 17.sp,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				buildHeroMeta(item),
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
				style = JellyfinTheme.typography.default.copy(fontSize = 13.sp),
				maxLines = 1,
			)
			Text(
				item.overview ?: "",
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
				style = JellyfinTheme.typography.default.copy(fontSize = 13.sp, lineHeight = 18.sp),
				maxLines = 4,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

@Composable
private fun LandscapeCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages.values.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(300.dp)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.width(300.dp)
				.height(170.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f))
				.border(
					width = if (focused) 3.dp else 0.dp,
					color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					shape = RoundedCornerShape(8.dp),
				),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 500),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Text(
			item.name ?: "",
			color = JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = FontWeight.Medium,
				fontSize = 14.sp,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(300.dp),
		)
		Text(
			buildHeroMeta(item),
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.65f),
			style = JellyfinTheme.typography.default.copy(fontSize = 12.sp),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(300.dp),
		)
	}
}

@Composable
private fun CircularDiscCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(160.dp)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(10.dp),
	) {
		Box(
			modifier = Modifier
				.size(160.dp)
				.clip(CircleShape)
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f))
				.border(
					width = if (focused) 4.dp else 0.dp,
					color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					shape = CircleShape,
				),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 320),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			}
			// Simulate disc hole in the centre.
			Box(
				Modifier
					.size(26.dp)
					.align(Alignment.Center)
					.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.65f), CircleShape)
					.border(2.dp, JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.25f), CircleShape),
			)
		}

		Text(
			item.name ?: "",
			color = JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = FontWeight.Medium,
				fontSize = 13.sp,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(160.dp),
		)
		Text(
			buildHeroMeta(item),
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.60f),
			style = JellyfinTheme.typography.default.copy(fontSize = 11.sp),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(160.dp),
		)
	}
}

@Composable
private fun PortraitWithBarCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(200.dp)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.width(200.dp)
				.height(294.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f))
				.border(
					width = if (focused) 3.dp else 0.dp,
					color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					shape = RoundedCornerShape(8.dp),
				),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 400),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.FIT_CENTER,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		// Info bar under the poster.
		Row(
			modifier = Modifier
				.width(200.dp)
				.clip(RoundedCornerShape(6.dp))
				.background(JellyfinTheme.colorScheme.surface.copy(alpha = 0.40f))
				.padding(horizontal = 10.dp, vertical = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.SpaceBetween,
		) {
			Text(
				item.name ?: "",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontWeight = FontWeight.Medium,
					fontSize = 13.sp,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f),
			)
			Text(
				"${item.userData?.unplayedItemCount ?: 0} 集",
				color = JellyfinTheme.colorScheme.buttonFocused,
				style = JellyfinTheme.typography.default.copy(fontSize = 11.sp),
			)
		}
	}
}

@Composable
private fun LargeLandscapeCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages.values.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(420.dp)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.width(420.dp)
				.height(236.dp)
				.clip(RoundedCornerShape(10.dp))
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f))
				.border(
					width = if (focused) 3.dp else 0.dp,
					color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					shape = RoundedCornerShape(10.dp),
				),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 700),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			}
		}

		Text(
			item.name ?: "",
			color = JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = FontWeight.Medium,
				fontSize = 15.sp,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(420.dp),
		)
	}
}

// endregion
