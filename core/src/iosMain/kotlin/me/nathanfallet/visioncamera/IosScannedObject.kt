package me.nathanfallet.visioncamera

import platform.AVFoundation.AVMetadataFaceObject
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObject

/**
 * Gives access to the native [AVMetadataObject] of a [ScannedObject] (port of V5's `Public/NativeScannedObject.swift`).
 */
internal interface NativeScannedObject<T : AVMetadataObject> {
    /** The underlying [AVMetadataObject]. */
    val metadataObject: T
}

/**
 * The iOS [ScannedObject], wrapping any [AVMetadataObject] (port of V5's `HybridScannedObject`).
 */
internal class IosScannedObject(
    override val metadataObject: AVMetadataObject,
) : ScannedObject, NativeScannedObject<AVMetadataObject> {
    override val type: ScannedObjectType get() = scannedObjectTypeOf(metadataObject.type)
    override val boundingBox: BoundingBox get() = metadataObject.bounds.toBoundingBox()

    override fun toString(): String = "ScannedObject($type, $boundingBox)"
}

/**
 * The iOS [ScannedCode], wrapping an [AVMetadataMachineReadableCodeObject] (port of V5's `HybridScannedCode`).
 */
internal class IosScannedCode(
    override val metadataObject: AVMetadataMachineReadableCodeObject,
) : ScannedCode, NativeScannedObject<AVMetadataMachineReadableCodeObject> {
    override val type: ScannedObjectType get() = scannedObjectTypeOf(metadataObject.type)
    override val boundingBox: BoundingBox get() = metadataObject.bounds.toBoundingBox()
    override val value: String? get() = metadataObject.stringValue
    override val cornerPoints: List<Point>
        get() = metadataObject.corners.mapNotNull { pointOfDictionaryRepresentation(it) }

    override fun toString(): String = "ScannedCode($type, $value)"
}

/**
 * The iOS [ScannedFace], wrapping an [AVMetadataFaceObject] (port of V5's `HybridScannedFace`). V5's `hasRollAngle`
 * and `hasYawAngle` are expressed as nullable angles.
 */
internal class IosScannedFace(
    override val metadataObject: AVMetadataFaceObject,
) : ScannedFace, NativeScannedObject<AVMetadataFaceObject> {
    override val type: ScannedObjectType get() = scannedObjectTypeOf(metadataObject.type)
    override val boundingBox: BoundingBox get() = metadataObject.bounds.toBoundingBox()
    override val faceID: Int get() = metadataObject.faceID.toInt()
    override val rollAngle: Double? get() = if (metadataObject.hasRollAngle) metadataObject.rollAngle else null
    override val yawAngle: Double? get() = if (metadataObject.hasYawAngle) metadataObject.yawAngle else null

    override fun toString(): String = "ScannedFace(#$faceID, $boundingBox)"
}

/**
 * Wraps an [AVMetadataObject] in the matching [ScannedObject]: a [ScannedCode], a [ScannedFace], or a plain
 * [ScannedObject] (V5's `HybridCameraObjectOutput.createHybridScannedObject(from:)`).
 */
internal fun createScannedObject(metadataObject: AVMetadataObject): ScannedObject = when (metadataObject) {
    // Code
    is AVMetadataMachineReadableCodeObject -> IosScannedCode(metadataObject)
    // Face
    is AVMetadataFaceObject -> IosScannedFace(metadataObject)
    // Any other AVMetadataObject
    else -> IosScannedObject(metadataObject)
}
