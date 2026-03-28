package cs.colman.talento.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


object DateTimeUtils {
    @SuppressLint("ConstantLocale")
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    @SuppressLint("ConstantLocale")
    private val DISPLAY_DATE_FORMAT = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())

    fun formatDateForDisplay(dateString: String): String {
        if (dateString.contains(",")) {
            return dateString
        }

        return try {
            val date = DATE_FORMAT.parse(dateString)
            date?.let { DISPLAY_DATE_FORMAT.format(it) } ?: dateString
        } catch (e: Exception) {
            dateString
        }
    }

    fun isDateAvailable(dateString: String): Boolean {
        try {
            val date = DATE_FORMAT.parse(dateString) ?: return false
            val calendar = Calendar.getInstance()
            calendar.time = date

            if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {
                return false
            }

            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis >= today.timeInMillis
        } catch (e: Exception) {
            return false
        }
    }

    @SuppressLint("DefaultLocale")
    fun generateTimeSlotsForDate(dateString: String): List<String> {
        try {
            val date = DATE_FORMAT.parse(dateString) ?: return emptyList()
            val calendar = Calendar.getInstance()
            calendar.time = date
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

            if (dayOfWeek == Calendar.SATURDAY) {
                return emptyList()
            }

            val slots = mutableListOf<String>()
            val startHour = 8
            val endHour = if (dayOfWeek == Calendar.FRIDAY) 14 else 20

            val today = Calendar.getInstance()
            val isToday = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    calendar.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                    calendar.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)

            val currentHour = if (isToday) today.get(Calendar.HOUR_OF_DAY) else -1
            val currentMinute = if (isToday) today.get(Calendar.MINUTE) else -1

            for (hour in startHour until endHour) {
                if (isToday && hour < currentHour) continue

                if (isToday && hour == currentHour) {
                    if (currentMinute < 30) {
                        slots.add(String.format("%02d:30", hour))
                    }
                } else {
                    slots.add(String.format("%02d:00", hour))
                    slots.add(String.format("%02d:30", hour))
                }
            }
            return slots
        } catch (e: Exception) {
            return emptyList()
        }
    }

}
