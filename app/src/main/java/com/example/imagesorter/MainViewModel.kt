package com.example.imagesorter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagesorter.data.AppState
import com.example.imagesorter.data.ImageFile
import com.example.imagesorter.data.ImageGroup
import com.example.imagesorter.data.ImageRepository
import com.example.imagesorter.data.PersistentStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class MainUiState(
    val currentPath: String = "",
    val recursiveSearch: Boolean = false,
    val images: List<ImageFile> = emptyList(),
    val groups: List<ImageGroup> = emptyList(),
    val selectedImages: Set<ImageFile> = emptySet(),
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ImageRepository(application)
    private val storage = PersistentStorage(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadState()
    }

    private fun loadState() {
        viewModelScope.launch {
            val savedState = withContext(Dispatchers.IO) { storage.loadState() }
            if (savedState != null) {
                _uiState.update {
                    it.copy(
                        currentPath = savedState.currentPath,
                        recursiveSearch = savedState.recursiveSearch,
                        groups = savedState.groups
                    )
                }
                // Optionally auto-scan if path exists
                if (savedState.currentPath.isNotBlank()) {
                    scanImages()
                }
            }
        }
    }

    private fun saveState() {
        val currentState = uiState.value
        val appState = AppState(
            currentPath = currentState.currentPath,
            recursiveSearch = currentState.recursiveSearch,
            groups = currentState.groups
        )
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveState(appState)
        }
    }

    fun updatePath(path: String) {
        _uiState.update { it.copy(currentPath = path) }
        saveState()
    }

    fun toggleRecursiveSearch(enabled: Boolean) {
        _uiState.update { it.copy(recursiveSearch = enabled) }
        saveState()
    }

    fun scanImages() {
        val path = uiState.value.currentPath
        val recursive = uiState.value.recursiveSearch
        if (path.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val imagesFromDisk = withContext(Dispatchers.IO) {
                repository.getImagesFromFolder(path, recursive)
            }

            _uiState.update { state ->
                // Filter out images that are already in groups
                val groupImagePaths = state.groups.flatMap { group -> group.images.map { it.path } }.toSet()
                val filteredImages = imagesFromDisk.filter { !groupImagePaths.contains(it.path) }

                state.copy(images = filteredImages, isLoading = false)
            }
        }
    }

    fun addGroup(name: String) {
        val newGroup = ImageGroup(name = name)
        _uiState.update { it.copy(groups = it.groups + newGroup) }
        saveState()
    }

    fun deleteGroup(groupId: String) {
        // When deleting a group, move images back to the viewer?
        val group = uiState.value.groups.find { it.id == groupId }
        if (group != null) {
            _uiState.update { state ->
                state.copy(
                    groups = state.groups.filter { it.id != groupId },
                    images = state.images + group.images
                )
            }
            saveState()
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        _uiState.update { state ->
            state.copy(groups = state.groups.map {
                if (it.id == groupId) it.copy(name = newName) else it
            })
        }
        saveState()
    }

    fun toggleImageSelection(image: ImageFile) {
        _uiState.update { state ->
            val newSelection = state.selectedImages.toMutableSet()
            if (newSelection.contains(image)) {
                newSelection.remove(image)
            } else {
                newSelection.add(image)
            }
            state.copy(selectedImages = newSelection)
        }
    }

    fun moveSelectedImagesToGroup(groupId: String) {
        val selected = uiState.value.selectedImages
        if (selected.isEmpty()) return

        _uiState.update { state ->
            val updatedGroups = state.groups.map { group ->
                if (group.id == groupId) {
                    group.copy(images = group.images + selected)
                } else {
                    group
                }
            }
            val remainingImages = state.images.filter { !selected.contains(it) }

            state.copy(
                groups = updatedGroups,
                images = remainingImages,
                selectedImages = emptySet()
            )
        }
        saveState()
    }

    fun moveImageBetweenGroups(image: ImageFile, fromGroupId: String, toGroupId: String) {
        _uiState.update { state ->
            // Remove from source group
            val updatedGroups = state.groups.map { group ->
                when (group.id) {
                    fromGroupId -> group.copy(images = group.images.filter { it != image })
                    toGroupId -> group.copy(images = group.images + image)
                    else -> group
                }
            }
            state.copy(groups = updatedGroups)
        }
        saveState()
    }

    fun clearImageList() {
        _uiState.update { it.copy(images = emptyList(), selectedImages = emptySet()) }
    }

    fun moveGroupImagesToFolder(groupId: String, folderPath: String) {
         val group = uiState.value.groups.find { it.id == groupId } ?: return

         viewModelScope.launch {
             val successImages = withContext(Dispatchers.IO) {
                 val succeeded = mutableListOf<ImageFile>()
                 group.images.forEach { image ->
                     if (repository.moveImageToFolder(image, folderPath)) {
                         succeeded.add(image)
                     }
                 }
                 succeeded
             }

             // Update group by removing successfully moved images
             _uiState.update { state ->
                 val updatedGroups = state.groups.map { g ->
                     if (g.id == groupId) {
                         g.copy(images = g.images.filter { !successImages.contains(it) })
                     } else {
                         g
                     }
                 }
                 state.copy(groups = updatedGroups)
             }
             saveState()
         }
    }
}
