package com.example.photogrammetryapp.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object UriFileMapper {

    /**
     * Копирует файл из Uri во временный File в cacheDir.
     */
    fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream")

        val tempFile = File.createTempFile("temp_", ".jpg", context.cacheDir)

        inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }
}