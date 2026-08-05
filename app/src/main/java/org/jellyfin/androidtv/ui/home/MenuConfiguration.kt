package org.jellyfin.androidtv.ui.home

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.R

/**
 * Persistent configuration for the LORLA left sidebar menus and the per-category
 * country sub-rows. Users can rename, reorder and hide entries; defaults can be
 * restored at any time.
 */

@Serializable
enum class RegionBucket(val label: String) {
	@SerialName("HUAYU") HUAYU("华语"),
	@SerialName("JPKR") JPKR("日韩"),
	@SerialName("WEST") WEST("欧美"),
	@SerialName("OTHER") OTHER("其它"),
}

@Serializable
enum class ProgramType(val label: String) {
	@SerialName("MOVIE") MOVIE("电影"),
	@SerialName("SERIES") SERIES("剧集"),
	@SerialName("VARIETY") VARIETY("综艺"),
	@SerialName("ANIME") ANIME("动漫"),
	@SerialName("DOC") DOC("纪录片"),
}

fun ProgramType.iconRes(): Int = when (this) {
	ProgramType.MOVIE -> R.drawable.ic_movie
	ProgramType.SERIES -> R.drawable.ic_tv_play
	ProgramType.VARIETY -> R.drawable.ic_masks
	ProgramType.ANIME -> R.drawable.ic_album
	ProgramType.DOC -> R.drawable.ic_camera
}

@Serializable
data class SubRowConfig(
	val id: String,
	val label: String,
	val visible: Boolean = true,
	val order: Int,
)

@Serializable
data class CategoryMenuConfig(
	val id: String,
	val regionBucket: RegionBucket,
	val programType: ProgramType,
	val label: String,
	val visible: Boolean = true,
	val order: Int,
	val subRows: List<SubRowConfig>,
)

@Serializable
data class LorlaMenuConfig(
	val version: Int = 1,
	val categories: List<CategoryMenuConfig>,
)

object MenuDefaults {

	fun defaultConfig(): LorlaMenuConfig {
		var order = 0
		return LorlaMenuConfig(
			categories = listOf(
				// 华语
				category(RegionBucket.HUAYU, ProgramType.MOVIE, order++),
				category(RegionBucket.HUAYU, ProgramType.SERIES, order++),
				category(RegionBucket.HUAYU, ProgramType.ANIME, order++),
				category(RegionBucket.HUAYU, ProgramType.VARIETY, order++),
				category(RegionBucket.HUAYU, ProgramType.DOC, order++),
				// 日韩
				category(RegionBucket.JPKR, ProgramType.MOVIE, order++),
				category(RegionBucket.JPKR, ProgramType.SERIES, order++),
				category(RegionBucket.JPKR, ProgramType.ANIME, order++),
				category(RegionBucket.JPKR, ProgramType.VARIETY, order++),
				category(RegionBucket.JPKR, ProgramType.DOC, order++),
				// 欧美
				category(RegionBucket.WEST, ProgramType.MOVIE, order++),
				category(RegionBucket.WEST, ProgramType.SERIES, order++),
				category(RegionBucket.WEST, ProgramType.ANIME, order++),
				category(RegionBucket.WEST, ProgramType.VARIETY, order++),
				category(RegionBucket.WEST, ProgramType.DOC, order++),
				// 其它
				category(RegionBucket.OTHER, ProgramType.MOVIE, order++),
				category(RegionBucket.OTHER, ProgramType.SERIES, order++),
				category(RegionBucket.OTHER, ProgramType.ANIME, order++),
				category(RegionBucket.OTHER, ProgramType.VARIETY, order++),
				category(RegionBucket.OTHER, ProgramType.DOC, order++),
			),
		)
	}

	private fun category(bucket: RegionBucket, type: ProgramType, order: Int): CategoryMenuConfig {
		return CategoryMenuConfig(
			id = "${bucket.name}_${type.name}",
			regionBucket = bucket,
			programType = type,
			label = "${bucket.label}${type.label}",
			order = order,
			subRows = defaultSubRows(bucket, type),
		)
	}

