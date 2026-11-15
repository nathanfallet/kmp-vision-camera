package me.nathanfallet.visioncamera

import kotlinx.cinterop.CValue
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDepthDataOutput
import platform.AVFoundation.AVCaptureDepthDataOutputDelegateProtocol
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureOutputDataDroppedReason
import platform.AVFoundation.AVDepthData
import platform.AVFoundation.AVMetadataObject
import platform.CoreMedia.CMTime
import platform.darwin.NSObject
import kotlin.concurrent.AtomicReference

/** The callback of a [DepthFrameDelegate]: depth data, timestamp, orientation and mirroring. */
internal typealias DepthFrameCallback = (
    depthData: AVDepthData,
    timestamp: CValue<CMTime>,
    orientation: CameraOrientation,
    isMirrored: Boolean,
) -> Unit

/**
 * The [AVCaptureDepthDataOutputDelegateProtocol] of a depth data output (port of V5's `DepthFrameDelegate.swift`).
 *
 * The callbacks can be set from any thread; they are called on the output's queue.
 */
internal class DepthFrameDelegate : NSObject(), AVCaptureDepthDataOutputDelegateProtocol {
    private val onDepthFrameRef = AtomicReference<DepthFrameCallback?>(null)
    private val onDepthFrameDroppedRef = AtomicReference<((AVCaptureOutputDataDroppedReason) -> Unit)?>(null)

    /** Called with each depth frame, its timestamp, and its connection's orientation and mirroring. */
    var onDepthFrame: DepthFrameCallback?
        get() = onDepthFrameRef.value
        set(value) {
            onDepthFrameRef.value = value
        }

    /** Called with the reason of each dropped depth frame. */
    var onDepthFrameDropped: ((AVCaptureOutputDataDroppedReason) -> Unit)?
        get() = onDepthFrameDroppedRef.value
        set(value) {
            onDepthFrameDroppedRef.value = value
        }

    override fun depthDataOutput(
        output: AVCaptureDepthDataOutput,
        didDropDepthData: AVDepthData,
        timestamp: CValue<CMTime>,
        connection: AVCaptureConnection,
        reason: AVCaptureOutputDataDroppedReason,
    ) {
        onDepthFrameDropped?.invoke(reason)
    }

    override fun depthDataOutput(
        output: AVCaptureDepthDataOutput,
        didOutputDepthData: AVDepthData,
        timestamp: CValue<CMTime>,
        connection: AVCaptureConnection,
    ) {
        onDepthFrame?.invoke(didOutputDepthData, timestamp, connection.orientation, connection.videoMirrored)
    }
}

/**
 * The [AVCaptureMetadataOutputObjectsDelegateProtocol] of a metadata output (port of V5's `MetadataDelegate.swift`).
 *
 * The callback can be set from any thread; it is called on the output's queue.
 */
internal class MetadataDelegate : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    private val onMetadataObjectsRef = AtomicReference<((List<AVMetadataObject>) -> Unit)?>(null)

    /** Called with the metadata objects detected in each frame. */
    var onMetadataObjects: ((List<AVMetadataObject>) -> Unit)?
        get() = onMetadataObjectsRef.value
        set(value) {
            onMetadataObjectsRef.value = value
        }

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        onMetadataObjects?.invoke(didOutputMetadataObjects.filterIsInstance<AVMetadataObject>())
    }
}
