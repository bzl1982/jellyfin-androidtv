package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.compose.koinInject

// region Data model

data class ArcticRow(
	val title: String,
	val items: List<BaseItemDto>,
)

private data class ClassifiedItem(
	val item: BaseItemDto,
	val programType: ProgramType,
	val bucket: RegionBucket,
	val subRowId: String?,
)

/**
 * Six home layout modes translated from the FUSE 2 reference screenshots.
 * Mode 5 (LARGE_LANDSCAPE) is the full-screen hero mode and hides the rows below it.
 */
private enum class HomeLayoutMode {
	PORTRAIT_POSTERS,
	WIDE_INFO_CARDS,
	LANDSCAPE_CARDS,
	CIRCULAR_DISCS,
	PORTRAIT_WITH_BAR,
	LARGE_LANDSCAPE,
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

private val HomeLayoutMode.isFullScreenHero: Boolean
	get() = this == HomeLayoutMode.LARGE_LANDSCAPE

// endregion

@Composable
fun ArcticHomeScreen() {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	val menuStore = remember { MenuConfigurationStore(context) }

	var menuConfig by remember { mutableStateOf(menuStore.load()) }
	var allItems by remember { mutableStateOf<List<ClassifiedItem>>(emptyList()) }
	var featuredItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var homeRows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var loaded by remember { mutableStateOf(false) }

	var selectedCategory by remember { mutableStateOf<CategoryMenuConfig?>(null) }
	var categoryHeroItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var categoryRows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var categoryLoading by remember { mutableStateOf(false) }

	var layoutMode by remember { mutableStateOf(HomeLayoutMode.PORTRAIT_POSTERS) }
	var homeHeroIndex by remember { mutableIntStateOf(0) }
	var categoryHeroIndex by remember { mutableIntStateOf(0) }
	var sidebarExpanded by remember { mutableStateOf(false) }

	val sidebarFocus = remember { FocusRequester() }
	val mainContentFocus = remember { FocusRequester() }

	// ---- Load and classify every Movie/Series item once ----
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			runCatching {
				val classified = mutableListOf<ClassifiedItem>()
				var startIndex = 0
				val pageSize = 500
				while (true) {
					val resp = api.itemsApi.getItems(
						limit = pageSize,
						startIndex = startIndex,
						recursive = true,
						includeItemTypes = setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
						fields = setOf(
						ItemFields.PRODUCTION_LOCATIONS,
						ItemFields.GENRES,
						ItemFields.OVERVIEW,
						ItemFields.DATE_CREATED,
						ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
						),
						sortBy = setOf(ItemSortBy.DATE_CREATED),
						sortOrder = setOf(SortOrder.DESCENDING),
					).content
					val items = resp.items.orEmpty()
					for (item in items) {
						val programType = MenuDefaults.classifyProgramType(item) ?: continue
						val bucket = MenuDefaults.classifyBucket(item.productionLocations)
						val subRowId = item.productionLocations.orEmpty()
							.mapNotNull { MenuDefaults.countrySubRowId(bucket, it) }
							.firstOrNull()
						classified.add(ClassifiedItem(item, programType, bucket, subRowId))
					}
					val total = resp.totalRecordCount ?: 0
					startIndex += items.size
					if (items.isEmpty() || startIndex >= total) break
				}

				// Featured stage: up to 12 newest items with an image.
				val featured = classified
					.map { it.item }
					.filter { it.itemBackdropImages.isNotEmpty() || it.itemImages.isNotEmpty() }
					.distinctBy { it.id }
					.take(12)

				// Home rows: one row per program type, newest first.
				val typeRows = ProgramType.entries.mapNotNull { type ->
					val items = classified
						.filter { it.programType == type }
						.map { it.item }
						.take(30)
					if (items.isEmpty()) null else ArcticRow(type.label, items)
				}

				withContext(Dispatchers.Main) {
					allItems = classified
					featuredItems = featured
					homeRows = typeRows
					loaded = true
				}
			}.onFailure { it.printStackTrace() }
		}
	}

	// ---- Build category view rows whenever selection or data changes ----
	LaunchedEffect(selectedCategory, allItems) {
		val cat = selectedCategory
		if (cat == null || allItems.isEmpty()) {
			categoryHeroItems = emptyList()
			categoryRows = emptyList()
			return@LaunchedEffect
		}
		categoryLoading = true
		categoryHeroIndex = 0
		withContext(Dispatchers.Default) {
			val matching = allItems.filter { it.programType == cat.programType && it.bucket == cat.regionBucket }
			val heroItems = matching.map { it.item }.distinctBy { it.id }
			val rows = buildList {
				// Visible sub-rows in configured order.
				cat.subRows.filter { it.visible }.sortedBy { it.order }.forEach { sub ->
					val subItems = matching
						.filter { it.subRowId == sub.id }
						.map { it.item }
						.distinctBy { it.id }
					if (subItems.isNotEmpty()) {
						add(ArcticRow(sub.label, subItems))
					}
				}
				// Fallback: if no sub-rows matched (e.g. OTHER bucket), chunk everything.
				if (isEmpty() && heroItems.isNotEmpty()) {
					heroItems.chunked(14).forEach { add(ArcticRow(cat.label, it)) }
				}
			}
			withContext(Dispatchers.Main) {
				categoryHeroItems = heroItems
				categoryRows = rows
				categoryLoading = false
			}
		}
	}

	// Auto-rotate the hero every 8s.
	val activeHeroCount = if (selectedCategory == null) featuredItems.size else categoryHeroItems.size
	LaunchedEffect(homeHeroIndex, categoryHeroIndex, selectedCategory, activeHeroCount) {
		if (activeHeroCount <= 1) return@LaunchedEffect
		delay(8_000)
		if (selectedCategory == null) {
			homeHeroIndex = (homeHeroIndex + 1) % activeHeroCount
		} else {
			categoryHeroIndex = (categoryHeroIndex + 1) % activeHeroCount
		}
	}

	val heroItem = when (selectedCategory) {
		null -> featuredItems.getOrNull(homeHeroIndex)
		else -> categoryHeroItems.getOrNull(categoryHeroIndex)
	}

	BoxWithConstraints(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background)) {
		val screenHeight = maxHeight

		// Full-bleed hero backdrop in the top screen area, drawn behind the sidebar
		// and the main content so the poster extends all the way to the left edge.
		HeroBackground(
			item = heroItem,
			modifier = Modifier
				.fillMaxWidth()
				.height(screenHeight)
				.align(Alignment.TopStart),
		)

		Row(Modifier.fillMaxSize()) {
			ArcticSidebar(
				expanded = sidebarExpanded,
				onExpandedChange = { sidebarExpanded = it },
				initialFocus = sidebarFocus,
				mainContentFocus = mainContentFocus,
				config = menuConfig,
				onHome = {
					selectedCategory = null
				},
				onSearch = {
					sidebarExpanded = false
					navigationRepository.navigate(Destinations.search())
				},
				onSettings = {
					sidebarExpanded = false
					navigationRepository.navigate(Destinations.fuseSettings)
				},
				onCategory = { cat ->
					selectedCategory = cat
					Toast.makeText(context, "正在加载 ${cat.label}", Toast.LENGTH_SHORT).show()
				},
			)

			ArcticMainContent(
				modifier = Modifier.weight(1f).fillMaxHeight(),
				selectedCategory = selectedCategory,
				heroItem = heroItem,
				heroCount = activeHeroCount,
				heroIndex = if (selectedCategory == null) homeHeroIndex else categoryHeroIndex,
				onHeroIndexChange = { idx ->
					if (selectedCategory == null) homeHeroIndex = idx else categoryHeroIndex = idx
				},
				homeRows = homeRows,
				categoryRows = categoryRows,
				categoryLoading = categoryLoading,
				loaded = loaded,
				layoutMode = layoutMode,
				heroHeight = screenHeight,
				onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
				onHeroPlay = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
				onHeroInfo = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
				onCycleLayoutMode = { layoutMode = HomeLayoutMode.entries[(layoutMode.ordinal + 1) % HomeLayoutMode.entries.size] },
				initialFocus = mainContentFocus,
				sidebarFocus = sidebarFocus,
			)
		}
	}

	LaunchedEffect(Unit) { runCatching { mainContentFocus.requestFocus() } }
}