	private fun defaultSubRows(bucket: RegionBucket, type: ProgramType): List<SubRowConfig> {
		val suffix = type.label
		return when (bucket) {
			RegionBucket.HUAYU -> listOf(
				SubRowConfig("MAINLAND", "大陆$suffix", order = 0),
				SubRowConfig("HONGKONG", "香港$suffix", order = 1),
				SubRowConfig("TAIWAN", "台湾$suffix", order = 2),
				SubRowConfig("MACAO_OTHER", "澳门及其它$suffix", order = 3),
			)
			RegionBucket.JPKR -> listOf(
				SubRowConfig("KOREA", "韩国$suffix", order = 0),
				SubRowConfig("JAPAN", "日本$suffix", order = 1),
				SubRowConfig("JPKR_OTHER", "其它$suffix", order = 2, visible = false),
			)
			RegionBucket.WEST -> listOf(
				SubRowConfig("EUROPE", "欧洲$suffix", order = 0),
				SubRowConfig("NORTH_AMERICA", "北美$suffix", order = 1),
				SubRowConfig("SOUTH_AMERICA", "南美$suffix", order = 2),
				SubRowConfig("WEST_OTHER", "其它$suffix", order = 3, visible = false),
			)
			RegionBucket.OTHER -> emptyList()
		}
	}

	/**
	 * Map a single NFO country string to a sub-row id inside its [bucket].
	 * The matching is intentionally strict about the user's requested partitions.
	 */
	fun countrySubRowId(bucket: RegionBucket, country: String): String? {
		val s = country.lowercase().trim()
		return when (bucket) {
			RegionBucket.HUAYU -> when {
				s.contains("大陆") || s.contains("中国") && !s.contains("香港") && !s.contains("台湾") && !s.contains("臺灣") && !s.contains("澳门") && !s.contains("澳門") ||
					s == "cn" || s == "china" || s.contains("people's republic of china") || s.contains("中华人民共和国") -> "MAINLAND"
				s.contains("香港") || s.contains("hong kong") || s.contains("hongkong") -> "HONGKONG"
				s.contains("台湾") || s.contains("臺灣") || s.contains("taiwan") -> "TAIWAN"
				s.contains("澳门") || s.contains("澳門") || s.contains("macao") || s.contains("macau") ||
					s.contains("新加坡") || s.contains("singapore") || s.contains("malaysia") || s.contains("马来西亚") -> "MACAO_OTHER"
				else -> "MACAO_OTHER"
			}
			RegionBucket.JPKR -> when {
				s.contains("韩国") || s.contains("韓國") || s.contains("korea") || s == "kr" -> "KOREA"
				s.contains("日本") || s.contains("japan") || s == "jp" -> "JAPAN"
				else -> "JPKR_OTHER"
			}
			RegionBucket.WEST -> when {
				s.contains("美国") || s.contains("united states") || s.contains("usa") || s == "us" ||
					s.contains("加拿大") || s.contains("canada") || s == "ca" -> "NORTH_AMERICA"
				s.contains("巴西") || s.contains("brazil") || s.contains("brasil") ||
					s.contains("阿根廷") || s.contains("argentina") ||
					s.contains("智利") || s.contains("chile") ||
					s.contains("哥伦比亚") || s.contains("colombia") ||
					s.contains("秘鲁") || s.contains("peru") ||
					s.contains("委内瑞拉") || s.contains("venezuela") ||
					s.contains("乌拉圭") || s.contains("uruguay") -> "SOUTH_AMERICA"
				else -> "EUROPE"
			}
			RegionBucket.OTHER -> null
		}
	}

