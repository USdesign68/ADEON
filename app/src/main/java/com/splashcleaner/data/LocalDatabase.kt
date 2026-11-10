package com.splashcleaner.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_cfg", primaryKeys = ["packageName"])
data class AppConfigEntity(
    val packageName: String,
    val enabled: Boolean = false,
    val lastUsedAt: Long = 0L,
    val userApprovedAt: Long? = null
)

@Entity(tableName = "hit", indices = [Index("occurredAt"), Index("packageHash"), Index("ruleId")])
data class HitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageHash: String,
    val ruleId: String,
    val result: String,
    val costMs: Long,
    val occurredAt: Long
) // Never add text/contentDescription fields.

@Entity(tableName = "report", indices = [Index("createdAt")])
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appVersion: String,
    val score: Int,
    val grade: String,
    val coverage: Double,
    val payloadJson: String,
    val createdAt: Long
)

@Entity(tableName = "daily_aggregate", primaryKeys = ["day", "packageHash", "ruleId", "result"])
data class DailyAggregateEntity(
    val day: String,
    val packageHash: String,
    val ruleId: String,
    val result: String,
    val count: Long
)

@Entity(tableName = "rule_package")
data class RulePackageEntity(
    @PrimaryKey val channel: String,
    val version: String,
    val installedAt: Long,
    val previousVersion: String? = null
)

@Dao interface AppConfigDao {
    @Query("SELECT * FROM app_cfg ORDER BY lastUsedAt DESC") fun observeAll(): Flow<List<AppConfigEntity>>
    @Query("SELECT enabled FROM app_cfg WHERE packageName = :pkg LIMIT 1") suspend fun isEnabled(pkg: String): Boolean?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: AppConfigEntity)
}
@Dao interface HitDao {
    @Insert suspend fun insert(item: HitEntity)
    @Query("SELECT * FROM hit ORDER BY occurredAt DESC LIMIT 20") fun recent(): Flow<List<HitEntity>>
    @Query("DELETE FROM hit WHERE occurredAt < :cutoff") suspend fun deleteBefore(cutoff: Long)
    @Query("DELETE FROM hit") suspend fun clear()
}
@Dao interface ReportDao {
    @Insert suspend fun insert(item: ReportEntity): Long
    @Query("SELECT * FROM report ORDER BY createdAt DESC LIMIT 20") fun recent(): Flow<List<ReportEntity>>
    @Query("DELETE FROM report WHERE id NOT IN (SELECT id FROM report ORDER BY createdAt DESC LIMIT 20)") suspend fun trim()
}
@Dao interface AggregateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: DailyAggregateEntity)
    @Query("SELECT * FROM daily_aggregate") suspend fun all(): List<DailyAggregateEntity>
}
@Dao interface RulePackageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: RulePackageEntity)
    @Query("SELECT * FROM rule_package WHERE channel = :channel LIMIT 1") suspend fun get(channel: String): RulePackageEntity?
}

@Database(entities = [AppConfigEntity::class, HitEntity::class, ReportEntity::class, DailyAggregateEntity::class, RulePackageEntity::class], version = 1, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun appConfigDao(): AppConfigDao
    abstract fun hitDao(): HitDao
    abstract fun reportDao(): ReportDao
    abstract fun aggregateDao(): AggregateDao
    abstract fun rulePackageDao(): RulePackageDao
    companion object {
        @Volatile private var INSTANCE: LocalDatabase? = null
        fun get(context: Context): LocalDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, LocalDatabase::class.java, "splash_cleaner.db").build().also { INSTANCE = it }
        }
    }
}
