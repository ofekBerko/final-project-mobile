package cs.colman.talento.utils

import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import cs.colman.talento.TAG
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

object ImageUploadService {

    suspend fun uploadImage(imageUri: Uri, identifier: String): String? {
        try {
            val publicId = "$identifier-${UUID.randomUUID()}"
            return suspendCancellableCoroutine { continuation ->
                val requestId = MediaManager.get().upload(imageUri)
                    .option("resource_type", "image")
                    .option("public_id", publicId)
                    .callback(object : UploadCallback {
                        override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                            val imageUrl = resultData?.get("secure_url") as? String
                            continuation.resume(imageUrl)
                        }

                        override fun onError(requestId: String?, error: ErrorInfo?) {
                            Log.e(TAG, "Cloudinary upload error: ${error?.description}")
                            continuation.resume(null)
                        }

                        override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                        override fun onStart(requestId: String?) {}
                        override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    })
                    .dispatch()

                continuation.invokeOnCancellation {
                    try {
                        MediaManager.get().cancelRequest(requestId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error canceling upload", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Cloudinary upload", e)
            return null
        }
    }
}
