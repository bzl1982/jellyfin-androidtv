package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.jellyfin.androidtv.BuildConfig
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.model.ConnectedState
import org.jellyfin.androidtv.auth.model.Server
import org.jellyfin.androidtv.auth.model.UnableToConnectState
import org.jellyfin.androidtv.data.model.AppNotification
import org.jellyfin.androidtv.data.repository.NotificationsRepository
import org.jellyfin.androidtv.databinding.FragmentSelectServerBinding
import org.jellyfin.androidtv.ui.ServerButton
import org.jellyfin.androidtv.ui.base.CircularProgressIndicator
import org.jellyfin.androidtv.ui.base.FuseColors
import org.jellyfin.androidtv.ui.base.Icon
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.button.ButtonDefaults
import org.jellyfin.androidtv.ui.startup.StartupViewModel
import org.jellyfin.androidtv.util.createBundle
import org.jellyfin.androidtv.util.getSummary
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.compose.koinInject
import java.util.UUID

class SelectServerFragment : Fragment() {
	private var _binding: FragmentSelectServerBinding? = null
	private val binding get() = _binding!!
	private val startupViewModel: StartupViewModel by activityViewModel()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentSelectServerBinding.inflate(inflater, container, false)

		binding.composeView.setContent {
			JellyfinTheme {
				val stored by startupViewModel.storedServers.collectAsState()
				val discovered by startupViewModel.discoveredServers.collectAsState()
				var discoveryLoading by remember { mutableStateOf(true) }
				val notificationsRepository = koinInject<NotificationsRepository>()
				val notifications by notificationsRepository.notifications.collectAsState()

				// Hide the discovery spinner once the first real scan result arrives.
				LaunchedEffect(Unit) {
					startupViewModel.discoveredServers.drop(1).collect { discoveryLoading = false }
				}

				// 宸蹭繚瀛樼殑鏈嶅姟鍣ㄤ笉鍐嶉噸澶嶅嚭鐜板湪銆屽彂鐜般�嶅尯锛氫竴鏄鎰熼噸澶嶏紝
				// 浜屾槸涓や釜鍒嗗尯鍦ㄥ悓涓�涓? LazyColumn 閲岋紝鍚? id 浼氳Е鍙? duplicate key 宕╂簝銆?
				// 已保存的服务器不再重复出现在「发现」区：双重去重
				//   (1) 过滤跨列表重复（NAS 既被保存又被发现广播回包）
				//   (2) 发现广播本身可能收到多份相同 id 回包 → 同一 LazyColumn 内也会撞 key
				val discoveredOnly = remember(stored, discovered) {
					discovered
						.filter { d -> stored.none { it.id == d.id } }
						.distinctBy { it.id }
				}

				SelectServerScreen(
					storedServers = stored,
					discoveredServers = discoveredOnly,
					discoveryLoading = discoveryLoading,
					notifications = notifications.filter { it.public },
					onStoredServerClick = { server -> navigateToServer(server.id) },
					onStoredServerLongClick = { server ->
						startupViewModel.deleteServer(server.id)
						Toast.makeText(
							requireContext(),
							getString(R.string.server_removed, server.name.ifBlank { server.address }),
							Toast.LENGTH_SHORT,
						).show()
					},
					onDiscoveryServerClick = { server -> addDiscoveryServer(server) },
					onManualAddClick = { navigateToServerAdd() },
				)
			}
		}

		binding.root.requestFocus()
		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()

		_binding = null
	}

	override fun onResume() {
		super.onResume()

		startupViewModel.reloadStoredServers()
		startupViewModel.loadDiscoveryServers()
	}

	private fun navigateToServer(serverId: UUID) {
		requireActivity().supportFragmentManager.commit {
			replace<StartupToolbarFragment>(R.id.content_view)
			add<ServerFragment>(
				R.id.content_view,
				null,
				createBundle {
					putString(ServerFragment.ARG_SERVER_ID, serverId.toString())
				},
			)
			addToBackStack(null)
		}
	}

	private fun addDiscoveryServer(server: Server) {
		startupViewModel.addServer(server.address).onEach { state ->
			if (state is ConnectedState) {
				parentFragmentManager.commit {
					replace<StartupToolbarFragment>(R.id.content_view)
					add<ServerFragment>(
						R.id.content_view,
						null,
						createBundle {
							putString(ServerFragment.ARG_SERVER_ID, state.id.toString())
						},
					)
				}
			} else if (state is UnableToConnectState) {
				Toast.makeText(
					requireContext(),
					getString(
						R.string.server_connection_failed_candidates,
						state.addressCandidates
							.map { "${it.key} ${it.value.getSummary(requireContext())}" }
							.joinToString(prefix = "\n", separator = "\n"),
					),
					Toast.LENGTH_LONG,
				).show()
			}
		}.launchIn(lifecycleScope)
	}

	private fun navigateToServerAdd() {
		parentFragmentManager.commit {
			addToBackStack(null)
			replace<ServerAddFragment>(R.id.content_view)
		}
	}
}

