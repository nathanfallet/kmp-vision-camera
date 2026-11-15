package me.nathanfallet.visioncamera

/**
 * The bounding box around a [ScannedObject], in camera-space coordinates (`0.0` to `1.0`). Convert it to view-space
 * with the preview view's `convertCameraPointToViewPoint`.
 */
data class BoundingBox(
    /** The minimum X coordinate. */
    val x: Double,
    /** The minimum Y coordinate. */
    val y: Double,
    /** The width of the bounding box. */
    val width: Double,
    /** The height of the bounding box. */
    val height: Double,
)

/** The type of a [ScannedObject]. */
enum class ScannedObjectType {
    /** A Codabar barcode. */
    CODABAR,

    /** A Code 39 barcode. */
    CODE_39,

    /** A Code 39 barcode with a mod 43 checksum. */
    CODE_39_MOD_43,

    /** A Code 93 barcode. */
    CODE_93,

    /** A Code 128 barcode. */
    CODE_128,

    /** An EAN-8 barcode. */
    EAN_8,

    /** An EAN-13 barcode. */
    EAN_13,

    /** A GS1 DataBar barcode. */
    GS1_DATA_BAR,

    /** A GS1 DataBar Expanded barcode. */
    GS1_DATA_BAR_EXPANDED,

    /** A GS1 DataBar Limited barcode. */
    GS1_DATA_BAR_LIMITED,

    /** An Interleaved 2 of 5 barcode. */
    INTERLEAVED_2_OF_5,

    /** An ITF-14 barcode. */
    ITF_14,

    /** A UPC-E barcode. */
    UPC_E,

    /** An Aztec code. */
    AZTEC,

    /** A Data Matrix code. */
    DATA_MATRIX,

    /** A MicroPDF417 code. */
    MICRO_PDF_417,

    /** A Micro QR code. */
    MICRO_QR,

    /** A PDF417 code. */
    PDF_417,

    /** A QR code. */
    QR,

    /** A human body (upper body). */
    HUMAN_BODY,

    /** A full human body. */
    HUMAN_FULL_BODY,

    /** A dog's head. */
    DOG_HEAD,

    /** A dog's body. */
    DOG_BODY,

    /** A cat's head. */
    CAT_HEAD,

    /** A cat's body. */
    CAT_BODY,

    /** A face, see [ScannedFace]. */
    FACE,

    /** A salient object, i.e. the most prominent object in the scene. */
    SALIENT_OBJECT,

    /** An unknown object type. */
    UNKNOWN,
}

/**
 * Any kind of object scanned by a [CameraObjectOutput]: a [ScannedCode], a [ScannedFace], or another object like a
 * body. iOS only.
 */
interface ScannedObject {
    /** The [ScannedObjectType] of this object. */
    val type: ScannedObjectType

    /** The bounding box of this object, in camera-space coordinates (`0.0` to `1.0`). */
    val boundingBox: BoundingBox
}

/** A machine-readable code, such as a QR code. iOS only. */
interface ScannedCode : ScannedObject {
    /** The decoded (error-corrected) value of this code, or `null` if it cannot be decoded. */
    val value: String?

    /** The corner points outlining this code, in camera-space coordinates (`0.0` to `1.0`). */
    val cornerPoints: List<Point>
}

/** A face. iOS only. */
interface ScannedFace : ScannedObject {
    /** The ID of this face, to identify multiple faces in the same scene. */
    val faceID: Int

    /** The roll angle of the face, in degrees, or `null` if it is not available (VisionCamera's `hasRollAngle`). */
    val rollAngle: Double?

    /** The yaw angle of the face, in degrees, or `null` if it is not available (VisionCamera's `hasYawAngle`). */
    val yawAngle: Double?
}
