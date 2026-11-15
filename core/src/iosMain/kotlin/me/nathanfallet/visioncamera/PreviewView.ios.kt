@file:OptIn(ExperimentalNativeApi::class)

package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCoder
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.dispatch_get_main_queue
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * A UIKit view displaying the Camera feed of a [CameraPreviewOutput] (port of V5's `HybridPreviewView.swift`).
 *
 * It can be used directly in UIKit (or wrapped with `UIKitView` in Compose Multiplatform). The view can be created
 * with a zero frame: the preview layer always follows the view's bounds.
 *
 * ```kotlin
 * val previewOutput = VisionCamera.createPreviewOutput()
 * val previewView = PreviewView()
 * previewView.previewOutput = previewOutput
 * previewView.resizeMode = PreviewResizeMode.COVER
 * previewView.gestureControllers = listOf(
 *     VisionCamera.createZoomGestureController().apply { controller = cameraController },
 * )
 * ```
 */
@OptIn(BetaInteropApi::class)
class PreviewView : UIView {
    /** Creates a [PreviewView] with the given [frame]. */
    @OverrideInit
    constructor(frame: CValue<CGRect>) : super(frame = frame)

    /** Creates a [PreviewView] from the given [coder] (e.g. from a storyboard). */
    @OverrideInit
    constructor(coder: NSCoder) : super(coder = coder)

    /** Creates a [PreviewView] with a zero frame, to be laid out later. */
    constructor() : this(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))

    /**
     * The [CameraPreviewOutput] to display, or `null` to display nothing. The same output can only be displayed by
     * one view at a time.
     */
    var previewOutput: CameraPreviewOutput? = null
        set(value) {
            field = value
            updatePreviewLayer()
        }

    /**
     * How the Camera feed is scaled into this view's bounds. Defaults to [PreviewResizeMode.COVER].
     */
    var resizeMode: PreviewResizeMode = PreviewResizeMode.COVER
        set(value) {
            if (field == value) return
            field = value
            val previewLayer = previewLayer ?: return
            runOnMainThread {
                previewLayer.videoGravity = value.toAVLayerVideoGravity()
            }
        }

    /**
     * The [GestureController]s attached to this view, e.g. a [ZoomGestureController] to install a native
     * pinch-to-zoom gesture, or a [TapToFocusGestureController] to focus where the user taps.
     */
    var gestureControllers: List<GestureController> = emptyList()
        set(value) {
            val oldValue = field
            field = value
            runOnMainThread {
                oldValue.filterIsInstance<NativeGestureController>().forEach { oldController ->
                    removeGestureRecognizer(oldController.gestureRecognizer)
                    oldController.onDetached(this)
                }
                value.filterIsInstance<NativeGestureController>().forEach { newController ->
                    addGestureRecognizer(newController.gestureRecognizer)
                    newController.onAttached(this)
                }
            }
        }

    /** Called on the main thread when the preview started. */
    var onPreviewStarted: (() -> Unit)? = null

    /** Called on the main thread when the preview stopped. */
    var onPreviewStopped: (() -> Unit)? = null

    /** Incremented to stop the current `previewing` observation, see [observePreviewing]. */
    private var previewingObservation = 0

    private val previewLayer: AVCaptureVideoPreviewLayer?
        get() = (previewOutput as? NativePreviewViewOutput)?.previewLayer

    /** All preview layers currently attached to this view's layer. */
    private val attachedPreviewLayers: List<AVCaptureVideoPreviewLayer>
        get() = layer.sublayers?.filterIsInstance<AVCaptureVideoPreviewLayer>().orEmpty()

    /**
     * Creates a [MeteringPoint] to focus AE/AF/AWB on with [CameraController.focusTo].
     *
     * The coordinates are relative to this view (e.g. the location of a tap), and take orientation, scaling and
     * cropping into account.
     *
     * @param viewX The X coordinate within this view's coordinate system.
     * @param viewY The Y coordinate within this view's coordinate system.
     * @param size The size of the point within this view's coordinate system, or `null` for the default size.
     * @throws VisionCameraException If this view has no [previewOutput] yet.
     */
    fun createMeteringPoint(viewX: Double, viewY: Double, size: Double? = null): MeteringPoint {
        val previewLayer = previewLayer
            ?: throw VisionCameraException("Cannot create MeteringPoint - PreviewView is not ready yet!")
        val normalizedPoint = previewLayer.captureDevicePointOfInterestForPoint(CGPointMake(viewX, viewY))
        return normalizedPoint.useContents {
            IosMeteringPoint.relative(
                relativeX = viewX,
                relativeY = viewY,
                relativeSize = size,
                normalizedX = x,
                normalizedY = y,
            )
        }
    }

    /**
     * Converts the given [cameraPoint] in camera sensor coordinates into a [Point] in view coordinates, relative to
     * this view.
     *
     * @throws VisionCameraException If this view has no [previewOutput] yet.
     */
    fun convertCameraPointToViewPoint(cameraPoint: Point): Point {
        val previewLayer = previewLayer
            ?: throw VisionCameraException("Cannot convert camera point to view point - PreviewView isn't ready yet!")
        return previewLayer.pointForCaptureDevicePointOfInterest(cameraPoint.toCGPoint()).toPoint()
    }

    /**
     * Converts the given [viewPoint] in view coordinates relative to this view into a [Point] in camera sensor
     * coordinates.
     *
     * @throws VisionCameraException If this view has no [previewOutput] yet.
     */
    fun convertViewPointToCameraPoint(viewPoint: Point): Point {
        val previewLayer = previewLayer
            ?: throw VisionCameraException("Cannot convert view point to camera point - PreviewView isn't ready yet!")
        return previewLayer.captureDevicePointOfInterestForPoint(viewPoint.toCGPoint()).toPoint()
    }

    /**
     * Returns a new [ScannedObject] where all coordinates of the given [scannedObject] (its bounding box, and the
     * corner points of a [ScannedCode]) are converted into view coordinates, relative to this view. Uses
     * `AVCaptureVideoPreviewLayer.transformedMetadataObject(for:)`, so orientation, scaling and cropping are taken
     * into account.
     *
     * @throws VisionCameraException If this view has no [previewOutput] yet, if the [scannedObject] was not created
     * by VisionCamera, or if it cannot be transformed.
     */
    @Suppress("ThrowsCount")
    fun convertScannedObjectCoordinatesToViewCoordinates(scannedObject: ScannedObject): ScannedObject {
        val previewLayer = previewLayer ?: throw VisionCameraException(
            "Cannot convert Scanned Object to view coordinates - PreviewView isn't ready yet!",
        )
        val nativeObject = scannedObject as? NativeScannedObject<*>
            ?: throw VisionCameraException("The given `scannedObject` is not of type `NativeScannedObject`!")
        val transformedObject = previewLayer.transformedMetadataObjectForMetadataObject(nativeObject.metadataObject)
            ?: throw VisionCameraException(
                "Failed to transform the Scanned Object's coordinates into view coordinates!",
            )
        return createScannedObject(transformedObject)
    }

    /**
     * Resizes the preview layer to this view's bounds (port of V5's `AutoLayerResizingView.swift`).
     */
    override fun layoutSubviews() {
        super.layoutSubviews()
        attachedPreviewLayers.forEach { it.resizeTo(bounds) }
    }

    private fun updatePreviewLayer() = runOnMainThread {
        val previewLayer = previewLayer
        if (previewLayer != null) {
            // Remove all preview layers that are not our AVCaptureVideoPreviewLayer
            attachedPreviewLayers.filter { it != previewLayer }.forEach { it.removeFromSuperlayer() }
            if (previewLayer.superlayer != layer) {
                // If we don't have the preview layer, we add it here
                layer.addSublayer(previewLayer)
            }
            previewLayer.resizeTo(bounds)
            // Update resizeMode
            previewLayer.videoGravity = resizeMode.toAVLayerVideoGravity()
            // Add listener to `previewing`
            observePreviewing(previewLayer)
        } else {
            // Remove all preview layers
            attachedPreviewLayers.forEach { it.removeFromSuperlayer() }
            // Remove listener to `previewing`
            onPreviewStopped?.invoke()
            previewingObservation++
        }
    }

    /**
     * Calls [onPreviewStarted] and [onPreviewStopped] whenever `previewLayer.previewing` changes. V5 observes it with
     * KVO, which Kotlin/Native cannot do: we poll it on the main thread instead, until [previewingObservation]
     * changes or this view is deallocated.
     */
    private fun observePreviewing(previewLayer: AVCaptureVideoPreviewLayer) {
        val observation = ++previewingObservation
        pollPreviewing(WeakReference(this), observation, previewLayer, wasPreviewing = previewLayer.previewing)
    }

    /** Polls `previewing` once. The scheduled block only holds a weak reference to this view. */
    private fun pollPreviewing(
        view: WeakReference<PreviewView>,
        observation: Int,
        previewLayer: AVCaptureVideoPreviewLayer,
        wasPreviewing: Boolean,
    ) {
        dispatch_get_main_queue().asyncAfter(PREVIEWING_POLL_INTERVAL) {
            val previewView = view.get()
            if (previewView == null || previewView.previewingObservation != observation) return@asyncAfter
            val isPreviewing = previewLayer.previewing
            if (isPreviewing != wasPreviewing) {
                if (isPreviewing) {
                    Logger.info("PreviewView started!")
                    previewView.onPreviewStarted?.invoke()
                } else {
                    Logger.info("PreviewView stopped!")
                    previewView.onPreviewStopped?.invoke()
                }
            }
            previewView.pollPreviewing(view, observation, previewLayer, isPreviewing)
        }
    }
}

/** How often [PreviewView] polls `AVCaptureVideoPreviewLayer.previewing`. */
private val PREVIEWING_POLL_INTERVAL = 100.milliseconds

/** Resizes this layer to the given [frame], without implicit animations so it never lags behind the view. */
private fun CALayer.resizeTo(frame: CValue<CGRect>) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    setFrame(frame)
    CATransaction.commit()
}

/** Converts this [Point] to a [CGPoint]. */
private fun Point.toCGPoint(): CValue<CGPoint> = CGPointMake(x, y)

/** Converts this [CGPoint] to a [Point]. */
private fun CValue<CGPoint>.toPoint(): Point = useContents { Point(x = x, y = y) }
