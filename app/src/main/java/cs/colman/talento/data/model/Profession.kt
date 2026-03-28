package cs.colman.talento.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "professions")
data class Profession(
    @PrimaryKey val id: String,
    val name: String
)
