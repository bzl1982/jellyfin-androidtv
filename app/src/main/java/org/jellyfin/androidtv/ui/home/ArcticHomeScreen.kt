package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import org.jellyfin.sdk.model.api.ItemFields
import org.koin.compose.koinInject

// region Data model

data class ArcticRow(
	val title: String,
	val items: List<BaseItemDto>,
)

// Region buckets derived from each title's NFO <country> field.
// 华语(大陆/港台) / 日韩 / 欧美 / 其它 — never one menu per country.
private enum class Region(val label: String) {
	MAINLAND("大陆"),
	HKTW("港台"),
	JPKR("日韩"),
	WEST("欧美"),
	OTHER("其它"),
}

// Program types: a movie is Movie; a series is split by genre into
// 剧集 / 综艺 / 动漫 / 纪录片.
private enum class ProgramType(val label: String) {
	MOVIE("电影"),
	SERIES("剧集"),
	VARIETY("综艺"),
	ANIME("动漫"),
	DOC("纪录片"),
}

private fun classifyRegion(countries: List<String>?): Region {
	if (countries.isNullOrEmpty()) return Region.OTHER
	for (raw in countries) {
		val s = raw.lowercase().trim()
		if (s.contains("中国") || s.contains("大陆") || s.contains("china") || s == "cn") return Region.MAINLAND
		if (s.contains("香港") || s.contains("台湾") || s.contains("臺灣") ||
			s.contains("hong kong") || s.contains("taiwan") ||
			s.contains("macao") || s.contains("澳门") || s.contains("hongkong")) return Region.HKTW
		if (s.contains("日本") || s.contains("japan") || s.contains("韩国") || s.contains("韓國") || s.contains("korea")) return Region.JPKR
		if (s.contains("美国") || s.contains("united states") || s.contains("usa") || s == "us" ||
			s.contains("英国") || s.contains("uk") || s.contains("britain") ||
			s.contains("法国") || s.contains("france") ||
			s.contains("德国") || s.contains("germany") ||
			s.contains("意大利") || s.contains("italy") ||
			s.contains("西班牙") || s.contains("spain") ||
			s.contains("加拿大") || s.contains("canada") ||
			s.contains("澳大利亚") || s.contains("australia") ||
			s.contains("俄罗斯") || s.contains("russia") ||
			s.contains("新西兰") || s.contains("zealand") ||
			s.contains("荷兰") || s.contains("netherlands") || s.contains("holland") ||
			s.contains("葡萄牙") || s.contains("portugal") ||
			s.contains("瑞典") || s.contains("sweden") ||
			s.contains("丹麦") || s.contains("denmark") ||
			s.contains("挪威") || s.contains("norway") ||
			s.contains("比利时") || s.contains("belgium") ||
			s.contains("奥地利") || s.contains("austria") ||
			s.contains("爱尔兰") || s.contains("ireland") ||
			s.contains("波兰") || s.contains("poland") ||
			s.contains("巴西") || s.contains("brazil") ||
			s.contains("墨西哥") || s.contains("mexico") ||
			s.contains("阿根廷") || s.contains("argentina") ||
			s.contains("瑞士") || s.contains("switzerland") ||
			s.contains("土耳其") || s.contains("turkey") ||
			s.contains("希腊") || s.contains("greece") ||
			s.contains("捷克") || s.contains("czech")) return Region.WEST
		// Romanised (pure-latin) country names default to 欧美.
		if (s.all { it.isLetter() && it.code < 128 }) return Region.WEST
	}
	return Region.OTHER
}

private fun classifyType(item: BaseItemDto): ProgramType? {
	return when (item.type) {
		BaseItemKind.MOVIE -> ProgramType.MOVIE
		BaseItemKind.SERIES -> {
			val g = item.genres.orEmpty().map { it.lowercase() }
			when {
				g.any { it.contains("综艺") || it.contains("variety") || it.contains("show") || it.contains("talk") } -> ProgramType.VARIETY
				g.any { it.contains("动漫") || it.contains("anime") || it.contains("动画") } -> ProgramType.ANIME
				g.any { it.contains("纪录") || it.contains("documentary") } -> ProgramType.DOC
				else -> ProgramType.SERIES
			}
		}
		else -> null
	}
}

private data class MenuCategory(
	val id: String,
	val label: String,
	val region: Region,
	val type: ProgramType,
	val isOther: Boolean,
	val count: Int,
	val icon: Int,
)

