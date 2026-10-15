package com.vc.jobfinder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResumeDao {
    @Query("SELECT * FROM resume WHERE id = 0")
    fun observe(): Flow<ResumeEntity?>

    @Query("SELECT * FROM resume WHERE id = 0")
    suspend fun get(): ResumeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(r: ResumeEntity)

    @Query("DELETE FROM resume")
    suspend fun clear()
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY company, title")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs")
    suspend fun all(): List<JobEntity>

    @Query("SELECT COUNT(*) FROM jobs")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(jobs: List<JobEntity>)

    @Query("DELETE FROM jobs WHERE company = :company")
    suspend fun deleteForCompany(company: String)

    @Query("DELETE FROM jobs")
    suspend fun clear()
}

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE fitScore >= :minScore ORDER BY fitScore DESC LIMIT :limit")
    fun observe(minScore: Double, limit: Int): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun get(id: String): MatchEntity?

    @Query("SELECT status FROM matches WHERE id = :id")
    suspend fun getStatus(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(m: MatchEntity)

    @Query("UPDATE matches SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("DELETE FROM matches")
    suspend fun clear()
}

@Dao
interface CompetitionDao {
    @Query("""
        SELECT * FROM competitions
        WHERE (:trackedOnly = 0 OR isTrackedCompany = 1)
          AND (:hiringOnly = 0 OR isHiring = 1)
        ORDER BY isTrackedCompany DESC, COALESCE(startsAt, '9999') ASC
        LIMIT :limit
    """)
    fun observe(trackedOnly: Boolean, hiringOnly: Boolean, limit: Int): Flow<List<CompetitionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CompetitionEntity>)

    @Query("DELETE FROM competitions")
    suspend fun clear()
}
