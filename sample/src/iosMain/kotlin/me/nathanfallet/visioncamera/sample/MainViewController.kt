package me.nathanfallet.visioncamera.sample

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The entry point of the iOS sample, used by the Xcode project in sample-ios.
 */
@Suppress("FunctionNaming")
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
