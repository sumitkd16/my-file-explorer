package com.raival.compose.file.explorer.common.extension

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable

fun <T : Parcelable> Intent.putParcelableArrayListExtra(name: String, value: ArrayList<T>) {
    putParcelableArrayListExtra(name, value)
}

fun <T : Parcelable> Intent.getParcelableArrayListExtra(name: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, Parcelable::class.java) as? ArrayList<T>
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }
}

fun <T : Parcelable> Bundle.putParcelableArrayList(name: String, value: ArrayList<T>) {
    putParcelableArrayList(name, value)
}

fun <T : Parcelable> Bundle.getParcelableArrayList(name: String): ArrayList<T>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(name, Parcelable::class.java) as? ArrayList<T>
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayList(name)
    }
}
