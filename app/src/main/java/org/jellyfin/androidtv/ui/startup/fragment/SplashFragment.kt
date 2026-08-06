package org.jellyfin.androidtv.ui.startup.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.compose.content
import kotlinx.coroutines.delay
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.Text

/**
 * 启动闪屏：洛拉 logo +「欢迎使用 Lorla」+ 循环加载点动画。
 *
 * 早期版本只显示一张 logo 图，在大库 / 弱网环境下，启动画面一闪而过、
 * 紧接着就是白屏 + 慢慢加载图片的首页，给人很糟糕的「卡了」错觉。
 * 加上欢迎语和加载点后，至少让用户看到「系统正在准备内容」的状态。
 *
 * 点动画用 3 个 alpha 错峰的 Circle，分阶段 fade，最朴素但稳。
 */
@Composable
fun SplashScreen() {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color(0xFF0A0A0A)),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			Image(
				painter = painterResource(R.drawable.lorla_splash),
				contentDescription = stringResource(R.string.app_name),
				modifier = Modifier
					.fillMaxHeight(0.55f)
					.width(360.dp),
			)

			Spacer(Modifier.height(28.dp))

			Text(
				text = "欢迎使用 Lorla",
				color = Color(0xFFFFFFFF),
				style = JellyfinTheme.typography.default.copy(
					fontSize = 26.sp,
					fontWeight = FontWeight.SemiBold,
				),
			)

			Spacer(Modifier.height(18.dp))

			LoadingDotsRow()
		}
	}
}

/**
 * 三个小圆点循环呼吸：phase 0/120/240ms 错峰 alpha 1↔0.25。
 * 用 rememberInfiniteTransition 实现，不需要外部依赖。
 */
@Composable
private fun LoadingDotsRow() {
	val transition = rememberInfiniteTransition(label = "splash-dots")
	val phases = listOf(0, 120, 240)
	Row(
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		phases.forEach { delayMs ->
			val a by transition.animateFloat(
				initialValue = 0.25f,
				targetValue = 1.0f,
				animationSpec = infiniteRepeatable(
					animation = tween(720, delayMillis = delayMs, easing = LinearEasing),
				),
				label = "dot-$delayMs",
			)
			Box(
				Modifier
					.size(8.dp)
					.alpha(a)
					.background(Color(0xFFE50914), CircleShape),
			)
		}
	}
	Text(
		text = "  加载中请稍后",
		color = Color(0xFFCCCCCC),
		style = JellyfinTheme.typography.default.copy(fontSize = 14.sp),
	)
}

class SplashFragment : Fragment() {
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	) = content {
		JellyfinTheme {
			SplashScreen()
		}
	}
}
