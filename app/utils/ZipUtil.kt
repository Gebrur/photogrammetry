package com.example.photogrammetryapp.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtil {

    fun createZip(files: List<File>): File {
        require(files.isNotEmpty()) { "File list must not be empty" }

        val zipFile = File.createTempFile("images_", ".zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            files.forEach { file ->
                FileInputStream(file).use { fis ->
                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)
                    fis.copyTo(zos)
                    zos.closeEntry()
                }
            }
        }

        return zipFile
    }
}