// region Sidebar

@Composable
private fun ArcticSidebar(
	expanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	initialFocus: FocusRequester,
	mainContentFocus: FocusRequester,
	config: LorlaMenuConfig,
	onHome: () -> Unit,
	onSearch: () -> Unit,
	onSettings: () -> Unit,
	onCategory: (CategoryMenuConfig) -> Unit,
) {
	val categories = remember(config) {
		config.categories.filter { it.visible }.sortedBy { it.order }
	}

	val targetWidth by animateDpAsState(
		targetValue = if (expanded) 216.dp else 64.dp,
		animationSpec = tween(250),
		label = "sidebar-width",
	)

	val scrollState = rememberScrollState()
	val focusStates = remember { mutableListOf<Boolean>() }
	// Re-allocate focus states when category count changes.
	val statesList = remember(categories.size) { List(categories.size + 3) { mutableStateOf(false) } }
	val anyFocused = statesList.any { it.value }
	LaunchedEffect(anyFocused) { onExpandedChange(anyFocused) }

		Column(
			modifier = Modifier
				.fillMaxHeight()
				.width(targetWidth)
				.background(
					Brush.horizontalGradient(
						0.00f to JellyfinTheme.colorScheme.background.copy(alpha = if (expanded) 0.88f else 0.60f),
						0.70f to JellyfinTheme.colorScheme.background.copy(alpha = if (expanded) 0.50f else 0.0f),
						1.00f to Color.Transparent,
					),
				)
				.padding(vertical = 100.dp)
				.verticalScroll(scrollState)
				.onPreviewKeyEvent {
					if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) {
						runCatching { mainContentFocus.requestFocus() }
						true
					} else {
						false
					}
				},
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
		) {
		SidebarItem(
			icon = R.drawable.ic_house,
			label = "首页",
			expanded = expanded,
			modifier = Modifier.focusRequester(initialFocus),
			onFocusChange = {
				statesList[0].value = it
				if (it) onHome()
			},
			onClick = {
				onHome()
				runCatching { mainContentFocus.requestFocus() }
			},
		)
		SidebarItem(
			icon = R.drawable.ic_search,
			label = "搜索",
			expanded = expanded,
			onFocusChange = { statesList[1].value = it },
			onClick = onSearch,
		)
		categories.forEachIndexed { index, cat ->
			SidebarItem(
				icon = cat.programType.iconRes(),
				label = cat.label,
				expanded = expanded,
				onFocusChange = {
					statesList[index + 2].value = it
					if (it) onCategory(cat)
				},
				onClick = {
					onCategory(cat)
					runCatching { mainContentFocus.requestFocus() }
				},
			)
		}
		SidebarItem(
			icon = R.drawable.ic_settings,
			label = "系统设置",
			expanded = expanded,
			onFocusChange = { statesList[categories.size + 2].value = it },
			onClick = onSettings,
		)
	}
}

