package com.example.imagesorter.data

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class ImageGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val images: List<ImageFile> = emptyList()
)