/**
 * Six home layout modes translated from the FUSE 2 reference screenshots.
 * Mode 0 is the existing portrait-poster look; modes 1-5 replicate the 5 screenshots.
 * LARGE_LANDSCAPE is the full-screen hero mode and hides the rows below it.
 */
private enum class HomeLayoutMode {
	PORTRAIT_POSTERS,   // 竖版海报
	WIDE_INFO_CARDS,    // 宽信息卡
	LANDSCAPE_CARDS,    // 横向海报
	CIRCULAR_DISCS,     // 圆形光盘
	PORTRAIT_WITH_BAR,  // 竖版+信息栏
	LARGE_LANDSCAPE,    // 大海报
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
	val userViewsRepository = koinInject<UserViewsRepository>()
	val navigationRepository = koinInject<NavigationRepository>()

	var libraries by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var featuredItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var heroIndex by remember { mutableStateOf(0) }
	var rows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var loaded by remember { mutableStateOf(false) }

	// Dynamic country × type menu (derived from NFO <country> + genre).
	var menuCategories by remember { mutableStateOf<List<MenuCategory>>(emptyList()) }
	var selectedCategory by remember { mutableStateOf<MenuCategory?>(null) }
	var categoryRows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var categoryLoading by remember { mutableStateOf(false) }

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

			val perLibrary = views.mapNotNull { view ->
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

	// ---- Build the country × type menu by aggregating every Movie/Series item ----
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			runCatching {
			val counts = mutableMapOf<Pair<Region, ProgramType>, Int>()
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
							ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
						),
					).content
					val items = resp.items.orEmpty()
					for (item in items) {
						val type = classifyType(item) ?: continue
						val countries = item.productionLocations
						val key = classifyRegion(countries) to type
						counts[key] = (counts[key] ?: 0) + 1
					}
					val total = resp.totalRecordCount ?: 0
					startIndex += items.size
					if (items.isEmpty() || startIndex >= total) break
				}
				withContext(Dispatchers.Main) {
					menuCategories = buildMenuCategories(counts)
				}
			}.onFailure { it.printStackTrace() }
		}
	}

	// ---- Load items for the selected country × type category ----
	LaunchedEffect(selectedCategory) {
		val cat = selectedCategory
		if (cat == null) {
			categoryRows = emptyList()
			return@LaunchedEffect
		}
		categoryLoading = true
		withContext(Dispatchers.IO) {
			runCatching {
				val includeTypes = if (cat.type == ProgramType.MOVIE) {
					setOf(BaseItemKind.MOVIE)
				} else {
					setOf(BaseItemKind.SERIES)
				}
				val items = buildList {
					if (cat.isOther) {
						// Catch-all: every item of this type except the explicitly-shown regions.
						val shownRegions = menuCategories
							.filter { !it.isOther && it.type == cat.type }
							.map { it.region }
							.toSet()
						var start = 0
						while (true) {
							val resp = api.itemsApi.getItems(
								limit = 200,
								startIndex = start,
								recursive = true,
								includeItemTypes = includeTypes,
								fields = ItemRepository.browseFields,
								imageTypeLimit = 1,
							).content
							val page = resp.items.orEmpty()
								.filter { classifyType(it) == cat.type && classifyRegion(it.productionLocations) !in shownRegions }
							addAll(page)
							start += resp.items.orEmpty().size
							if (resp.items.orEmpty().isEmpty() || start >= (resp.totalRecordCount ?: 0) || size >= 200) break
						}
					} else {
						var start = 0
						while (true) {
							val resp = api.itemsApi.getItems(
								limit = 200,
								startIndex = start,
								recursive = true,
								includeItemTypes = includeTypes,
								fields = ItemRepository.browseFields,
								imageTypeLimit = 1,
							).content
							val page = resp.items.orEmpty()
								.filter { classifyType(it) == cat.type && classifyRegion(it.productionLocations) == cat.region }
							addAll(page)
							start += resp.items.orEmpty().size
							if (resp.items.orEmpty().isEmpty() || start >= (resp.totalRecordCount ?: 0) || size >= 200) break
						}
					}
				}
				val chunked = items.chunked(14).map { ArcticRow(cat.label, it) }
				withContext(Dispatchers.Main) {
					categoryRows = chunked
					categoryLoading = false
				}
			}.onFailure {
				it.printStackTrace()
				categoryLoading = false
			}
		}
	}

	// Auto-rotate the featured stage every 8s when on home and not interacting.
	LaunchedEffect(heroIndex, featuredItems.size, selectedCategory) {
		if (selectedCategory != null || featuredItems.size <= 1) return@LaunchedEffect
		delay(8_000)
		heroIndex = (heroIndex + 1) % featuredItems.size
	}

	val backgroundItem = if (selectedCategory == null) {
		featuredItems.getOrNull(heroIndex)
	} else {
		categoryRows.firstOrNull()?.items?.firstOrNull()
	}

	Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background)) {
		// 1) Full-screen fanart background.
		ArcticBackground(
			item = backgroundItem,
			modifier = Modifier.fillMaxSize(),
		)

		// 2) Sidebar + main content in a Row so the main content is pushed right when the sidebar expands.
		Row(Modifier.fillMaxSize()) {
			ArcticSidebar(
				expanded = sidebarExpanded,
				onExpandedChange = { sidebarExpanded = it },
				initialFocus = sidebarFocus,
				categories = menuCategories,
				onHome = {
					selectedCategory = null
					sidebarExpanded = false
					runCatching { mainContentFocus.requestFocus() }
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
					sidebarExpanded = false
					runCatching { mainContentFocus.requestFocus() }
					Toast.makeText(context, "正在加载 ${cat.label}", Toast.LENGTH_SHORT).show()
				},
			)

			// 3) Main content area: scrollable, rows sit below the hero stage.
			ArcticMainContent(
				modifier = Modifier.weight(1f).fillMaxHeight(),
				selectedCategory = selectedCategory,
				hero = featuredItems.getOrNull(heroIndex),
				featuredCount = featuredItems.size,
				heroIndex = heroIndex,
				onHeroIndexChange = { heroIndex = it },
				rows = rows,
				categoryRows = categoryRows,
				categoryLoading = categoryLoading,
				loaded = loaded,
				layoutMode = layoutMode,
				onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
				onHeroPlay = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
				onHeroInfo = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
				onCycleLayoutMode = { layoutMode = HomeLayoutMode.entries[(layoutMode.ordinal + 1) % HomeLayoutMode.entries.size] },
				initialFocus = mainContentFocus,
			)
		}
	}

	// Initial focus goes to the main content, not the sidebar, so the sidebar stays collapsed.
	LaunchedEffect(Unit) { runCatching { mainContentFocus.requestFocus() } }
}

