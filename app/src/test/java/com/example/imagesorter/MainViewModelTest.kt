package com.example.imagesorter

import android.net.Uri
import com.example.imagesorter.data.ImageFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class MainViewModelTest {

    @Test
    fun `test initial state`() {
        val initialState = MainUiState()
        assertEquals("", initialState.currentPath)
        assertEquals(false, initialState.recursiveSearch)
        assertEquals(0, initialState.images.size)
        assertEquals(0, initialState.groups.size)
    }

    @Test
    fun `test state immutability`() {
        // Verify that copying state works as expected
        val state = MainUiState()
        val newState = state.copy(currentPath = "/test")

        assertEquals("", state.currentPath)
        assertEquals("/test", newState.currentPath)
    }
}
