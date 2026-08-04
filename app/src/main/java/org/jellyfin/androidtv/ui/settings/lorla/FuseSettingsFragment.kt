package org.jellyfin.androidtv.ui.settings.lorla

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.navigation.LocalRouter
import org.jellyfin.androidtv.ui.navigation.ProvideRouter
import org.jellyfin.androidtv.ui.settings.Routes
import org.jellyfin.androidtv.ui.settings.composable.SettingsRouterContent
import org.jellyfin.androidtv.ui.settings.routes

class FuseSettingsFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	) = content {
		JellyfinTheme {
			ProvideRouter(
				routes = routes,
				defaultRoute = Routes.FUSE_SETTINGS,
			) {
				val router = LocalRouter.current
				val canGoBack by remember { derivedStateOf { router.backStack.size > 1 } }

				BackHandler(enabled = canGoBack) {
					router.back()
				}

				SettingsRouterContent()
			}
		}
	}
}
