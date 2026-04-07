package kr.co.iefriends.pcsx2.ps2

import android.content.Context
import kr.co.iefriends.pcsx2.NativeApp
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.regex.Pattern

// ==================== 枚举定义 ====================

enum class GSRenderer(val id: Int, val iniName: String) {
    Auto(-1, "Auto"),
    Vulkan(14, "Vulkan"),
    OpenGL(12, "OpenGL"),
    Software(13, "Software");

    val menuIndex: Int get() = when (this) { Auto -> 0; Vulkan -> 1; OpenGL -> 2; Software -> 3 }

    companion object {
        fun fromId(id: Int): GSRenderer = entries.firstOrNull { it.id == id } ?: Auto
        fun fromName(name: String): GSRenderer = entries.firstOrNull { it.iniName.equals(name, ignoreCase = true) } ?: Auto
        fun fromMenuIndex(index: Int): GSRenderer = when (index) { 1 -> Vulkan; 2 -> OpenGL; 3 -> Software; else -> Auto }
    }
}

enum class AspectRatio(val id: Int) {
    Stretch(0), Auto(1), R4_3(2), R16_9(3), R10_7(4);

    companion object {
        fun fromId(id: Int): AspectRatio = entries.firstOrNull { it.id == id } ?: Auto
    }
}

enum class AccBlendLevel(val id: Int) {
    Minimum(0), Basic(1), Medium(2), High(3), Full(4), Maximum(5);

    companion object {
        fun fromId(id: Int): AccBlendLevel = entries.firstOrNull { it.id == id } ?: Basic
    }
}

/**
 * 硬件 Mipmap 模式。
 * 0 = 自动，1 = 基础（程序生成），2 = 完整（从纹理读取）
 */
enum class MipmapMode(val id: Int) {
    Automatic(0), Basic(1), Full(2);

    companion object {
        fun fromId(id: Int): MipmapMode = entries.firstOrNull { it.id == id } ?: Automatic
    }
}

/**
 * 半像素偏移（子像素混合修复）。
 * 0 = 关闭，1 = 普通（顶点），2 = 特殊（纹理），3 = 特殊（纹理+激进）
 */
enum class HalfPixelOffset(val id: Int) {
    Off(0), Normal(1), Special(2), SpecialAggressive(3);

    companion object {
        fun fromId(id: Int): HalfPixelOffset = entries.firstOrNull { it.id == id } ?: Off
    }
}

/**
 * GPU 端调色板/CLUT 纹理预加载级别。
 * 0 = 禁用，1 = 部分，2 = 完整
 */
enum class TexturePreloading(val id: Int) {
    Disabled(0), Partial(1), Full(2);

    companion object {
        fun fromId(id: Int): TexturePreloading = entries.firstOrNull { it.id == id } ?: Full
    }
}

/**
 * 帧限制器 / 速度模式。
 * 0 = 正常（1×），1 = 慢动作，2 = 加速，3 = 无限制
 */
enum class LimiterMode(val id: Int) {
    Nominal(0), Slomo(1), Turbo(2), Unlimited(3);

    companion object {
        fun fromId(id: Int): LimiterMode = entries.firstOrNull { it.id == id } ?: Nominal
    }
}

// ==================== 配置数据类 ====================

/**
 * PCSX2 模拟器完整配置。
 *
 * 对应 PCSX2 INI 文件中的以下 section：
 *   [EmuCore/GS]         – 图形 / 渲染器选项
 *   [EmuCore]            – 核心模拟选项（补丁、金手指）
 *   [EmuCore/Speedhacks] – CPU 加速选项
 *
 * 纹理替换相关标志（loadTextures、asyncTextureLoading、precacheTextureReplacements）
 * 为全局选项，仅保存到 SharedPreferences，不写入单游戏 INI 文件。
 */
