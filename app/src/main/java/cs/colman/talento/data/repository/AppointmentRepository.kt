package cs.colman.talento.data.repository

import android.content.Context
import android.util.Log
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.local.dao.AppointmentDao
import cs.colman.talento.data.model.Appointment
import cs.colman.talento.data.model.AppointmentWithDetails
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.model.User
import cs.colman.talento.utils.NetworkResult
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val userRepository: UserRepository,
    private val businessRepository: BusinessRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val context: Context
) {
    private val businessCache = ConcurrentHashMap<String, Business>()
    private val userCache = ConcurrentHashMap<String, User>()

    suspend fun bookAppointment(
        userId: String,
        businessId: String,
        date: String,
        time: String
    ): NetworkResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val appointmentData = mapOf(
                    "userId" to userId,
                    "businessId" to businessId,
                    "date" to date,
                    "time" to time
                )

                try {
                    val docRef = firestore.collection("appointments")
                        .document()
                    docRef.set(appointmentData).await()
                    val appointment = Appointment(
                        appointmentId = docRef.id,
                        userId = userId,
                        businessId = businessId,
                        date = date,
                        time = time
                    )
                    try {
                        appointmentDao.insertAppointment(appointment)
                    } catch (e: Exception) {
                        Log.e(TAG, "bookAppointment: Error saving to local database", e)
                    }

                    NetworkResult.Success(appointment.appointmentId)
                } catch (e: Exception) {
                    Log.e(TAG, "bookAppointment: Error saving to Firestore", e)
                    return@withContext NetworkResult.Error(context.getString(R.string.error_firestore_save))
                }
            } catch (e: Exception) {
                Log.e(TAG, "bookAppointment: Unexpected error", e)
                NetworkResult.Error(context.getString(R.string.error_booking_general))
            }
        }
    }

    suspend fun cancelAppointment(appointmentId: String): NetworkResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                firestore.collection("appointments")
                    .document(appointmentId)
                    .delete()
                    .await()

                NetworkResult.Success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling appointment", e)
                NetworkResult.Error(context.getString(R.string.error_cancel_appointment))
            }
        }
    }

    suspend fun fetchPaginatedAppointments(
        userId: String,
        businessId: String?,
        isUpcoming: Boolean,
        limit: Int = 15,
        excludeIds: Set<String> = emptySet()
    ): NetworkResult<List<Appointment>> {
        return withContext(Dispatchers.IO) {
            try {
                val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                return@withContext coroutineScope {
                    val userAppointmentsDeferred = async {
                        firestore.collection("appointments")
                            .whereEqualTo("userId", userId)
                            .get()
                            .await()
                            .documents
                            .mapNotNull { processAppointmentDocument(it) }
                    }

                    val businessAppointmentsDeferred = if (businessId != null) {
                        async {
                            firestore.collection("appointments")
                                .whereEqualTo("businessId", businessId)
                                .get()
                                .await()
                                .documents
                                .mapNotNull { processAppointmentDocument(it) }
                        }
                    } else null

                    val userAppointments = userAppointmentsDeferred.await()
                    val businessAppointments = businessAppointmentsDeferred?.await() ?: emptyList()

                    val allAppointments = (userAppointments + businessAppointments)
                        .distinctBy { it.appointmentId }
                        .filter { !excludeIds.contains(it.appointmentId) }
                        .filter { appointment ->
                            if (isUpcoming) {
                                (appointment.date > currentDate) || 
                                (appointment.date == currentDate && appointment.time > currentTime)
                            } else {
                                (appointment.date < currentDate) || 
                                (appointment.date == currentDate && appointment.time <= currentTime)
                            }
                        }
                        .let { list ->
                            if (isUpcoming) {
                                list.sortedWith(compareBy({ it.date }, { it.time }))
                            } else {
                                list.sortedWith(compareByDescending<Appointment> { it.date }.thenByDescending { it.time })
                            }
                        }
                        .take(limit)

                    NetworkResult.Success(allAppointments)
                }
            } catch (e: Exception) {
                Log.e(TAG, "fetchPaginatedAppointments: ERROR", e)
                NetworkResult.Error(context.getString(R.string.error_fetch_appointments))
            }
        }
    }

    private fun processAppointmentDocument(doc: DocumentSnapshot): Appointment? {
        return try {
            val appointmentId = doc.id
            val userId = doc.getString("userId")
            val businessId = doc.getString("businessId")
            val date = doc.getString("date")
            val time = doc.getString("time")

            if (userId == null || businessId == null || date == null || time == null) {
                Log.e(TAG, "processAppointmentDocument: missing fields in doc ${doc.id}")
                return null
            }

            val appointment = Appointment(
                appointmentId = appointmentId,
                userId = userId,
                businessId = businessId,
                date = date,
                time = time
            )
            appointment
        } catch (e: Exception) {
            Log.e(TAG, "processAppointmentDocument: error parsing doc ${doc.id}", e)
            null
        }
    }

    suspend fun fetchAppointmentsForBusinessOnDate(businessId: String, date: String): NetworkResult<List<Appointment>> {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("appointments")
                    .whereEqualTo("businessId", businessId)
                    .whereEqualTo("date", date)
                    .get()
                    .await()

                val appointments = snapshot.documents.mapNotNull { doc ->
                    try {
                        val appointmentId = doc.id
                        val userId = doc.getString("userId") ?: return@mapNotNull null
                        val time = doc.getString("time") ?: return@mapNotNull null

                        Appointment(
                            appointmentId = appointmentId,
                            userId = userId,
                            businessId = businessId,
                            date = date,
                            time = time
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "fetchAppointmentsForBusinessOnDate: Error parsing document", e)
                        null
                    }
                }

                NetworkResult.Success(appointments)
            } catch (e: Exception) {
                Log.e(TAG, "fetchAppointmentsForBusinessOnDate: Error", e)
                NetworkResult.Error(context.getString(R.string.error_fetch_date_appointments))
            }
        }
    }

    fun getTimestampFromAppointment(appointment: Appointment): String {
        return "${appointment.date}|${appointment.time}"
    }

    suspend fun getAppointmentsWithDetails(appointments: List<Appointment>): List<AppointmentWithDetails> {
        if (appointments.isEmpty()) return emptyList()

        return coroutineScope {
            val userIds = appointments.map { it.userId }.distinct()
            val businessIds = appointments.map { it.businessId }.distinct()

            val usersDeferred = userIds.map { userId ->
                async {
                    if (userCache.containsKey(userId)) {
                        userId to userCache[userId]!!
                    } else {
                        val result = userRepository.loadUser(userId)
                        if (result is NetworkResult.Success) {
                            userCache[userId] = result.data
                            userId to result.data
                        } else null
                    }
                }
            }

            val businessDeferred = businessIds.map { businessId ->
                async {
                    if (businessCache.containsKey(businessId)) {
                        businessId to businessCache[businessId]!!
                    } else {
                        val result = businessRepository.getBusinessById(businessId)
                        if (result is NetworkResult.Success) {
                            businessCache[businessId] = result.data
                            businessId to result.data
                        } else null
                    }
                }
            }

            val usersMap = usersDeferred.awaitAll().filterNotNull().toMap()
            val businessesMap = businessDeferred.awaitAll().filterNotNull().toMap()

            appointments.mapNotNull { appointment ->
                try {
                    val user = usersMap[appointment.userId] ?: return@mapNotNull null
                    val business = businessesMap[appointment.businessId] ?: return@mapNotNull null

                    AppointmentWithDetails(
                        appointment = appointment,
                        user = user,
                        business = business
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error building AppointmentWithDetails: ${e.message}")
                    null
                }
            }
        }
    }
}
