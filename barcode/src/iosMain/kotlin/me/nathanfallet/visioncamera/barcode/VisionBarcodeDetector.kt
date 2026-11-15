package me.nathanfallet.visioncamera.barcode

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import me.nathanfallet.visioncamera.VisionCameraException
import me.nathanfallet.visioncamera.barcode.extensions.VisionCoordinateSpace
import me.nathanfallet.visioncamera.barcode.extensions.barcodeFormatOfVisionSymbology
import me.nathanfallet.visioncamera.barcode.extensions.isIOSAtLeast
import me.nathanfallet.visioncamera.barcode.extensions.resolveVisionFormat
import me.nathanfallet.visioncamera.barcode.extensions.toBarcodeFormats
import me.nathanfallet.visioncamera.barcode.extensions.toByteArray
import me.nathanfallet.visioncamera.barcode.extensions.toCGImagePropertyOrientation
import me.nathanfallet.visioncamera.barcode.extensions.toVisionSymbologies
import me.nathanfallet.visioncamera.barcode.parser.BarcodeValueParser
import platform.CoreGraphics.CGImageRelease
import platform.CoreImage.CIAztecCodeDescriptor
import platform.CoreImage.CIContext
import platform.CoreImage.CIDataMatrixCodeDescriptor
import platform.CoreImage.CIPDF417CodeDescriptor
import platform.CoreImage.CIQRCodeDescriptor
import platform.CoreImage.createCGImage
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.UIKit.UIImage
import platform.UIKit.UIImageOrientation
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler

/**
 * Detects barcodes with Apple Vision (`VNDetectBarcodesRequest`), and reports them like ML Kit does in V5.
 *
 * This replaces V5's ML Kit `BarcodeScanner` on iOS. It is shared by the [BarcodeScanner] and the Barcode Scanner
 * output, and is thread-safe: each detection uses its own Vision request.
 */
@OptIn(BetaInteropApi::class)
internal class VisionBarcodeDetector(targetFormats: List<TargetBarcodeFormat>) {
    /** The formats to report (ML Kit's `BarcodeScannerOptions.formats`). */
    private val requestedFormats: Set<BarcodeFormat> = targetFormats.flatMap { it.toBarcodeFormats() }.toSet()

    /** The Vision symbologies detecting the [requestedFormats]. */
    private val symbologies = requestedFormats.flatMap { it.toVisionSymbologies() }.distinct()

    /**
     * Synchronously detects barcodes in the given [pixelBuffer], whose content has the given [orientation]. The
     * coordinates are relative to the [pixelBuffer] (the `Frame`'s coordinate system), like ML Kit's.
     *
     * @throws VisionCameraException If Vision failed to detect barcodes.
     */
    fun detect(pixelBuffer: CVPixelBufferRef, orientation: UIImageOrientation): List<Barcode> = autoreleasepool {
        val space = VisionCoordinateSpace(
            orientation = orientation,
            width = CVPixelBufferGetWidth(pixelBuffer).toDouble(),
            height = CVPixelBufferGetHeight(pixelBuffer).toDouble(),
        )
        val handler = VNImageRequestHandler(
            cVPixelBuffer = pixelBuffer,
            orientation = orientation.toCGImagePropertyOrientation(),
            options = emptyMap<Any?, Any?>(),
        )
        perform(handler, space)
    }

    /**
     * Synchronously detects barcodes in the given [image], oriented by its `imageOrientation`. The coordinates are
     * relative to the upright image, in pixels (the `Image`'s coordinate system), like ML Kit's.
     *
     * @throws VisionCameraException If the image has no pixels, or if Vision failed to detect barcodes.
     */
    fun detect(image: UIImage): List<Barcode> = autoreleasepool {
        val (width, height) = image.size.useContents { (width * image.scale) to (height * image.scale) }
        val space = VisionCoordinateSpace(UIImageOrientation.UIImageOrientationUp, width, height)
        // A UIImage is backed by a CGImage, or by a CIImage that we render.
        val renderedImage = if (image.CGImage == null) {
            image.CIImage?.let { ciImage ->
                CIContext.contextWithOptions(null).createCGImage(ciImage, fromRect = ciImage.extent)
            }
        } else {
            null
        }
        try {
            val cgImage = image.CGImage ?: renderedImage ?: throw VisionCameraException("Image has no pixel data!")
            val handler = VNImageRequestHandler(
                cGImage = cgImage,
                orientation = image.imageOrientation.toCGImagePropertyOrientation(),
                options = emptyMap<Any?, Any?>(),
            )
            perform(handler, space)
        } finally {
            CGImageRelease(renderedImage)
        }
    }

    private fun perform(handler: VNImageRequestHandler, space: VisionCoordinateSpace): List<Barcode> {
        if (symbologies.isEmpty()) return emptyList()
        val request = VNDetectBarcodesRequest()
        request.symbologies = symbologies
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!handler.performRequests(listOf(request), error = error.ptr)) {
                val description = error.value?.localizedDescription ?: "Unknown error"
                throw VisionCameraException("Failed to scan barcodes: $description")
            }
        }
        return request.results.orEmpty()
            .filterIsInstance<VNBarcodeObservation>()
            .mapNotNull { it.toBarcode(space) }
    }

    /** Converts this observation to a [Barcode], or returns `null` if its format was not requested. */
    private fun VNBarcodeObservation.toBarcode(space: VisionCoordinateSpace): Barcode? {
        val detectedFormat = symbology?.let { barcodeFormatOfVisionSymbology(it) } ?: BarcodeFormat.UNKNOWN
        val payload = payloadStringValue
        return resolveVisionFormat(detectedFormat, payload, requestedFormats)?.let { resolved ->
            IosBarcode(
                format = resolved.format,
                boundingBox = space.convert(boundingBox),
                // ML Kit's order: top-left, top-right, bottom-right, bottom-left
                cornerPoints = listOf(topLeft, topRight, bottomRight, bottomLeft).map { space.convert(it) },
                rawBytes = rawBytes(payload, resolved.rawValue),
                rawValue = resolved.rawValue,
                parsedValue = BarcodeValueParser.parse(resolved.rawValue, resolved.format),
            )
        }
    }

    /**
     * The raw bytes of this observation: its payload (iOS 17+), else the error-corrected payload of its 2D code
     * descriptor. If the [payload] was rewritten to [rawValue] (a UPC-A), the bytes of the payload are rewritten too.
     */
    private fun VNBarcodeObservation.rawBytes(payload: String?, rawValue: String?): ByteArray? {
        val payloadData = if (isIOSAtLeast(major = 17)) payloadData else null
        val bytes = (payloadData ?: errorCorrectedPayload())?.toByteArray()
        val isRewrittenPayload = rawValue != payload && bytes?.decodeToString() == payload
        return if (isRewrittenPayload) rawValue?.encodeToByteArray() else bytes
    }

    /** The error-corrected payload of this observation's 2D code descriptor (QR, Aztec, PDF417, Data Matrix). */
    private fun VNBarcodeObservation.errorCorrectedPayload(): NSData? {
        // Vision's binding types it as a forward declaration of CIBarcodeDescriptor
        val descriptor: Any? = barcodeDescriptor
        return when (descriptor) {
            is CIQRCodeDescriptor -> descriptor.errorCorrectedPayload
            is CIAztecCodeDescriptor -> descriptor.errorCorrectedPayload
            is CIPDF417CodeDescriptor -> descriptor.errorCorrectedPayload
            is CIDataMatrixCodeDescriptor -> descriptor.errorCorrectedPayload
            else -> null
        }
    }
}
