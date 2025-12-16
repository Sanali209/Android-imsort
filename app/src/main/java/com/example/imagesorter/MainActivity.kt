package com.example.imagesorter

import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.imagesorter.data.ImageFile
import com.example.imagesorter.data.ImageGroup
import com.example.imagesorter.ui.theme.ImageSorterTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageSorterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionWrapper {
                        MainScreen(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionWrapper(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                // For simplified check on older androids (though checkSelfPermission is better)
                true
            }
        )
    }

    // For Android 11+ we need MANAGE_EXTERNAL_STORAGE for file manager apps
    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    // For Android < 11 we need READ/WRITE_EXTERNAL_STORAGE
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Simplified check: just proceed if user says yes or if we are on < 11
        // Real app should handle denial gracefully
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", context.packageName))
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    manageStorageLauncher.launch(intent)
                }
            }
        } else {
             requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (hasPermission) {
            content()
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please grant All Files Access permission to use this app.")
            }
        }
    } else {
        // For older versions, we assume permission granted or system handled it via dialog
        content()
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Simple state to control which screen is visible
    var showFolderSelection by remember { mutableStateOf(true) }
    var fullScreenImage by remember { mutableStateOf<ImageFile?>(null) }

    if (fullScreenImage != null) {
        FullScreenImageScreen(
            image = fullScreenImage!!,
            onBack = { fullScreenImage = null }
        )
    } else if (showFolderSelection) {
        FolderSelectionScreen(
            currentPath = uiState.currentPath,
            recursiveSearch = uiState.recursiveSearch,
            onPathChange = viewModel::updatePath,
            onRecursiveChange = viewModel::toggleRecursiveSearch,
            onSearchClick = {
                viewModel.scanImages()
                showFolderSelection = false
            }
        )
    } else {
        ImageSorterScreen(
            uiState = uiState,
            onBackClick = { showFolderSelection = true },
            viewModel = viewModel,
            onImageLongClick = { image -> fullScreenImage = image }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenImageScreen(
    image: ImageFile,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(image.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(image.uri),
                contentDescription = image.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun FolderSelectionScreen(
    currentPath: String,
    recursiveSearch: Boolean,
    onPathChange: (String) -> Unit,
    onRecursiveChange: (Boolean) -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = FileUtils.getPathFromUri(context, it)
            onPathChange(path)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = currentPath,
                onValueChange = onPathChange,
                label = { Text("Folder Path") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { launcher.launch(null) }) {
                Icon(Icons.Default.Folder, contentDescription = "Select Folder")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = recursiveSearch,
                onCheckedChange = onRecursiveChange
            )
            Text("Recursive Search")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onSearchClick) {
            Text("Start Sorting")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSorterScreen(
    uiState: MainUiState,
    onBackClick: () -> Unit,
    viewModel: MainViewModel,
    onImageLongClick: (ImageFile) -> Unit
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf<String?>(null) } // GroupId
    var showRenameGroupDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // GroupId, CurrentName

    // Folder Picker for "Move Group to Folder"
    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val path = FileUtils.getPathFromUri(context, it)
            // If showMoveToFolderDialog has a group ID, trigger the move
            showMoveToFolderDialog?.let { groupId ->
                viewModel.moveGroupImagesToFolder(groupId, path)
            }
            showMoveToFolderDialog = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Sorter") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Back") // Using Menu icon as placeholder or Back button
                    }
                },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear List") },
                            onClick = {
                                viewModel.clearImageList()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Folder") },
                            onClick = {
                                onBackClick()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.isTopPanelVisible) "Hide Top Panel" else "Show Top Panel") },
                            onClick = {
                                viewModel.toggleTopPanel()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.isBottomPanelVisible) "Hide Bottom Panel" else "Show Bottom Panel") },
                            onClick = {
                                viewModel.toggleBottomPanel()
                                showMenu = false
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Selection Action Bar
            if (uiState.selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${uiState.selectedImages.size} selected")

                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text("Move to Group")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            uiState.groups.forEach { group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        viewModel.moveSelectedImagesToGroup(group.id)
                                        expanded = false
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Create New Group") },
                                onClick = {
                                    showCreateGroupDialog = true
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

        // Upper Part: Image Viewer (Grid)
        if (uiState.isTopPanelVisible) {
            Box(modifier = Modifier.weight(1f)) {
                Column {
                     // Search Bar for Top Image List
                    OutlinedTextField(
                        value = uiState.topSearchQuery,
                        onValueChange = viewModel::updateTopSearchQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        placeholder = { Text("Search images...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                             if (uiState.topSearchQuery.isNotEmpty()) {
                                 IconButton(onClick = { viewModel.updateTopSearchQuery("") }) {
                                     Icon(Icons.Default.Close, contentDescription = "Clear")
                                 }
                             }
                        },
                        singleLine = true
                    )

                    val filteredImages = if (uiState.topSearchQuery.isBlank()) {
                        uiState.images
                    } else {
                        uiState.images.filter { it.name.contains(uiState.topSearchQuery, ignoreCase = true) }
                    }

                    if (filteredImages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (uiState.images.isEmpty()) "No images found" else "No matching images")
                        }
                    } else {
                        ImageGrid(
                            images = filteredImages,
                            selectedImages = uiState.selectedImages,
                            onImageClick = viewModel::toggleImageSelection,
                            onImageLongClick = onImageLongClick
                        )
                    }
                }
            }
        }

        if (uiState.isTopPanelVisible && uiState.isBottomPanelVisible) {
            Divider(thickness = 4.dp, color = MaterialTheme.colorScheme.primary)
        }

        // Lower Part: Groups (Sorter)
        if (uiState.isBottomPanelVisible) {
            Box(modifier = Modifier.weight(1f)) {
                Column {
                    // Search Bar for Filtering
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.bottomSearchQuery,
                            onValueChange = viewModel::updateBottomSearchQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Search by name...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            trailingIcon = {
                                 if (uiState.bottomSearchQuery.isNotEmpty()) {
                                     IconButton(onClick = { viewModel.updateBottomSearchQuery("") }) {
                                         Icon(Icons.Default.Close, contentDescription = "Clear")
                                     }
                                 }
                            },
                            singleLine = true
                        )
                        IconButton(onClick = { showCreateGroupDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Group")
                        }
                    }

                    // Filter logic
                    val filteredGroups = if (uiState.bottomSearchQuery.isBlank()) {
                        uiState.groups
                    } else {
                        uiState.groups.mapNotNull { group ->
                        val matchingImages = group.images.filter { it.name.contains(uiState.bottomSearchQuery, ignoreCase = true) }
                        if (matchingImages.isNotEmpty()) {
                            group.copy(images = matchingImages)
                        } else {
                            null
                        }
                    }
                }

                GroupList(
                    groups = filteredGroups,
                    allGroups = uiState.groups,
                    onRenameGroup = { id, name -> showRenameGroupDialog = id to name },
                    onDeleteGroup = viewModel::deleteGroup,
                    onMoveToFolder = { id ->
                        showMoveToFolderDialog = id
                        folderPickerLauncher.launch(null)
                    },
                    onMoveImageToGroup = viewModel::moveImageBetweenGroups,
                    onImageLongClick = onImageLongClick
                )
            }
        }
    }
    } // End Scaffold content

    if (showCreateGroupDialog) {
        TextInputDialog(
            title = "Create Group",
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name ->
                viewModel.addGroup(name)
                // If there are selected images, move them to the new group immediately?
                // The spec implies selecting group from menu.
                // But if user creates group here, maybe they want to populate it?
                // For now just create.
                if (uiState.selectedImages.isNotEmpty()) {
                    // Find the newly created group ID is tricky here as addGroup doesn't return ID.
                    // Ideally addGroup returns ID or we select the last added group.
                    // For simplicity, let's just create the group. The user can then select it from the menu.
                }
                showCreateGroupDialog = false
            }
        )
    }

    showRenameGroupDialog?.let { (id, name) ->
        TextInputDialog(
            title = "Rename Group",
            initialValue = name,
            onDismiss = { showRenameGroupDialog = null },
            onConfirm = { newName ->
                viewModel.renameGroup(id, newName)
                showRenameGroupDialog = null
            }
        )
    }

    // showMoveToFolderDialog is now handled by the launcher callback for the actual path,
    // but we use the state variable to store WHICH group ID we are moving.
    // If the launcher is cancelled, we should probably clear the state, but we can't easily detect cancellation
    // unless we wrap the launcher result. For MVP, if user cancels, the state remains until next click which overwrites it.
}

