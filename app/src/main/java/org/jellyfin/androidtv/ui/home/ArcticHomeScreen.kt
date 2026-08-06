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
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.key.onKeyEvent
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
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.preference.HeroLayoutMode
import org.jellyfin.androidtv.preference.HeroLayoutModePreferences
import org.jellyfin.androidtv.preference.SidebarMode
import org.jellyfin.androidtv.preference.SidebarModePreferences
import org.jellyfin.androidtv.preference.HomeRowsPreferences
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
import org.jellyfin.sdk.model.api.UserDto
import org.koin.compose.koinInject
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.toBitmap
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

private val RowLayoutMode.next: RowLayoutMode
	get() = when (this) {
		RowLayoutMode.PORTRAIT -> RowLayoutMode.LANDSCAPE
		RowLayoutMode.LANDSCAPE -> RowLayoutMode.PORTRAIT
	}

/**
 * 初次进入首页时只生成前几个栏目，避免一次性渲染太多海报行导致卡顿。
 * 其余栏目在「设置 → 首页 → 加载全部栏目」中手动开启。
 */
private const val HOME_ROWS_INITIAL = 5

// endregion

@Composable
fun ArcticHomeScreen() {
	val context = LocalContext.current
	val api = koinInject<ApiClient>()
	val navigationRepository = koinInject<NavigationRepository>()
	// 顶部右上角用户名 / 登录服务器 chip 数据源（注入一次，订阅 StateFlow）
	val userRepository = koinInject<UserRepository>()
	val serverRepository = koinInject<ServerRepository>()
	val currentUser by userRepository.currentUser.collectAsState()
	val currentServer by serverRepository.currentServer.collectAsState()
	val menuStore = remember { MenuConfigurationStore(context) }

	var menuConfig by remember { mutableStateOf(menuStore.load()) }
	var allItems by remember { mutableStateOf<List<ClassifiedItem>>(emptyList()) }
	var featuredItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var homeRows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var loaded by remember { mutableStateOf(false) }
	// Full-screen poster wall opened from a row's 「更多」button. While set it
	// covers the whole screen; BACK closes it (never the app).
	var posterWall by remember { mutableStateOf<Pair<String, List<BaseItemDto>>?>(null) }

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

	// 大舞台 = 「当前选中项」的实时预览（FUSE Spotlight 行为）：底部竖海报被
	// 选中时，大舞台立即显示那部片的大海报 + 左侧详情。activeItem 由海报聚焦驱动。
	var activeItem by remember { mutableStateOf<BaseItemDto?>(null) }
	// 当前选中海报的主题色（边缘采样），用于全屏渐变染色。
	var themeColor by remember { mutableStateOf(Color(0xFF1A1D24)) }
	var browseIndex by remember { mutableIntStateOf(0) }
	val themeColorCache = remember { mutableMapOf<String, Color>() }
	val imageLoader = koinInject<ImageLoader>()
	var sidebarExpanded by remember { mutableStateOf(false) }

	// Bumped by every BACK press so focus can be re-armed on the play button.
	var backTick by remember { mutableIntStateOf(0) }

	val sidebarFocus = remember { FocusRequester() }
	val mainContentFocus = remember { FocusRequester() }
	// 顶部右上角「用户名 / 服务器」chip 的焦点锚点：左边 → 侧栏，下边 → 大舞台标题。
	// 加进去之后，遥控按 ↑ 走到这里再也不会全屏显示「半截大舞台」——按左还能
	// 立刻回到侧栏，按下则落到大舞台片名，等于在右上角多了一个导航出入口。
	val userChipFocus = remember { FocusRequester() }
	// 从侧栏（菜单栏）按右键进入内容区时，焦点应落在「大海报的片名」上，而不是
	// 播放键——落在播放键会让大舞台只露出半截海报，影响观感。NO_STAGE 没有片名，
	// 此时回退到首行首海报锚点。
	val heroTitleFocus = remember { FocusRequester() }
	val contentEntryFocus = if (heroLayoutMode == HeroLayoutMode.NO_STAGE) mainContentFocus else heroTitleFocus

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
				// 初次进入只生成前几个栏目，减轻一次性渲染压力；其余在设置里开启。
				val visibleRows = if (HomeRowsPreferences.getLoadAll(context)) typeRows else typeRows.take(HOME_ROWS_INITIAL)
				// 大舞台初始选中：最新带图影片（与 featured 一致）。
				val initialActive = featured.firstOrNull() ?: typeRows.firstOrNull()?.items?.firstOrNull()

				withContext(Dispatchers.Main) {
					allItems = classified
					featuredItems = featured
					homeRows = visibleRows
					if (activeItem == null) activeItem = initialActive
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
		browseIndex = 0
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
				// 进入分类时把大舞台切换到该分类的首个影片。
				if (cat != null) {
					activeItem = heroItems.firstOrNull() ?: rows.firstOrNull()?.items?.firstOrNull()
					browseIndex = 0
				}
				categoryLoading = false
			}
		}
	}

	// 浏览列表：大舞台左右箭头、海报选中都在其中步进。随视图（首页/分类）变化。
	val browseList = remember(homeRows, categoryRows, featuredItems, categoryHeroItems, selectedCategory) {
		if (selectedCategory == null) (homeRows.flatMap { it.items } + featuredItems).distinctBy { it.id }
		else (categoryHeroItems + categoryRows.flatMap { it.items }).distinctBy { it.id }
	}

	// 海报聚焦驱动大舞台：更新选中项并在浏览列表中定位（用于左右箭头高亮）。
	val setActiveItem: (BaseItemDto?) -> Unit = { item ->
		activeItem = item
		val idx = browseList.indexOfFirst { it.id == item?.id }
		if (idx >= 0) browseIndex = idx
	}

	// 选中海报的主题色：从背景图边缘采样，缓存避免重复网络拉取与计算。
	LaunchedEffect(activeItem?.id) {
		val item = activeItem ?: return@LaunchedEffect
		themeColorCache[item.id.toString()]?.let { cached ->
			themeColor = cached
			return@LaunchedEffect
		}
		val bd = item.itemBackdropImages.firstOrNull() ?: item.itemImages.values.firstOrNull() ?: return@LaunchedEffect
		val url = bd.getUrl(api, maxWidth = 240)
		withContext(Dispatchers.IO) {
			runCatching {
				val bmp = imageLoader.execute(
					ImageRequest.Builder(context).data(url).size(96, 54).build(),
				).image?.toBitmap()
				bmp?.let {
					val c = extractEdgeColor(it)
					themeColorCache[item.id.toString()] = c
					withContext(Dispatchers.Main) { themeColor = c }
				}
			}
		}
	}

	// The remote's BACK key must NEVER drop the user onto the TV launcher.
	// On the home screen it either leaves a category (back to the home feed) or
	// simply re-arms focus on the big stage's play button - it is always
	// swallowed, so the app can only be left with the TV's own HOME key.
	BackHandler(enabled = true) {
		when {
			posterWall != null -> posterWall = null
			selectedCategory != null -> selectedCategory = null
			else -> backTick += 1
		}
	}

	// 全屏按当前海报主题色做渐变底（大舞台、海报下方空隙、左侧 L 空间都染色）。
	BoxWithConstraints(Modifier.fillMaxSize().background(
		Brush.verticalGradient(
			0.00f to themeColor.copy(alpha = 0.45f),
			0.45f to themeColor.copy(alpha = 0.16f),
			1.00f to JellyfinTheme.colorScheme.background,
		),
	).padding(top = 24.dp)) {
		val screenHeight = maxHeight
		// Netflix-style: the hero is the FIRST screen of one vertical scroll. It is NOT
		// pinned — when you browse down, the whole page scrolls up and the hero glides
		// off the top, then the first row occupies the screen fully. No half-poster
		// peeking from under a fixed banner.
		// 原 0.78f 把小海报行压到下半屏只剩 22% 显示区，行首卡几乎只露出半个身子；
		// 收到反馈后改为 0.62f（约 62% 屏高），让首排海报至少占满剩下 30%+ 屏高，
		// 下文 HeroStandard 也同步把文字上移，整个首页视觉重心更均衡。
		val heroHeight = screenHeight * 0.62f

		val scrollState = rememberScrollState()
		val firstRowFocus = remember { FocusRequester() }
		val homeScope = rememberCoroutineScope()

		// One scroll container holds hero + rows. The left sidebar stays as a fixed
		// overlay on top so it is always reachable. While the full-screen poster
		// wall is open we skip composing the home content entirely, so its
		// (covered) focusables can't steal the remote at the wall's edges.
		if (posterWall == null) {
		ArcticMainContent(
			modifier = Modifier.fillMaxSize(),
			scrollState = scrollState,
			firstRowFocus = firstRowFocus,
			selectedCategory = selectedCategory,
			heroItem = activeItem,
			themeColor = themeColor,
			heroCount = browseList.size,
			heroIndex = browseIndex,
			onHeroIndexChange = { idx ->
				browseIndex = idx
				activeItem = browseList.getOrNull(idx)
			},
			onActiveItemChange = setActiveItem,
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
			onRowMore = { title, items -> posterWall = title to items },
			initialFocus = mainContentFocus,
			heroTitleFocus = heroTitleFocus,
			homeSidebarFocus = homeSidebarFocus,
			categorySidebarFocus = categorySidebarFocus,
		)

		ArcticSidebar(
			expanded = sidebarExpanded,
			onExpandedChange = { sidebarExpanded = it },
			mode = sidebarMode,
			itemFocusRequesters = sidebarItemFocus,
			mainContentFocus = contentEntryFocus,
			config = menuConfig,
			onHome = {
				selectedCategory = null
				activeItem = featuredItems.firstOrNull() ?: homeRows.firstOrNull()?.items?.firstOrNull()
				browseIndex = 0
				homeScope.launch { runCatching { scrollState.scrollTo(0) } }
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

		// 顶部右上角「用户名 / 服务器」chip：让用户随时知道当前登录身份、切换账号或
		// 登出。位置固定在 screen 顶部 +24dp 右侧，按 ← 交给侧栏，按 ↓ 进入大舞台。
		// 用户在反馈里特别提到：「哪里都不会只显示半个大舞台」——加了 chip 之后，
		// 远程 ↑ 走到这里时大舞台仍完整可见，因为该位置不影响 verticalScroll 视区。
		TopRightUserChip(
			modifier = Modifier
				.align(Alignment.TopEnd)
				.padding(top = 4.dp, end = 32.dp),
			user = currentUser,
			server = currentServer,
			focusRequester = userChipFocus,
			leftFocus = homeSidebarFocus,
			downFocus = if (heroLayoutMode == HeroLayoutMode.NO_STAGE) mainContentFocus else heroTitleFocus,
			onClick = {
				// 暂时只把侧栏展开，让用户从侧栏走「切换用户」路径。
				sidebarExpanded = true
				runCatching { homeSidebarFocus.requestFocus() }
			},
		)

		}

		// Full-screen poster wall opened from a row's 「更多」. It covers the home
		// content and the rail; BACK (handled above) closes it, never the app.
		posterWall?.let { (pwTitle, pwItems) ->
			ArcticPosterWallScreen(
				title = pwTitle,
				items = pwItems,
				onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
				onBack = { posterWall = null },
			)
		}
	}

	// 进入首页以及每次按返回键，焦点都落在大舞台的「片名」上（NO_STAGE 时落到首行
	// 首海报）。片名在大舞台偏上位置，获得焦点时整页滑回顶部，从而始终看到完整海报，
	// 避免落在播放键导致的「半截海报」。
	LaunchedEffect(backTick) {
		delay(80)
		runCatching { contentEntryFocus.requestFocus() }
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
	themeColor: Color,
	sidebarExpanded: Boolean,
	sidebarMode: SidebarMode,
	heroHeight: Dp,
	onActiveItemChange: (BaseItemDto) -> Unit,
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
	onRowMore: (String, List<BaseItemDto>) -> Unit,
	initialFocus: FocusRequester,
	heroTitleFocus: FocusRequester,
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
				themeColor = themeColor,
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
				onDown = { runCatching { firstRowFocus.requestFocus() }.getOrDefault(false) },
				onLeftEdge = { runCatching { heroSidebarFocus.requestFocus() } },
				playFocus = initialFocus,
				titleFocus = heroTitleFocus,
				onTitleClick = { heroItem?.let(onHeroInfo) },
				scrollState = scrollState,
			)
		}

		// 大舞台与首排海报之间的「空隙」用主题色渐变衔接，使整页视觉连续。
		Box(
			Modifier
				.fillMaxWidth()
				.height(18.dp)
				.background(
					Brush.verticalGradient(
						0.00f to themeColor.copy(alpha = 0.35f),
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
					onPosterFocus = onActiveItemChange,
					onLayoutModeChange = { newMode ->
						setRowModes(rowModes.toMutableMap().apply { put(row.title, newMode) })
					},
					onItemClick = onItemClick,
					onMore = { onRowMore(row.title, row.items) },
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

/**
 * 从背景图边缘采样像素估算「主题色 / 边缘色」：优先取更鲜艳（高饱和）的边缘像素，
 * 得到适合做大舞台与空隙渐变的颜色。纯 Android API，不依赖额外库。
 */
private fun extractEdgeColor(bmp: Bitmap): Color {
	val w = bmp.width
	val h = bmp.height
	if (w <= 0 || h <= 0) return Color(0xFF1A1D24)
	val xMax = if ((w * 0.20).toInt() > 1) (w * 0.20).toInt() else 1
	val yMax = if ((h * 0.30).toInt() > 1) (h * 0.30).toInt() else 1
	var r = 0.0; var g = 0.0; var b = 0.0; var wsum = 0.0
	fun sample(x: Int, y: Int) {
		val p = bmp.getPixel(x, y)
		val cr = (p shr 16) and 0xFF
		val cg = (p shr 8) and 0xFF
		val cb = p and 0xFF
		val max = if (cr >= cg && cr >= cb) cr else if (cg >= cb) cg else cb
		val min = if (cr <= cg && cr <= cb) cr else if (cg <= cb) cg else cb
		val sat = if (max == 0) 0.0 else (max - min).toDouble() / max
		val weight = 0.25 + sat
		r += cr * weight; g += cg * weight; b += cb * weight; wsum += weight
	}
	for (x in 0 until xMax) for (y in 0 until h) sample(x, y)
	for (y in h - yMax until h) for (x in 0 until w) sample(x, y)
	if (wsum <= 0.0) return Color(0xFF1A1D24)
	val rr = (r / wsum).roundToInt().coerceIn(0, 255)
	val gg = (g / wsum).roundToInt().coerceIn(0, 255)
	val bb = (b / wsum).roundToInt().coerceIn(0, 255)
	return Color(255, rr, gg, bb)
}

// endregion

// region Rows

@Composable
private fun ArcticRowView(
	title: String,
	items: List<BaseItemDto>,
	layoutMode: RowLayoutMode,
	onPosterFocus: (BaseItemDto) -> Unit,
	onLayoutModeChange: (RowLayoutMode) -> Unit,
	onItemClick: (BaseItemDto) -> Unit,
	onMore: () -> Unit,
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
			.padding(top = 18.dp, bottom = 28.dp)
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
	// Title row + poster row stack vertically (Column). The title is now a plain
	// label — the display-mode switch moved to "hold OK 3s on any poster", and the
	// 「更多」wall entry is a poster card after the 20th item (see below).
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
		}

		LazyRow(
			modifier = Modifier.focusRestorer(),
			contentPadding = PaddingValues(start = 36.dp, end = 36.dp),
			horizontalArrangement = Arrangement.spacedBy(gap),
		) {
			// 「更多」海报墙入口放在第 20 个影片之后（不足 20 个则放在末尾）。
			val morePos = if (items.size > 20) 20 else items.size
			for (index in items.indices) {
				// key 带上下标：同一部影片可能在一排里重复出现（数据源交叠），
				// 裸用 id 会 duplicate key 崩溃。
				item(key = "${items[index].id}_$index") {
					val extra = Modifier
						.then(if (isFirstRow && index == 0 && firstRowFocus != null) Modifier.focusRequester(firstRowFocus) else Modifier)
						.onPreviewKeyEvent { ev ->
							if (ev.type == KeyEventType.KeyDown) {
								when (ev.key) {
									Key.DirectionLeft -> {
										if (index == 0) { runCatching { sidebarFocus.requestFocus() }; true } else false
									}
									Key.DirectionUp -> {
										if (isFirstRow && index == 0) { runCatching { heroFocus.requestFocus() }; true } else false
									}
									else -> false
								}
							} else false
						}
					when (layoutMode) {
						RowLayoutMode.PORTRAIT -> PortraitPosterCard(
							item = items[index],
							onClick = { onItemClick(items[index]) },
							onLongPress = { onLayoutModeChange(layoutMode.next) },
							onPosterFocus = { onPosterFocus(items[index]) },
							modifier = extra,
							width = cardWidth,
						)
						RowLayoutMode.LANDSCAPE -> LandscapeCard(
							item = items[index],
							onClick = { onItemClick(items[index]) },
							onLongPress = { onLayoutModeChange(layoutMode.next) },
							onPosterFocus = { onPosterFocus(items[index]) },
							modifier = extra,
							width = cardWidth,
						)
					}
				}
				if (index == morePos - 1) {
					item(key = "__more__${title}") {
						MorePosterCard(onClick = onMore, width = cardWidth)
					}
				}
			}
		}
	}
}
}

/**
 * 「更多」海报墙入口卡：外形与竖屏海报完全一致（同样的宽高比与强调色描边），
 * 放在该排第 20 个影片之后，选中即进入该目录的全屏海报墙。
 */
@Composable
private fun MorePosterCard(
	onClick: () -> Unit,
	width: Dp,
	modifier: Modifier = Modifier,
) {
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
				.background(
					if (focused) JellyfinTheme.colorScheme.buttonFocused
					else JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.16f),
				)
				.border(
					width = if (focused) 3.dp else 0.dp,
					color = if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					shape = RoundedCornerShape(8.dp),
				),
			contentAlignment = Alignment.Center,
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(6.dp),
			) {
				Text(
					"全部",
					color = if (focused) JellyfinTheme.colorScheme.onButtonFocused
						else JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontWeight = FontWeight.Bold,
						fontSize = 18.sp,
					),
				)
				Text(
					"海报墙 ›",
					color = if (focused) JellyfinTheme.colorScheme.onButtonFocused
						else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.7f),
					style = JellyfinTheme.typography.default.copy(fontSize = 12.sp),
				)
			}
		}
		// 与海报下方名称占位对齐
		Spacer(Modifier.height(20.dp))
	}
}

