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

    // Simple state to control which screen is visible, for simplicity using boolean
    var showFolderSelection by remember { mutableStateOf(true) }

    if (showFolderSelection) {
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
            viewModel = viewModel
        )
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = currentPath,
            onValueChange = onPathChange,
            label = { Text("Folder Path") },
            modifier = Modifier.fillMaxWidth()
        )
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

@Composable
fun ImageSorterScreen(
    uiState: MainUiState,
    onBackClick: () -> Unit,
    viewModel: MainViewModel
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showMoveToFolderDialog by remember { mutableStateOf<String?>(null) } // GroupId
    var showRenameGroupDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // GroupId, CurrentName

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackClick) {
                Text("Back")
            }
            if (uiState.selectedImages.isNotEmpty()) {
                Text("${uiState.selectedImages.size} selected")

                // Dropdown or list of groups to move to
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
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.images.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No images found")
                }
            } else {
                ImageGrid(
                    images = uiState.images,
                    selectedImages = uiState.selectedImages,
                    onImageClick = viewModel::toggleImageSelection
                )
            }
        }

        Divider(thickness = 4.dp, color = MaterialTheme.colorScheme.primary)

        // Lower Part: Groups (Sorter)
        Box(modifier = Modifier.weight(1f)) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Groups", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Group")
                    }
                }

                GroupList(
                    groups = uiState.groups,
                    onRenameGroup = { id, name -> showRenameGroupDialog = id to name },
                    onDeleteGroup = viewModel::deleteGroup,
                    onMoveToFolder = { id -> showMoveToFolderDialog = id }
                )
            }
        }
    }

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

    showMoveToFolderDialog?.let { groupId ->
        TextInputDialog(
            title = "Move Group to Folder (Path)",
            onDismiss = { showMoveToFolderDialog = null },
            onConfirm = { path ->
                viewModel.moveGroupImagesToFolder(groupId, path)
                showMoveToFolderDialog = null
            }
        )
    }
}

@Composable
fun ImageGrid(
    images: List<ImageFile>,
    selectedImages: Set<ImageFile>,
    onImageClick: (ImageFile) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        items(images) { image ->
            ImageItem(
                image = image,
                isSelected = selectedImages.contains(image),
                onClick = { onImageClick(image) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageItem(
    image: ImageFile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .aspectRatio(1f)
            .border(
                width = if (isSelected) 4.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .combinedClickable(onClick = onClick)
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
    onRenameGroup: (String, String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onMoveToFolder: (String) -> Unit
) {
    LazyColumn {
        items(groups) { group ->
            GroupItem(
                group = group,
                onRename = { onRenameGroup(group.id, group.name) },
                onDelete = { onDeleteGroup(group.id) },
                onMoveToFolder = { onMoveToFolder(group.id) }
            )
        }
    }
}

@Composable
fun GroupItem(
    group: ImageGroup,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: () -> Unit
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

            // Preview of images in group
            LazyRow(
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(group.images) { image ->
                    Image(
                        painter = rememberAsyncImagePainter(image.uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .aspectRatio(1f)
                    )
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