/**
 * Build the left-menu categories from the aggregated (region × type) counts.
 * When more than 12 entries exist, the low-count ones are merged into a
 * per-type "其它{type}" catch-all (其它电影 / 其它综艺 / 其它动漫 / 其它纪录片 …).
 */
private fun buildMenuCategories(
	counts: Map<Pair<Region, ProgramType>, Int>,
): List<MenuCategory> {
	val entries = counts.filterValues { it > 0 }.map { (key, count) ->
		val (region, type) = key
		MenuCategory(
			id = "${region.name}_${type.name}",
			label = "${region.label}${type.label}",
			region = region,
			type = type,
			isOther = false,
			count = count,
			icon = categoryIcon(region, type),
		)
	}

	val sorted = entries.sortedByDescending { it.count }
	if (sorted.size <= 12) return sorted

	// Keep the highest-count entries until we would exceed 12, then merge the
	// remaining low-count entries into a per-type "其它{type}" entry.
	val result = mutableListOf<MenuCategory>()
	val merged = mutableMapOf<ProgramType, Int>()
	var i = 0
	while (i < sorted.size) {
		if (result.size + merged.size >= 12) break
		result.add(sorted[i])
		i++
	}
	for (j in i until sorted.size) {
		val e = sorted[j]
		merged[e.type] = (merged[e.type] ?: 0) + e.count
	}
	merged.forEach { (type, count) ->
		result.add(
			MenuCategory(
				id = "OTHER_${type.name}",
				label = "其它${type.label}",
				region = Region.OTHER,
				type = type,
				isOther = true,
				count = count,
				icon = categoryIcon(Region.OTHER, type),
			),
		)
	}
	return result.sortedWith(compareBy({ it.type.ordinal }, { if (it.isOther) 1 else it.region.ordinal }))
}

