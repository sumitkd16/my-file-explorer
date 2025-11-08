package com.raival.compose.file.explorer.screen.main.tab.files.misc

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val size: Long,
    val extension: String
) : Parcelable
