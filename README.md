# Image Sorter

An Android application for sorting images.
Allows users to browse folders, view images, organize them into groups, and perform file operations like moving to specific folders.

## Features

*   **Folder Browsing**: Select a folder to scan for images.
*   **Recursive Search**: Option to scan subdirectories recursively.
*   **Image Viewer**: Grid view of images in the selected folder.
*   **Grouping**: Create named groups to organize images.
*   **Selection & Move**: Select multiple images and move them to a group.
*   **File Operations**: Move images from a group to a physical folder on the device.
*   **Management**: Rename or delete groups.

## Building the Project

The project is built using Gradle.

To build the project:

```bash
./gradlew build
```

## Architecture

*   **MVVM**: Uses Model-View-ViewModel architecture.
*   **Jetpack Compose**: UI is built entirely with Jetpack Compose.
*   **Coroutines**: Asynchronous operations for file scanning and moving.

## GitHub Actions

A GitHub Action is configured to build the project on every push to `main` or `master` branches.
