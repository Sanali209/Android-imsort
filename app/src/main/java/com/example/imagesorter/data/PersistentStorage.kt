package com.example.imagesorter.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class AppState(
    val currentPath: String = "",
    val recursiveSearch: Boolean = false,
    val groups: List<ImageGroup> = emptyList(),
    // We optionally save images, but maybe better to just rescan to ensure sync with file system.
    // However, if we want to persist which images are "excluded" (moved to groups) vs "available",
    // we need to persist the list or filter at startup.
    // The previous implementation filtered: images = scan() - images_in_groups.
    // So storing groups + current path is enough if we trigger a re-scan on load.
    // BUT, if user had "No images found" or specific state, maybe nice to restore.
    // Let's rely on re-scan logic for now to avoid stale file paths.
)

class PersistentStorage(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val fileName = "app_state.json"

    fun saveState(state: AppState) {
        try {
            val file = File(context.filesDir, fileName)
            val jsonString = json.encodeToString(state)
            file.writeText(jsonString)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun loadState(): AppState? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return try {
            val jsonString = file.readText()
            json.decodeFromString<AppState>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
