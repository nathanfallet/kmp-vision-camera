package me.nathanfallet.visioncamera

import cnames.structs.__CFDictionary
import kotlinx.cinterop.CValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVMetadataObjectTypeAztecCode
import platform.AVFoundation.AVMetadataObjectTypeCatBody
import platform.AVFoundation.AVMetadataObjectTypeCatHead
import platform.AVFoundation.AVMetadataObjectTypeCodabarCode
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Mod43Code
import platform.AVFoundation.AVMetadataObjectTypeCode93Code
import platform.AVFoundation.AVMetadataObjectTypeDataMatrixCode
import platform.AVFoundation.AVMetadataObjectTypeDogBody
import platform.AVFoundation.AVMetadataObjectTypeDogHead
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeFace
import platform.AVFoundation.AVMetadataObjectTypeGS1DataBarCode
import platform.AVFoundation.AVMetadataObjectTypeGS1DataBarExpandedCode
import platform.AVFoundation.AVMetadataObjectTypeGS1DataBarLimitedCode
import platform.AVFoundation.AVMetadataObjectTypeHumanBody
import platform.AVFoundation.AVMetadataObjectTypeHumanFullBody
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeInterleaved2of5Code
import platform.AVFoundation.AVMetadataObjectTypeMicroPDF417Code
import platform.AVFoundation.AVMetadataObjectTypeMicroQRCode
import platform.AVFoundation.AVMetadataObjectTypePDF417Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeSalientObject
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGPointMakeWithDictionaryRepresentation
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetHeight
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGRectGetWidth
import platform.Foundation.CFBridgingRetain

// Port of V5's AV+ScannedObjectType.swift, CG+BoundingBox.swift and CG+Point.swift.

/**
 * All [ScannedObjectType]s available on this iOS version, mapped to their `AVMetadataObjectType`. Like V5, newer types
 * are only available on the iOS versions introducing them.
 */
private val avMetadataObjectTypes: Map<ScannedObjectType, String?> by lazy {
    buildMap {
        put(ScannedObjectType.CODE_39, AVMetadataObjectTypeCode39Code)
        put(ScannedObjectType.CODE_39_MOD_43, AVMetadataObjectTypeCode39Mod43Code)
        put(ScannedObjectType.CODE_93, AVMetadataObjectTypeCode93Code)
        put(ScannedObjectType.CODE_128, AVMetadataObjectTypeCode128Code)
        put(ScannedObjectType.EAN_8, AVMetadataObjectTypeEAN8Code)
        put(ScannedObjectType.EAN_13, AVMetadataObjectTypeEAN13Code)
        put(ScannedObjectType.INTERLEAVED_2_OF_5, AVMetadataObjectTypeInterleaved2of5Code)
        put(ScannedObjectType.ITF_14, AVMetadataObjectTypeITF14Code)
        put(ScannedObjectType.UPC_E, AVMetadataObjectTypeUPCECode)
        put(ScannedObjectType.AZTEC, AVMetadataObjectTypeAztecCode)
        put(ScannedObjectType.DATA_MATRIX, AVMetadataObjectTypeDataMatrixCode)
        put(ScannedObjectType.PDF_417, AVMetadataObjectTypePDF417Code)
        put(ScannedObjectType.QR, AVMetadataObjectTypeQRCode)
        put(ScannedObjectType.HUMAN_BODY, AVMetadataObjectTypeHumanBody)
        put(ScannedObjectType.DOG_BODY, AVMetadataObjectTypeDogBody)
        put(ScannedObjectType.CAT_BODY, AVMetadataObjectTypeCatBody)
        put(ScannedObjectType.FACE, AVMetadataObjectTypeFace)
        put(ScannedObjectType.SALIENT_OBJECT, AVMetadataObjectTypeSalientObject)
        if (isIOSAtLeast(major = 15, minor = 4)) {
            put(ScannedObjectType.CODABAR, AVMetadataObjectTypeCodabarCode)
            put(ScannedObjectType.GS1_DATA_BAR, AVMetadataObjectTypeGS1DataBarCode)
            put(ScannedObjectType.GS1_DATA_BAR_EXPANDED, AVMetadataObjectTypeGS1DataBarExpandedCode)
            put(ScannedObjectType.GS1_DATA_BAR_LIMITED, AVMetadataObjectTypeGS1DataBarLimitedCode)
            put(ScannedObjectType.MICRO_PDF_417, AVMetadataObjectTypeMicroPDF417Code)
            put(ScannedObjectType.MICRO_QR, AVMetadataObjectTypeMicroQRCode)
        }
        if (isIOSAtLeast(major = 17)) {
            put(ScannedObjectType.HUMAN_FULL_BODY, AVMetadataObjectTypeHumanFullBody)
        }
        if (isIOSAtLeast(major = 26)) {
            put(ScannedObjectType.DOG_HEAD, AVMetadataObjectTypeDogHead)
            put(ScannedObjectType.CAT_HEAD, AVMetadataObjectTypeCatHead)
        }
    }
}

/** Converts an `AVMetadataObjectType` to a [ScannedObjectType]. */
internal fun scannedObjectTypeOf(type: String?): ScannedObjectType =
    avMetadataObjectTypes.entries.firstOrNull { it.value == type }?.key ?: run {
        Logger.error("Unknown AVMetadataObject.ObjectType: $type")
        ScannedObjectType.UNKNOWN
    }

/**
 * Converts this [ScannedObjectType] to an `AVMetadataObjectType`, or `null` if it is not available on this iOS
 * version.
 */
internal fun ScannedObjectType.toAVMetadataObjectType(): String? = avMetadataObjectTypes[this]

/** Converts a [CGRect] to a [BoundingBox]. */
internal fun CValue<CGRect>.toBoundingBox(): BoundingBox = BoundingBox(
    x = CGRectGetMinX(this),
    y = CGRectGetMinY(this),
    width = CGRectGetWidth(this),
    height = CGRectGetHeight(this),
)

/**
 * Converts a `CGPoint` dictionary representation (e.g. an element of `AVMetadataMachineReadableCodeObject.corners`)
 * to a [Point], or `null` if it is not a valid point dictionary.
 */
internal fun pointOfDictionaryRepresentation(dictionary: Any?): Point? {
    val cfDictionary = dictionary?.let { CFBridgingRetain(it) } ?: return null
    try {
        return memScoped {
            val point = alloc<CGPoint>()
            val isValid = CGPointMakeWithDictionaryRepresentation(cfDictionary.reinterpret<__CFDictionary>(), point.ptr)
            if (isValid) Point(x = point.x, y = point.y) else null
        }
    } finally {
        CFRelease(cfDictionary)
    }
}
