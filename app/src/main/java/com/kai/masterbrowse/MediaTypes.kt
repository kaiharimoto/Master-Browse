package com.kai.masterbrowse

import java.io.File

private val IMAGE_EXT = setOf(
    "jpg", "jpeg", "jfif", "png", "webp", "gif", "bmp", "heic", "heif", "avif"
)

private val VIDEO_EXT = setOf(
    "mp4", "m4v", "mkv", "webm", "mov", "3gp", "avi", "ts", "mts", "m2ts", "wmv", "flv", "mpg", "mpeg"
)

fun File.isImageFile(): Boolean = isFile && extension.lowercase() in IMAGE_EXT

fun File.isVideoFile(): Boolean = isFile && extension.lowercase() in VIDEO_EXT

fun File.isMediaFile(): Boolean = isImageFile() || isVideoFile()
