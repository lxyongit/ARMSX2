package kr.co.iefriends.pcsx2.ps2.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.window.DialogProperties

import androidx.activity.compose.BackHandler
import androidx.compose.material3.RadioButton

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
fun GameMenuCheatsScreen(
    viewModel: GameMenuCheatsViewModel,
    showAddDialog: Boolean,
    onDismissAddDialog: () -> Unit,
    onBack: () -> Unit,
) {
    val cheats by viewModel.cheats.collectAsState()

    BackHandler {
        onBack()
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 8.dp, vertical = 0.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(cheats.size) { index ->
                val cheat = cheats[index]
                CheatItem(
                    cheat = cheat,
                    onToggle = { enabled -> viewModel.toggleCheat(index, enabled) },
                    onOptionSelect = { optionIndex -> viewModel.selectOption(index, optionIndex) },
                    onDelete = if (cheat.isCustom) { { viewModel.deleteCheat(index) } } else null
                )
            }
        }
    }

    if (showAddDialog) {
        AddCheatDialog(
            onDismiss = onDismissAddDialog,
            onAdd = { name, code ->
                viewModel.addCheat(name, code)
                onDismissAddDialog()
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheatItem(
    cheat: Cheat,
    onToggle: (Boolean) -> Unit,
    onOptionSelect: (Int) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (cheat.options.isNotEmpty()) {
                    Text(
                        text = cheat.name,
                        modifier = Modifier.weight(1f),
                        fontSize = 12.sp,
                        lineHeight = 14.sp
                    )
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cheat.name,
                            fontSize = 12.sp,
                            lineHeight = 14.sp
                        )
                        if (!cheat.isCustom && !cheat.code.trim().startsWith("_L")) {
                            Text(
                                text = cheat.code,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cheat.options.isEmpty()) {
                        Switch(
                            checked = cheat.enabled,
                            onCheckedChange = onToggle,
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (cheat.options.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    cheat.options.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp),
                        ) {
                            RadioButton(
                                selected = cheat.selectedOptionIndex == index,
                                onClick = { onOptionSelect(index) },
                                modifier = Modifier.size(24.dp).scale(0.8f),
                            )
                            Text(
                                text = option.name,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCheatDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier.fillMaxWidth(0.9f),
        title = { Text(text = "金手指") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("代码") },
                    placeholder = { Text("") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, code) }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
