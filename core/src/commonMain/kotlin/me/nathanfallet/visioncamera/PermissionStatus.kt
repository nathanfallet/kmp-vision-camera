package me.nathanfallet.visioncamera

/**
 * Represents the status of a Permission - e.g. Camera or Microphone.
 *
 * You can only request permission if the status is [NOT_DETERMINED].
 */
enum class PermissionStatus {
    /**
     * The app may request permission right now. On iOS this means the permission has never been requested yet.
     * On Android this can also mean the user denied once, but did not select "don't ask again".
     */
    NOT_DETERMINED,

    /** The app is authorized to use said permission and can proceed. */
    AUTHORIZED,

    /** The permission has been denied by the user. The user must grant it from the system Settings. */
    DENIED,

    /** The permission has been restricted, e.g. via parental controls, and can not be requested. */
    RESTRICTED,
}