@Composable
private fun SidebarItem(
	icon: Int,
	label: String,
	expanded: Boolean,
	onFocusChange: (Boolean) -> Unit = {},
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(50.dp)
			.padding(horizontal = if (expanded) 12.dp else 8.dp)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.16f) else Color.Transparent,
				RoundedCornerShape(10.dp),
			)
			.onFocusChanged {
				focused = it.hasFocus
				onFocusChange(it.hasFocus)
			}
			.clickable(onClick = onClick)
			.padding(horizontal = if (expanded) 12.dp else 0.dp)
			.animateContentSize(),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = if (expanded) Arrangement.spacedBy(12.dp, Alignment.Start) else Arrangement.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(icon),
			contentDescription = label,
			tint = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
			modifier = Modifier.size(22.dp),
		)

		if (expanded) {
			Text(
				label,
				color = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.85f),
				style = JellyfinTheme.typography.default.copy(
					fontSize = 14.sp,
					fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
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
	selectedCategory: CategoryMenuConfig?,
	heroItem: BaseItemDto?,
	heroCount: Int,
	heroIndex: Int,
	onHeroIndexChange: (Int) -> Unit,
	homeRows: List<ArcticRow>,
	categoryRows: List<ArcticRow>,
	categoryLoading: Boolean,
	loaded: Boolean,
	layoutMode: HomeLayoutMode,
	heroHeight: Dp,
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
	onCycleLayoutMode: () -> Unit,
	initialFocus: FocusRequester,
	sidebarFocus: FocusRequester,
) {
	val scrollState = rememberScrollState()

	// Whenever the active category changes, snap the scroll back to the top so the
	// hero and first row are visible instead of leaving the user stuck mid-scroll.
	LaunchedEffect(selectedCategory) {
		scrollState.scrollTo(0)
	}

	Column(
		Modifier
			.fillMaxSize()
			.then(modifier)
			.verticalScroll(scrollState)
			.onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft) {
					runCatching { sidebarFocus.requestFocus() }
					true
				} else {
					false
				}
			},
	) {
		val isHome = selectedCategory == null
		// Hero is now always full-screen; rows below are revealed only by scrolling.
		val showRows = when {
			!isHome -> true
			else -> !layoutMode.isFullScreenHero
		}

			HeroStage(
				modifier = Modifier
					.fillMaxWidth()
					.height(heroHeight),
				item = heroItem,
				featuredCount = heroCount,
				heroIndex = heroIndex,
				layoutMode = layoutMode,
				onPlay = { heroItem?.let(onHeroPlay) },
				onInfo = { heroItem?.let(onHeroInfo) },
				onNextFeatured = {
					if (heroCount > 0) onHeroIndexChange((heroIndex + 1) % heroCount)
				},
				onPreviousFeatured = {
					if (heroCount > 0) onHeroIndexChange((heroIndex - 1 + heroCount) % heroCount)
				},
				onCycleLayoutMode = onCycleLayoutMode,
				initialFocus = initialFocus,
			)

			if (showRows) {
				// Opaque background so rows cover the full-bleed hero backdrop when scrolled up.
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.background(JellyfinTheme.colorScheme.background),
				) {
					if (!loaded || (selectedCategory != null && categoryLoading)) {
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
					} else if (selectedCategory != null) {
						if (categoryRows.isEmpty()) {
							Box(
								Modifier
									.fillMaxWidth()
									.padding(top = 28.dp),
								contentAlignment = Alignment.Center,
							) {
								Text(
									"暂无内容",
									color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
									style = JellyfinTheme.typography.default.copy(fontSize = 18.sp),
								)
							}
						} else {
							categoryRows.forEach { ArcticRowView(it.title, it.items, layoutMode, onItemClick) }
						}
					} else {
						homeRows.forEach { ArcticRowView(it.title, it.items, layoutMode, onItemClick) }
					}
					Spacer(Modifier.height(48.dp))
				}
			}
		}
	}

