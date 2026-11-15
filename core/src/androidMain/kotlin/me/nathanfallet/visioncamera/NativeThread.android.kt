package me.nathanfallet.visioncamera

internal actual fun createPlatformNativeThread(name: String): NativeThread =
    AndroidNativeThread(IdentifiableExecutor(name))
