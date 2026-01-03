package com.example.practive

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

// 1. 資料表結構 (Entity)
@Entity(tableName = "scan_records")
data class ScanRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,    // 掃描內容
    val price: Int,         // 金額 (手動輸入)
    val latitude: Double,   // 緯度
    val longitude: Double,  // 經度
    val timestamp: Long = System.currentTimeMillis() // 時間
)

// 2. 操作介面 (DAO)
@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ScanRecord>> // 回傳即時更新的列表

    @Insert
    suspend fun insert(record: ScanRecord)

    @Query("SELECT SUM(price) FROM scan_records")
    fun getTotalExpense(): Flow<Int?> // 計算總花費

    @Delete
    suspend fun delete(record: ScanRecord)
}

// 3. 資料庫實體 (Database)
@Database(entities = [ScanRecord::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "app_db")
                    .build().also { INSTANCE = it }
            }
        }
    }
}
