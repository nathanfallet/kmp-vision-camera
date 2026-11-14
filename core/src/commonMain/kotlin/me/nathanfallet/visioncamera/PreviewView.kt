package me.nathanfallet.visioncamera

/**
 * How the preview view scales the Camera feed into its bounds.
 */
enum class PreviewResizeMode {
    /** Fills the whole view, cropping the Camera feed if the aspect ratios differ. */
    COVER,

    /** Fits the whole Camera feed in the view, adding bars if the aspect ratios differ. */
    CONTAIN,
}

/**
 * The implementation used by the preview view on Android. Ignored on iOS.
 */
enum class PreviewImplementationMode {
    /** Uses a `SurfaceView`, which is more performant. */
    PERFORMANCE,

    /** Uses a `TextureView`, which supports transformations, transparency and animations. */
    COMPATIBLE,
}
