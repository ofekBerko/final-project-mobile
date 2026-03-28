package cs.colman.talento.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val profilePicUrl: String,
    val businessId: String?
)

data class UserWithBusiness(
    @Embedded val user: User,
    @Relation(
        parentColumn = "userId",
        entityColumn = "userId"
    )
    val business: Business?
)
