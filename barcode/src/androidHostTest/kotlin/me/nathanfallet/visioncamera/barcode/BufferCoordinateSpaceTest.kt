package me.nathanfallet.visioncamera.barcode

import me.nathanfallet.visioncamera.Point
import kotlin.test.Test
import kotlin.test.assertEquals

class BufferCoordinateSpaceTest {
    private val width = 4
    private val height = 2

    /**
     * Rotates a [width]x[height] grid of pixel ids clockwise by [rotationDegrees], like ML Kit does with an
     * `InputImage`'s rotation, and returns the upright grid (rows of pixel ids).
     */
    private fun rotatedGrid(rotationDegrees: Int): List<List<Int>> {
        var grid = List(height) { y -> List(width) { x -> y * width + x } }
        repeat(rotationDegrees / 90) {
            // Clockwise: the last row becomes the first column.
            grid = List(grid[0].size) { row -> List(grid.size) { column -> grid[grid.size - 1 - column][row] } }
        }
        return grid
    }

    @Test
    fun convertsEveryPixelBackToTheBuffer() {
        for (rotationDegrees in listOf(0, 90, 180, 270)) {
            val space = BufferCoordinateSpace(width, height, rotationDegrees)
            rotatedGrid(rotationDegrees).forEachIndexed { uprightY, row ->
                row.forEachIndexed { uprightX, id ->
                    // The center of the pixel in the upright image, and in the buffer.
                    val converted = space.convert(uprightX + 0.5, uprightY + 0.5)
                    val expected = Point(x = id % width + 0.5, y = id / width + 0.5)
                    assertEquals(expected, converted, "Pixel $id with a rotation of $rotationDegrees°")
                }
            }
        }
    }

    @Test
    fun convertsTheUprightImageBoundsToTheBufferBounds() {
        for (rotationDegrees in listOf(0, 90, 180, 270)) {
            val isSwapped = rotationDegrees % 180 != 0
            val uprightWidth = if (isSwapped) height else width
            val uprightHeight = if (isSwapped) width else height
            val space = BufferCoordinateSpace(width, height, rotationDegrees)
            val rect = space.convert(0.0, 0.0, uprightWidth.toDouble(), uprightHeight.toDouble())
            assertEquals(Rect(0.0, width.toDouble(), 0.0, height.toDouble()), rect, "Rotation of $rotationDegrees°")
        }
    }

    @Test
    fun convertsARotatedRectangle() {
        // The top-left 1x1 pixel of a 90° upright image is the bottom-left pixel of the buffer.
        val space = BufferCoordinateSpace(width, height, 90)
        assertEquals(Rect(left = 0.0, right = 1.0, top = 1.0, bottom = 2.0), space.convert(0.0, 0.0, 1.0, 1.0))
    }

    @Test
    fun keepsImageCoordinates() {
        val space = BufferCoordinateSpace(width, height, 0)
        assertEquals(Point(1.5, 0.25), space.convert(1.5, 0.25))
    }

    @Test
    fun normalizesTheRotation() {
        assertEquals(
            BufferCoordinateSpace(width, height, 270).convert(0.5, 1.5),
            BufferCoordinateSpace(width, height, -90).convert(0.5, 1.5),
        )
    }
}
