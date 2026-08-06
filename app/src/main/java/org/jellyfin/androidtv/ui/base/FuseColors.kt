package org.jellyfin.androidtv.ui.base

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 1:1 port of the Arctic Fuse 3 colour palette and its "soft transition" masks.
 *
 * Palette values come straight from the skin:
 *   colors/defaults.xml                    -> main / overlay / shadow / dialog
 *   colors/Skin default - Light dialogs.xml -> the light dialog variant
 *
 * The gradients below replace the PNG masks the skin blends its artwork with.
 * Every ramp is an 11-point sample (0%..100%) taken from the actual images, so
 * the Compose gradient follows the exact same curve as Kodi does:
 *   media/diffuse/flixart/flixart_new.png            -> artwork feather
 *   extras/backgrounds/overlay/combined_flixart.png  -> the soft vignette
 *
 * That is why nothing in the big stage has a hard edge: the artwork itself
 * dissolves into the background instead of being cut off by a scrim band.
 */
object FuseColors {
	// region defaults.xml - main palette

	val mainFg100 = Color(0xFFEDEDED)
	val mainFg90 = Color(0xE6EDEDED)
	val mainFg75 = Color(0xBFEDEDED)
	val mainFg50 = Color(0x80EDEDED)
	val mainFg25 = Color(0x40EDEDED)

	val mainBg100 = Color(0xFF000000)
	val mainBg90 = Color(0xE6000000)
	val mainBg75 = Color(0xBF000000)
	val mainBg50 = Color(0x80000000)
	val mainBg25 = Color(0x40000000)

	/** main_soft = 4d4d4d4d - the translucent panel fill used all over FUSE. */
	val mainSoft = Color(0x4D4D4D4D)

	/** main_hard = b34d4d4d - the same grey but nearly opaque. */
	val mainHard = Color(0xB34D4D4D)

	/** main_back = ff4d4d4d - opaque panel/card background. */
	val mainBack = Color(0xFF4D4D4D)

	/** main_logo = ffb3b3b3 - clear-logo tint. */
	val mainLogo = Color(0xFFB3B3B3)

	val overlaySoft = Color(0x12FFFFFF)
	val overlayHard = Color(0x33FFFFFF)

	val shadowSoft = Color(0x1A000000)
	val shadowHard = Color(0x80000000)
	val shadowFull = Color(0xC0000000)

	val yellowStar = Color(0xD7FFCD3C)
	val watchedProgress = Color(0xFF03B585)

	/** Includes_Colors.xml: ColorHighlight / ColorSelected. */
	val focusHighlight = Color(0xFFFFFFFF)
	val focusSelected = Color(0xFF272727)

	// endregion

	// region dialog palette

	val dialogNib = Color(0xFFA3A5AE)
	val dialogOverlay = Color(0xB3F0F0F8)
	val dialogBgLight = Color(0xFFEDEDED)
	val dialogFgLight = Color(0xFF181818)
	val dialogBgDark = Color(0xFF000000)
	val dialogFgDark = Color(0xFFEDEDED)

	// endregion

	// region sampled masks

	/** flixart_new.png horizontal alpha, left -> right (art appears from the left). */
	private val FLIXART_H = floatArrayOf(
		0.000f, 0.161f, 0.843f, 0.996f, 0.996f, 1.000f, 1.000f, 1.000f, 1.000f, 0.988f, 0.588f,
	)

	/** flixart_new.png vertical alpha, top -> bottom (art dissolves at the bottom). */
	private val FLIXART_V = floatArrayOf(
		0.545f, 0.894f, 0.988f, 1.000f, 1.000f, 1.000f, 0.973f, 0.780f, 0.349f, 0.055f, 0.000f,
	)

	/** combined_flixart.png horizontal alpha - dark on the text side, clear on the right. */
	private val VIGNETTE_H = floatArrayOf(
		0.851f, 0.843f, 0.831f, 0.780f, 0.659f, 0.486f, 0.345f, 0.247f, 0.196f, 0.169f, 0.153f,
	)

	/** combined_flixart.png vertical alpha - clear on top, dark under the controls. */
	private val VIGNETTE_V = floatArrayOf(
		0.082f, 0.098f, 0.133f, 0.196f, 0.310f, 0.486f, 0.702f, 0.898f, 0.969f, 0.992f, 0.996f,
	)

	private fun stops(curve: FloatArray, color: Color, strength: Float): Array<Pair<Float, Color>> =
		Array(curve.size) { index ->
			val position = index / (curve.size - 1f)
			position to color.copy(alpha = (curve[index] * strength).coerceIn(0f, 1f))
		}

	private fun inverted(curve: FloatArray): FloatArray = FloatArray(curve.size) { 1f - curve[it] }

	/**
	 * Artwork feather on the left edge: the backdrop melts into [background]
	 * instead of ending on a straight line.
	 */
	fun artFeatherLeft(background: Color, strength: Float = 1f): Brush =
		Brush.horizontalGradient(*stops(inverted(FLIXART_H), background, strength))

	/** Artwork feather at the bottom - blends the stage into the poster wall below. */
	fun artFeatherBottom(background: Color, strength: Float = 1f): Brush =
		Brush.verticalGradient(*stops(inverted(FLIXART_V), background, strength))

	/** combined_flixart left vignette: readable text without a visible band. */
	fun vignetteLeft(strength: Float = 1f): Brush =
		Brush.horizontalGradient(*stops(VIGNETTE_H, mainBg100, strength))

	/** combined_flixart bottom vignette: seats the control row on soft shadow. */
	fun vignetteBottom(strength: Float = 1f): Brush =
		Brush.verticalGradient(*stops(VIGNETTE_V, mainBg100, strength))

	/** Rail gradient - the sidebar fades away instead of ending on an edge. */
	fun railFade(background: Color, peak: Float): Brush = Brush.horizontalGradient(
		0.00f to background.copy(alpha = peak),
		0.35f to background.copy(alpha = peak * 0.72f),
		0.70f to background.copy(alpha = peak * 0.26f),
		1.00f to Color.Transparent,
	)

	// endregion
}
