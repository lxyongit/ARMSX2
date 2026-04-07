package kr.co.iefriends.pcsx2.ps2.cheats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.content.SharedPreferences
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kr.co.iefriends.pcsx2.NativeApp

import java.io.Serializable

data class CheatOption(
    val name: String,
    val code: String
) : Serializable

data class Cheat(
    val name: String,
    val code: String,
    val enabled: Boolean,
    val options: List<CheatOption> = emptyList(),
    val selectedOptionIndex: Int = -1,
    val isCustom: Boolean = false,
    val arcadeVarPrefix: String = ""
) : Serializable


class GameMenuCheatsViewModel(
    private val initialCheats: List<Cheat>,
    private val sharedPreferences: SharedPreferences,
    private val gameId: Int,
    private val systemId: String
) : ViewModel() {

    private val _cheats = MutableStateFlow<List<Cheat>>(emptyList())
    val cheats: StateFlow<List<Cheat>> = _cheats.asStateFlow()

    val isAddCheatSupported: Boolean = systemId != "fbneo"

    init {
        val customCheatsFromStorage = loadCustomCheats()
        
        // Split initial cheats
        val initialCustomCheats = initialCheats.filter { it.isCustom }
        val initialBuiltInCheats = initialCheats.filter { !it.isCustom }

        // Merge custom cheats: use storage as base for existence, but prefer initialCheats for state (enabled/options)
        val mergedCustomCheats = customCheatsFromStorage.map { storedCheat ->
            initialCustomCheats.find { it.name == storedCheat.name && it.code == storedCheat.code } ?: storedCheat
        }

        // Combine and sort
        val states = loadCheatsState()
        val allCheats = (initialBuiltInCheats + mergedCustomCheats).map { cheat ->
            if (states != null) {
                val savedState = states[cheat.name]
                if (savedState != null) {
                    cheat.copy(enabled = true, selectedOptionIndex = savedState)
                } else {
                    cheat.copy(enabled = false)
                }
            } else {
                cheat
            }
        }.sortedByDescending { it.isCustom }
        _cheats.value = allCheats

    }

    fun addCheat(name: String, code: String) {
        val lines = code.split("\n").filter { it.isNotBlank() }
        val isMultiOption = lines.size > 1 && lines.any { it.contains("=") }

        if (isMultiOption) {
            val options = mutableListOf<CheatOption>()
            // Add default "Close" option
            options.add(CheatOption("关闭", ""))
            
            lines.forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    options.add(CheatOption(parts[0].trim(), parts[1].trim()))
                } else {
                    // Fallback: use the whole line as code, name it same as code or generic
                    options.add(CheatOption(line.trim(), line.trim()))
                }
            }
            _cheats.update { currentCheats ->
                (currentCheats + Cheat(name, code, false, options, -1, true)).sortedByDescending { it.isCustom }
            }
        } else {
            _cheats.update { currentCheats ->
                (currentCheats + Cheat(name, code, false, emptyList(), -1, true)).sortedByDescending { it.isCustom }
            }
        }
        saveCheatsState()
    }

    fun deleteCheat(index: Int) {
        _cheats.update { currentCheats ->
            val cheatToDelete = currentCheats.getOrNull(index)
            if (cheatToDelete != null && cheatToDelete.isCustom) {
                val newCheats = currentCheats.toMutableList()
                newCheats.removeAt(index)
                newCheats
            } else {
                currentCheats
            }
        }
        saveCheatsState()
    }

    private fun saveCheatsState() {
        saveCustomCheats()
    }

    private fun loadCheatsState(): Map<String, Int>? {
        return null
    }

    private fun saveCustomCheats() {
        println("Loading custom cheats for gameId: $gameId")

        val customCheats = _cheats.value.filter { it.isCustom }
        if (customCheats.isEmpty()) {
            sharedPreferences.edit().remove("cheats_custom_$gameId").apply()
            return
        }
        
        try {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            objectOutputStream.writeObject(ArrayList(customCheats))
            objectOutputStream.close()
            val base64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            sharedPreferences.edit().putString("cheats_custom_$gameId", base64).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCustomCheats(): List<Cheat> {
        val base64 = sharedPreferences.getString("cheats_custom_$gameId", null) ?: return emptyList()
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val objectInputStream = ObjectInputStream(ByteArrayInputStream(bytes))
            @Suppress("UNCHECKED_CAST")
            objectInputStream.readObject() as List<Cheat>
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun toggleCheat(index: Int, enabled: Boolean) {
        _cheats.update { currentCheats ->
            currentCheats.mapIndexed { i, cheat ->
                if (i == index) cheat.copy(enabled = enabled) else cheat
            }
        }
        saveCheatsState()
    }

    fun selectOption(cheatIndex: Int, optionIndex: Int) {
        _cheats.update { currentCheats ->
            currentCheats.mapIndexed { i, cheat ->
                if (i == cheatIndex) {
                    cheat.copy(selectedOptionIndex = optionIndex, enabled = true)
                } else {
                    cheat
                }
            }
        }
        saveCheatsState()
    }

    fun updateCheatState(
        cheat: Cheat,
        setCheat: (String) -> Unit,
        resetCheat: () -> Unit
    ) {
        val code = if (cheat.options.isNotEmpty()) {
            cheat.options.getOrNull(cheat.selectedOptionIndex)?.code
        } else {
            cheat.code
        }

        if (cheat.enabled && !code.isNullOrBlank()) {
            setCheat(code)
        } else {
            resetCheat()
            _cheats.value.forEach { currentCheat ->
                if (currentCheat.enabled) {
                    val currentCode = if (currentCheat.options.isNotEmpty()) {
                        currentCheat.options.getOrNull(currentCheat.selectedOptionIndex)?.code
                    } else {
                        currentCheat.code
                    }
                    if (!currentCode.isNullOrBlank()) {
                        setCheat(currentCode)
                    }
                }
            }
        }
    }

    class Factory(
        private val initialCheats: List<Cheat>,
        private val sharedPreferences: SharedPreferences,
        private val gameId: Int,
        private val systemId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameMenuCheatsViewModel(initialCheats, sharedPreferences, gameId, systemId) as T
        }
    }
}
