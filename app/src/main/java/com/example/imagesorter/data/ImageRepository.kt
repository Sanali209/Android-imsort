package com.example.imagesorter.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File

class ImageRepository(private val context: Context) {

    fun getImagesFromFolder(folderPath: String, recursive: Boolean): List<ImageFile> {
        val images = mutableListOf<ImageFile>()
        val directory = File(folderPath)

        if (directory.exists() && directory.isDirectory) {
            val walk = if (recursive) directory.walk() else directory.walk().maxDepth(1)
            walk.forEach { file ->
                if (file.isFile && isImageFile(file)) {
                    images.add(
                        ImageFile(
                            uri = Uri.fromFile(file),
                            name = file.name,
                            path = file.absolutePath
                        )
                    )
                }
            }
        }
        return images
    }

    // For now simple file extension check. Can be improved with mime type.
    private fun isImageFile(file: File): Boolean {
        val extensions = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
        return extensions.any { file.extension.equals(it, ignoreCase = true) }
    }

    fun moveImageToFolder(image: ImageFile, destinationFolder: String): Boolean {
        val sourceFile = File(image.path)
        val destinationDir = File(destinationFolder)

        if (!destinationDir.exists()) {
             destinationDir.mkdirs()
        }

        val destinationFile = File(destinationDir, image.name)

        return try {
            sourceFile.renameTo(destinationFile)
        } catch (e: Exception) {
            Log.e("ImageRepository", "Error moving file", e)
            false
        }
    }
}
