package cs.colman.talento.data.model

import androidx.room.*

@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["reviewerId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["reviewedId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Review(
    @PrimaryKey
    val reviewId: String,

    @ColumnInfo(index = true)
    val reviewerId: String,

    @ColumnInfo(index = true)
    val reviewedId: String,

    val content: String,
    val date: String,
    val imageUrl: String? = null
)

data class ReviewWithUser(
    @Embedded val review: Review,
    @Relation(
        parentColumn = "reviewerId",
        entityColumn = "userId"
    )
    val reviewer: User,
    @Relation(
        parentColumn = "reviewedId",
        entityColumn = "userId"
    )
    val reviewed: User
)