data class PS2Config(

    // ---------- [EmuCore/GS] ----------

    /** 渲染后端。 */
    val renderer: GSRenderer = GSRenderer.Auto,

    /** 内部分辨率倍数（1–8，1 = PS2 原生分辨率）。 */
    val upscaleMultiplier: Int = 1,

    /** 显示宽高比。 */
    val aspectRatio: AspectRatio = AspectRatio.Auto,

    /** Alpha 混合精度等级，越高越准确但越慢。 */
    val blendingAccuracy: AccBlendLevel = AccBlendLevel.Basic,

    /** 硬件 Mipmap 生成模式。 */
    val mipmapMode: MipmapMode = MipmapMode.Automatic,

    /** 半像素偏移，用于减少硬件渲染器纹理接缝。 */
    val halfPixelOffset: HalfPixelOffset = HalfPixelOffset.Off,

    /** 调色板/CLUT 纹理向 GPU 预加载的激进程度。 */
    val texturePreloading: TexturePreloading = TexturePreloading.Full,

    /** 启用色调增强后处理（亮度/对比度/饱和度）。 */
    val shadeBoost: Boolean = false,

    /** 色调增强亮度调整（0–200，默认 50）。 */
    val shadeBoostBrightness: Int = 50,

    /** 色调增强对比度调整（0–200，默认 50）。 */
    val shadeBoostContrast: Int = 50,

    /** 色调增强饱和度调整（0–200，默认 50）。 */
    val shadeBoostSaturation: Int = 50,

    // ---------- [EmuCore] ----------

    /** 为兼容游戏应用社区宽屏（16:9）补丁代码。 */
    val enableWideScreenPatches: Boolean = true,

    /** 为兼容游戏应用社区去交错补丁代码。 */
    val enableNoInterlacingPatches: Boolean = true,

    /** 应用 PCSX2 补丁数据库中的兼容性补丁代码。 */
    val enablePatches: Boolean = true,

    /** 启用用户自定义金手指（pnach 文件）。 */
    val enableCheats: Boolean = true,

    // ---------- [EmuCore/Speedhacks] ----------

    /**
     * EE（情感引擎）CPU 频率偏置。
     * 范围：-3（降频）到 3（超频），默认 0 = 精确。
     */
    val eeCycleRate: Int = 0,

    /**
     * EE 周期跳过，以准确性换取速度。
     * 范围：0（关闭）到 3。
     */
    val eeCycleSkip: Int = 0,

    /** 帧限制器 / 速度模式。 */
    val limiterMode: LimiterMode = LimiterMode.Nominal,

    /** 正常运行速度（倍数，如 0.5、1.0、2.0 等）。 */
    val nominalScalar: Float = 1.0f,

    // ---------- 全局选项（不写入单游戏 INI） ----------

    /** 从纹理文件夹加载自定义纹理替换包。 */
    val loadTextures: Boolean = false,

    /** 异步流式加载纹理替换，避免卡顿。 */
    val asyncTextureLoading: Boolean = true,

    /** 启动时将所有纹理替换预缓存到内存。 */
    val precacheTextureReplacements: Boolean = false

) {

    // ==================== INI 序列化 ====================

    /**
     * 序列化为 PCSX2 单游戏 INI 格式。
     * 纹理替换标志为全局选项，不包含在此输出中。
     */
    fun toIniString(): String = buildString {
        appendLine("[EmuCore/GS]")
        appendLine("Renderer=${renderer.iniName}")
        appendLine("upscale_multiplier=$upscaleMultiplier")
        appendLine("AspectRatio=${aspectRatio.id}")
        appendLine("accurate_blending_unit=${blendingAccuracy.id}")
        appendLine("MipMap=${mipmapMode.id}")
        appendLine("UserHacks_HalfPixelOffset=${halfPixelOffset.id}")
        appendLine("TexturePreloading=${texturePreloading.id}")
        appendLine("ShadeBoost=$shadeBoost")
        appendLine("ShadeBoostBrightness=$shadeBoostBrightness")
        appendLine("ShadeBoostContrast=$shadeBoostContrast")
        appendLine("ShadeBoostSaturation=$shadeBoostSaturation")
        appendLine()
        appendLine("[EmuCore]")
        appendLine("EnableWideScreenPatches=$enableWideScreenPatches")
        appendLine("EnableNoInterlacingPatches=$enableNoInterlacingPatches")
        appendLine("EnablePatches=$enablePatches")
        appendLine("EnableCheats=$enableCheats")
        appendLine()
        appendLine("[EmuCore/Speedhacks]")
        appendLine("EECycleRate=$eeCycleRate")
        appendLine("EECycleSkip=$eeCycleSkip")
        appendLine()
        appendLine("[Framerate]")
        appendLine("NominalScalar=$nominalScalar")
    }

    // ==================== 应用到模拟器 ====================

    /**
     * 通过 [NativeApp] 将全部配置推送到正在运行的 PCSX2 实例。
     * 在 UI 线程调用（NativeApp 内部会排队处理变更）。
     */
    fun applyToEmulator() {
        NativeApp.setAspectRatio(aspectRatio.id)
        NativeApp.renderMipmap(mipmapMode.id)
        NativeApp.renderHalfpixeloffset(halfPixelOffset.id)
        NativeApp.renderPreloading(texturePreloading.id)
        NativeApp.setSetting("EmuCore/GS", "ShadeBoost", "bool", shadeBoost.toString())
        NativeApp.setSetting("EmuCore/GS", "ShadeBoostBrightness", "int", shadeBoostBrightness.toString())
        NativeApp.setSetting("EmuCore/GS", "ShadeBoostContrast", "int", shadeBoostContrast.toString())
        NativeApp.setSetting("EmuCore/GS", "ShadeBoostSaturation", "int", shadeBoostSaturation.toString())
        NativeApp.setSetting("Framerate", "NominalScalar", "float", nominalScalar.toString())
        NativeApp.speedhackEecyclerate(eeCycleRate)
        NativeApp.speedhackEecycleskip(eeCycleSkip)
        NativeApp.speedhackLimitermode(limiterMode.id)
        NativeApp.setSetting("EmuCore/GS", "LoadTextureReplacements", "bool", loadTextures.toString())
        NativeApp.setSetting("EmuCore/GS", "LoadTextureReplacementsAsync", "bool", asyncTextureLoading.toString())
        NativeApp.setSetting("EmuCore/GS", "PrecacheTextureReplacements", "bool", precacheTextureReplacements.toString())
        NativeApp.renderGpu(renderer.id)
        NativeApp.renderUpscalemultiplier(upscaleMultiplier.toFloat())
        NativeApp.setSetting("EmuCore/GS", "accurate_blending_unit", "int", blendingAccuracy.id.toString())
        NativeApp.setSetting("EmuCore", "EnableWideScreenPatches", "bool", enableWideScreenPatches.toString())
        NativeApp.setSetting("EmuCore", "EnableNoInterlacingPatches", "bool", enableNoInterlacingPatches.toString())
        NativeApp.setSetting("EmuCore", "EnablePatches", "bool", enablePatches.toString())
        NativeApp.setEnableCheats(enableCheats)
    }

    // ==================== 工具方法 ====================

    /** 将所有字段裁剪到合法范围后返回新副本。 */
    fun sanitized(): PS2Config = copy(
        upscaleMultiplier    = upscaleMultiplier.coerceIn(1, 8),
        shadeBoostBrightness = shadeBoostBrightness.coerceIn(0, 200),
        shadeBoostContrast   = shadeBoostContrast.coerceIn(0, 200),
        shadeBoostSaturation = shadeBoostSaturation.coerceIn(0, 200),
        nominalScalar        = nominalScalar.coerceIn(0.05f, 10.0f),
        eeCycleRate          = eeCycleRate.coerceIn(-3, 3),
        eeCycleSkip          = eeCycleSkip.coerceIn(0, 3)
    )

    // ==================== 伴生对象（工厂 / 持久化） ====================

    companion object {

        val DEFAULT = PS2Config()

        // ---- SharedPreferences 键名（与现有 PS2Menu 保持向后兼容） ----

        private const val PREFS_NAME = "app_prefs"

        /**
         * 从 [SharedPreferences] 加载全局（非单游戏）设置。
         * 键名与现有 PS2Menu 实现保持兼容。
         */
        fun loadGlobal(context: Context): PS2Config {
            val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return PS2Config(
                renderer                   = GSRenderer.fromId(sp.getInt("renderer", -1)),
                upscaleMultiplier          = sp.getFloat("upscale_multiplier", 1f).toInt().coerceIn(1, 8),
                aspectRatio                = AspectRatio.fromId(sp.getInt("aspect_ratio", 1)),
                blendingAccuracy           = AccBlendLevel.fromId(sp.getInt("blending_accuracy", 1)),
                mipmapMode                 = MipmapMode.fromId(sp.getInt("mipmap_mode", 0)),
                halfPixelOffset            = HalfPixelOffset.fromId(sp.getInt("half_pixel_offset", 0)),
                texturePreloading          = TexturePreloading.fromId(sp.getInt("texture_preloading", 2)),
                shadeBoost                 = sp.getBoolean("shade_boost", false),
                shadeBoostBrightness       = sp.getInt("shade_boost_brightness", 50),
                shadeBoostContrast         = sp.getInt("shade_boost_contrast", 50),
                shadeBoostSaturation       = sp.getInt("shade_boost_saturation", 50),
                enableWideScreenPatches    = sp.getBoolean("widescreen_patches", true),
                enableNoInterlacingPatches = sp.getBoolean("no_interlacing_patches", true),
                enablePatches              = sp.getBoolean("enable_patches", true),
                enableCheats               = sp.getBoolean("enable_cheats", true),
                eeCycleRate                = sp.getInt("ee_cycle_rate", 0),
                eeCycleSkip                = sp.getInt("ee_cycle_skip", 0),
                limiterMode                = LimiterMode.fromId(sp.getInt("limiter_mode", 0)),
                nominalScalar              = sp.getFloat("nominal_scalar", 1.0f),
                loadTextures               = sp.getBoolean("load_textures", false),
                asyncTextureLoading        = sp.getBoolean("async_texture_loading", true),
                precacheTextureReplacements = sp.getBoolean("precache_texture_replacements", false)
            )
        }

        /** 将 [config] 作为全局默认设置持久化保存。 */
        fun saveGlobal(context: Context, config: PS2Config) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().run {
                putInt("renderer", config.renderer.id)
                putFloat("upscale_multiplier", config.upscaleMultiplier.toFloat())
                putInt("aspect_ratio", config.aspectRatio.id)
                putInt("blending_accuracy", config.blendingAccuracy.id)
                putInt("mipmap_mode", config.mipmapMode.id)
                putInt("half_pixel_offset", config.halfPixelOffset.id)
                putInt("texture_preloading", config.texturePreloading.id)
                putBoolean("shade_boost", config.shadeBoost)
                putInt("shade_boost_brightness", config.shadeBoostBrightness)
                putInt("shade_boost_contrast", config.shadeBoostContrast)
                putInt("shade_boost_saturation", config.shadeBoostSaturation)
                putBoolean("widescreen_patches", config.enableWideScreenPatches)
                putBoolean("no_interlacing_patches", config.enableNoInterlacingPatches)
                putBoolean("enable_patches", config.enablePatches)
                putBoolean("enable_cheats", config.enableCheats)
                putInt("ee_cycle_rate", config.eeCycleRate)
                putInt("ee_cycle_skip", config.eeCycleSkip)
                putInt("limiter_mode", config.limiterMode.id)
                putFloat("nominal_scalar", config.nominalScalar)
                putBoolean("load_textures", config.loadTextures)
                putBoolean("async_texture_loading", config.asyncTextureLoading)
                putBoolean("precache_texture_replacements", config.precacheTextureReplacements)
                apply()
            }
        }

        // ---- 单游戏 INI 文件 ----

        /**
         * 从外部存储的 gamesettings 目录加载 [serial] 对应的单游戏覆盖配置。
         * 文件中未包含的键将回退到 [globalFallback]（或 [DEFAULT]）。
         */
        fun loadPerGame(context: Context, serial: String, globalFallback: PS2Config = DEFAULT): PS2Config {
            val dataRoot = context.getExternalFilesDir(null)?.absolutePath ?: return globalFallback
            val ini = File(File(dataRoot, "gamesettings"), "$serial.ini")
            return loadFromIni(ini, globalFallback)
        }

        /** 将 [serial] 对应的单游戏覆盖配置持久化到外部存储的 gamesettings 目录。 */
        fun savePerGame(context: Context, serial: String, config: PS2Config) {
            val dataRoot = context.getExternalFilesDir(null)?.absolutePath ?: return
            val ini = File(File(dataRoot, "gamesettings"), "$serial.ini")
            saveToIni(ini, config)
        }

        /** 删除 [serial] 对应的单游戏 INI 文件并通知 Native 层。 */
        fun deletePerGame(context: Context, serial: String) {
            val dataRoot = context.getExternalFilesDir(null)?.absolutePath ?: return
            File(File(dataRoot, "gamesettings"), "$serial.ini").delete()
        }

        // ---- 底层 INI 文件辅助方法 ----

        /** 从任意 [file] 读取 [PS2Config]，缺失的键从 [fallback] 合并。 */
        fun loadFromIni(file: File, fallback: PS2Config = DEFAULT): PS2Config {
            if (!file.exists()) return fallback
            return try {
                val text = FileInputStream(file).bufferedReader().use { it.readText() }
                parseIni(text, fallback)
            } catch (e: Exception) {
                fallback
            }
        }

        /** 将 [config] 以 PCSX2 INI 格式写入 [file]，不存在时自动创建父目录。 */
        fun saveToIni(file: File, config: PS2Config) {
            file.parentFile?.mkdirs()
            FileOutputStream(file, false).use { out ->
                out.write(config.toIniString().toByteArray(Charsets.UTF_8))
            }
        }

        // ---- INI 解析器 ----

        private fun parseIni(content: String, base: PS2Config): PS2Config {
            fun str(key: String): String? =
                Pattern.compile("(?m)^${Pattern.quote(key)}=\\s*(.+)$")
                    .matcher(content).run { if (find()) group(1)?.trim() else null }

            fun bool(key: String, default: Boolean): Boolean {
                val s = str(key)?.lowercase()
                return when (s) {
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> default
                }
            }

            fun int(key: String, default: Int): Int =
                str(key)?.toIntOrNull() ?: default

            fun float(key: String, default: Float): Float =
                str(key)?.toFloatOrNull() ?: default

            // 渲染器可存储为名称（"Vulkan"）或旧版整数（"14"）。
            val renderer: GSRenderer = str("Renderer")?.let { raw ->
                raw.toIntOrNull()?.let { GSRenderer.fromId(it) } ?: GSRenderer.fromName(raw)
            } ?: base.renderer

            return base.copy(
                renderer                   = renderer,
                upscaleMultiplier          = int("upscale_multiplier", base.upscaleMultiplier).coerceIn(1, 8),
                aspectRatio                = AspectRatio.fromId(int("AspectRatio", base.aspectRatio.id)),
                blendingAccuracy           = AccBlendLevel.fromId(int("accurate_blending_unit", base.blendingAccuracy.id)),
                mipmapMode                 = MipmapMode.fromId(int("MipMap", base.mipmapMode.id)),
                halfPixelOffset            = HalfPixelOffset.fromId(int("UserHacks_HalfPixelOffset", base.halfPixelOffset.id)),
                texturePreloading          = TexturePreloading.fromId(int("TexturePreloading", base.texturePreloading.id)),
                shadeBoost                 = bool("ShadeBoost", base.shadeBoost),
                shadeBoostBrightness       = int("ShadeBoostBrightness", base.shadeBoostBrightness),
                shadeBoostContrast         = int("ShadeBoostContrast", base.shadeBoostContrast),
                shadeBoostSaturation       = int("ShadeBoostSaturation", base.shadeBoostSaturation),
                enableWideScreenPatches    = bool("EnableWideScreenPatches", base.enableWideScreenPatches),
                enableNoInterlacingPatches = bool("EnableNoInterlacingPatches", base.enableNoInterlacingPatches),
                enablePatches              = bool("EnablePatches", base.enablePatches),
                enableCheats               = bool("EnableCheats", base.enableCheats),
                eeCycleRate                = int("EECycleRate", base.eeCycleRate).coerceIn(-3, 3),
                eeCycleSkip                = int("EECycleSkip", base.eeCycleSkip).coerceIn(0, 3),
                nominalScalar              = float("NominalScalar", base.nominalScalar).coerceIn(0.05f, 10.0f)
                // limiterMode / 纹理替换标志：不在单游戏 INI 中，保留基础值
            )
        }
    }
}