private fun categoryIcon(region: Region, type: ProgramType): Int = when (type) {
	ProgramType.MOVIE -> R.drawable.ic_movie
	ProgramType.SERIES -> R.drawable.ic_tv_play
	ProgramType.VARIETY -> R.drawable.ic_masks
	ProgramType.ANIME -> R.drawable.ic_album
	ProgramType.DOC -> R.drawable.ic_camera
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
	expanded: Boolean,
	onExpandedChange: (Boolean) -> Unit,
	initialFocus: FocusRequester,
	categories: List<MenuCategory>,
	onHome: () -> Unit,
	onSearch: () -> Unit,
	onSettings: () -> Unit,
	onCategory: (MenuCategory) -> Unit,
) {
	val entries = remember(categories) {
		buildList {
			add(Triple(R.drawable.ic_house, "首页", onHome))
			add(Triple(R.drawable.ic_search, "搜索", onSearch))
			categories.forEach { cat ->
				add(Triple(cat.icon, cat.label, { onCategory(cat) }))
			}
			add(Triple(R.drawable.ic_settings, "系统设置", onSettings))
		}
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
			.padding(vertical = 120.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Top),
	) {
		entries.forEachIndexed { index, (icon, label, onClick) ->
			SidebarItem(
				icon = icon,
				label = label,
				expanded = expanded,
				modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
				onFocusChange = { focusStates[index].value = it },
				onClick = onClick,
			)
		}
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
	val selected = focused

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(50.dp)
			.padding(horizontal = if (expanded) 12.dp else 8.dp)
			.background(
				if (selected) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.16f) else Color.Transparent,
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
			tint = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
			modifier = Modifier.size(22.dp),
		)

		if (expanded) {
			Text(
				label,
				color = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.85f),
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
	selectedCategory: MenuCategory?,
	hero: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onHeroIndexChange: (Int) -> Unit,
	rows: List<ArcticRow>,
	categoryRows: List<ArcticRow>,
	categoryLoading: Boolean,
	loaded: Boolean,
	layoutMode: HomeLayoutMode,
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
	onCycleLayoutMode: () -> Unit,
	initialFocus: FocusRequester,
) {
	val scrollState = rememberScrollState()

	BoxWithConstraints(modifier = modifier) {
		if (selectedCategory == null) {
			// ----- Home view: hero stage + library rows -----
			val configuration = LocalConfiguration.current
			val screenHeight = configuration.screenHeightDp.dp
			val heroHeight = if (layoutMode.isFullScreenHero) screenHeight else (screenHeight * 0.68f)
			val showRows = !layoutMode.isFullScreenHero

			Column(
				Modifier
					.fillMaxSize()
					.verticalScroll(scrollState),
			) {
				Box(
					Modifier
						.fillMaxWidth()
						.height(heroHeight),
				) {
					// FUSE 2 info panel sits at left=view_side area, top around 200dp.
					ArcticInfoPanel(
						modifier = Modifier
							.fillMaxWidth()
							.padding(
								start = view_side_dp,
								top = 180.dp,
								end = view_pad_dp,
								bottom = if (showRows) 60.dp else 80.dp,
							)
							.align(Alignment.BottomStart),
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
						onCycleLayoutMode = onCycleLayoutMode,
						layoutMode = layoutMode,
						initialFocus = initialFocus,
					)
				}

				if (showRows) {
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
		} else {
			// ----- Category view: header + rows of posters -----
			Column(
				Modifier
					.fillMaxSize()
					.verticalScroll(scrollState),
			) {
				Spacer(Modifier.height(48.dp))
				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(start = view_side_dp, end = view_pad_dp, bottom = 16.dp),
				) {
					Box(
						Modifier
							.width(4.dp)
							.height(22.dp)
							.background(JellyfinTheme.colorScheme.buttonFocused, RoundedCornerShape(2.dp)),
					)
					Spacer(Modifier.width(10.dp))
					Text(
						selectedCategory.label,
						color = JellyfinTheme.colorScheme.listHeader,
						style = JellyfinTheme.typography.default.copy(
							fontWeight = FontWeight.Bold,
							fontSize = 28.sp,
						),
					)
					Spacer(Modifier.width(12.dp))
					Text(
						"${selectedCategory.count} 部",
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.6f),
						style = JellyfinTheme.typography.default.copy(fontSize = 16.sp),
					)
				}

				if (categoryLoading) {
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
				} else if (categoryRows.isEmpty()) {
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
					Spacer(Modifier.height(48.dp))
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
	onCycleLayoutMode: () -> Unit,
	layoutMode: HomeLayoutMode,
	initialFocus: FocusRequester,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.Bottom),
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
			// Play: pressing LEFT on it cycles to previous featured item.
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

			// Temporary layout-mode switch until the real settings page is ready.
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
