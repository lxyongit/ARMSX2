package kr.co.iefriends.pcsx2.ps2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PS2GamepadMappingDialog(
    uiState: PS2GamepadUiState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onRestoreSuggested: () -> Unit,
    onBeginCapture: (PS2GamepadAction) -> Unit,
    onClearBinding: (PS2GamepadAction) -> Unit,
    onDialogKeyEvent: (android.view.KeyEvent) -> Boolean,
) {
    val editingDevice = uiState.editingDevice ?: return
    var showTips by remember(editingDevice.deviceKey) { mutableStateOf(false) }
    val dialogFocusRequester = remember(editingDevice.deviceKey) { FocusRequester() }

    BackHandler(enabled = true) { }

    LaunchedEffect(editingDevice.deviceKey) {
        dialogFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .focusRequester(dialogFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent -> onDialogKeyEvent(keyEvent.nativeKeyEvent) }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("手柄映射", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(editingDevice.name, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { showTips = !showTips },
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Text(if (showTips) "收起说明" else "说明")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val hint = if (uiState.mappingDialogAutoPrompt) {
                    "检测到新手柄，请先保存推荐布局或逐项绑定。"
                } else {
                    "点击卡片开始绑定，每行显示 3 个按键配置。"
                }
                Text(hint, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))

                if (showTips) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("摇杆与扳机", style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(PS2GamepadManager.AXIS_SUMMARY, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("菜单键也支持映射，按下后会直接呼出游戏菜单。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.capturingAction != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "正在等待按键: ${uiState.capturingAction.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PS2GamepadAction.remappableActions.chunked(3).forEach { actionRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            actionRow.forEach { action ->
                                val bindingLabel = uiState.editingBindings[action]?.let(PS2GamepadManager::keyCodeLabel) ?: "未绑定"
                                val isCapturing = uiState.capturingAction == action
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    tonalElevation = 1.dp,
                                    color = if (isCapturing) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties { canFocus = false }
                                            .clickable { onBeginCapture(action) }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${action.title} / ${action.englishTitle}",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = if (isCapturing) "等待输入" else bindingLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isCapturing) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            OutlinedButton(
                                                onClick = { onBeginCapture(action) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .sizeIn(minHeight = 32.dp)
                                                    .focusProperties { canFocus = false },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("绑定", style = MaterialTheme.typography.labelSmall)
                                            }
                                            TextButton(
                                                onClick = { onClearBinding(action) },
                                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .sizeIn(minHeight = 32.dp)
                                                    .focusProperties { canFocus = false }
                                            ) {
                                                Text("清除", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                            repeat(3 - actionRow.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onRestoreSuggested,
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Text("恢复推荐")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Text("关闭")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}