// endregion

// region Hero stage

@Composable
private fun HeroStage(
	modifier: Modifier = Modifier,
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	layoutMode: HomeLayoutMode,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onCycleLayoutMode: () -> Unit,
	initialFocus: FocusRequester,
) {
	// The backdrop is now drawn full-bleed by ArcticHomeScreen behind the sidebar.
	// This container only holds the info panel so text/buttons scroll with the rows.
	Box(modifier = modifier) {
		ArcticInfoPanel(
			modifier = Modifier
				.fillMaxWidth()
				.padding(
					start = view_side_dp,
					top = 120.dp,
					end = view_pad_dp,
					bottom = 60.dp,
				)
				.align(Alignment.BottomStart),
			item = item,
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onCycleLayoutMode = onCycleLayoutMode,
			layoutMode = layoutMode,
			initialFocus = initialFocus,
		)
	}
}

@Composable
private fun HeroBackground(
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
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)
		} else {
			Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background))
		}

		// Single uniform scrim: just enough darkness for left text + bottom controls.
		// No heavy bottom band — the poster must stay fully visible edge-to-edge.
		Box(
			Modifier
				.fillMaxSize()
				.background(
					Brush.verticalGradient(
						0.00f to Color.Transparent,
						0.45f to Color.Transparent,
						0.78f to JellyfinTheme.colorScheme.background.copy(alpha = 0.45f),
						0.92f to JellyfinTheme.colorScheme.background.copy(alpha = 0.72f),
						1.00f to JellyfinTheme.colorScheme.background,
					),
				),
		)

		// Left darken so title/meta text on the left side has good contrast.
		Box(
			Modifier
				.fillMaxSize()
				.background(
					Brush.horizontalGradient(
						0.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.55f),
						0.20f to JellyfinTheme.colorScheme.background.copy(alpha = 0.30f),
						0.45f to Color.Transparent,
						1.00f to Color.Transparent,
					),
				),
		)
	}
}

