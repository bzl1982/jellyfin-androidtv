package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.animation.Crossfade
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
import org.jellyfin.androidtv.ui.composable.item.ItemCard
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
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
import org.koin.compose.viewmodel.koinActivityViewModel

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

// endregion

@Composable
fun ArcticHomeScreen() {
	val api = koinInject<ApiClient>()
	val userViewsRepository = koinInject<UserViewsRepository>()
	val navigationRepository = koinInject<NavigationRepository>()
	val settingsViewModel = koinActivityViewModel<SettingsViewModel>()

	var libraries by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var featuredItems by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
	var heroIndex by remember { mutableStateOf(0) }
	var rows by remember { mutableStateOf<List<ArcticRow>>(emptyList()) }
	var loaded by remember { mutableStateOf(false) }

	val sidebarFocus = remember { FocusRequester() }

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

	// Auto-rotate the featured stage every 8s (FUSE: ~3s idle auto-scroll).
	LaunchedEffect(heroIndex, featuredItems.size) {
		if (featuredItems.size <= 1) return@LaunchedEffect
		delay(8_000)
		heroIndex = (heroIndex + 1) % featuredItems.size
	}

	// FUSE 2 home is a single immersive layer:
	// full-screen fanart background + gradient overlays + floating info panel + rows.
	Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background)) {
		ArcticBackground(
			item = featuredItems.getOrNull(heroIndex),
			modifier = Modifier.fillMaxSize(),
		)

		ArcticMainContent(
			modifier = Modifier.fillMaxSize(),
			hero = featuredItems.getOrNull(heroIndex),
			featuredCount = featuredItems.size,
			heroIndex = heroIndex,
			onHeroIndexChange = { heroIndex = it },
			rows = rows,
			loaded = loaded,
			onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
			onHeroPlay = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
			onHeroInfo = { item -> navigationRepository.navigate(Destinations.itemDetails(item.id)) },
		)

		// FUSE 2 vertical sidemenu floats over the fanart on the left edge.
		ArcticSidebar(
			libraries = libraries,
			initialFocus = sidebarFocus,
			onHome = { navigationRepository.navigate(Destinations.home, replace = true) },
			onSearch = { navigationRepository.navigate(Destinations.search()) },
			onSettings = { settingsViewModel.show() },
			onLibrary = { navigationRepository.navigate(Destinations.libraryBrowser(it)) },
		)
	}
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

	Crossfade(
		targetState = backdrop?.getUrl(api, maxWidth = 1920),
		label = "hero-backdrop",
	) { url ->
		Box(modifier = modifier) {
			if (url != null) {
				AsyncImage(
					url = url,
					blurHash = backdrop?.blurHash,
					// FUSE 2 uses aspectratio=scale: cover the whole screen with the fanart.
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background))
			}

			// Left-to-right scrim so the left-side info stays legible over bright artwork.
			Box(
				Modifier
					.fillMaxSize()
					.background(
						Brush.horizontalGradient(
							0.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.92f),
							0.18f to JellyfinTheme.colorScheme.background.copy(alpha = 0.72f),
							0.38f to JellyfinTheme.colorScheme.background.copy(alpha = 0.35f),
							0.65f to Color.Transparent,
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
							0.45f to Color.Transparent,
							0.78f to JellyfinTheme.colorScheme.background.copy(alpha = 0.82f),
							1.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.96f),
						),
					),
			)

			// Vignette overlay for the "spotlight" feel: edges darker, centre brighter.
			Box(
				Modifier
					.fillMaxSize()
					.background(
						Brush.radialGradient(
							0.00f to Color.Transparent,
							0.55f to Color.Transparent,
							0.88f to JellyfinTheme.colorScheme.background.copy(alpha = 0.45f),
							1.00f to JellyfinTheme.colorScheme.background.copy(alpha = 0.75f),
						),
					),
			)
		}
	}
}

// endregion

// region Sidebar