@Composable
private fun SelectServerScreen(
	storedServers: List<Server>,
	discoveredServers: List<Server>,
	discoveryLoading: Boolean,
	notifications: List<AppNotification>,
	onStoredServerClick: (Server) -> Unit,
	onStoredServerLongClick: (Server) -> Unit,
	onDiscoveryServerClick: (Server) -> Unit,
	onManualAddClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val accent = JellyfinTheme.colorScheme.rangeControlFill

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(FuseColors.mainBg100),
	) {
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 56.dp, vertical = 40.dp),
			verticalArrangement = Arrangement.spacedBy(14.dp),
			contentPadding = PaddingValues(bottom = 40.dp),
		) {
			// Brand header
			item {
				Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
					Text(
						text = "Lorla",
						color = accent,
						fontSize = 40.sp,
						fontWeight = FontWeight.Bold,
					)
					Text(
						text = stringResource(R.string.welcome_content),
						color = FuseColors.mainFg50,
						fontSize = 18.sp,
					)
				}
			}

			// Stored servers
			if (storedServers.isNotEmpty()) {
				item { SectionHeader(stringResource(R.string.saved_servers)) }
				// key 蹇呴』甯﹀垎鍖哄墠缂�锛氬悓涓�鍙版湇鍔″櫒鍙兘鏃㈠凡淇濆瓨銆佸張琚眬鍩熺綉鍙戠幇锛?
				// 涓や釜鍒嗗尯鍦ㄥ悓涓�涓? LazyColumn 閲岋紝瑁哥敤 it.id 浼? duplicate key 宕╂簝銆?
				items(storedServers, key = { "stored_${it.id}" }) { server ->
					ServerCard(
						server = server,
						onClick = { onStoredServerClick(server) },
						onLongClick = { onStoredServerLongClick(server) },
					)
				}
			} else {
				item {
					Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(
							text = stringResource(R.string.welcome_title),
							color = FuseColors.mainFg100,
							fontSize = 24.sp,
							fontWeight = FontWeight.Medium,
						)
						Text(
							text = stringResource(R.string.welcome_content),
							color = FuseColors.mainFg50,
							fontSize = 16.sp,
						)
					}
				}
			}

			// Manual add
			item { ManualAddButton(onClick = onManualAddClick) }

			// Discovery
			item {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					SectionHeader(stringResource(R.string.discovered_servers_title))
					if (discoveryLoading) {
						CircularProgressIndicator(Modifier.width(18.dp).height(18.dp))
					}
				}
			}
			if (discoveredServers.isNotEmpty()) {
				items(discoveredServers, key = { "found_${it.id}" }) { server ->
					ServerCard(
						server = server,
						onClick = { onDiscoveryServerClick(server) },
					)
				}
			} else if (!discoveryLoading) {
				item {
					Text(
						text = stringResource(R.string.discovered_servers_empty),
						color = FuseColors.mainFg50,
						fontSize = 16.sp,
					)
				}
			}

			// Notifications
			if (notifications.isNotEmpty()) {
				item { SectionHeader("閫氱煡") }
				items(notifications) { notification ->
					Box(
						modifier = Modifier
							.fillMaxWidth()
							.clip(JellyfinTheme.shapes.medium)
							.background(FuseColors.mainSoft)
							.padding(12.dp),
					) {
						Text(
							text = notification.message,
							color = FuseColors.mainFg90,
							fontSize = 14.sp,
						)
					}
				}
			}

			// Footer
			item {
				Text(
					text = "lorla-androidtv ${BuildConfig.VERSION_NAME} ${BuildConfig.BUILD_TYPE}",
					color = FuseColors.mainFg25,
					fontSize = 12.sp,
				)
			}
		}
	}
}

@Composable
private fun SectionHeader(text: String) {
	Text(
		text = text,
		color = JellyfinTheme.colorScheme.rangeControlFill,
		fontSize = 16.sp,
		fontWeight = FontWeight.Medium,
	)
}

@Composable
private fun ServerCard(
	server: Server,
	onClick: () -> Unit,
	onLongClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val accent = JellyfinTheme.colorScheme.rangeControlFill

	Box(
		modifier = modifier
			.fillMaxWidth()
			.border(
				width = if (focused) 2.dp else 0.dp,
				color = accent,
				shape = ButtonDefaults.Shape,
			),
	) {
		ServerButton(
			icon = {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_house),
					contentDescription = null,
				)
			},
			name = { Text(server.name.ifBlank { server.address }) },
			address = { Text(server.address) },
			version = { Text(server.version.orEmpty()) },
			onClick = onClick,
			onLongClick = onLongClick,
			interactionSource = interactionSource,
			shape = ButtonDefaults.Shape,
			modifier = Modifier.height(64.dp),
		)
	}
}

@Composable
private fun ManualAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
	val accent = JellyfinTheme.colorScheme.rangeControlFill

	Box(
		modifier = modifier
			.fillMaxWidth()
			.border(width = 2.dp, color = accent, shape = ButtonDefaults.Shape),
	) {
		ServerButton(
			icon = {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.ic_add),
					contentDescription = null,
				)
			},
			name = { Text(stringResource(R.string.add_server_manually)) },
			address = { },
			version = { },
			onClick = onClick,
			shape = ButtonDefaults.Shape,
			modifier = Modifier.height(56.dp),
		)
	}
}
