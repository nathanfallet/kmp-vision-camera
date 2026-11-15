---
title: Video
parent: Guides
nav_order: 8
---

# Video

A `CameraVideoOutput` records videos through `Recorder`s. Create one and connect it to the session with the other
outputs:

```kotlin
val videoOutput = rememberVideoOutput(VideoOutputOptions(enableAudio = true))   // Compose
// or: val videoOutput = VisionCamera.createVideoOutput(...)                      // imperative
```

`VideoOutputOptions` sets the `targetResolution` (`CommonResolutions.FHD_16_9` by default), `enableAudio` (requires
the microphone permission), a `targetBitRate`, the `fileType` on iOS (`MOV` or `MP4`, Android always records MP4), and
`enablePersistentRecorder` to keep recording while switching cameras.

## Recording

A `Recorder` records a single video: create a new one for every recording.

```kotlin
val recorder = videoOutput.createRecorder(RecorderSettings(maxDuration = 30.seconds))
recorder.startRecording(
    onRecordingFinished = { filePath, reason -> println("Saved to $filePath ($reason)") },
    onRecordingError = { error -> println("Recording failed: $error") },
)
// later:
recorder.stopRecording()
```

`RecorderSettings` sets the `filePath` (a temporary file by default), a `maxDuration`, a `maxFileSize` and a
`Location` to embed in the metadata. `RecordingFinishedReason` says whether the recording was stopped or reached a
limit. Recordings can also be paused (`pauseRecording`), resumed (`resumeRecording`) and canceled
(`cancelRecording`, which deletes the file).

On iOS, `getSupportedVideoCodecs` and `setOutputSettings` choose the `VideoCodec` once the output is connected to the
session.
