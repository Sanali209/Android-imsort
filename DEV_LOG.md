# Development Log

## Initial Setup
*   Initialized Android project structure with Gradle Kotlin DSL.
*   Configured `.gitignore` and `gradle.properties`.
*   Created `settings.gradle.kts` and root `build.gradle.kts`.
*   Created `app/build.gradle.kts` with dependencies for Jetpack Compose, ViewModel, and Coil.
*   Created `AndroidManifest.xml` with necessary permissions (READ/WRITE EXTERNAL STORAGE).

## CI/CD
*   Added `.github/workflows/android.yml` for automated builds on push.

## Data Layer
*   Implemented `ImageFile` data class to represent an image.
*   Implemented `ImageGroup` data class to represent a group of images.
*   Implemented `ImageRepository` to handle file system operations:
    *   Scanning images from a folder (recursive option).
    *   Checking file extensions.
    *   Moving files to destination folders.

## UI Layer
*   Implemented `MainActivity` as the entry point.
*   Implemented `MainViewModel` to manage state:
    *   `MainUiState` holds current path, images list, groups, selection, etc.
    *   Functions to update path, scan images, create/delete/rename groups, select images, and move images.
*   Implemented Jetpack Compose screens:
    *   `MainScreen`: Orchestrates navigation between folder selection and sorter.
    *   `FolderSelectionScreen`: Input for folder path and recursive toggle.
    *   `ImageSorterScreen`: Main UI with split view (Image Grid and Group List).
    *   `ImageGrid`: Displays images.
    *   `GroupList`: Displays groups with management buttons.
    *   `TextInputDialog`: Reusable dialog for text input.

## Logic Implementation
*   Recursive search logic is handled in `ImageRepository` using `File.walk()`.
*   Moving images logic:
    *   From Viewer to Group: Logic in `MainViewModel` (`moveSelectedImagesToGroup`). Images are logically moved to the group object.
    *   From Group to Folder: Logic in `MainViewModel` (`moveGroupImagesToFolder`) calling `repository.moveImageToFolder`.
    *   Deleting Group: Logic to return images to the main list (Viewer).

## Best Practices
*   Used MVVM architecture.
*   Used Jetpack Compose for declarative UI.
*   Used Coroutines for background tasks (file I/O).
*   Followed Material Design guidelines.

## Challenges
*   Testing in the current environment is limited due to the absence of the Android SDK.
*   Mocking `Uri` and `Context` requires `Robolectric` or instrumentation tests, which cannot be run without the SDK.
*   Added unit tests for data classes and basic structure verification.
