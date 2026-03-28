package cs.colman.talento.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import cs.colman.talento.data.local.dao.AppointmentDao
import cs.colman.talento.data.local.dao.BusinessDao
import cs.colman.talento.data.local.dao.ProfessionDao
import cs.colman.talento.data.local.dao.UserDao
import cs.colman.talento.data.local.dao.ReviewDao
import cs.colman.talento.data.model.Appointment
import cs.colman.talento.data.model.Business
import cs.colman.talento.data.model.Profession
import cs.colman.talento.data.model.User
import cs.colman.talento.data.model.Review
import cs.colman.talento.utils.Converters

@Database(
    entities = [
        User::class,
        Business::class,
        Profession::class,
        Appointment::class,
        Review:: class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun businessDao(): BusinessDao
    abstract fun professionDao(): ProfessionDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "talento_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
