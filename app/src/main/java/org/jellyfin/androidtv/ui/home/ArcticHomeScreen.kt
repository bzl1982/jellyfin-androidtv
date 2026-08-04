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

	Row(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background)) {
		ArcticSidebar(
			libraries = libraries,
			initialFocus = sidebarFocus,
			onHome = { navigationRepository.navigate(Destinations.home, replace = true) },
			onSearch = { navigationRepository.navigate(Destinations.search()) },
			onSettings = { settingsViewModel.show() },
			onLibrary = { navigationRepository.navigate(Destinations.libraryBrowser(it)) },
		)
		ArcticMainContent(
			modifier = Modifier.weight(1f).fillMaxHeight(),
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
		libraries.forEach { view ->
			add(SidebarEntry(collectionIcon(view.collectionType), view.name ?: "", { onLibrary(view) }))
		}
		add(SidebarEntry(R.drawable.ic_settings, "设置", onSettings))
	}

	Column(
		modifier = Modifier
			.width(232.dp)
			.fillMaxHeight()
			.background(JellyfinTheme.colorScheme.surface.copy(alpha = 0.94f))
			.padding(top = 28.dp, bottom = 28.dp),
	) {
		entries.forEachIndexed { index, entry ->
			SidebarItem(
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
private fun SidebarItem(
	entry: SidebarEntry,
	modifier: Modifier = Modifier,
) {
	var focused by remember { mutableStateOf(false) }
	val selected = focused

	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 5.dp)
			.background(
				if (selected) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.18f) else Color.Transparent,
				RoundedCornerShape(12.dp),
			)
			.border(
				width = if (selected) 2.dp else 0.dp,
				color = if (selected) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
				shape = RoundedCornerShape(12.dp),
			)
			// onFocusChanged MUST precede the focusable/clickable modifier it observes
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = entry.onClick)
			.padding(horizontal = 14.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(entry.icon),
			contentDescription = null,
			tint = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.55f),
			modifier = Modifier.size(23.dp),
		)
		Text(
			entry.label,
			color = if (selected) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
			style = JellyfinTheme.typography.default.copy(
				fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
			),
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
	BoxWithConstraints(modifier = modifier) {
		val heroHeight = maxHeight * 0.58f

		Column(Modifier.fillMaxSize()) {
			ArcticHeroStage(
				modifier = Modifier
					.fillMaxWidth()
					.height(heroHeight),
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
	val backdrop: JellyfinImage? = item?.itemBackdropImages?.firstOrNull()
		?: item?.itemImages?.values?.firstOrNull()

	Box(modifier = modifier) {
		// Full-bleed backdrop with crossfade when the featured item changes
		Crossfade(
			targetState = backdrop?.getUrl(api, maxWidth = 1280),
			label = "hero-backdrop",
		) { url ->
			if (url != null) {
				AsyncImage(
					url = url,
					blurHash = backdrop?.blurHash,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			} else {
				Box(Modifier.fillMaxSize().background(JellyfinTheme.colorScheme.background))
			}
		}

		// Left-to-right scrim so left-aligned text stays readable
		Box(
			Modifier.fillMaxSize().background(
				Brush.horizontalGradient(
					colors = listOf(
						JellyfinTheme.colorScheme.background.copy(alpha = 0.82f),
						JellyfinTheme.colorScheme.background.copy(alpha = 0.55f),
						Color.Transparent,
					),
				),
			),
		)

		// Bottom gradient scrim to blend into the rows below
		Box(
			Modifier.fillMaxSize().background(
				Brush.verticalGradient(
					colors = listOf(
						Color.Transparent,
						Color.Transparent,
						JellyfinTheme.colorScheme.background.copy(alpha = 0.65f),
						JellyfinTheme.colorScheme.background,
					),
				),
			),
		)

		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(start = 44.dp, end = 44.dp, top = 30.dp, bottom = 34.dp),
			verticalArrangement = Arrangement.SpaceBetween,
		) {
			// Header label + page dots
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically,
			) {
				Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Box(
						Modifier
							.width(4.dp)
							.height(20.dp)
							.background(JellyfinTheme.colorScheme.buttonFocused, RoundedCornerShape(2.dp)),
					)
					Text(
						"精选推荐",
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.9f),
						style = JellyfinTheme.typography.default.copy(
							fontSize = 16.sp,
							fontWeight = FontWeight.Bold,
						),
					)
				}

				if (featuredCount > 1) {
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						repeat(featuredCount) { i ->
							Box(
								Modifier
									.size(width = if (i == heroIndex) 20.dp else 6.dp, height = 6.dp)
									.background(
										if (i == heroIndex) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.3f),
										RoundedCornerShape(3.dp),
									),
							)
						}
					}
				}
			}

			// Info panel + action buttons (bottom-left)
			Column(
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Text(
					item?.name ?: "",
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default.copy(
						fontSize = 34.sp,
						fontWeight = FontWeight.Bold,
					),
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)

				Text(
					buildHeroMeta(item),
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.75f),
					style = JellyfinTheme.typography.default.copy(
						fontSize = 15.sp,
					),
					maxLines = 1,
				)

				item?.overview?.let { overview ->
					Text(
						overview,
						color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.82f),
						style = JellyfinTheme.typography.default.copy(fontSize = 16.sp),
						maxLines = 2,
						overflow = TextOverflow.Ellipsis,
						modifier = Modifier.width(520.dp),
					)
				}

				Row(
					horizontalArrangement = Arrangement.spacedBy(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier.padding(top = 8.dp),
				) {
					HeroPlayButton(onClick = onPlay)
					HeroInfoButton(onClick = onInfo)
				}
			}
		}
	}
}