	/**
	 * Classify a country string into one of the four top-level buckets.
	 */
	fun classifyBucket(countries: List<String>?): RegionBucket {
		if (countries.isNullOrEmpty()) return RegionBucket.OTHER
		for (raw in countries) {
			val s = raw.lowercase().trim()
			when {
				s.contains("中国") || s.contains("大陆") || s == "cn" || s.contains("china") ||
					s.contains("中华人民共和国") || s.contains("香港") || s.contains("台湾") || s.contains("臺灣") ||
					s.contains("澳门") || s.contains("澳門") || s.contains("macao") || s.contains("macau") ||
					s.contains("新加坡") || s.contains("singapore") || s.contains("马来西亚") || s.contains("malaysia") -> return RegionBucket.HUAYU
				s.contains("日本") || s.contains("japan") || s == "jp" ||
					s.contains("韩国") || s.contains("韓國") || s.contains("korea") || s == "kr" -> return RegionBucket.JPKR
				s.contains("美国") || s.contains("united states") || s.contains("usa") || s == "us" ||
					s.contains("英国") || s.contains("uk") || s.contains("britain") ||
					s.contains("法国") || s.contains("france") ||
					s.contains("德国") || s.contains("germany") ||
					s.contains("意大利") || s.contains("italy") ||
					s.contains("西班牙") || s.contains("spain") ||
					s.contains("加拿大") || s.contains("canada") ||
					s.contains("澳大利亚") || s.contains("australia") ||
					s.contains("俄罗斯") || s.contains("russia") ||
					s.contains("新西兰") || s.contains("zealand") ||
					s.contains("荷兰") || s.contains("netherlands") || s.contains("holland") ||
					s.contains("葡萄牙") || s.contains("portugal") ||
					s.contains("瑞典") || s.contains("sweden") ||
					s.contains("丹麦") || s.contains("denmark") ||
					s.contains("挪威") || s.contains("norway") ||
					s.contains("比利时") || s.contains("belgium") ||
					s.contains("奥地利") || s.contains("austria") ||
					s.contains("爱尔兰") || s.contains("ireland") ||
					s.contains("波兰") || s.contains("poland") ||
					s.contains("巴西") || s.contains("brazil") ||
					s.contains("墨西哥") || s.contains("mexico") ||
					s.contains("阿根廷") || s.contains("argentina") ||
					s.contains("瑞士") || s.contains("switzerland") ||
					s.contains("土耳其") || s.contains("turkey") ||
					s.contains("希腊") || s.contains("greece") ||
					s.contains("捷克") || s.contains("czech") -> return RegionBucket.WEST
			}
		}
		// Pure-latin names default to 欧美; otherwise 其它.
		val first = countries.first().lowercase().trim()
		return if (first.all { it.isLetter() && it.code < 128 }) RegionBucket.WEST else RegionBucket.OTHER
	}

	fun classifyProgramType(item: org.jellyfin.sdk.model.api.BaseItemDto): ProgramType? {
		return when (item.type) {
			org.jellyfin.sdk.model.api.BaseItemKind.MOVIE -> ProgramType.MOVIE
			org.jellyfin.sdk.model.api.BaseItemKind.SERIES -> {
				val g = item.genres.orEmpty().map { it.lowercase() }
				when {
					g.any { it.contains("综艺") || it.contains("variety") || it.contains("show") || it.contains("talk") } -> ProgramType.VARIETY
					g.any { it.contains("动漫") || it.contains("anime") || it.contains("动画") } -> ProgramType.ANIME
					g.any { it.contains("纪录") || it.contains("documentary") } -> ProgramType.DOC
					else -> ProgramType.SERIES
				}
			}
			else -> null
		}
	}
}

class MenuConfigurationStore(context: Context) {
	private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

	fun load(): LorlaMenuConfig {
		val json = prefs.getString(KEY_CONFIG, null) ?: return MenuDefaults.defaultConfig()
		return try {
			Json.decodeFromString(LorlaMenuConfig.serializer(), json)
		} catch (e: Exception) {
			e.printStackTrace()
			MenuDefaults.defaultConfig()
		}
	}

	fun save(config: LorlaMenuConfig) {
		prefs.edit().putString(KEY_CONFIG, Json.encodeToString(LorlaMenuConfig.serializer(), config)).apply()
	}

	fun reset() {
		prefs.edit().remove(KEY_CONFIG).apply()
	}

	companion object {
		private const val PREF_NAME = "lorla_menu_config"
		private const val KEY_CONFIG = "config"
	}
}
