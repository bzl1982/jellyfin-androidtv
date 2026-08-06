package org.jellyfin.androidtv.ui.composable.compat

import android.content.Context
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jellyfin.androidtv.ui.browsing.DestinationFragmentView
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationAction
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.koin.compose.koinInject

@Composable
fun AppNavigationHost(
	modifier: Modifier = Modifier,
	navigationRepository: NavigationRepository = koinInject(),
) {
	val factory = remember { AppNavigationHostViewFactory() }

	val canGoBack by remember {
		navigationRepository.currentAction.map { navigationRepository.canGoBack }.distinctUntilChanged()
	}.collectAsState(navigationRepository.canGoBack)

	// BACK never leaves the app. Wherever the user is, one press goes straight
	// home (the whole stack is dropped, not popped one screen at a time); once
	// home, the key is swallowed so the TV launcher is unreachable from here.
	// Screens with their own internal stack (settings, players) still register
	// their own BackHandler and get the key first.
	BackHandler(enabled = true) {
		if (canGoBack) navigationRepository.reset(Destinations.home, clearHistory = true)
	}

	AndroidView(
		factory = factory,
		modifier = modifier,
	)

	LaunchedEffect(Unit) {
		navigationRepository.currentAction.collect { action ->
			when (action) {
				is NavigationAction.NavigateFragment -> factory.view.navigate(action)
				NavigationAction.GoBack -> factory.view.goBack()
				NavigationAction.Nothing -> Unit
			}
		}
	}
}

private class AppNavigationHostViewFactory : (Context) -> View {
	private var _view: DestinationFragmentView? = null

	val view get() = requireNotNull(_view)

	override operator fun invoke(
		context: Context
	): View = DestinationFragmentView(context).also { view ->
		_view = view
	}
}
