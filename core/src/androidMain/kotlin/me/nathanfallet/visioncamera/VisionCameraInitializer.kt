package me.nathanfallet.visioncamera

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Captures the application [Context] and keeps track of the current [Activity] when the app starts, so
 * [VisionCamera] can be used without any setup (like `NitroModules.applicationContext` in VisionCamera).
 *
 * It is declared in the library's manifest and merged automatically into the app.
 */
internal class VisionCameraInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context?.applicationContext ?: return false
        AndroidContext.initialize(context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/**
 * Holds the application [Context] and the current [Activity].
 */
internal object AndroidContext {
    private var applicationContext: Context? = null
    private var currentActivityReference: WeakReference<Activity>? = null

    /** The application [Context]. */
    val context: Context
        get() = applicationContext ?: throw VisionCameraException(
            "VisionCamera has not been initialized! Make sure the VisionCameraInitializer provider is not removed " +
                "from your merged AndroidManifest.xml.",
        )

    /** The current resumed (or last resumed) [Activity], if any. */
    val currentActivity: Activity?
        get() = currentActivityReference?.get()

    fun initialize(context: Context) {
        if (applicationContext != null) return
        applicationContext = context
        (context as? Application)?.registerActivityLifecycleCallbacks(ActivityTracker)
    }

    private object ActivityTracker : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            currentActivityReference = WeakReference(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            currentActivityReference = WeakReference(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivityReference?.get() == activity) currentActivityReference = null
        }

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }
}
