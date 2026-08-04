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
import androidx.compose.foundation.lazy.LazyColumn
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

	// Auto-rotate the featured stage every 8s (FUSE 3: ~3s idle auto-scroll).
	// Changing heroIndex cancels and restarts this coroutine, resetting the timer.
	LaunchedEffect(heroIndex, featuredItems.size) {
		if (featuredItems.size <= 1) return@LaunchedEffect
		delay(8_000)
		heroIndex = (heroIndex + 1) % featuredItems.size
	}

	Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background)) {
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

		// Icon-only sidebar floating over the content (FUSE reference style).
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
		// Keep only the first few library icons so the bar stays compact like FUSE.
		libraries.take(4).forEach { view ->
			add(SidebarEntry(collectionIcon(view.collectionType), view.name ?: "", { onLibrary(view) }))
		}
		add(SidebarEntry(R.drawable.ic_settings, "设置", onSettings))
	}

	Column(
		modifier = Modifier
			.fillMaxHeight()
			.width(76.dp)
			.padding(vertical = 42.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
	) {
		entries.forEachIndexed { index, entry ->
			SidebarIcon(
				entry = entry,
				modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
			)
		}
	}

	// Guarded: the FocusRequester may not be attached yet on the first frame
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
private fun SidebarIcon(
	entry: SidebarEntry,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val selected = focused

	Box(
		modifier = modifier
			.size(48.dp)
			.background(
				if (selected) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.18f) else Color.Transparent,
				RoundedCornerShape(12.dp),
			)
			// onFocusChanged MUST precede the focusable/clickable modifier it observes
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = entry.onClick),
		contentAlignment = Alignment.Center,
	) {
		Icon(
			imageVector = ImageVector.vectorResource(entry.icon),
			contentDescription = entry.label,
			tint = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.45f),
			modifier = Modifier.size(24.dp),
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
	BoxWithConstraints(modifier = modifier.padding(start = 76.dp)) {
		val heroHeight = maxHeight * 0.55f

		Column(Modifier.fillMaxSize()) {
			Text(
				"Discover",
				modifier = Modifier.padding(start = 36.dp, top = 28.dp, bottom = 8.dp),
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 28.sp,
					fontWeight = FontWeight.Bold,
				),
			)

			ArcticHeroStage(
				modifier = Modifier
					.fillMaxWidth()
					.height(heroHeight)
					.padding(horizontal = 28.dp, vertical = 10.dp),
				item = hero,
				featuredCount = featuredCount,
				heroIndex = heroIndex,
				onPlay = { hero?.let(onHeroPlay) },
				onInfo = { hero?.let(onHeroInfo) },
			)

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

@Composable
private fun ArcticHeroStage(
	item: BaseItemDto?,
	featuredCount: Int,
	heroIndex: Int,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val poster: JellyfinImage? = item?.itemImages?.values?.firstOrNull()
	val backdrop: JellyfinImage? = item?.itemBackdropImages?.firstOrNull() ?: poster

	Row(
		modifier = modifier
			.clip(RoundedCornerShape(24.dp))
			.border(
				width = 1.5.dp,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.10f),
				shape = RoundedCornerShape(24.dp),
			)
			.background(JellyfinTheme.colorScheme.surface.copy(alpha = 0.25f)),
	) {
		// Left: info panel
		Column(
			modifier = Modifier
				.weight(0.46f)
				.fillMaxHeight()
				.padding(horizontal = 32.dp, vertical = 28.dp),
			verticalArrangement = Arrangement.SpaceBetween,
		) {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
						fontSize = 40.sp,
						fontWeight = FontWeight.Bold,
					),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)

				Row(
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Box(
						Modifier
							.background(
								JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.15f),
								RoundedCornerShape(4.dp),
							)
							.padding(horizontal = 8.dp, vertical = 3.dp),
					) {
						Text(
							"INFO",
							color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.85f),
							style = JellyfinTheme.typography.default.copy(
								fontSize = 12.sp,
								fontWeight = FontWeight.Bold,
							),
						)
					}
					Text(
						buildHeroMeta(item),
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.70f),
						style = JellyfinTheme.typography.default.copy(fontSize = 15.sp),
						maxLines = 1,
					)
				}

				item?.overview?.let { overview ->
					Text(
						overview,
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.78f),
						style = JellyfinTheme.typography.default.copy(fontSize = 15.sp),
						maxLines = 3,
						overflow = TextOverflow.Ellipsis,
					)
				}
			}

			Row(
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				HeroPlayButton(size = 52.dp, iconSize = 20.dp, onClick = onPlay)
				HeroInfoButton(size = 42.dp, iconSize = 17.dp, onClick = onInfo)
			}
		}

		// Right: large poster
		Box(
			modifier = Modifier
				.weight(0.54f)
				.fillMaxHeight()
				.clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
				.background(JellyfinTheme.colorScheme.background),
		) {
			Crossfade(
				targetState = poster?.getUrl(api, maxWidth = 800),
				label = "hero-poster",
			) { url ->
				if (url != null) {
					AsyncImage(
						url = url,
						blurHash = poster?.blurHash,
						scaleType = ImageView.ScaleType.CENTER_CROP,
						modifier = Modifier.fillMaxSize(),
					)
				} else {
					Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background))
				}
			}

			// Page dots overlaid bottom-center of the poster
			if (featuredCount > 1) {
				Row(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.padding(bottom = 14.dp),
					horizontalArrangement = Arrangement.spacedBy(6.dp),
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
			.scale(if (focused) 1.10f else 1f)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
				CircleShape,
			)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.25f),
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
			.scale(if (focused) 1.10f else 1f)
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = 0.55f),
				CircleShape,
			)
			.border(
				width = if (focused) 0.dp else 1.dp,
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.25f),
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
			.padding(top = 26.dp, bottom = 6.dp),
	) {
		Row(
			modifier = Modifier
				.padding(start = 36.dp, end = 36.dp, bottom = 14.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(10.dp),
		) {
			Box(
				Modifier
					.width(4.dp)
					.height(18.dp)
					.background(JellyfinTheme.colorScheme.buttonFocused, RoundedCornerShape(2.dp)),
			)
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
			contentPadding = PaddingValues(start = 36.dp, end = 36.dp),
			horizontalArrangement = Arrangement.spacedBy(16.dp),
		) {
			items(items, key = { it.id }) { item ->
				ArcticWideCard(item = item, onClick = { onItemClick(item) })
			}
		}
	}
}

