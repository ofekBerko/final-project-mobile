package cs.colman.talento.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import cs.colman.talento.data.model.User
import cs.colman.talento.data.model.UserWithBusiness

@Dao
interface UserDao {
    @Transaction
    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserWithBusiness(userId: String): LiveData<UserWithBusiness?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserByIdSync(userId: String): User?
}
