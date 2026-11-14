package me.nathanfallet.visioncamera

/**
 * Represents the container format of a captured Photo.
 */
enum class PhotoContainerFormat {
    /** JPEG. */
    JPEG,

    /** HEIC. */
    HEIC,

    /** DNG (RAW). */
    DNG,

    /** TIFF. */
    TIFF,

    /** DICOM. */
    DCM,

    /** Unknown format. */
    UNKNOWN,
}

/**
 * Represents a [PhotoContainerFormat] that can be used to capture Photos in.
 */
enum class TargetPhotoContainerFormat {
    /** JPEG. */
    JPEG,

    /** HEIC. */
    HEIC,

    /** DNG (RAW). */
    DNG,

    /** Whatever format the platform captures natively. */
    NATIVE,
}
