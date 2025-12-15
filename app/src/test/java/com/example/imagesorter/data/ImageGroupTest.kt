package com.example.imagesorter.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class ImageGroupTest {

    @Test
    fun `test group creation`() {
        val group = ImageGroup(name = "Test Group")
        assertEquals("Test Group", group.name)
        assertEquals(0, group.images.size)
    }

    @Test
    fun `test adding images to group`() {
        // Mocking Uri is hard in unit tests without robolectric as it is android.net.Uri
        // But here I'm using it in a data class.
        // I can just pass null if I change the type or mock it if I use a mocking framework that supports static/final classes
        // or just rely on the fact that I don't call methods on it in this test.

        // Actually, Uri is abstract. I can create a mock.
        val mockUri = Mockito.mock(Uri::class.java)

        val image = ImageFile(
            uri = mockUri,
            name = "img.jpg",
            path = "/path/img.jpg"
        )

        var group = ImageGroup(name = "Test Group")
        group = group.copy(images = listOf(image))

        assertEquals(1, group.images.size)
        assertEquals("img.jpg", group.images[0].name)
    }
}
