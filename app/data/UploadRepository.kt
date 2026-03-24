package com.example.photogrammetryapp.data

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException

class UploadRepository(
    private val client: OkHttpClient,
    private val serverUrl: String
) {

    suspend fun upload(file: File): Result<ByteArray> {
        return try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "scan.zip",
                    file.asRequestBody("application/zip".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(serverUrl)
                .post(body)
                .build()

            val response = client.newCall(request).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IOException("Server error ${resp.code}"))
                }

                val bytes = resp.body?.bytes()

                if (bytes == null || bytes.isEmpty()) {
                    return Result.failure(IOException("Empty body"))
                }

                Result.success(bytes)
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}