@Composable
private fun ArcticWideCard(
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
			.clip(RoundedCornerShape(16.dp))
			.background(
				Brush.horizontalGradient(
					colors = listOf(
						JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.28f),
						JellyfinTheme.colorScheme.surface.copy(alpha = 0.45f),
					),
				),
			)
			.border(
				width = if (focused) 3.dp else 1.dp,
				color = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.12f),
				shape = RoundedCornerShape(16.dp),
			)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick)
			.padding(12.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(width = 110.dp, height = 156.dp)
				.clip(RoundedCornerShape(12.dp))
				.background(JellyfinTheme.colorScheme.background),
		) {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 360),
					blurHash = image.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
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
					fontSize = 18.sp,
				),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)

			Text(
				buildCardMeta(item),
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.65f),
				style = JellyfinTheme.typography.default.copy(fontSize = 13.sp),
				maxLines = 1,
			)

			Text(
				item.overview ?: "",
				color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.78f),
				style = JellyfinTheme.typography.default.copy(fontSize = 14.sp, lineHeight = 20.sp),
				maxLines = 4,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}

private fun buildCardMeta(item: BaseItemDto?): String = buildString {
	item ?: return@buildString
	val genres = item.genres?.take(3)?.joinToString(" / ")
	if (!genres.isNullOrBlank()) append(genres)
	item.productionYear?.let { if (isNotBlank()) append(" · "); append(it) }
	item.communityRating?.let { if (isNotBlank()) append(" · "); append("★ "); append("%.1f".format(it)) }
}

// endregion
