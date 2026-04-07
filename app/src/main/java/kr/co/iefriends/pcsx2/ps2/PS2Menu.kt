package kr.co.iefriends.pcsx2.ps2

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.co.iefriends.pcsx2.NativeApp
import androidx.compose.ui.res.stringResource
import kr.co.iefriends.pcsx2.R
import kotlin.math.roundToInt

val ASPECT_RATIOS = listOf("拉伸", "自动 4:3/3:2", "4:3", "16:9", "10:7")
val RENDERERS = listOf("自动", "Vulkan", "OpenGL", "软件")
val RESOLUTIONS = listOf("1× 原生", "2× (720p)", "3× (1080p)", "4× (1440p)", "5× (1800p)", "6× (2160p)", "7× (2520p)", "8× (2880p)")
val MIPMAP_MODES = listOf("自动", "基础", "完整")
val HALF_PIXEL_OFFSETS = listOf("关闭", "普通", "特殊", "特殊（激进）")
val TEXTURE_PRELOADINGS = listOf("禁用", "部分", "完整")
val LIMITER_MODES = listOf("正常", "慢动作", "加速", "无限制")
val EE_CYCLE_SKIPS = listOf("0（关闭）", "1", "2", "3")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PS2Menu(
    gameTitle: String = "",
    gameSerial: String = "",
    gameCrc: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var config by remember { mutableStateOf(PS2Config.DEFAULT) }
    var showStatesView by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        NativeApp.pause()
        onDispose {
            NativeApp.resume()
        }
    }

    LaunchedEffect(Unit) {
        val serial = gameSerial.ifEmpty {
            try { NativeApp.getGameSerial() } catch (e: Exception) { "" }
        } ?: ""

        val globalConfig = PS2Config.loadGlobal(context)
        config = if (serial.isNotEmpty()) {
            PS2Config.loadPerGame(context, serial, globalConfig)
        } else {
            globalConfig
        }

        try {
            config.applyToEmulator()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    if (showStatesView) {
        PS2StatesView(
            onDismiss = { showStatesView = false },
            onLoadComplete = {
                showStatesView = false
                onDismiss()
            }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            modifier = Modifier.padding(16.dp).fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.ps2_per_game_settings), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showStatesView = true }) {
                        Text(stringResource(R.string.ps2_save_states))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val blendingAccuracies = listOf(
                    stringResource(R.string.ps2_blend_min),
                    stringResource(R.string.ps2_blend_basic),
                    stringResource(R.string.ps2_blend_medium),
                    stringResource(R.string.ps2_blend_high),
                    stringResource(R.string.ps2_blend_full),
                    stringResource(R.string.ps2_blend_max)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_aspect_ratio), ASPECT_RATIOS, config.aspectRatio.id) {
                            config = config.copy(aspectRatio = AspectRatio.fromId(it))
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_blending_accuracy), blendingAccuracies, config.blendingAccuracy.id) {
                            config = config.copy(blendingAccuracy = AccBlendLevel.fromId(it))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_renderer), RENDERERS, config.renderer.menuIndex) {
                            config = config.copy(renderer = GSRenderer.fromMenuIndex(it))
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_resolution_multiplier), RESOLUTIONS, config.upscaleMultiplier - 1) {
                            config = config.copy(upscaleMultiplier = it + 1)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_mipmap_mode), MIPMAP_MODES, config.mipmapMode.id) {
                            config = config.copy(mipmapMode = MipmapMode.fromId(it))
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_half_pixel_offset), HALF_PIXEL_OFFSETS, config.halfPixelOffset.id) {
                            config = config.copy(halfPixelOffset = HalfPixelOffset.fromId(it))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_texture_preloading), TEXTURE_PRELOADINGS, config.texturePreloading.id) {
                            config = config.copy(texturePreloading = TexturePreloading.fromId(it))
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_limiter_mode), LIMITER_MODES, config.limiterMode.id) {
                            config = config.copy(limiterMode = LimiterMode.fromId(it))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_widescreen_patches), config.enableWideScreenPatches) {
                            config = config.copy(enableWideScreenPatches = it)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_no_interlacing_patches), config.enableNoInterlacingPatches) {
                            config = config.copy(enableNoInterlacingPatches = it)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_enable_patch_codes), config.enablePatches) {
                            config = config.copy(enablePatches = it)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_enable_cheats), config.enableCheats) {
                            config = config.copy(enableCheats = it)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_load_textures), config.loadTextures) {
                            config = config.copy(loadTextures = it)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_async_textures), config.asyncTextureLoading) {
                            config = config.copy(asyncTextureLoading = it)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_precache_textures), config.precacheTextureReplacements) {
                            config = config.copy(precacheTextureReplacements = it)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SwitchField(stringResource(R.string.ps2_shade_boost), config.shadeBoost) {
                            config = config.copy(shadeBoost = it)
                        }
                    }
                }
                if (config.shadeBoost) {
                    SliderField(stringResource(R.string.ps2_shade_boost_brightness), config.shadeBoostBrightness, 0..200) {
                        config = config.copy(shadeBoostBrightness = it)
                    }
                    SliderField(stringResource(R.string.ps2_shade_boost_contrast), config.shadeBoostContrast, 0..200) {
                        config = config.copy(shadeBoostContrast = it)
                    }
                    SliderField(stringResource(R.string.ps2_shade_boost_saturation), config.shadeBoostSaturation, 0..200) {
                        config = config.copy(shadeBoostSaturation = it)
                    }
                }
                SliderField(
                    label = stringResource(R.string.ps2_ee_cycle_rate),
                    value = config.eeCycleRate,
                    valueRange = -3..3,
                    steps = 5
                ) {
                    config = config.copy(eeCycleRate = it)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DropdownMenuField(stringResource(R.string.ps2_ee_cycle_skip), EE_CYCLE_SKIPS, config.eeCycleSkip) {
                            config = config.copy(eeCycleSkip = it)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) { /* placeholder */ }
                }
                }

                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ps2_cancel)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val serial = gameSerial.ifEmpty {
                            try { NativeApp.getGameSerial() } catch (e: Exception) { "" }
                        } ?: ""

                        val sanitized = config.sanitized()
                        PS2Config.saveGlobal(context, sanitized)
                        if (serial.isNotEmpty()) {
                            PS2Config.savePerGame(context, serial, sanitized)
                        }
                        try {
                            sanitized.applyToEmulator()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.ps2_save))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownMenuField(label: String, options: List<String>, selectedIndex: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        OutlinedTextField(
            value = options.getOrNull(selectedIndex) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
            textStyle = MaterialTheme.typography.bodySmall,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth().height(52.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SwitchField(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.scale(0.75f))
    }
}

@Composable
fun SliderField(
    label: String,
    value: Int,
    valueRange: IntRange,
    steps: Int = 0,
    onValueChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toString(), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
