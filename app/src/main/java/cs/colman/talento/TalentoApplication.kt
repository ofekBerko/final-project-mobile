package cs.colman.talento

import android.app.Application
import android.util.Log
import cs.colman.talento.data.local.AppDatabase

const val TAG = "TalentoApplication"

class TalentoApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()

         initializeCloudinary()
    }

    private fun initializeCloudinary() {
        try {
            // Placeholder: These would come from BuildConfig or secrets
            val config: HashMap<String, String> = hashMapOf(
                "cloud_name" to "maorpikris",
                "api_key" to "781634439992256",
                "api_secret" to "MwVfYw-3H3BwaQi4UhOSQ8hBDg4"
            )
            com.cloudinary.android.MediaManager.init(this, config)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Cloudinary")
        }
    }
}