@Composable
private fun PortraitPosterCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	width: Dp = 200.dp,
	onLongPress: () -> Unit = {},
	onPosterFocus: () -> Unit = {},
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }
	val scope = rememberCoroutineScope()
	// 长按确定键 3 秒：在「竖屏/横幅」之间切换整排展示形式（遥控器无指针长按）。
	var lpHeld by remember { mutableStateOf(false) }
	var lpFired by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.onFocusChanged {
			focused = it.hasFocus
			if (it.hasFocus) onPosterFocus()
		}
			.clickable(onClick = onClick)
			.onKeyEvent { ev ->
				val isSelect = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
				if (!isSelect) return@onKeyEvent false
				if (ev.type == KeyEventType.KeyDown) {
					if (!lpHeld) {
						lpHeld = true
						lpFired = false
						scope.launch {
							delay(3_000)
							if (lpHeld) {
								lpFired = true
								onLongPress()
							}
						}
					}
					false
				} else {
					lpHeld = false
					if (lpFired) { lpFired = false; true } else false
				}
			},
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
	onLongPress: () -> Unit = {},
	onPosterFocus: () -> Unit = {},
) {
	val api = koinInject<ApiClient>()
	val image = item.itemBackdropImages.firstOrNull() ?: item.itemImages.values.firstOrNull()
	var focused by remember { mutableStateOf(false) }
	val scope = rememberCoroutineScope()
	var lpHeld by remember { mutableStateOf(false) }
	var lpFired by remember { mutableStateOf(false) }

	Column(
		modifier = modifier
			.width(width)
			.onFocusChanged {
			focused = it.hasFocus
			if (it.hasFocus) onPosterFocus()
		}
			.clickable(onClick = onClick)
			.onKeyEvent { ev ->
				val isSelect = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
				if (!isSelect) return@onKeyEvent false
				if (ev.type == KeyEventType.KeyDown) {
					if (!lpHeld) {
						lpHeld = true
						lpFired = false
						scope.launch {
							delay(3_000)
							if (lpHeld) {
								lpFired = true
								onLongPress()
							}
						}
					}
					false
				} else {
					lpHeld = false
					if (lpFired) { lpFired = false; true } else false
				}
			},
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

// region Full-screen poster wall (「更多」)

/**
 * Opened from a row's 「更多」button. Shows that row's items as a full-screen
 * wall of portrait posters (7 per row) with tag filters at the top:
 * 全部 / 影片类型(genre) / 地区(productionLocations). Selecting a tag narrows the
 * wall. BACK (handled by the home screen's BackHandler) closes it.
 */
@Composable
private fun ArcticPosterWallScreen(
	title: String,
	items: List<BaseItemDto>,
	onItemClick: (BaseItemDto) -> Unit,
	onBack: () -> Unit,
) {
	val background = JellyfinTheme.colorScheme.background
	val gap = 16.dp

	data class Chip(val label: String, val match: (BaseItemDto) -> Boolean)
	val chips = remember(items) {
		buildList {
			add(Chip("全部") { true })
			items.flatMap { it.genres.orEmpty() }.toSet().forEach { g ->
				add(Chip(g) { it.genres?.contains(g) == true })
			}
			items.flatMap { it.productionLocations.orEmpty() }.toSet().forEach { r ->
				add(Chip(r) { it.productionLocations?.contains(r) == true })
			}
		}
	}
	var selected by remember { mutableStateOf(0) }
	val filtered = remember(selected, items) { chips[selected].match.let { m -> items.filter(m) } }
	val gridFirstFocus = remember { FocusRequester() }

	Box(Modifier.fillMaxSize().background(background)) {
		Column(Modifier.fillMaxSize()) {
			// 顶部：标题 + 标签筛选
			Column(Modifier.padding(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 14.dp)) {
				Text(
					title,
					color = JellyfinTheme.colorScheme.listHeader,
					style = JellyfinTheme.typography.default.copy(fontWeight = FontWeight.Bold, fontSize = 30.sp),
				)
				Spacer(Modifier.height(14.dp))
				LazyRow(
					horizontalArrangement = Arrangement.spacedBy(10.dp),
					contentPadding = PaddingValues(vertical = 4.dp),
				) {
					itemsIndexed(chips) { idx, chip ->
						val sel = idx == selected
						var f by remember { mutableStateOf(false) }
						Box(
							Modifier
								.onFocusChanged { f = it.hasFocus }
								.clickable {
									selected = idx
									runCatching { gridFirstFocus.requestFocus() }
								}
								.background(
									if (sel) JellyfinTheme.colorScheme.buttonFocused
									else JellyfinTheme.colorScheme.surface.copy(alpha = 0.40f),
									RoundedCornerShape(8.dp),
								)
								.padding(horizontal = 14.dp, vertical = 9.dp),
							contentAlignment = Alignment.Center,
						) {
							Text(
								chip.label,
								color = if (sel) JellyfinTheme.colorScheme.onButtonFocused
									else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.8f),
								style = JellyfinTheme.typography.default.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
								maxLines = 1,
							)
						}
					}
				}
			}

			// 海报墙：一排 7 个竖屏海报
			BoxWithConstraints(
				Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 28.dp),
			) {
				val available = maxWidth
				val count = 7f
				val cardWidth = (available - gap * (count - 1f)) / count
				if (filtered.isEmpty()) {
					Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Text(
							"无内容",
							color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
							style = JellyfinTheme.typography.default.copy(fontSize = 18.sp),
						)
					}
				} else {
					LazyVerticalGrid(
						columns = GridCells.Fixed(7),
						horizontalArrangement = Arrangement.spacedBy(gap),
						verticalArrangement = Arrangement.spacedBy(gap + 10.dp),
						contentPadding = PaddingValues(vertical = 8.dp),
						modifier = Modifier.fillMaxSize().focusRestorer(),
					) {
						gridItemsIndexed(filtered, key = { i, it -> "${it.id}_$i" }) { idx, item ->
							PortraitPosterCard(
								item = item,
								onClick = { onItemClick(item) },
								modifier = if (idx == 0) Modifier.focusRequester(gridFirstFocus) else Modifier,
								width = cardWidth,
							)
						}
					}
				}
			}
		}
	}

	// 进入海报墙 + 每次切换标签后，焦点落到首张海报
	LaunchedEffect(selected) {
		delay(80)
		runCatching { gridFirstFocus.requestFocus() }
	}
}

