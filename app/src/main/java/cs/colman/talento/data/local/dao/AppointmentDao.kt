package cs.colman.talento.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import cs.colman.talento.data.model.Appointment
import cs.colman.talento.data.model.AppointmentWithDetails

@Dao
interface AppointmentDao {
    @Transaction
    @Query("SELECT * FROM appointments WHERE appointmentId = :appointmentId")
    fun getAppointmentWithDetails(appointmentId: String): LiveData<AppointmentWithDetails?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppointment(appointment: Appointment)

    @Delete
    suspend fun deleteAppointment(appointment: Appointment)
}
