package org.jellyfin.androidtv.ui.home

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.data.repository.ItemRepository
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.jellyfin.androidtv.ui.composable.item.ItemCard
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.androidtv.ui.settings.compat.SettingsViewModel
import org.jellyfin.androidtv.util.apiclient.JellyfinImage
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.androidtv.data.repository.UserViewsRepository
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
	var hero by remember { mutableStateOf<BaseItemDto?>(null) }
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

				val heroItem = resume.firstOrNull()
					?: nextUp.firstOrNull()
					?: recentlyAdded.firstOrNull()
					?: perLibrary.firstOrNull()?.items?.firstOrNull()

				val rowList = buildList {
					if (resume.isNotEmpty()) add(ArcticRow("继续观看", resume))
					if (nextUp.isNotEmpty()) add(ArcticRow("接下来播放", nextUp))
					if (recentlyAdded.isNotEmpty()) add(ArcticRow("最近添加", recentlyAdded))
					addAll(perLibrary)
				}

				withContext(Dispatchers.Main) {
					libraries = views
					hero = heroItem
					rows = rowList
					loaded = true
				}
			}.onFailure { it.printStackTrace() }
		}
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
			hero = hero,
			rows = rows,
			loaded = loaded,
			onItemClick = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
			onHeroPlay = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
			onHeroInfo = { navigationRepository.navigate(Destinations.itemDetails(it.id)) },
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
			.focusGroup()
			.padding(top = 28.dp, bottom = 28.dp),
	) {
		entries.forEachIndexed { index, entry ->
			SidebarItem(
				entry = entry,
				modifier = if (index == 0) Modifier.focusRequester(initialFocus) else Modifier,
			)
		}
	}

	LaunchedEffect(initialFocus) { initialFocus.requestFocus() }
}

private fun collectionIcon(type: CollectionType?): Int = when (type) {
	CollectionType.MOVIES -> R.drawable.ic_movie
	CollectionType.TV_SHOWS -> R.drawable.ic_tv
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

	Row(
		modifier = modifier
			.fillMaxWidth()
			.clickable(onClick = entry.onClick)
			.onFocusChanged { focused = it.hasFocus }
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused.copy(alpha = 0.22f)
				else Color.Transparent,
			)
			.padding(horizontal = 20.dp, vertical = 15.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp),
	) {
		Box(
			Modifier
				.width(4.dp)
				.height(26.dp)
				.background(
					if (focused) JellyfinTheme.colorScheme.buttonFocused else Color.Transparent,
					RoundedCornerShape(2.dp),
				),
		)
		Icon(
			imageVector = ImageVector.vectorResource(entry.icon),
			contentDescription = null,
			tint = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.8f),
			modifier = Modifier.size(22.dp),
		)
		Text(
			entry.label,
			color = if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
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
	rows: List<ArcticRow>,
	loaded: Boolean,
	onItemClick: (BaseItemDto) -> Unit,
	onHeroPlay: (BaseItemDto) -> Unit,
	onHeroInfo: (BaseItemDto) -> Unit,
) {
	Column(
		modifier = modifier
			.verticalScroll(rememberScrollState())
			.focusGroup()
			.padding(top = 16.dp),
	) {
		if (!loaded) {
			Box(
				Modifier.fillMaxWidth().height(320.dp),
				contentAlignment = Alignment.Center,
			) {
				Text("加载中…", color = JellyfinTheme.colorScheme.onBackground)
			}
		} else {
			ArcticHero(hero, onHeroPlay, onHeroInfo)
			Spacer(Modifier.height(8.dp))
			rows.forEach { ArcticRowView(it.title, it.items, onItemClick) }
			Spacer(Modifier.height(48.dp))
		}
	}
}

@Composable
private fun ArcticHero(
	item: BaseItemDto?,
	onPlay: () -> Unit,
	onInfo: () -> Unit,
) {
	val api = koinInject<ApiClient>()
	val backdrop: JellyfinImage? = item?.itemBackdropImages?.firstOrNull()
		?: item?.itemImages?.values?.firstOrNull()

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.height(322.dp),
	) {
		if (backdrop != null) {
			AsyncImage(
				url = backdrop.getUrl(api, maxWidth = 1280),
				blurHash = backdrop.blurHash,
				scaleType = ImageView.ScaleType.CENTER_CROP,
				modifier = Modifier.fillMaxSize(),
			)
		}

		// Bottom gradient scrim for legibility (FUSE style)
		Box(
			Modifier.fillMaxSize().background(
				Brush.verticalGradient(
					colors = listOf(
						Color.Transparent,
						JellyfinTheme.colorScheme.background.copy(alpha = 0.55f),
						JellyfinTheme.colorScheme.background,
					),
				),
			),
		)

		Column(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.padding(start = 44.dp, bottom = 34.dp, end = 44.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(
				item?.name ?: "",
				color = JellyfinTheme.colorScheme.onBackground,
				style = JellyfinTheme.typography.default.copy(
					fontSize = 30.sp,
					fontWeight = FontWeight.Bold,
				),
				maxLines = 2,
				overflow = TextOverflow.Ellipsis,
			)
			item?.overview?.let { overview ->
				Text(
					overview,
					color = JellyfinTheme.colorScheme.onBackground.copy(alpha = 0.85f),
					style = JellyfinTheme.typography.default,
					maxLines = 3,
					overflow = TextOverflow.Ellipsis,
				)
			}
			Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
				ArcticActionButton(R.drawable.ic_play, "播放", onPlay)
				ArcticActionButton(R.drawable.ic_info, "详情", onInfo)
			}
		}
	}
}

@Composable
private fun ArcticActionButton(
	icon: Int,
	label: String,
	onClick: () -> Unit,
) {
	var focused by remember { mutableStateOf(false) }

	Row(
		modifier = Modifier
			.clickable(onClick = onClick)
			.onFocusChanged { focused = it.hasFocus }
			.background(
				if (focused) JellyfinTheme.colorScheme.buttonFocused else JellyfinTheme.colorScheme.surface.copy(alpha = 0.6f),
				RoundedCornerShape(8.dp),
			)
			.padding(horizontal = 20.dp, vertical = 11.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(9.dp),
	) {
		Icon(
			imageVector = ImageVector.vectorResource(icon),
			contentDescription = null,
			tint = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			modifier = Modifier.size(20.dp),
		)
		Text(
			label,
			color = if (focused) JellyfinTheme.colorScheme.onButtonFocused else JellyfinTheme.colorScheme.onBackground,
			style = JellyfinTheme.typography.default,
		)
	}
}

@Composable
private fun ArcticRowView(
	title: String,
	items: List<BaseItemDto>,
	onItemClick: (BaseItemDto) -> Unit,
) {
	Column(Modifier.padding(vertical = 10.dp)) {
		Text(
			title,
			color = JellyfinTheme.colorScheme.listHeader,
			style = JellyfinTheme.typography.default.copy(
				fontWeight = FontWeight.Bold,
				fontSize = 19.sp,
			),
			modifier = Modifier.padding(start = 44.dp, bottom = 10.dp),
		)
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
			.clickable(onClick = onClick)
			.onFocusChanged { focused = it.hasFocus },
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
			Box(
				Modifier
					.fillMaxWidth()
					.align(Alignment.BottomStart)
					.background(JellyfinTheme.colorScheme.surface.copy(alpha = 0.72f))
					.padding(7.dp),
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
