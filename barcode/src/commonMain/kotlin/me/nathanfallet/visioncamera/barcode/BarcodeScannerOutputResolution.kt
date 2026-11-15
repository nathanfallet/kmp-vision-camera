package me.nathanfallet.visioncamera.barcode

/**
 * Controls the camera buffer resolution used for barcode scanning.
 */
enum class BarcodeScannerOutputResolution {
    /** Prefer preview-sized buffers for lower latency. */
    PREVIEW,

    /** Prefer full/highest available buffers for better detail. */
    FULL,
}
