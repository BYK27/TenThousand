import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tenthousand.data.local.dao.HabitDao

@Database(entities = [HabitEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase()
{
    abstract fun habitDao(): HabitDao
}
