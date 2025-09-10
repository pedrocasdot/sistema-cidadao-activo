package ao.co.isptec.aplm.sca.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ao.co.isptec.aplm.sca.database.converter.DateConverter
import ao.co.isptec.aplm.sca.database.dao.OcorrenciaDao
import ao.co.isptec.aplm.sca.database.entity.OcorrenciaEntity

@Database(
    entities = [OcorrenciaEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class SCADatabase : RoomDatabase() {
    
    abstract fun ocorrenciaDao(): OcorrenciaDao
    
    companion object {
        @Volatile
        private var INSTANCE: SCADatabase? = null
        
        private const val DATABASE_NAME = "sca_database"
        
        fun getInstance(context: Context): SCADatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SCADatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // Only for development
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
            }
        }
        
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
