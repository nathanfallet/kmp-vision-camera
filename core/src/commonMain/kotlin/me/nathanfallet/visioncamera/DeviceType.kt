package me.nathanfallet.visioncamera

/**
 * Represents the type of a physical or logical Camera lens on the [CameraDevice].
 *
 * @see CameraDevice.physicalDevices
 */
enum class DeviceType {
    /** Physical: the default Camera, with a wide field of view. */
    WIDE_ANGLE,

    /** Physical: a very wide-angle lens, capturing a much larger field of view. */
    ULTRA_WIDE_ANGLE,

    /** Physical: a lens with a longer focal length, used for optical zoom. */
    TELEPHOTO,

    /** Logical: a combination of a wide-angle and a telephoto lens. */
    DUAL,

    /** Logical: a combination of a wide-angle and an ultra-wide-angle lens. */
    DUAL_WIDE,

    /** Logical: a combination of three physical Cameras (typically ultra-wide, wide, and telephoto). */
    TRIPLE,

    /** Logical: a combination of four physical Cameras. */
    QUAD,

    /** Physical: an external Continuity Camera (iPhone used as a Camera on macOS). */
    CONTINUITY,

    /** Physical: a LiDAR-based depth sensor. */
    LIDAR_DEPTH,

    /** Physical: a structured-light based depth sensor (such as the FaceID front Camera). */
    TRUE_DEPTH,

    /** Physical: a time-of-flight based depth sensor. */
    TIME_OF_FLIGHT_DEPTH,

    /** Physical: a generic external Camera (e.g. USB). */
    EXTERNAL,

    /** The Camera type is unknown or not reported. */
    UNKNOWN,
}
