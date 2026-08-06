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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import org.jellyfin.androidtv.preference.HeroLayoutMode
import org.jellyfin.androidtv.preference.HeroLayoutModePreferences
import org.jellyfin.androidtv.preference.SidebarMode
import org.jellyfin.androidtv.preference.SidebarModePreferences
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
import org.jellyfin.androidtv.util.apiclient.primaryImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.koin.compose.koinInject
import kotlin.math.roundToInt

// region Data model

data class ArcticRow(
	val title: String,
	val items: List<BaseItemDto>,
	val layoutMode: RowLayoutMode = RowLayoutMode.PORTRAIT,
)

private data class ClassifiedItem(
	val item: BaseItemDto,
	val programType: ProgramType,
	val bucket: RegionBucket,
	val subRowId: String?,
)

/**
 * Rows below the hero support only two display modes.
 * Each row stores its own mode so one wall can mix portrait and landscape rows.
 */
enum class RowLayoutMode {
	PORTRAIT,
	LANDSCAPE,
}

private val RowLayoutMode.label: String
	get() = when (this) {
		RowLayoutMode.PORTRAIT -> "竖屏"
		RowLayoutMode.LANDSCAPE -> "横幅"
	}

private val RowLayoutMode.next: RowLayoutMode
	get() = when (this) {
		RowLayoutMode.PORTRAIT -> RowLayoutMode.LANDSCAPE
		RowLayoutMode.LANDSCAPE -> RowLayoutMode.PORTRAIT
	}

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

	// Hero layout is a global preference (Settings -> Home -> FUSE 大舞台展示方式).
	// The remote can also cycle it from the big stage: press RIGHT on the Info
	// button 8 times to enter layout-switch mode, then RIGHT cycles the 6 modes.
	// Writes go straight back to the preference store.
	var heroLayoutMode by remember { mutableStateOf(HeroLayoutModePreferences.get(context)) }
	val sidebarMode = remember { SidebarModePreferences.get(context) }

	var homeHeroIndex by remember { mutableIntStateOf(0) }
	var categoryHeroIndex by remember { mutableIntStateOf(0) }
	var sidebarExpanded by remember { mutableStateOf(false) }

	val sidebarFocus = remember { FocusRequester() }
	val mainContentFocus = remember { FocusRequester() }

	// One FocusRequester per left-rail entry so any row's leftmost poster (←) can
	// jump straight to the matching rail item: index 0 = Home, 1 = Search,
	// 2.. = categories in order, last = Settings.
	val categoriesList = remember(menuConfig) {
		menuConfig.categories.filter { it.visible }.sortedBy { it.order }
	}
	val sidebarItemFocus = remember(categoriesList.size) {
		List(categoriesList.size + 3) { FocusRequester() }
	}
	val homeSidebarFocus = sidebarItemFocus[0]
	val categorySidebarFocus = remember(selectedCategory, categoriesList) {
		val idx = categoriesList.indexOf(selectedCategory)
		if (idx >= 0) sidebarItemFocus[idx + 2] else sidebarItemFocus[0]
	}

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

				// Featured stage: up to 7 newest items with an image. The big stage
				// is capped at 7 — the remote steps through them with RIGHT on the
				// Info button, and the 8th press enters layout-switch mode.
				val featured = classified
					.map { it.item }
					.filter { it.itemBackdropImages.isNotEmpty() || it.itemImages.isNotEmpty() }
					.distinctBy { it.id }
					.take(7)

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
			// Cap the category big stage at 7 too, same as the home one.
			val heroItems = matching.map { it.item }.distinctBy { it.id }.take(7)
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
		// Netflix-style: the hero is the FIRST screen of one vertical scroll. It is NOT
		// pinned — when you browse down, the whole page scrolls up and the hero glides
		// off the top, then the first row occupies the screen fully. No half-poster
		// peeking from under a fixed banner.
		val heroHeight = screenHeight * 0.88f

		val scrollState = rememberScrollState()
		val firstRowFocus = remember { FocusRequester() }
		val homeScope = rememberCoroutineScope()

		// One scroll container holds hero + rows. The left sidebar stays as a fixed
		// overlay on top so it is always reachable.
		ArcticMainContent(
			modifier = Modifier.fillMaxSize(),
			scrollState = scrollState,
			firstRowFocus = firstRowFocus,
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
			heroLayoutMode = heroLayoutMode,
			onHeroLayoutModeChange = { newMode ->
				HeroLayoutModePreferences.set(context, newMode)
				heroLayoutMode = newMode
			},
			sidebarExpanded = sidebarExpanded,
			sidebarMode = sidebarMode,
			heroHeight = heroHeight,
			onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
			onHeroPlay = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
			onHeroInfo = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
			initialFocus = mainContentFocus,
			homeSidebarFocus = homeSidebarFocus,
			categorySidebarFocus = categorySidebarFocus,
		)

		ArcticSidebar(
			expanded = sidebarExpanded,
			onExpandedChange = { sidebarExpanded = it },
			mode = sidebarMode,
			itemFocusRequesters = sidebarItemFocus,
			mainContentFocus = mainContentFocus,
			config = menuConfig,
			onHome = {
				if (selectedCategory != null) {
					selectedCategory = null
					homeScope.launch { runCatching { scrollState.scrollTo(0) } }
				}
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
				if (selectedCategory != cat) {
					selectedCategory = cat
					Toast.makeText(context, "正在加载 ${cat.label}", Toast.LENGTH_SHORT).show()
				}
			},
		)
	}

	LaunchedEffect(Unit) { runCatching { mainContentFocus.requestFocus() } }
}