@Composable
private fun HeroPlayButton(onClick: () -> Unit) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.size(120.dp)
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
			modifier = Modifier.size(44.dp),
		)
	}
}

@Composable
private fun HeroInfoButton(onClick: () -> Unit) {
	var focused by remember { mutableStateOf(false) }

	Box(
		modifier = Modifier
			.size(60.dp)
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
			modifier = Modifier.size(24.dp),
		)
	}
}

private fun buildHeroMeta(item: BaseItemDto?): String = buildString {
	item ?: return@buildString
	item.productionYear?.let { append(it); append("  ") }
	item.communityRating?.let { append("★ "); append("%.1f".format(it)); append("  ") }
	item.officialRating?.let { append(it); append("  ") }
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
		Row(
			modifier = Modifier
				.padding(start = 44.dp, end = 44.dp, bottom = 12.dp),
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
			contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 44.dp, end = 44.dp),
			horizontalArrangement = Arrangement.spacedBy(15.dp),
		) {
			items(items, key = { it.id }) { item ->
				ArcticCard(item = item, onClick = { onItemClick(item) })
			}
		}
	}
}

@Composable
private fun ArcticCard(
	item: BaseItemDto,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val api = koinInject<ApiClient>()
	val image = item.itemImages.values.firstOrNull() ?: item.itemBackdropImages.firstOrNull()
	val aspect = image?.aspectRatio?.takeIf { it > 0.1f } ?: 0.667f
	var focused by remember { mutableStateOf(false) }

	ItemCard(
		modifier = modifier
			.width(146.dp)
			.height((146.dp / aspect))
			.scale(if (focused) 1.07f else 1f)
			.onFocusChanged { focused = it.hasFocus }
			.clickable(onClick = onClick),
		focused = focused,
		image = {
			if (image != null) {
				AsyncImage(
					url = image.getUrl(api, maxWidth = 360),
					blurHash = image.blurHash,
					aspectRatio = aspect,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.fillMaxSize(),
				)
			}
		},
		overlay = {
			// Focus ring drawn ON TOP of the artwork
			if (focused) {
				Box(
					Modifier
						.fillMaxSize()
						.border(
							width = 3.dp,
							color = JellyfinTheme.colorScheme.buttonFocused,
							shape = JellyfinTheme.shapes.medium,
						),
				)
			}
			Box(
				Modifier
					.fillMaxWidth()
					.align(Alignment.BottomStart)
					.background(
						Brush.verticalGradient(
							colors = listOf(Color.Transparent, JellyfinTheme.colorScheme.background.copy(alpha = 0.85f)),
						),
					)
					.padding(8.dp),
			) {
				Text(
					item.name ?: "",
					color = JellyfinTheme.colorScheme.onBackground,
					style = JellyfinTheme.typography.default,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		},
	)
}

// endregion
