package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
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

	// Hero layout is a GLOBAL preference and nothing on this page may write it.
	// It is picked in Settings -> Home -> FUSE 大舞台展示方式, exactly like the
	// skin's own SkinSettings. The remote used to be able to cycle this value,
	// which is how the big stage could silently end up on NO_STAGE and vanish.
	val heroLayoutMode = remember { HeroLayoutModePreferences.get(context) }
	val sidebarMode = remember { SidebarModePreferences.get(context) }

	var homeHeroIndex by remember { mutableIntStateOf(0) }
	var categoryHeroIndex by remember { mutableIntStateOf(0) }
	var sidebarExpanded by remember { mutableStateOf(false) }

	// Bumped by every BACK press so focus can be re-armed on the play button.
	var backTick by remember { mutableIntStateOf(0) }

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

	// The remote's BACK key must NEVER drop the user onto the TV launcher.
	// On the home screen it either leaves a category (back to the home feed) or
	// simply re-arms focus on the big stage's play button - it is always
	// swallowed, so the app can only be left with the TV's own HOME key.
	BackHandler(enabled = true) {
		if (selectedCategory != null) selectedCategory = null
		backTick += 1
	}

	BoxWithConstraints(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background).padding(top = 24.dp)) {
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

	// Entering the screen, and every BACK press, parks the remote on the big
	// stage's play button. The stage reacts to gaining focus by gliding the page
	// back to the top, so BACK always lands on a clean, fully visible home.
	LaunchedEffect(backTick) {
		delay(80)
		runCatching { mainContentFocus.requestFocus() }
	}
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
					0.00f to JellyfinTheme.colorScheme.background.copy(alpha = if (expanded) 0.94f else 0.82f),
					0.80f to JellyfinTheme.colorScheme.background.copy(alpha = if (expanded) 0.94f else 0.82f),
					1.00f to Color.Transparent,
				),
			)
			.padding(vertical = 72.dp)
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
	// LEFT on the leftmost hero control returns to the rail entry that matches
	// the current view, same rule the poster rows follow.
	val heroSidebarFocus = if (selectedCategory == null) homeSidebarFocus else categorySidebarFocus

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
		// MINI_STAGE is a short top strip, not a full screen.
		val stageHeight = when (heroLayoutMode) {
			HeroLayoutMode.SLIDE_STAGE -> heroHeight * 0.55f
			HeroLayoutMode.MINI_STAGE -> heroHeight * 0.34f
			else -> heroHeight
		}
		if (heroLayoutMode != HeroLayoutMode.NO_STAGE) {
			ArcticHeroStage(
				modifier = Modifier
					.fillMaxWidth()
					.height(stageHeight),
				item = heroItem,
				featuredCount = heroCount,
				heroIndex = heroIndex,
				layoutMode = heroLayoutMode,
				onPlay = { heroItem?.let(onHeroPlay) },
				onInfo = { heroItem?.let(onHeroInfo) },
				onNextFeatured = {
					if (heroCount > 0) onHeroIndexChange((heroIndex + 1) % heroCount)
				},
				onPreviousFeatured = {
					if (heroCount > 0) onHeroIndexChange((heroIndex - 1 + heroCount) % heroCount)
				},
				onDown = { runCatching { firstRowFocus.requestFocus() } },
				onLeftEdge = { runCatching { heroSidebarFocus.requestFocus() } },
				playFocus = initialFocus,
				scrollState = scrollState,
			)
		}

		// Soft bridge: the dark hero base dissolves into the poster wall so the
		// hero/poster junction has no hard line (FUSE-style gradient blend).
		Box(
			Modifier
				.fillMaxWidth()
				.height(72.dp)
				.background(
					Brush.verticalGradient(
						0.00f to JellyfinTheme.colorScheme.background,
						1.00f to Color.Transparent,
					),
				),
		)

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
				// When the big stage is hidden (NO_STAGE) the mainContentFocus anchor
				// would be missing and the sidebar's RIGHT key has nothing to land on,
				// stranding the remote on the rail. Re-use mainContentFocus as the
				// first-row-first-poster anchor in that case so the chain stays live.
				val effectiveFirstRowFocus = if (heroLayoutMode == HeroLayoutMode.NO_STAGE) initialFocus else firstRowFocus
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
					firstRowFocus = if (index == 0) effectiveFirstRowFocus else null,
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

// region Hero helpers

// The big stage itself now lives in ArcticHeroStage.kt - see the notes there
// for why it was rebuilt (it used to be able to write NO_STAGE into the
// preference store from the remote and disappear for good).

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

	// Title row + poster row must stack vertically (Column), not pile on top of
	// each other — a bare BoxWithConstraints would overlay the posters on the
	// title and its layout-switch button.
	Column {
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