@Composable
fun ImageGrid(
    images: List<ImageFile>,
    selectedImages: Set<ImageFile>,
    onImageClick: (ImageFile) -> Unit,
    onImageLongClick: (ImageFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(images) { image ->
            ImageItem(
                image = image,
                isSelected = selectedImages.contains(image),
                onClick = { onImageClick(image) },
                onLongClick = { onImageLongClick(image) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageItem(
    image: ImageFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .border(
                width = if (isSelected) 4.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Image(
            painter = rememberAsyncImagePainter(image.uri),
            contentDescription = image.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
fun GroupList(
    groups: List<ImageGroup>,
    allGroups: List<ImageGroup>,
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMoveToFolder: (String) -> Unit,
    onMoveImageToGroup: (ImageFile, String, String) -> Unit,
    onImageLongClick: (ImageFile) -> Unit
) {
    LazyColumn {
        items(groups) { group ->
            GroupItem(
                group = group,
                allGroups = allGroups,
                onRename = { onRenameGroup(group.id, group.name) },
                onDelete = { onDeleteGroup(group.id) },
                onMoveToFolder = { onMoveToFolder(group.id) },
                onMoveImageToGroup = onMoveImageToGroup,
                onImageLongClick = onImageLongClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    group: ImageGroup,
    allGroups: List<ImageGroup>,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: () -> Unit,
    onMoveImageToGroup: (ImageFile, String, String) -> Unit,
    onImageLongClick: (ImageFile) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${group.name} (${group.images.size})",
                    style = MaterialTheme.typography.headlineSmall
                )
                Row {
                    IconButton(onClick = onRename) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = onMoveToFolder) {
                        Icon(Icons.Default.Folder, contentDescription = "Move to Folder")
                    }
                }
            }

            // Preview of images in group with context menu
            LazyRow(
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(group.images) { image ->
                    var showMenu by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .aspectRatio(1f)
                            .combinedClickable(
                                onClick = {
                                    // Maybe click does nothing or also open menu?
                                    // For now, let's keep click empty or select if needed.
                                },
                                onLongClick = { showMenu = true }
                            )
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(image.uri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View Full Screen") },
                                onClick = {
                                    onImageLongClick(image)
                                    showMenu = false
                                }
                            )
                            Divider()
                            allGroups.filter { it.id != group.id }.forEach { targetGroup ->
                                DropdownMenuItem(
                                    text = { Text("Move to ${targetGroup.name}") },
                                    onClick = {
                                        onMoveImageToGroup(image, group.id, targetGroup.id)
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(text) }) {
                Text("OK")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
