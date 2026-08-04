package org.jellyfin.androidtv.ui.base

import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import org.jellyfin.androidtv.R
import org.jellyfin.design.Tokens

fun colorScheme(): ColorScheme = ColorScheme(
	background = Tokens.Color.colorGrey975,
	onBackground = Tokens.Color.colorBluegrey25,
	button = Color(0xB3747474),
	onButton = Color(0xFFDDDDDD),
	buttonFocused = Color(0xE6CCCCCC),
	onButtonFocused = Color(0xFF444444),
	buttonDisabled = Color(0x33747474),
	onButtonDisabled = Color(0xFF686868),
	buttonActive = Color(0x4DCCCCCC),
	onButtonActive = Color(0xFFDDDDDD),
	input = Color(0xB3747474),
	onInput = Color(0xE6CCCCCC),
	inputFocused = Color(0xE6CCCCCC),
	onInputFocused = Color(0xFFDDDDDD),
	rangeControlBackground = Tokens.Color.colorBluegrey700,
	rangeControlFill = Tokens.Color.colorCyan500,
	rangeControlKnob = Tokens.Color.colorBluegrey100,
	seekbarBuffer = Tokens.Color.colorBluegrey300,
	recording = Tokens.Color.colorRed300,
	onRecording = Tokens.Color.colorRed25,
	badge = Tokens.Color.colorCyan500,
	onBadge = Tokens.Color.colorBluegrey100,
	listHeader = Tokens.Color.colorGrey50,
	listOverline = Tokens.Color.colorGrey500,
	listHeadline = Tokens.Color.colorGrey25,
	listCaption = Tokens.Color.colorGrey200,
	listButton = Color.Transparent,
	listButtonFocused = Tokens.Color.colorBluegrey800,
	surface = Tokens.Color.colorBluegrey900,
	scrim = Tokens.Color.colorBlack.copy(alpha = 0.67f),
)

/**
 * Build a [ColorScheme] by reading the current Android theme attributes.
 * This allows the XML theme (Jellyfin / Netflix / Infuse) to influence
 * Compose-based UI without hard-coding every color.
 */
@Composable
fun dynamicColorScheme(): ColorScheme {
	val context = LocalContext.current
	val theme = context.theme

	return remember(theme) {
		val attrs = intArrayOf(
			android.R.attr.colorPrimary,
			android.R.attr.colorAccent,
			R.attr.defaultBackground,
			R.attr.cardViewBackground,
			R.attr.buttonDefaultHighlightBackground,
			R.attr.headerTextColor,
		)
		val typedArray = theme.obtainStyledAttributes(attrs)

		val colorPrimary = typedArray.getColor(0, 0xFF1C2026.toInt())
		val colorAccent = typedArray.getColor(1, 0xFF00A4DD.toInt())
		val defaultBackgroundDrawable = typedArray.getDrawable(2)
		val cardViewBackground = typedArray.getColor(3, 0)
		val buttonHighlight = typedArray.getColor(4, colorAccent)
		val headerText = typedArray.getColor(5, 0xFFF5F5F5.toInt())

		typedArray.recycle()

		// NOTE: use Color(Int) for ARGB values. Color(ULong) expects Compose's packed
		// internal representation -- feeding it an ARGB int yields a transparent/black
		// color, which silently breaks every themed color.
		val defaultBackground = when (defaultBackgroundDrawable) {
			is ColorDrawable -> Color(defaultBackgroundDrawable.color)
			else -> Tokens.Color.colorGrey975
		}

		val base = colorScheme()
		base.copy(
			background = defaultBackground,
			surface = if (cardViewBackground == 0) base.surface else Color(cardViewBackground),
			buttonFocused = Color(buttonHighlight),
			onButtonFocused = Color.White,
			rangeControlFill = Color(colorAccent),
			badge = Color(colorAccent),
			listButtonFocused = Color(colorPrimary),
			listHeader = Color(headerText),
		)
	}
}

@Immutable
data class ColorScheme(
	val background: Color,
	val onBackground: Color,

	val button: Color,
	val onButton: Color,
	val buttonFocused: Color,
	val onButtonFocused: Color,
	val buttonDisabled: Color,
	val onButtonDisabled: Color,
	val buttonActive: Color,
	val onButtonActive: Color,

	val input: Color,
	val onInput: Color,
	val inputFocused: Color,
	val onInputFocused: Color,

	val rangeControlBackground: Color,
	val rangeControlFill: Color,
	val rangeControlKnob: Color,
	val seekbarBuffer: Color,

	val recording: Color,
	val onRecording: Color,

	val badge: Color,
	val onBadge: Color,

	val listHeader: Color,
	val listOverline: Color,
	val listHeadline: Color,
	val listCaption: Color,
	val listButton: Color,
	val listButtonFocused: Color,

	val surface: Color,
	val scrim: Color,
)

val LocalColorScheme = staticCompositionLocalOf { colorScheme() }