@Composable
private fun ArcticSidebar(
	libraries: List<BaseItemDto>,
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

	// FUSE 2 Home_Menu_List_Vert: left=0, top=260, bottom=260, width=view_side=200.
	Column(
		modifier = Modifier
			.fillMaxHeight()
			.width(200.dp)
			.padding(top = 260.dp, bottom = 260.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
	) {
		entries.forEachIndexed { index, entry ->
			SidebarItem(
				entry = entry,
				modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
			)
		}
	}

	LaunchedEffect(initialFocus) { runCatching { initialFocus.requestFocus() } }
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
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val selected = focused

	Row(
		modifier = modifier
			.fillMaxWidth()
			.height(64.dp)
			.padding(horizontal = 30.dp)
			.background(
				if (selected) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.15f) else Color.Transparent,
				RoundedCornerShape(12.dp),
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = entry.onClick)
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(14.dp),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(entry.icon),
			contentDescription = entry.label,
			tint = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			modifier = Modifier.size(26.dp),
		)

		Text(
			entry.label,
			color = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
			style = JellyfinTheme.typography.default.copy(
				fontSize = 15.sp,
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
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
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
) {
	BoxWithConstraints(modifier = modifier.padding(start = 200.dp)) {
		Column(Modifier.fillMaxSize()) {
			// FUSE 2 info panel sits at left=view_side=200, top=view_top=200 inside the main area.
			ArcticInfoPanel(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 0.dp, top = 200.dp, end = view_pad_dp, bottom = 0.dp)
					.height(300.dp),
				item = hero,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = { hero?.let(onHeroPlay) },
				onInfo = { hero?.let(onHeroInfo) },
			)

			Spacer(Modifier.height(28.dp))

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
					rows.forEach { ArcticRowView(it.title, it.items, onItemClick) }
					Spacer(Modifier.height(48.dp))
				}
			}
		}
	}
}

private val view_pad_dp = 80.dp

@Composable
private fun ArcticInfoPanel(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	modifier: Modifier = Modifier,
) {
	// FUSE 2 Info_Panel: width=info_panel_w=1120, height=300, constrained to main area.
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
			horizontalArrangement = Arrangement.spacedBy(14.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// FUSE 2 Spotlight main play circle is 100x100 Kodi units.
			HeroPlayButton(size = 72.dp, iconSize = 26.dp, onClick = onPlay)
			HeroInfoButton(size = 54.dp, iconSize = 20.dp, onClick = onInfo)

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
		}
	}
}

@Composable
private fun HeroPlayButton(
	size: androidx.compose.ui.unit.Dp,
	iconSize: androidx.compose.ui.unit.Dp,
	onClick: () -> Unit,
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

private fun buildHeroMeta(item: BaseItemDto?): String = buildString {
	item ?: return@buildString
	item.productionYear?.let { append(it); append(" · ") }
	item.communityRating?.let { append("★ "); append("%.1f".format(it)); append(" · ") }
	val genres = item.genres?.take(3)?.joinToString(" / ")
	if (!genres.isNullOrBlank()) append(genres)
}

@Composable
private fun ArcticRowView(
	title: String,
	items: List<BaseItemDto>,
	onItemClick: (BaseItemDto) -> Unit,
) {
	Column(
		Modifier
			.fillMaxWidth()
			.padding(top = 22.dp, bottom = 6.dp),
	) {
		Text(
			title,
			color = JellyfinTheme.colorScheme.listHeader,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = FontWeight.Bold,
				fontSize = 19.sp,
			),
			modifier = Modifier.padding(start = 0.dp, end = 36.dp, bottom = 12.dp),
		)

		LazyRow(
			contentPadding = PaddingValues(end = 36.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			items(items, key = { it.id }) { item ->
				ArcticPosterCard(item = item, onClick = { onItemClick(item) })
			}
		}
	}
}

@Composable
private fun ArcticPosterCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	var focused by remember { mutableStateOf(false) }

	// FUSE 2 view_poster_item_w=200, view_poster_item_h=294.
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
					// Show the whole poster without cropping heads/feet.
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

// endregion
