package me.nathanfallet.visioncamera.compose

import me.nathanfallet.visioncamera.BoundingBox
import me.nathanfallet.visioncamera.Point
import me.nathanfallet.visioncamera.ScannedCode
import me.nathanfallet.visioncamera.ScannedFace
import me.nathanfallet.visioncamera.ScannedObject
import me.nathanfallet.visioncamera.ScannedObjectType

/**
 * Scales all coordinates of this [ScannedObject] by [factor], e.g. from UIKit points to Compose pixels. The returned
 * object keeps the same kind: a [ScannedCode], a [ScannedFace], or a plain [ScannedObject].
 */
internal fun ScannedObject.scaled(factor: Double): ScannedObject {
    val boundingBox = boundingBox.let {
        BoundingBox(x = it.x * factor, y = it.y * factor, width = it.width * factor, height = it.height * factor)
    }
    return when (this) {
        is ScannedCode -> ScaledScannedCode(
            type = type,
            boundingBox = boundingBox,
            value = value,
            cornerPoints = cornerPoints.map { Point(x = it.x * factor, y = it.y * factor) },
        )
        is ScannedFace -> ScaledScannedFace(
            type = type,
            boundingBox = boundingBox,
            faceID = faceID,
            rollAngle = rollAngle,
            yawAngle = yawAngle,
        )
        else -> ScaledScannedObject(type = type, boundingBox = boundingBox)
    }
}

private data class ScaledScannedObject(
    override val type: ScannedObjectType,
    override val boundingBox: BoundingBox,
) : ScannedObject

private data class ScaledScannedCode(
    override val type: ScannedObjectType,
    override val boundingBox: BoundingBox,
    override val value: String?,
    override val cornerPoints: List<Point>,
) : ScannedCode

private data class ScaledScannedFace(
    override val type: ScannedObjectType,
    override val boundingBox: BoundingBox,
    override val faceID: Int,
    override val rollAngle: Double?,
    override val yawAngle: Double?,
) : ScannedFace
