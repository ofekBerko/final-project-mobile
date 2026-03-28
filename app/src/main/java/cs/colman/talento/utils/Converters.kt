package cs.colman.talento.utils

import androidx.room.TypeConverter
import org.maplibre.android.geometry.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromLatLng(latLng: LatLng?): String? {
        return latLng?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toLatLng(latLngString: String?): LatLng? {
        if (latLngString == null) return null
        val type = object : TypeToken<LatLng>() {}.type
        return gson.fromJson(latLngString, type)
    }
}
