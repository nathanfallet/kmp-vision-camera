package me.nathanfallet.visioncamera

import platform.AVFoundation.AVCaptureMultiCamSession
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetInputPriority
import platform.AVFoundation.AVCaptureSessionPresetPhoto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AVFoundation only offers RAW photo formats with the photo preset: a session that stayed on the input priority
 * preset failed every RAW capture on an iPhone 11 ("This PhotoOutput does not support raw capture").
 */
class RawPhotoPresetTest {
    private fun inputPrioritySession() = AVCaptureSession().apply {
        sessionPreset = AVCaptureSessionPresetInputPriority
    }

    @Test
    fun rawPhotoOutputUsesThePhotoPreset() {
        val session = inputPrioritySession()
        assertTrue(session.applyRawPhotoPreset(capturesRawPhotos = true))
        assertEquals(AVCaptureSessionPresetPhoto, session.sessionPreset)
    }

    @Test
    fun sessionGoesBackToInputPriorityWithoutRawPhotoOutput() {
        val session = inputPrioritySession()
        session.applyRawPhotoPreset(capturesRawPhotos = true)
        assertFalse(session.applyRawPhotoPreset(capturesRawPhotos = false))
        assertEquals(AVCaptureSessionPresetInputPriority, session.sessionPreset)
    }

    @Test
    fun sessionWithoutRawPhotoOutputStaysOnInputPriority() {
        val session = inputPrioritySession()
        assertFalse(session.applyRawPhotoPreset(capturesRawPhotos = false))
        assertEquals(AVCaptureSessionPresetInputPriority, session.sessionPreset)
    }

    @Test
    fun multiCamSessionStaysOnInputPriority() {
        val session = AVCaptureMultiCamSession()
        assertFalse(session.applyRawPhotoPreset(capturesRawPhotos = true))
        assertEquals(AVCaptureSessionPresetInputPriority, session.sessionPreset)
    }

    @Test
    fun onlyDngPhotoOutputsCaptureRawPhotos() {
        TargetPhotoContainerFormat.entries.forEach { format ->
            val output = IosCameraPhotoOutput(PhotoOutputOptions(containerFormat = format))
            assertEquals(format == TargetPhotoContainerFormat.DNG, output.capturesRawPhotos, "$format")
        }
    }
}
