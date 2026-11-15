package me.nathanfallet.visioncamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSCoder
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView

/**
 * A UIKit view displaying the [Frame]s rendered by a [FrameRenderer] (port of V5's `HybridFrameRendererView.swift`).
 *
 * It can be used directly in UIKit (or wrapped with `UIKitView` in Compose Multiplatform). The view can be created
 * with a zero frame: the renderer's layer always follows the view's bounds.
 *
 * ```kotlin
 * val renderer = VisionCamera.createFrameRenderer()
 * val rendererView = FrameRendererView()
 * rendererView.renderer = renderer
 * frameOutput.setOnFrameCallback { frame ->
 *     frame.use { renderer.renderFrame(it) }
 * }
 * ```
 */
@OptIn(BetaInteropApi::class)
class FrameRendererView : UIView {
    /** Creates a [FrameRendererView] with the given [frame]. */
    @OverrideInit
    constructor(frame: CValue<CGRect>) : super(frame = frame)

    /** Creates a [FrameRendererView] from the given [coder] (e.g. from a storyboard). */
    @OverrideInit
    constructor(coder: NSCoder) : super(coder = coder)

    /** Creates a [FrameRendererView] with a zero frame, to be laid out later. */
    constructor() : this(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))

    /**
     * The [FrameRenderer] whose [Frame]s are displayed, created with `VisionCamera.createFrameRenderer()`, or `null`
     * to display nothing.
     */
    var renderer: FrameRenderer? = null
        set(value) {
            field = value
            updateRendererLayer()
        }

    /**
     * Resizes all sublayers to this view's bounds (port of V5's `AutoLayerResizingView.swift`).
     */
    override fun layoutSubviews() {
        super.layoutSubviews()
        layer.sublayers?.filterIsInstance<CALayer>()?.forEach { it.resizeTo(bounds) }
    }

    private fun updateRendererLayer() = runOnMainThread {
        layer.sublayers?.filterIsInstance<CALayer>()?.forEach { it.removeFromSuperlayer() }
        val renderer = renderer as? NativeFrameRenderer ?: return@runOnMainThread
        layer.addSublayer(renderer.layer)
        renderer.layer.resizeTo(bounds)
    }
}

/** Resizes this layer to the given [frame], without implicit animations so it never lags behind the view. */
private fun CALayer.resizeTo(frame: CValue<CGRect>) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    setFrame(frame)
    CATransaction.commit()
}
