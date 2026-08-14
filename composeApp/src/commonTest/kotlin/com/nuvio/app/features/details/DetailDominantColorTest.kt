package com.nuvio.app.features.details

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class DetailDominantColorTest {
    @Test
    fun `missing artwork color keeps theme background`() {
        val background = Color(0.04f, 0.05f, 0.06f, 1f)

        assertEquals(background, dominantBackdropBlendColor(null, background))
    }

    @Test
    fun `dominant artwork color is blended forty two percent into theme background`() {
        val result = dominantBackdropBlendColor(
            dominantColor = Color(1f, 0.5f, 0.25f, 1f),
            backgroundColor = Color.Black,
        )

        assertEquals(0.42f, result.red, absoluteTolerance = 0.003f)
        assertEquals(0.21f, result.green, absoluteTolerance = 0.003f)
        assertEquals(0.105f, result.blue, absoluteTolerance = 0.003f)
        assertEquals(1f, result.alpha, absoluteTolerance = 0.0001f)
    }
}
