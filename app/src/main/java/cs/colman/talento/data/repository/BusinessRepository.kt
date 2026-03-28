package cs.colman.talento.data.repository

import android.content.Context
import android.util.Log
import cs.colman.talento.R
import cs.colman.talento.TAG
import cs.colman.talento.data.local.dao.BusinessDao
import cs.colman.talento.data.local.dao.UserDao
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.model.User
import cs.colman.talento.utils.NetworkResult
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng


class BusinessRepository(
    private val businessDao: BusinessDao,
    private val userDao: UserDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val context: Context
) {
    suspend fun getNearbyBusinesses(center: LatLng, radiusInKm: Double): NetworkResult<List<Business>> {
        return withContext(Dispatchers.IO) {
            try {
                val centerLocation = GeoLocation(center.latitude, center.longitude)
                val bounds = GeoFireUtils.getGeoHashQueryBounds(centerLocation, radiusInKm * 1000)

                val matchingBusinesses = mutableListOf<Business>()
                val processedBusinessIds = mutableSetOf<String>()

                bounds.forEach { bound ->
                    val query = firestore.collection("businesses")
                        .orderBy("geoHash")
                        .startAt(bound.startHash)
                        .endAt(bound.endHash)
                        .get()
                        .await()

                    for (doc in query.documents) {
                        val businessId = doc.id

                        if (businessId in processedBusinessIds) continue
                        processedBusinessIds.add(businessId)

                        val userId = doc.getString("userId") ?: continue
                        val businessName = doc.getString("businessName") ?: continue
                        val description = doc.getString("description") ?: ""
                        val address = doc.getString("address") ?: ""
                        val profession = doc.getString("profession") ?: ""
                        val geoHash = doc.getString("geoHash") ?: continue

                        val locationMap = doc.get("location") as? Map<*, *> ?: continue
                        val lat = locationMap["latitude"] as? Double ?: continue
                        val lng = locationMap["longitude"] as? Double ?: continue
                        val location = LatLng(lat, lng)

                        val distanceInM = GeoFireUtils.getDistanceBetween(
                            GeoLocation(lat, lng),
                            centerLocation
                        )

                        if (distanceInM <= radiusInKm * 1000) {
                            val business = Business(
                                businessId = businessId,
                                userId = userId,
                                businessName = businessName,
                                description = description,
                                address = address,
                                profession = profession,
                                location = location,
                                geoHash = geoHash
                            )

                            matchingBusinesses.add(business)

                            if (userDao.getUserByIdSync(userId) == null) {
                                try {
                                    val userDoc = firestore.collection("users").document(userId).get().await()
                                    if (userDoc.exists()) {
                                        val fullName = userDoc.getString("fullName") ?: ""
                                        val email = userDoc.getString("email") ?: ""
                                        val phone = userDoc.getString("phone") ?: ""
                                        val profilePicUrl = userDoc.getString("profilePicUrl") ?: ""
                                        val userBusinessId = userDoc.getString("businessId")

                                        val user = User(
                                            userId = userId,
                                            fullName = fullName,
                                            email = email,
                                            phone = phone,
                                            profilePicUrl = profilePicUrl,
                                            businessId = userBusinessId
                                        )

                                        userDao.insertUser(user)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching user data for business: $businessId", e)
                                    continue
                                }
                            }

                            try {
                                businessDao.insertBusiness(business)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error inserting business: $businessId", e)
                            }
                        }
                    }
                }

                NetworkResult.Success(matchingBusinesses)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching nearby businesses", e)
                NetworkResult.Error(context.getString(R.string.error_nearby_businesses))
            }
        }
    }

    suspend fun saveOrUpdateBusiness(
        businessId: String?,
        userId: String,
        businessName: String,
        description: String,
        address: String,
        profession: String,
        location: LatLng
    ): NetworkResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val geoHash = GeoFireUtils.getGeoHashForLocation(
                    GeoLocation(location.latitude, location.longitude)
                )

                val businessData = mapOf(
                    "userId" to userId,
                    "businessName" to businessName,
                    "description" to description,
                    "address" to address,
                    "profession" to profession,
                    "location" to mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude
                    ),
                    "geoHash" to geoHash
                )

                val newBusinessId = if (businessId != null) {
                    firestore.collection("businesses").document(businessId)
                        .update(businessData).await()
                    businessId
                } else {
                    val docRef = firestore.collection("businesses").document()
                    docRef.set(businessData).await()

                    docRef.id
                }

                val business = Business(
                    businessId = newBusinessId,
                    userId = userId,
                    businessName = businessName,
                    description = description,
                    address = address,
                    profession = profession,
                    location = location,
                    geoHash = geoHash
                )

                try {
                    businessDao.insertBusiness(business)
                } catch (e: Exception) {
                    Log.e(TAG, "BUSINESS INSERT FAILED", e)
                }
                NetworkResult.Success(newBusinessId)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving business", e)
                NetworkResult.Error(context.getString(R.string.error_updating_profile))
            }
        }
    }

    suspend fun deleteBusiness(businessId: String): NetworkResult<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                firestore.collection("businesses").document(businessId).delete().await()
                businessDao.deleteBusinessById(businessId)
                NetworkResult.Success(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting business", e)
                NetworkResult.Error(context.getString(R.string.error_deleting_business))
            }
        }
    }

    suspend fun getBusinessById(businessId: String): NetworkResult<Business> {
        return withContext(Dispatchers.IO) {
            try {
                val localBusiness = businessDao.getBusinessByIdSync(businessId)
                if (localBusiness != null) {
                    return@withContext NetworkResult.Success(localBusiness)
                }
                
                val doc = firestore.collection("businesses").document(businessId).get().await()

                if (!doc.exists()) {
                    Log.e(TAG, "getBusinessById: Business not found in Firestore")
                    return@withContext NetworkResult.Error(context.getString(R.string.error_business_not_found))
                }

                val userId = doc.getString("userId") ?:
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_business_data))
                val businessName = doc.getString("businessName") ?:
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_business_data))
                val description = doc.getString("description") ?: ""
                val address = doc.getString("address") ?: ""
                val profession = doc.getString("profession") ?: ""
                val geoHash = doc.getString("geoHash") ?: ""

                val locationMap = doc.get("location") as? Map<*, *> ?:
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_location_data))
                val lat = locationMap["latitude"] as? Double ?:
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_location_data))
                val lng = locationMap["longitude"] as? Double ?:
                return@withContext NetworkResult.Error(context.getString(R.string.error_invalid_location_data))
                val location = LatLng(lat, lng)

                val business = Business(
                    businessId = businessId,
                    userId = userId,
                    businessName = businessName,
                    description = description,
                    address = address,
                    profession = profession,
                    location = location,
                    geoHash = geoHash
                )

                try {
                    businessDao.insertBusiness(business)
                } catch (e: Exception) {
                    Log.e(TAG, "getBusinessById: Error saving to local DB", e)
                }

                NetworkResult.Success(business)
            } catch (e: Exception) {
                Log.e(TAG, "getBusinessById: Error fetching business", e)
                NetworkResult.Error(context.getString(R.string.error_business_not_found))
            }
        }
    }

    suspend fun searchBusinesses(
        center: LatLng,
        radiusInKm: Double,
        name: String = "",
        profession: String = ""
    ): NetworkResult<List<Business>> {
        return withContext(Dispatchers.IO) {
            try {
                val centerLocation = GeoLocation(center.latitude, center.longitude)
                val bounds = GeoFireUtils.getGeoHashQueryBounds(centerLocation, radiusInKm * 1000)

                val matchingBusinesses = mutableListOf<Business>()
                val processedBusinessIds = mutableSetOf<String>()

                val lowerCaseName = name.lowercase()
                val lowerCaseProfession = profession.lowercase()

                bounds.forEach { bound ->
                    val query = firestore.collection("businesses")
                        .orderBy("geoHash")
                        .startAt(bound.startHash)
                        .endAt(bound.endHash)

                    val querySnapshot = query.get().await()

                    for (doc in querySnapshot.documents) {
                        val businessId = doc.id

                        if (businessId in processedBusinessIds) continue
                        processedBusinessIds.add(businessId)

                        val userId = doc.getString("userId") ?: continue
                        val businessName = doc.getString("businessName") ?: continue
                        val description = doc.getString("description") ?: ""
                        val address = doc.getString("address") ?: ""
                        val docProfession = doc.getString("profession") ?: ""
                        val geoHash = doc.getString("geoHash") ?: continue

                        val locationMap = doc.get("location") as? Map<*, *> ?: continue
                        val lat = locationMap["latitude"] as? Double ?: continue
                        val lng = locationMap["longitude"] as? Double ?: continue
                        val location = LatLng(lat, lng)

                        val distanceInM = GeoFireUtils.getDistanceBetween(
                            GeoLocation(lat, lng),
                            centerLocation
                        )

                        if (distanceInM <= radiusInKm * 1000) {
                            if (profession.isNotEmpty() &&
                                !docProfession.lowercase().contains(lowerCaseProfession)) {
                                continue
                            }

                            var ownerName = ""
                            try {
                                val userDoc = firestore.collection("users").document(userId).get().await()
                                if (userDoc.exists()) {
                                    ownerName = userDoc.getString("fullName") ?: ""

                                    val email = userDoc.getString("email") ?: ""
                                    val phone = userDoc.getString("phone") ?: ""
                                    val profilePicUrl = userDoc.getString("profilePicUrl") ?: ""
                                    val userBusinessId = userDoc.getString("businessId")

                                    val user = User(
                                        userId = userId,
                                        fullName = ownerName,
                                        email = email,
                                        phone = phone,
                                        profilePicUrl = profilePicUrl,
                                        businessId = userBusinessId
                                    )

                                    userDao.insertUser(user)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching user data for business: $businessId", e)
                            }

                            val matchesName = if (name.isNotEmpty()) {
                                businessName.lowercase().contains(lowerCaseName) ||
                                        ownerName.lowercase().contains(lowerCaseName)
                            } else {
                                true
                            }

                            if (!matchesName) {
                                continue
                            }

                            val business = Business(
                                businessId = businessId,
                                userId = userId,
                                businessName = businessName,
                                description = description,
                                address = address,
                                profession = docProfession,
                                location = location,
                                geoHash = geoHash
                            )

                            matchingBusinesses.add(business)

                            try {
                                businessDao.insertBusiness(business)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error inserting business into Room database: $businessId", e)
                            }
                        }
                    }
                }

                NetworkResult.Success(matchingBusinesses)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching businesses", e)
                NetworkResult.Error(context.getString(R.string.error_nearby_businesses))
            }
        }
    }
}
