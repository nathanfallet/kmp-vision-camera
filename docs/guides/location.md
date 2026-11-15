---
title: Location
parent: Guides
nav_order: 13
---

# Location

Captured photos (see [Photo](photo.md)) and videos (see [Video](video.md)) can contain a location in their EXIF tags
or video metadata. The `location` module gives you the device's `Location` and ports VisionCamera's
`react-native-vision-camera-location`.

```kotlin
dependencies {
    implementation("me.nathanfallet.visioncamera:location:0.1.0")
    // For the Compose helpers (rememberLocation, rememberLocationManager):
    implementation("me.nathanfallet.visioncamera:location-compose:0.1.0")
}
```

## Platform setup

On Android, declare the location permissions in your `AndroidManifest.xml`. The library does not add them for you:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

Locations come from the fused location provider of Google Play Services. The module initializes itself: there is no
setup code to write.

On iOS, add `NSLocationWhenInUseUsageDescription` to your `Info.plist`, with the reason why your app needs the
location. The iOS `LocationManager` is backed by a `CLLocationManager`, which delivers its events to the thread that
created it. So create the manager on the main thread (`rememberLocationManager` and `rememberLocation` already do).

## Get a Location

### In Compose

Use `rememberLocation`. It creates a `LocationManager`, starts updating once the permission is granted, and stops when
it leaves the composition:

```kotlin
val location = rememberLocation(accuracy = LocationAccuracy.BALANCED, distanceFilter = 10.0)

LaunchedEffect(location.hasPermission) {
    if (!location.hasPermission) location.requestPermission()
}

location.currentLocation?.let { println("At ${it.latitude}, ${it.longitude}") }
```

`LocationState` has three members:

- `hasPermission`: whether the app can access the user's location.
- `currentLocation`: the last known `Location`, or `null` if none was reported yet (no permission, or no fix yet).
- `requestPermission()`: a `suspend` function that prompts the user and returns whether the permission was granted.

If you need the `LocationManager` itself, use `rememberLocationManager(accuracy, distanceFilter, updateInterval)`. It
is kept across recompositions, recreated when an option changes, and closed when it leaves the composition. You can
also pass it to `rememberLocation(locationManager)`.

### Imperative

Without Compose, create a `LocationManager` with `createLocationManager`:

```kotlin
val locationManager = createLocationManager(LocationManagerOptions(accuracy = LocationAccuracy.HIGH))

if (locationManager.locationPermissionStatus != PermissionStatus.AUTHORIZED) {
    val hasPermission = locationManager.requestLocationPermission()
    if (!hasPermission) return
}

val subscription = locationManager.addOnLocationChangedListener { location ->
    println("At ${location.latitude}, ${location.longitude}")
}
locationManager.startUpdating()

// Later:
subscription.remove()
locationManager.stopUpdating()
locationManager.close()
```

`startUpdating()` throws a `VisionCameraException` if the permission is not granted. You can read
`locationManager.lastKnownLocation` at any time. A `LocationManager` stops updating when it is closed.

### Options

`LocationManagerOptions` configures the manager:

- `accuracy`: a `LocationAccuracy`. `HIGH` is typically 3-10 m (GPS, Wi-Fi, cell and sensors), `BALANCED` (the
  default) 10-50 m (Wi-Fi and cell), `LOW` 100-1000 m (cell).
- `distanceFilter`: how far, in meters, the location has to move before a new update is sent. The default is `10.0`.
- `updateInterval`: how often updates are requested from the system, in milliseconds. The default is `10_000`. It is
  only used on Android.

### A Location

A `Location` has a `latitude` and a `longitude` (in degrees), and optionally an `altitude`, a `horizontalAccuracy` and
a `verticalAccuracy` (in meters), and `isMock`.

`timestamp` is the time the location was measured at, in **milliseconds since the Unix epoch**, like in VisionCamera V5.

### Manually creating a Location

You can create a custom `Location` with `createLocation`. This is useful for testing or for spoofing a location. The
`timestamp` is set to the current time:

```kotlin
val location = createLocation(latitude = 48.2084, longitude = 16.3735)
```

## Adding Location tags to Photos

To add a `Location` to a photo, pass it in the `CapturePhotoSettings` of `capturePhoto`:

```kotlin
val photo = photoOutput.capturePhoto(CapturePhotoSettings(location = location.currentLocation))
```

## Adding Location tags to Videos

To add a `Location` to a video, pass it in the `RecorderSettings` of `createRecorder`:

```kotlin
val recorder = videoOutput.createRecorder(RecorderSettings(location = location.currentLocation))
```

If the location is `null`, no location tags are written.
