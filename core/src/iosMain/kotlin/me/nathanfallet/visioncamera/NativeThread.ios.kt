package me.nathanfallet.visioncamera

import platform.darwin.dispatch_queue_create

/** Port of V5's `HybridNativeThreadFactory.swift`: a new serial queue with the default QoS. */
internal actual fun createPlatformNativeThread(name: String): NativeThread =
    IosNativeThread(queue = dispatch_queue_create(name, null), label = name)
