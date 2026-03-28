package cs.colman.talento.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.model.BusinessWithOwner

@Dao
interface BusinessDao {
    @Query("SELECT * FROM businesses WHERE businessId = :businessId")
    fun getBusinessById(businessId: String): LiveData<Business?>

    @Query("SELECT * FROM businesses WHERE businessId = :businessId")
    suspend fun getBusinessByIdSync(businessId: String): Business?

    @Transaction
    @Query("SELECT * FROM businesses")
    fun getAllBusinessesWithOwners(): LiveData<List<BusinessWithOwner>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusiness(business: Business)

    @Delete
    suspend fun deleteBusiness(business: Business)

    @Query("DELETE FROM businesses WHERE businessId = :businessId")
    suspend fun deleteBusinessById(businessId: String)
}
