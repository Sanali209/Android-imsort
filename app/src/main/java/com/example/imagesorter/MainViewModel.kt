package com.example.imagesorter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagesorter.data.ImageFile
import com.example.imagesorter.data.ImageGroup
import com.example.imagesorter.data.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun updatePath(path: String) {
        _uiState.update { it.copy(currentPath = path) }
    }

    fun toggleRecursiveSearch(enabled: Boolean) {
        _uiState.update { it.copy(recursiveSearch = enabled) }
    }

    fun scanImages() {
        val path = uiState.value.currentPath
        val recursive = uiState.value.recursiveSearch
        if (path.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val images = repository.getImagesFromFolder(path, recursive)
            _uiState.update { it.copy(images = images, isLoading = false) }
        }
    }

    fun addGroup(name: String) {
        val newGroup = ImageGroup(name = name)
        _uiState.update { it.copy(groups = it.groups + newGroup) }
    }

    fun deleteGroup(groupId: String) {
        // When deleting a group, move images back to the viewer?
        // The spec says: "при удалении изображение перемещается назад в просмотрщик"
        // Since images in groups are just logical grouping in this UI until moved physically?
        // Wait, the spec says "lower part sorter ... group has move button ... move to folder".
        // This implies images in groups are removed from the main viewer.

        val group = uiState.value.groups.find { it.id == groupId }
        if (group != null) {
            _uiState.update { state ->
                state.copy(
                    groups = state.groups.filter { it.id != groupId },
                    images = state.images + group.images
                )
            }
        }
    }

    fun renameGroup(groupId: String, newName: String) {
        _uiState.update { state ->
            state.copy(groups = state.groups.map {
                if (it.id == groupId) it.copy(name = newName) else it
            })
        }
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
    }

    fun moveGroupImagesToFolder(groupId: String, folderPath: String) {
         val group = uiState.value.groups.find { it.id == groupId } ?: return

         viewModelScope.launch {
             val successImages = mutableListOf<ImageFile>()
             group.images.forEach { image ->
                 if (repository.moveImageToFolder(image, folderPath)) {
                     successImages.add(image)
                 }
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
         }
    }
}
