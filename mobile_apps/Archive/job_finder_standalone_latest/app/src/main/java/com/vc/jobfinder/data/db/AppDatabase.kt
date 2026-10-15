package com.vc.jobfinder.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ResumeEntity::class, JobEntity::class, MatchEntity::class, CompetitionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun resumeDao(): ResumeDao
    abstract fun jobDao(): JobDao
    abstract fun matchDao(): MatchDao
    abstract fun competitionDao(): CompetitionDao
}