// region Sidebar

@Composable
private fun ArcticSidebar(
	expanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	mode: SidebarMode,
	itemFocusRequesters: List<FocusRequester>,
	mainContentFocus: FocusRequester,
	config: LorlaMenuConfig,
	onHome: () -> Unit,
	onSearch: () -> Unit,
	onSettings: () -> Unit,
	onCategory: (CategoryMenuConfig) -> Unit,
) {
	// HIDDEN mode removes the rail entirely (Settings -> Home -> 左侧菜单显示方式).
	if (mode == SidebarMode.HIDDEN) return

	val categories = remember(config) {
		config.categories.filter { it.visible }.sortedBy { it.order }
	}

	// ICONS_ONLY pins the rail at 64dp and never shows labels; ICONS_AND_LABELS
	// animates 64 <-> 216 and shows labels when expanded.
	val showLabels = mode == SidebarMode.ICONS_AND_LABELS
	val targetWidth by animateDpAsState(
		targetValue = if (mode == SidebarMode.ICONS_ONLY) 64.dp
			else if (expanded) 216.dp else 64.dp,
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
			expanded = showLabels,
			modifier = Modifier.focusRequester(itemFocusRequesters[0]),
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
			expanded = showLabels,
			modifier = Modifier.focusRequester(itemFocusRequesters[1]),
			onFocusChange = { statesList[1].value = it },
			onClick = onSearch,
		)
		categories.forEachIndexed { index, cat ->
			SidebarItem(
				icon = cat.programType.iconRes(),
				label = cat.label,
				expanded = showLabels,
				modifier = Modifier.focusRequester(itemFocusRequesters[index + 2]),
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
			expanded = showLabels,
			modifier = Modifier.focusRequester(itemFocusRequesters[categories.size + 2]),
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
	scrollState: ScrollState,
	firstRowFocus: FocusRequester,
	selectedCategory: CategoryMenuConfig?,
	heroItem: BaseItemDto?,
	heroCount: Int,
	heroIndex: Int,
	onHeroIndexChange: (Int) -> Unit,
	homeRows: List<ArcticRow>,
	categoryRows: List<ArcticRow>,
	categoryLoading: Boolean,
	loaded: Boolean,
	heroLayoutMode: HeroLayoutMode,
	onHeroLayoutModeChange: (HeroLayoutMode) -> Unit,
	sidebarExpanded: Boolean,
	sidebarMode: SidebarMode,
	heroHeight: Dp,
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
	initialFocus: FocusRequester,
	homeSidebarFocus: FocusRequester,
	categorySidebarFocus: FocusRequester,
) {
	// Snap back to the top whenever the active category changes.
	LaunchedEffect(selectedCategory) {
		runCatching { scrollState.scrollTo(0) }
	}

	// Local mutable copy of row modes so each row can toggle independently.
	var rowModesHome by remember { mutableStateOf<Map<String, RowLayoutMode>>(emptyMap()) }
	var rowModesCategory by remember { mutableStateOf<Map<String, RowLayoutMode>>(emptyMap()) }

	// Y of the scroll container on screen, used by rows to compute their own
	// content Y so focus can glide the page to the exact top of the focused row.
	var containerY by remember { mutableIntStateOf(0) }

	// When the rail expands, the whole page shifts right so it is never covered —
	// the rail is an overlay, and the content simply makes room for it.
	val contentLeft by animateDpAsState(
		targetValue = when {
			sidebarMode == SidebarMode.HIDDEN -> 0.dp
			sidebarExpanded && sidebarMode == SidebarMode.ICONS_AND_LABELS -> 216.dp
			else -> 64.dp
		},
		animationSpec = tween(250),
		label = "content-left",
	)

	// One vertical scroll holds the hero (first screen) and all rows below it, so the
	// whole page glides as a unit — exactly like Netflix's TV home.
	Column(
		Modifier
			.fillMaxSize()
			.padding(start = contentLeft)
			.then(modifier)
			.verticalScroll(scrollState)
			.onGloballyPositioned { containerY = it.positionInWindow().y.roundToInt() },
	) {
		// NO_STAGE removes the big stage entirely (rows start at the top).
		// SLIDE_STAGE renders the same standard layout but at half height.
		val stageHeight = if (heroLayoutMode == HeroLayoutMode.SLIDE_STAGE) heroHeight * 0.55f else heroHeight
		if (heroLayoutMode != HeroLayoutMode.NO_STAGE) {
			HeroStage(
				modifier = Modifier
					.fillMaxWidth()
					.height(stageHeight),
				item = heroItem,
				featuredCount = heroCount,
				heroIndex = heroIndex,
				layoutMode = heroLayoutMode,
				onLayoutModeChange = onHeroLayoutModeChange,
				onPlay = { heroItem?.let(onHeroPlay) },
				onInfo = { heroItem?.let(onHeroInfo) },
				onNextFeatured = {
					if (heroCount > 0) onHeroIndexChange((heroIndex + 1) % heroCount)
				},
				onPreviousFeatured = {
					if (heroCount > 0) onHeroIndexChange((heroIndex - 1 + heroCount) % heroCount)
				},
				initialFocus = initialFocus,
				scrollState = scrollState,
				onDown = { runCatching { firstRowFocus.requestFocus() } },
			)
		}

		if (!loaded || (selectedCategory != null && categoryLoading)) {
			Box(
				Modifier
					.fillMaxWidth()
					.padding(top = 24.dp),
				contentAlignment = Alignment.Center,
			) {
				CircularProgressIndicator(
					modifier = Modifier.size(40.dp),
					color = JellyfinTheme.colorScheme.buttonFocused,
				)
			}
		} else if (selectedCategory != null && categoryRows.isEmpty()) {
			Box(
				Modifier
					.fillMaxWidth()
					.padding(top = 24.dp),
				contentAlignment = Alignment.Center,
			) {
				Text(
					"暂无内容",
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
					style = JellyfinTheme.typography.default.copy(fontSize = 18.sp),
				)
			}
		} else {
			// Leftmost poster of ANY row jumps to the rail item that matches the
			// current view: Home row -> Home entry, category row -> that category.
			val rowSidebarFocus = if (selectedCategory == null) homeSidebarFocus else categorySidebarFocus
			val rows = if (selectedCategory != null) categoryRows else homeRows
			val rowModes = if (selectedCategory != null) rowModesCategory else rowModesHome
			val setRowModes = if (selectedCategory != null) { modes: Map<String, RowLayoutMode> -> rowModesCategory = modes } else { modes: Map<String, RowLayoutMode> -> rowModesHome = modes }

			rows.forEachIndexed { index, row ->
				val mode = rowModes[row.title] ?: row.layoutMode
				ArcticRowView(
					title = row.title,
					items = row.items,
					layoutMode = mode,
					onLayoutModeChange = { newMode ->
						setRowModes(rowModes.toMutableMap().apply { put(row.title, newMode) })
					},
					onItemClick = onItemClick,
					index = index,
					isFirstRow = index == 0,
					heroFocus = initialFocus,
					sidebarFocus = rowSidebarFocus,
					firstRowFocus = if (index == 0) firstRowFocus else null,
					scrollState = scrollState,
					containerY = containerY,
					sidebarMode = sidebarMode,
					sidebarExpanded = sidebarExpanded,
				)
			}
		}
		Spacer(Modifier.height(48.dp))
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
	layoutMode: HeroLayoutMode,
	onLayoutModeChange: (HeroLayoutMode) -> Unit,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	initialFocus: FocusRequester,
	scrollState: ScrollState,
	onDown: () -> Unit,
) {
	val scope = rememberCoroutineScope()
	val context = LocalContext.current
	// Layout-switch state lives HERE (not in HeroControlRow): when the layout
	// changes, the row below recomposes, but this stage does not, so the counter
	// survives a switch.
	var rightPressCount by remember { mutableIntStateOf(0) }
	var switchingLayout by remember { mutableStateOf(false) }

	val onInfoRight = {
		if (switchingLayout) {
			// Layout-switch mode: RIGHT cycles the 6 hero layouts (persisted).
			val entries = HeroLayoutMode.entries
			val next = entries[(HeroLayoutModePreferences.get(context).ordinal + 1) % entries.size]
			onLayoutModeChange(next)
			Toast.makeText(context, "大舞台：${next.label}", Toast.LENGTH_SHORT).show()
		} else {
			rightPressCount += 1
			if (rightPressCount >= 8) {
				// The 8th press enters layout-switch mode and applies one switch.
				switchingLayout = true
				rightPressCount = 0
				val entries = HeroLayoutMode.entries
				val next = entries[(HeroLayoutModePreferences.get(context).ordinal + 1) % entries.size]
				onLayoutModeChange(next)
				Toast.makeText(context, "大舞台：${next.label}（按其他键退出）", Toast.LENGTH_SHORT).show()
			} else {
				onNextFeatured()
			}
		}
	}
	val onExitLayoutSwitch = {
		if (switchingLayout) {
			switchingLayout = false
			rightPressCount = 0
		}
	}

	// When focus returns to the hero (e.g. UP from the first row), glide the whole
	// page back to the top so the big stage is fully visible again.
	Box(
		modifier
			.onFocusChanged {
				if (it.isFocused) scope.launch { runCatching { scrollState.animateScrollTo(0) } }
			},
	) {
		// Backdrop now lives INSIDE the scroll, so it scrolls away with the page
		// instead of staying pinned behind the rows.
		HeroBackground(
			item = item,
			modifier = Modifier.fillMaxSize(),
		)

		when (layoutMode) {
			HeroLayoutMode.FULL_BLEED_LEFT_INFO,
			HeroLayoutMode.SLIDE_STAGE,
			-> HeroInfoPanelLeft(
				modifier = Modifier.fillMaxSize(),
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onLayoutModeChange = onLayoutModeChange,
				switchingLayout = switchingLayout,
				onInfoRight = onInfoRight,
				onExitLayoutSwitch = onExitLayoutSwitch,
				initialFocus = initialFocus,
				onDown = onDown,
			)

			HeroLayoutMode.SHOWCASE_COLLAGE -> HeroShowcaseCollage(
				modifier = Modifier.fillMaxSize(),
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onLayoutModeChange = onLayoutModeChange,
				switchingLayout = switchingLayout,
				onInfoRight = onInfoRight,
				onExitLayoutSwitch = onExitLayoutSwitch,
				initialFocus = initialFocus,
				onDown = onDown,
			)

			HeroLayoutMode.FANART_ONLY -> HeroFanartOnly(
				modifier = Modifier.fillMaxSize(),
				item = item,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onLayoutModeChange = onLayoutModeChange,
				switchingLayout = switchingLayout,
				onInfoRight = onInfoRight,
				onExitLayoutSwitch = onExitLayoutSwitch,
				initialFocus = initialFocus,
				onDown = onDown,
			)

			HeroLayoutMode.MINI_STAGE -> HeroMiniStage(
				modifier = Modifier.fillMaxSize(),
				item = item,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onLayoutModeChange = onLayoutModeChange,
				switchingLayout = switchingLayout,
				onInfoRight = onInfoRight,
				onExitLayoutSwitch = onExitLayoutSwitch,
				initialFocus = initialFocus,
				onDown = onDown,
			)

			HeroLayoutMode.NO_STAGE -> Unit
		}
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
private fun HeroInfoPanelLeft(
	modifier: Modifier = Modifier,
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onLayoutModeChange: (HeroLayoutMode) -> Unit,
	switchingLayout: Boolean,
	onInfoRight: () -> Unit,
	onExitLayoutSwitch: () -> Unit,
	initialFocus: FocusRequester,
	onDown: () -> Unit,
) {
	val bringIntoViewRequester = remember { BringIntoViewRequester() }
	val bringIntoViewScope = rememberCoroutineScope()

	Column(
		modifier = modifier
			.fillMaxHeight()
			.padding(
				start = 24.dp,
				top = 120.dp,
				end = view_pad_dp,
				bottom = 60.dp,
			),
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

			HeroMetaRow(item = item)

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

		HeroControlRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(top = 24.dp)
				.bringIntoViewRequester(bringIntoViewRequester)
				.onFocusChanged { state ->
					if (state.isFocused) {
						bringIntoViewScope.launch {
							bringIntoViewRequester.bringIntoView()
						}
					}
				},
			onDown = onDown,
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onLayoutModeChange = onLayoutModeChange,
			switchingLayout = switchingLayout,
			onInfoRight = onInfoRight,
			onExitLayoutSwitch = onExitLayoutSwitch,
			initialFocus = initialFocus,
		)
	}
}

@Composable
private fun HeroShowcaseCollage(
	modifier: Modifier = Modifier,
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onLayoutModeChange: (HeroLayoutMode) -> Unit,
	switchingLayout: Boolean,
	onInfoRight: () -> Unit,
	onExitLayoutSwitch: () -> Unit,
	initialFocus: FocusRequester,
	onDown: () -> Unit,
) {
	val api = koinInject<ApiClient>()
	// Cast collage: pull up to 9 people (head shots) and tile them over a dimmed
	// backdrop on the right side — the Arctic Fuse "showcase" look.
	// The people are fetched ON DEMAND for the current item (NOT via the home
	// Items request — bundling People into that list made the response huge and
	// the home screen hung on a black screen).
	var people by remember(item?.id) { mutableStateOf<List<BaseItemPerson>>(emptyList()) }
	LaunchedEffect(item?.id) {
		people = emptyList()
		if (item?.id != null) {
			runCatching {
				api.itemsApi.getPeople(itemId = item.id).content.items.orEmpty()
			}.getOrElse { emptyList() }.take(9).let { people = it }
		}
	}
	val actors = people

	Box(modifier = modifier) {
		// Dim the base backdrop a bit harder so the collage pops.
		Box(
			Modifier
				.fillMaxSize()
				.background(JellyfinTheme.colorScheme.background.copy(alpha = 0.35f)),
		)

		if (actors.isNotEmpty()) {
			// Right-side collage: 3 columns x up to 3 rows of circular head shots.
			val rows = actors.chunked(3)
			Column(
				modifier = Modifier
					.fillMaxHeight()
					.align(Alignment.CenterEnd)
					.padding(end = 40.dp),
				verticalArrangement = Arrangement.Center,
				horizontalAlignment = Alignment.End,
			) {
				rows.forEach { rowActors ->
					Row(
						horizontalArrangement = Arrangement.spacedBy(14.dp),
						modifier = Modifier.padding(vertical = 7.dp),
					) {
						rowActors.forEach { person ->
							val head = person.primaryImage
							Box(
								Modifier
									.size(88.dp)
									.clip(CircleShape)
									.background(JellyfinTheme.colorScheme.surface.copy(alpha = 0.9f))
									.border(
										2.dp,
										JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.25f),
										CircleShape,
									),
							) {
								if (head != null) {
									AsyncImage(
										url = head.getUrl(api, maxWidth = 200),
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

		// Left info tower + bottom control row — same as the standard stage.
		HeroInfoPanelLeft(
			modifier = Modifier.fillMaxSize(),
			item = item,
			featuredCount = featuredCount,
			heroIndex = heroIndex,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onLayoutModeChange = onLayoutModeChange,
			switchingLayout = switchingLayout,
			onInfoRight = onInfoRight,
			onExitLayoutSwitch = onExitLayoutSwitch,
			initialFocus = initialFocus,
			onDown = onDown,
		)
	}
}

@Composable
private fun HeroFanartOnly(
	modifier: Modifier = Modifier,
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onLayoutModeChange: (HeroLayoutMode) -> Unit,
	switchingLayout: Boolean,
	onInfoRight: () -> Unit,
	onExitLayoutSwitch: () -> Unit,
	initialFocus: FocusRequester,
	onDown: () -> Unit,
) {
	// Pure artwork stage: no info tower, just a small title top-right and the
	// control row bottom-center. The full-bleed backdrop is already painted by
	// HeroStage.
	Box(modifier = modifier) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.align(Alignment.TopEnd)
				.padding(top = 36.dp, end = 48.dp),
			horizontalAlignment = Alignment.End,
		) {
			Text(
				item?.name ?: "",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 26.sp,
					fontWeight = FontWeight.SemiBold,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.align(Alignment.BottomCenter)
				.padding(bottom = 48.dp),
		) {
			HeroControlRow(
				modifier = Modifier.fillMaxWidth(),
				onDown = onDown,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = onPlay,
				onInfo = onInfo,
				onNextFeatured = onNextFeatured,
				onPreviousFeatured = onPreviousFeatured,
				onLayoutModeChange = onLayoutModeChange,
				switchingLayout = switchingLayout,
				onInfoRight = onInfoRight,
				onExitLayoutSwitch = onExitLayoutSwitch,
				initialFocus = initialFocus,
			)
		}
	}
}

@Composable
private fun HeroMiniStage(
	modifier: Modifier = Modifier,
	item: BaseItemDto?,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onLayoutModeChange: (HeroLayoutMode) -> Unit,
	switchingLayout: Boolean,
	onInfoRight: () -> Unit,
	onExitLayoutSwitch: () -> Unit,
	initialFocus: FocusRequester,
	onDown: () -> Unit,
) {
	val api = koinInject<ApiClient>()
	val poster = item?.itemImages?.values?.firstOrNull() ?: item?.itemBackdropImages?.firstOrNull()

	// Compact top strip: small poster + title + control row, all in one line.
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(start = 36.dp, top = 24.dp, end = 36.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(24.dp),
	) {
		Box(
			Modifier
				.size(width = 76.dp, height = 112.dp)
				.clip(RoundedCornerShape(8.dp))
				.background(JellyfinTheme.colorScheme.surface.copy(alpha = 0.6f)),
		) {
			if (poster != null) {
				AsyncImage(
					url = poster.getUrl(api, maxWidth = 200),
					blurHash = poster.blurHash,
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
				item?.name ?: "",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 28.sp,
					fontWeight = FontWeight.Bold,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			HeroMetaRow(item = item)
		}

		HeroControlRow(
			modifier = Modifier.fillMaxWidth(),
			onDown = onDown,
			featuredCount = 0,
			heroIndex = 0,
			onPlay = onPlay,
			onInfo = onInfo,
			onNextFeatured = onNextFeatured,
			onPreviousFeatured = onPreviousFeatured,
			onLayoutModeChange = onLayoutModeChange,
			switchingLayout = switchingLayout,
			onInfoRight = onInfoRight,
			onExitLayoutSwitch = onExitLayoutSwitch,
			initialFocus = initialFocus,
		)
	}
}

@Composable
private fun HeroMetaRow(item: BaseItemDto?) {
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
}

@Composable
private fun HeroControlRow(
	modifier: Modifier = Modifier,
	onDown: () -> Unit,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	onNextFeatured: () -> Unit,
	onPreviousFeatured: () -> Unit,
	onLayoutModeChange: (HeroLayoutMode) -> Unit,
	switchingLayout: Boolean,
	onInfoRight: () -> Unit,
	onExitLayoutSwitch: () -> Unit,
	initialFocus: FocusRequester? = null,
) {
	// Remote interaction model for the big stage:
	//  - The only focusable control on the hero is the "更多信息" button (the play
	//    pill is decorative; the dots are never focusable).
	//  - RIGHT on Info: step to the next featured item (the dots advance with it).
	//  - The 8th RIGHT press enters layout-switch mode: every further RIGHT cycles
	//    the 6 hero layouts (persisted). Any key other than RIGHT exits the mode.
	// The counter/state live in HeroStage so they survive a layout switch.

	// Re-grab focus on the Info button whenever this row (re)enters composition,
	// which happens after a layout switch — keeps the remote on the one control.
	LaunchedEffect(Unit) {
		if (initialFocus != null) runCatching { initialFocus.requestFocus() }
	}

	Row(
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.fillMaxWidth()
			.onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown) {
					when (it.key) {
						Key.DirectionDown -> {
							// Any key that is not RIGHT also exits layout-switch mode.
							onExitLayoutSwitch()
							onDown()
							true
						}
						Key.DirectionRight -> false // handled by the Info button itself
						else -> {
							onExitLayoutSwitch()
							false
						}
					}
				} else {
					false
				}
			},
	) {
		HeroPillButton(
			label = "播放",
			iconRes = R.drawable.ic_play,
			isPrimary = true,
			canFocus = false,
			onClick = onPlay,
		)

		HeroPillButton(
			label = if (switchingLayout) "切换展示形式" else "更多信息",
			iconRes = R.drawable.ic_info,
			isPrimary = false,
			canFocus = true,
			onClick = onInfo,
			onRight = onInfoRight,
			onDown = onDown,
			focusRequester = initialFocus,
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
	}
}

@Composable
private fun HeroPillButton(
	label: String,
	iconRes: Int,
	isPrimary: Boolean,
	onClick: () -> Unit,
	canFocus: Boolean = true,
	onLeft: (() -> Unit)? = null,
	onRight: (() -> Unit)? = null,
	onDown: (() -> Unit)? = null,
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
			.then(if (canFocus) Modifier.focusRestorer() else Modifier.focusProperties { this.canFocus = false })
			.height(44.dp)
			.background(bg, shape)
			.border(
				width = if (focused) 2.dp else 1.dp,
				color = borderColor,
				shape = shape,
			)
			.then(if (canFocus) Modifier.onFocusChanged { focused = it.hasFocus } else Modifier)
			.onPreviewKeyEvent {
				if (it.type == KeyEventType.KeyDown && canFocus) {
					when (it.key) {
						Key.DirectionLeft -> {
							onLeft?.invoke()
							true
						}
						Key.DirectionRight -> {
							onRight?.invoke()
							true
						}
						Key.DirectionDown -> {
							onDown?.invoke()
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
	layoutMode: RowLayoutMode,
	onLayoutModeChange: (RowLayoutMode) -> Unit,
	onItemClick: (BaseItemDto) -> Unit,
	index: Int,
	isFirstRow: Boolean,
	heroFocus: FocusRequester,
	sidebarFocus: FocusRequester,
	firstRowFocus: FocusRequester?,
	scrollState: ScrollState,
	containerY: Int,
	sidebarMode: SidebarMode,
	sidebarExpanded: Boolean,
) {
	val scope = rememberCoroutineScope()
	// Content Y of this row inside the scroll container. When the row gains
	// focus the page glides to the exact top of the row (Netflix behaviour:
	// "when focus reaches a level, the whole page scrolls with it").
	var rowY by remember { mutableIntStateOf(0) }

	BoxWithConstraints(
		Modifier
			.fillMaxWidth()
			.padding(top = 18.dp, bottom = 6.dp)
			.onGloballyPositioned { coords ->
				rowY = (coords.positionInWindow().y - containerY + scrollState.value).roundToInt()
			}
			.onFocusChanged { focused ->
				if (focused.isFocused) {
					scope.launch {
						runCatching { scrollState.animateScrollTo((rowY - 24).coerceAtLeast(0)) }
					}
				}
			},
	) {
		// Poster width derived from how many fit fully visible in one row:
		//  - rail hidden        -> 8 posters
		//  - rail icons+labels expanded -> 7
		//  - rail icons-only / collapsed -> 7.5 (last one half peeking)
		//  - landscape rows     -> always 4
		val gap = 16.dp
		val available = maxWidth - 72.dp
		val count = when {
			layoutMode == RowLayoutMode.LANDSCAPE -> 4f
			sidebarMode == SidebarMode.HIDDEN -> 8f
			sidebarMode == SidebarMode.ICONS_AND_LABELS && sidebarExpanded -> 7f
			else -> 7.5f
		}
		val cardWidth = (available - gap * (count - 1f)) / count

		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.padding(start = 36.dp, end = 36.dp, bottom = 12.dp),
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
			Spacer(Modifier.weight(1f))
			RowLayoutModeSwitch(
				current = layoutMode,
				onChange = onLayoutModeChange,
			)
		}

		LazyRow(
			modifier = Modifier.focusRestorer(),
			contentPadding = PaddingValues(start = 36.dp, end = 36.dp),
			horizontalArrangement = Arrangement.spacedBy(gap),
		) {
			itemsIndexed(items, key = { _, it -> it.id }) { listIndex, item ->
				val extraModifier = Modifier
					.then(if (isFirstRow && listIndex == 0 && firstRowFocus != null) Modifier.focusRequester(firstRowFocus) else Modifier)
					.onPreviewKeyEvent { ev ->
						if (ev.type == KeyEventType.KeyDown) {
							when (ev.key) {
								Key.DirectionLeft -> {
									if (listIndex == 0) { runCatching { sidebarFocus.requestFocus() }; true } else false
								}
								Key.DirectionUp -> {
									if (isFirstRow && listIndex == 0) { runCatching { heroFocus.requestFocus() }; true } else false
								}
								else -> false
							}
						} else false
					}
				when (layoutMode) {
					RowLayoutMode.PORTRAIT -> PortraitPosterCard(
						item = item,
						onClick = { onItemClick(item) },
						modifier = extraModifier,
						width = cardWidth,
					)
					RowLayoutMode.LANDSCAPE -> LandscapeCard(
						item = item,
						onClick = { onItemClick(item) },
						modifier = extraModifier,
						width = cardWidth,
					)
				}
			}
		}
	}
}

@Composable
private fun RowLayoutModeSwitch(
	current: RowLayoutMode,
	onChange: (RowLayoutMode) -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Row(
		modifier = Modifier
			.height(32.dp)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused
				else JellyfinTheme.colorScheme.surface.copy(alpha = 0.40f),
				RoundedCornerShape(8.dp),
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable { onChange(current.next) }
			.padding(horizontal = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Text(
			current.label,
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused
				else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 12.sp,
				fontWeight = FontWeight.SemiBold,
			),
			maxLines = 1,
		)
		Text(
			"⇄",
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused
				else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 12.sp,
				fontWeight = FontWeight.Bold,
			),
		)
	}
}

@Composable
private fun PortraitPosterCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	width: Dp = 200.dp,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.width(width)
				.height(width * 1.5f)
				.clip(RoundedCornerShape(8.dp))
				// Light placeholder: a missing poster reads as "no art" instead of
				// a black hole against the dark background.
				.background(Color(0xFF555A63))
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
			modifier = Modifier.width(width),
		)
	}
}

@Composable
private fun LandscapeCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	width: Dp = 300.dp,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages.values.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		verticalArrangement = Arrangement.spacedBy(8.dp),
	) {
		Box(
			modifier = Modifier
				.width(width)
				.height(width * 9f / 16f)
				.clip(RoundedCornerShape(8.dp))
				// Light placeholder, same as portrait.
				.background(Color(0xFF555A63))
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
			modifier = Modifier.width(width),
		)
		Text(
			buildHeroMeta(item),
			color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.65f),
			style = JellyfinTheme.typography.default.copy(fontSize = 12.sp),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier.width(width),
		)
	}
}

private val view_side_dp = 88.dp
private val view_pad_dp = 24.dp

// endregion
