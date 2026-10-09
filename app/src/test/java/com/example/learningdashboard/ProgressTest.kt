package com.example.learningdashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressTest {
    @Test
    fun progressIsCalculatedSafely() {
        assertEquals(60, calculateProgress(3, 5))
        assertEquals(0, calculateProgress(0, 0))
        assertEquals(100, calculateProgress(9, 5))
    }
}
