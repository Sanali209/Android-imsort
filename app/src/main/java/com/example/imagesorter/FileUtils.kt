package com.example.imagesorter

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract

object FileUtils {
    fun getPathFromUri(context: Context, uri: Uri): String {
        // Simple heuristic for "primary" storage which is common in Android
        // This is a simplified version. A robust version would need to handle various schemes.
        // For ImageSorter with MANAGE_EXTERNAL_STORAGE, we want the absolute path.

        if (DocumentsContract.isTreeUri(uri)) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            if (parts.isNotEmpty() && parts[0] == "primary") {
                 return Environment.getExternalStorageDirectory().toString() + "/" + parts.drop(1).joinToString(":")
            }
            // Add other handlers if needed (e.g. SD cards often have a UUID)
        }
        return uri.path ?: ""
    }
}
