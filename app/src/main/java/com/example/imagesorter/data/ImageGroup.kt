package com.example.imagesorter.data

import java.util.UUID

data class ImageGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val images: List<ImageFile> = emptyList()
)
