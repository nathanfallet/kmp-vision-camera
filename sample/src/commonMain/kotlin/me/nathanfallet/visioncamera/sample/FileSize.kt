package me.nathanfallet.visioncamera.sample

/** The size in bytes of the file at [path], or `null` if it can't be read. */
expect fun fileSize(path: String): Long?
