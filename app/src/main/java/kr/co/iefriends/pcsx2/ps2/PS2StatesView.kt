package kr.co.iefriends.pcsx2.ps2

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.co.iefriends.pcsx2.R
import kr.co.iefriends.pcsx2.NativeApp
import java.text.SimpleDateFormat
import java.util.*

data class SaveSlot(
    val slot: Int,
    val gamePath: String,
    val isEmpty: Boolean,
    val timestamp: String,
    val screenshot: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SaveSlot
        if (slot != other.slot) return false
        if (gamePath != other.gamePath) return false
        if (isEmpty != other.isEmpty) return false
        if (timestamp != other.timestamp) return false
        if (screenshot != null) {
            if (other.screenshot == null) return false
            if (!screenshot.contentEquals(other.screenshot)) return false
        } else if (other.screenshot != null) return false
        return true
    }
    override fun hashCode(): Int {
        var result = slot
        result = 31 * result + gamePath.hashCode()
        result = 31 * result + isEmpty.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (screenshot?.contentHashCode() ?: 0)
        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PS2StatesView(
    onDismiss: () -> Unit,
    onLoadComplete: () -> Unit = onDismiss
) {
    var saveSlots by remember { mutableStateOf<List<SaveSlot>>(emptyList()) }
    var enlargedScreenshot by remember { mutableStateOf<Pair<ByteArray, Int>?>(null) }
    var confirmOverwriteSlot by remember { mutableStateOf<Int?>(null) }

    fun loadSlots() {
        val slots = mutableListOf<SaveSlot>()
        for (i in 1..10) {
            val gamePath = NativeApp.getGamePathSlot(i)
            val screenshot = NativeApp.getImageSlot(i)
            if (screenshot != null && screenshot.isNotEmpty()) {
                val timestamp = SimpleDateFormat.getDateTimeInstance(
                    SimpleDateFormat.SHORT, SimpleDateFormat.SHORT, Locale.getDefault()
                ).format(Date())
                slots.add(SaveSlot(i, gamePath ?: "", false, timestamp, screenshot))
            } else {
                slots.add(SaveSlot(i, gamePath ?: "", true, "", null))
            }
        }
        saveSlots = slots
    }

    LaunchedEffect(Unit) {
        loadSlots()
    }

    if (enlargedScreenshot != null) {
        Dialog(onDismissRequest = { enlargedScreenshot = null }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.wrapContentSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = BitmapFactory.decodeByteArray(enlargedScreenshot!!.first, 0, enlargedScreenshot!!.first.size)
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.ps2_enlarged_screenshot),
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = stringResource(R.string.ps2_save_slot, enlargedScreenshot!!.second),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = { enlargedScreenshot = null },
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(stringResource(R.string.ps2_close))
                    }
                }
            }
        }
    }

    if (confirmOverwriteSlot != null) {
        AlertDialog(
            onDismissRequest = { confirmOverwriteSlot = null },
            title = { Text(text = stringResource(R.string.ps2_overwrite_confirm_title)) },
            text = { Text(text = stringResource(R.string.ps2_overwrite_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOverwriteSlot?.let { slotIndex ->
                        if (NativeApp.saveStateToSlot(slotIndex)) {
                            onDismiss()
                        }
                    }
                    confirmOverwriteSlot = null
                }) {
                    Text(stringResource(R.string.ps2_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOverwriteSlot = null }) {
                    Text(stringResource(R.string.ps2_cancel))
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ps2_save_states),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(saveSlots) { slot ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!slot.isEmpty && slot.screenshot != null) {
                                    val bitmap = BitmapFactory.decodeByteArray(slot.screenshot, 0, slot.screenshot.size)
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = stringResource(R.string.ps2_screenshot),
                                        modifier = Modifier
                                            .width(80.dp)
                                            .aspectRatio(4f/3f)
                                            .clickable { enlargedScreenshot = Pair(slot.screenshot, slot.slot) },
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .aspectRatio(4f/3f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(R.string.ps2_empty_slot, slot.slot), style = MaterialTheme.typography.bodySmall)
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val title = if (slot.isEmpty) {
                                        stringResource(R.string.ps2_empty_slot, slot.slot)
                                    } else {
                                        stringResource(R.string.ps2_save_slot, slot.slot)
                                    }
                                    Text(text = title, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    
                                    if (slot.timestamp.isNotEmpty()) {
                                        Text(text = slot.timestamp, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Button(onClick = {
                                            if (!slot.isEmpty) {
                                                confirmOverwriteSlot = slot.slot
                                            } else {
                                                if (NativeApp.saveStateToSlot(slot.slot)) {
                                                    onDismiss()
                                                }
                                            }
                                        }, modifier = Modifier.weight(1f).height(32.dp), contentPadding = PaddingValues(0.dp)) {
                                            Text(stringResource(R.string.ps2_save), style = MaterialTheme.typography.labelSmall)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Button(
                                            onClick = {
                                                if (NativeApp.loadStateFromSlot(slot.slot)) {
                                                    onLoadComplete()
                                                }
                                            },
                                            enabled = !slot.isEmpty,
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(stringResource(R.string.ps2_load), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ps2_cancel))
                    }
                }
            }
        }
    }
}