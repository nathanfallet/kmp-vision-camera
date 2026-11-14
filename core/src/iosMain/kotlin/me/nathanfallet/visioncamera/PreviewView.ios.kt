package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCoder
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

/**
 * A UIKit view displaying the Camera feed of a [CameraPreviewOutput].
 *
 * It can be used directly in UIKit (or wrapped with `UIKitView` in Compose Multiplatform). The view can be created
 * with a zero frame: the preview layer always follows the view's bounds.
 *
 * ```kotlin
 * val previewOutput = VisionCamera.createPreviewOutput()
 * val previewView = PreviewView()
 * previewView.previewOutput = previewOutput
 * previewView.resizeMode = PreviewResizeMode.COVER
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

    private val previewLayer: AVCaptureVideoPreviewLayer?
        get() = (previewOutput as? NativePreviewViewOutput)?.previewLayer

    /** All preview layers currently attached to this view's layer. */
    private val attachedPreviewLayers: List<AVCaptureVideoPreviewLayer>
        get() = layer.sublayers?.filterIsInstance<AVCaptureVideoPreviewLayer>().orEmpty()

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
        } else {
            // Remove all preview layers
            attachedPreviewLayers.forEach { it.removeFromSuperlayer() }
        }
    }
}

/** Resizes this layer to the given [frame], without implicit animations so it never lags behind the view. */
private fun CALayer.resizeTo(frame: CValue<CGRect>) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    setFrame(frame)
    CATransaction.commit()
}