@Composable
private fun ArcticInfoPanel(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onCycleLayoutMode: () -> Unit,
	layoutMode: HomeLayoutMode,
	initialFocus: FocusRequester,
	modifier: Modifier = Modifier,
) {
	// rememberBringIntoViewRequester: when focus enters the button row below, the
	// parent scroll container scrolls the row fully into view (and the hero above it).
	val bringIntoViewRequester = remember { BringIntoViewRequester() }
	val bringIntoViewScope = rememberCoroutineScope()

	Column(
		modifier = modifier.fillMaxHeight(),
		verticalArrangement = Arrangement.Bottom,
	) {
		Spacer(Modifier.weight(1f))
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

		// Fixed bottom row: play/info labeled pills + dots + mode label.
		// Pills are always-visible (not focus-dependent for visibility) so users
		// can see what controls are available regardless of focus state.
		// The bringIntoViewRequester triggers a scroll so the row (and the hero above it)
		// becomes fully visible whenever any of its children receives focus.
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.bringIntoViewRequester(bringIntoViewRequester)
				.onFocusChanged { state ->
					if (state.isFocused) {
						bringIntoViewScope.launch {
							bringIntoViewRequester.bringIntoView()
						}
					}
				},
		) {
			HeroPillButton(
				label = "播放",
				iconRes = R.drawable.ic_play,
				isPrimary = true,
				onClick = onPlay,
				onLeft = onPreviousFeatured,
				focusRequester = initialFocus,
			)

			HeroPillButton(
				label = "更多信息",
				iconRes = R.drawable.ic_info,
				isPrimary = false,
				onClick = onInfo,
				onRight = onNextFeatured,
			)

			if (featuredCount > 1) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(6.dp),
					modifier = Modifier.padding(start = 16.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					repeat(featuredCount) { i ->
						Box(
							Modifier
								.size(width = if (i == heroIndex) 22.dp else 6.dp, height = 6.dp)
								.background(
									if (i == heroIndex) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.45f),
									RoundedCornerShape(3.dp),
								),
						)
					}
				}
			}

			Spacer(Modifier.width(8.dp))

			HeroModePill(
				label = layoutMode.label,
				onClick = onCycleLayoutMode,
			)
		}
	}
}

@Composable
private fun HeroPillButton(
	label: String,
	iconRes: Int,
	isPrimary: Boolean,
	onClick: () -> Unit,
	onLeft: (() -> Unit)? = null,
	onRight: (() -> Unit)? = null,
	focusRequester: FocusRequester? = null,
) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(10.dp)

	val bg = when {
		focused -> JellyfinTheme.colorScheme.buttonFocused
		isPrimary -> JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.92f)
		else -> Color.Transparent
	}
	val content = when {
		focused -> JellyfinTheme.colorScheme.onButtonFocused
		isPrimary -> JellyfinTheme.colorScheme.onButtonFocused
		else -> JellyfinTheme.colorScheme.onBackground
	}
	val borderColor = when {
		focused -> Color.Transparent
		isPrimary -> Color.Transparent
		else -> JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f)
	}

	Row(
		modifier = Modifier
			.then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
			.height(44.dp)
			.background(bg, shape)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = borderColor,
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown) {
					when (it.key) {
						Key.DirectionLeft -> {
							onLeft?.invoke()
							true
						}
						Key.DirectionRight -> {
							onRight?.invoke()
							true
						}
						else -> false
					}
				} else {
					false
				}
			}
			.clickable(onClick = onClick)
			.padding(horizontal = 18.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(iconRes),
			contentDescription = null,
			tint = content,
			modifier = Modifier.size(18.dp),
		)
		Text(
			label,
			color = content,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 15.sp,
				fontWeight = FontWeight.SemiBold,
				letterSpacing = 0.4.sp,
			),
			maxLines = 1,
		)
	}
}

@Composable
private fun HeroModePill(
	label: String,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(10.dp)

	Row(
		modifier = Modifier
			.height(44.dp)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused
				else JellyfinTheme.colorScheme.surface.copy(alpha = 0.50f),
				shape,
			)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = if (focused) Color.Transparent
					else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.40f),
				shape = shape,
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(horizontal = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			label,
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused
				else JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontSize = 13.sp,
				fontWeight = FontWeight.SemiBold,
			),
			maxLines = 1,
		)
		Text(
			"⇄",
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused
				else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 14.sp,
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

private val view_side_dp = 24.dp
private val view_pad_dp = 24.dp

// endregion
