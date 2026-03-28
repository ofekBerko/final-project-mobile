package cs.colman.talento.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "appointments",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Business::class,
            parentColumns = ["businessId"],
            childColumns = ["businessId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index("businessId")
    ]
)
data class Appointment(
    @PrimaryKey val appointmentId: String,
    val userId: String,
    val businessId: String,
    val date: String,
    val time: String,
)

data class AppointmentWithDetails(
    @Embedded val appointment: Appointment,

    @Relation(
        parentColumn = "userId",
        entityColumn = "userId"
    )
    val user: User,

    @Relation(
        parentColumn = "businessId",
        entityColumn = "businessId"
    )
    val business: Business
)