// endregion

// region Top-right user/server chip

/**
 * 顶部右上角小卡片：展示当前登录用户名 + 服务器名。
 *
 * 关键设计——不该影响 verticalScroll 布局：这个 chip 是 BoxWithConstraints 的
 * 一个 align(TopEnd) 子元素，**不参与**主内容 verticalScroll 的高度计算，
 * 因此它永远不会把大舞台往下挤、也永远不会被滚走。整个 chip 始终钉在右上角。
 *
 * 焦点约定（用户要求「增加这个位置的选中」）：
 *   ←  →  跳到 homeSidebarFocus（侧栏第一项）
 *   ↓  →  跳到 heroTitleFocus（NO_STAGE 时回到 mainContentFocus / 首行首海报）
 *   ↑ / →  → 进入焦点自然搜索（不会困死）
 *   OK / 点击 → 展开侧栏，让用户走「切换用户 / 登出」流程
 */
@Composable
private fun TopRightUserChip(
	modifier: Modifier = Modifier,
	user: UserDto?,
	server: org.jellyfin.androidtv.auth.model.Server?,
	focusRequester: FocusRequester,
	leftFocus: FocusRequester,
	downFocus: FocusRequester,
	onClick: () -> Unit,
) {
	val userName = user?.name?.takeIf { it.isNotBlank() } ?: "未登录"
	val serverName = server?.name?.takeIf { it.isNotBlank() }
		?: server?.address?.takeIf { it.isNotBlank() } ?: "未连接服务器"

	Row(
		modifier = modifier
			.focusRequester(focusRequester)
			.focusable()
			.onPreviewKeyEvent { event ->
				if (event.type == KeyEventType.KeyDown) {
					when (event.key) {
						Key.DirectionLeft -> {
							runCatching { leftFocus.requestFocus() }
							true
						}
						Key.DirectionDown -> {
							runCatching { downFocus.requestFocus() }
							true
						}
						else -> false
					}
				} else {
					false
				}
			}
			.onFocusChanged { state ->
				// 焦点变化时刷新侧栏状态，让侧栏知道右上角也有可选项。
				if (state.isFocused) {
					// 无需拉侧栏，只是不消费。
				}
			}
			.clip(RoundedCornerShape(24.dp))
			.background(
				Brush.horizontalGradient(
					0.0f to Color(0xCC1A1F2C),
					1.0f to Color(0xCC2A2F38),
				)
			)
			.border(
				width = 1.dp,
				// 焦点时使用 accent 色描边（与列表焦点态一致）
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.18f),
				shape = RoundedCornerShape(24.dp),
			)
			.padding(horizontal = 14.dp, vertical = 8.dp)
			.clickable(onClick = onClick),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		// 头像圆点（临时方案：FUSE 风格用首字母 / 颜色代替）
		Box(
			Modifier
				.size(28.dp)
				.clip(CircleShape)
				.background(JellyfinTheme.colorScheme.buttonFocused),
			contentAlignment = Alignment.Center,
		) {
			Text(
				text = userName.firstOrNull()?.toString()?.uppercase() ?: "·",
				color = Color.White,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 14.sp,
					fontWeight = FontWeight.SemiBold,
				),
			)
		}
		Column(
			verticalArrangement = Arrangement.spacedBy(0.dp),
		) {
			Text(
				text = userName,
				color = Color.White,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 13.sp,
					fontWeight = FontWeight.SemiBold,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = serverName,
				color = Color(0xFFB8C0CC),
				style = JellyfinTheme.typography.default.copy(fontSize = 11.sp),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

// endregion
