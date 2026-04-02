package com.vignesh.jobautomation.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==================== Enums ====================

enum class JobStatus {
    NEW, ANALYZED, READY_TO_APPLY, APPLYING, APPLIED, SKIPPED, ERROR
}

enum class ApplicationStatus {
    SUBMITTED, CONFIRMED, UNDER_REVIEW, INTERVIEW_SCHEDULED,
    INTERVIEWED, OFFER_RECEIVED, REJECTED, WITHDRAWN, NO_RESPONSE
}

enum class CompanyPreference {
    ALLOWED, BLOCKED, NEUTRAL, PRIORITY
}

// ==================== Type Converters ====================

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String = value?.joinToString("|||") ?: ""
    
    @TypeConverter
    fun toStringList(value: String): List<String> = 
        if (value.isBlank()) emptyList() else value.split("|||")
    
    @TypeConverter
    fun fromJobStatus(status: JobStatus): String = status.name
    
    @TypeConverter
    fun toJobStatus(value: String): JobStatus = JobStatus.valueOf(value)
    
    @TypeConverter
    fun fromApplicationStatus(status: ApplicationStatus): String = status.name
    
    @TypeConverter
    fun toApplicationStatus(value: String): ApplicationStatus = ApplicationStatus.valueOf(value)
    
    @TypeConverter
    fun fromCompanyPreference(pref: CompanyPreference): String = pref.name
    
    @TypeConverter
    fun toCompanyPreference(value: String): CompanyPreference = CompanyPreference.valueOf(value)
}

// ==================== Entities ====================

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1, // Single profile
    @ColumnInfo(name = "full_name") val fullName: String,
    val email: String,
    val phone: String? = null,
    val location: String? = null,
    @ColumnInfo(name = "linkedin_url") val linkedinUrl: String? = null,
    @ColumnInfo(name = "github_url") val githubUrl: String? = null,
    @ColumnInfo(name = "portfolio_url") val portfolioUrl: String? = null,
    val summary: String? = null,
    @ColumnInfo(name = "skills_json") val skillsJson: String = "{}", // JSON string
    @ColumnInfo(name = "experience_json") val experienceJson: String = "[]",
    @ColumnInfo(name = "education_json") val educationJson: String = "[]",
    @ColumnInfo(name = "projects_json") val projectsJson: String = "[]",
    @ColumnInfo(name = "certifications_json") val certificationsJson: String = "[]",
    @ColumnInfo(name = "desired_roles") val desiredRoles: List<String> = emptyList(),
    @ColumnInfo(name = "desired_locations") val desiredLocations: List<String> = emptyList(),
    @ColumnInfo(name = "min_salary") val minSalary: Int? = null,
    @ColumnInfo(name = "max_salary") val maxSalary: Int? = null,
    @ColumnInfo(name = "resume_text") val resumeText: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val industry: String? = null,
    val size: String? = null,
    val website: String? = null,
    @ColumnInfo(name = "careers_url") val careersUrl: String? = null,
    @ColumnInfo(name = "linkedin_url") val linkedinUrl: String? = null,
    val preference: CompanyPreference = CompanyPreference.NEUTRAL,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "jobs",
    foreignKeys = [
        ForeignKey(
            entity = CompanyEntity::class,
            parentColumns = ["id"],
            childColumns = ["company_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("company_id"), Index("status"), Index("match_score")]
)
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    @ColumnInfo(name = "company_name") val companyName: String,
    @ColumnInfo(name = "company_id") val companyId: Int? = null,
    val location: String? = null,
    @ColumnInfo(name = "remote_type") val remoteType: String? = null, // "remote", "hybrid", "onsite"
    @ColumnInfo(name = "job_type") val jobType: String? = null, // "full-time", "contract", etc.
    @ColumnInfo(name = "experience_level") val experienceLevel: String? = null,
    val description: String? = null,
    @ColumnInfo(name = "description_full") val descriptionFull: String? = null,
    val url: String,
    @ColumnInfo(name = "salary_min") val salaryMin: Int? = null,
    @ColumnInfo(name = "salary_max") val salaryMax: Int? = null,
    @ColumnInfo(name = "easy_apply") val easyApply: Boolean = false,
    val source: String? = null, // "linkedin", "greenhouse", "manual"
    val status: JobStatus = JobStatus.NEW,
    @ColumnInfo(name = "match_score") val matchScore: Float? = null,
    @ColumnInfo(name = "match_justification") val matchJustification: String? = null, // JSON
    @ColumnInfo(name = "strengths_json") val strengthsJson: String? = null,
    @ColumnInfo(name = "weaknesses_json") val weaknessesJson: String? = null,
    @ColumnInfo(name = "posted_at") val postedAt: Long? = null,
    @ColumnInfo(name = "discovered_at") val discoveredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "analyzed_at") val analyzedAt: Long? = null
)

@Entity(
    tableName = "applications",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["job_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("job_id"), Index("status")]
)
data class ApplicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "job_id") val jobId: Int,
    val status: ApplicationStatus = ApplicationStatus.SUBMITTED,
    @ColumnInfo(name = "applied_at") val appliedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "status_updated_at") val statusUpdatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "resume_used") val resumeUsed: String? = null, // customized resume
    @ColumnInfo(name = "cover_letter") val coverLetter: String? = null,
    @ColumnInfo(name = "interview_prep") val interviewPrep: String? = null, // JSON
    val notes: String? = null,
    @ColumnInfo(name = "next_action") val nextAction: String? = null,
    @ColumnInfo(name = "next_action_date") val nextActionDate: Long? = null
)

@Entity(tableName = "chat_history")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    @ColumnInfo(name = "context_type") val contextType: String? = null, // "job", "application", etc.
    @ColumnInfo(name = "context_id") val contextId: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Relationships ====================

data class JobWithCompany(
    @Embedded val job: JobEntity,
    @Relation(
        parentColumn = "company_id",
        entityColumn = "id"
    )
    val company: CompanyEntity?
)

data class ApplicationWithJob(
    @Embedded val application: ApplicationEntity,
    @Relation(
        parentColumn = "job_id",
        entityColumn = "id"
    )
    val job: JobEntity
)

// ==================== DAOs ====================

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun getProfile(): Flow<ProfileEntity?>
    
    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfileOnce(): ProfileEntity?
    
    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)
    
    @Query("DELETE FROM profile")
    suspend fun deleteProfile()
}

@Dao
interface CompanyDao {
    @Query("SELECT * FROM companies ORDER BY name ASC")
    fun getAllCompanies(): Flow<List<CompanyEntity>>
    
    @Query("SELECT * FROM companies WHERE preference = :preference ORDER BY name ASC")
    fun getCompaniesByPreference(preference: CompanyPreference): Flow<List<CompanyEntity>>
    
    @Query("SELECT * FROM companies WHERE id = :id")
    suspend fun getCompanyById(id: Int): CompanyEntity?
    
    @Query("SELECT * FROM companies WHERE normalized_name = :normalizedName")
    suspend fun getCompanyByNormalizedName(normalizedName: String): CompanyEntity?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompany(company: CompanyEntity): Long
    
    @Update
    suspend fun updateCompany(company: CompanyEntity)
    
    @Query("UPDATE companies SET preference = :preference, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePreference(id: Int, preference: CompanyPreference, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteCompany(company: CompanyEntity)
}

@Dao
interface JobDao {
    @Transaction
    @Query("SELECT * FROM jobs ORDER BY discovered_at DESC")
    fun getAllJobs(): Flow<List<JobWithCompany>>
    
    @Transaction
    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY CASE WHEN match_score IS NULL THEN 1 ELSE 0 END, match_score DESC")
    fun getJobsByStatus(status: JobStatus): Flow<List<JobWithCompany>>
    
    @Transaction
    @Query("SELECT * FROM jobs WHERE match_score >= :minScore ORDER BY match_score DESC")
    fun getJobsByMinScore(minScore: Float): Flow<List<JobWithCompany>>
    
    @Transaction
    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getJobById(id: Int): JobWithCompany?
    
    @Query("SELECT * FROM jobs WHERE url = :url")
    suspend fun getJobByUrl(url: String): JobEntity?
    
    @Query("SELECT COUNT(*) FROM jobs WHERE status = :status")
    fun countByStatus(status: JobStatus): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJob(job: JobEntity): Long
    
    @Update
    suspend fun updateJob(job: JobEntity)
    
    @Query("UPDATE jobs SET status = :status, match_score = :score, match_justification = :justification, strengths_json = :strengths, weaknesses_json = :weaknesses, analyzed_at = :analyzedAt WHERE id = :id")
    suspend fun updateAnalysis(
        id: Int,
        status: JobStatus,
        score: Float,
        justification: String,
        strengths: String,
        weaknesses: String,
        analyzedAt: Long = System.currentTimeMillis()
    )
    
    @Delete
    suspend fun deleteJob(job: JobEntity)
    
    @Query("DELETE FROM jobs WHERE status = :status AND discovered_at < :olderThan")
    suspend fun deleteOldJobs(status: JobStatus, olderThan: Long)
}

@Dao
interface ApplicationDao {
    @Transaction
    @Query("SELECT * FROM applications ORDER BY applied_at DESC")
    fun getAllApplications(): Flow<List<ApplicationWithJob>>
    
    @Transaction
    @Query("SELECT * FROM applications WHERE status = :status ORDER BY applied_at DESC")
    fun getApplicationsByStatus(status: ApplicationStatus): Flow<List<ApplicationWithJob>>
    
    @Transaction
    @Query("SELECT * FROM applications WHERE id = :id")
    suspend fun getApplicationById(id: Int): ApplicationWithJob?
    
    @Query("SELECT * FROM applications WHERE job_id = :jobId")
    suspend fun getApplicationByJobId(jobId: Int): ApplicationEntity?
    
    @Query("SELECT COUNT(*) FROM applications")
    fun countTotal(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM applications WHERE status = :status")
    fun countByStatus(status: ApplicationStatus): Flow<Int>
    
    @Insert
    suspend fun insertApplication(application: ApplicationEntity): Long
    
    @Update
    suspend fun updateApplication(application: ApplicationEntity)
    
    @Query("UPDATE applications SET status = :status, status_updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: ApplicationStatus, updatedAt: Long = System.currentTimeMillis())
    
    @Delete
    suspend fun deleteApplication(application: ApplicationEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMessages(limit: Int = 50): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_history WHERE context_type = :contextType AND context_id = :contextId ORDER BY timestamp ASC")
    fun getMessagesForContext(contextType: String, contextId: Int): Flow<List<ChatMessageEntity>>
    
    @Insert
    suspend fun insertMessage(message: ChatMessageEntity): Long
    
    @Query("DELETE FROM chat_history WHERE timestamp < :olderThan")
    suspend fun deleteOldMessages(olderThan: Long)
    
    @Query("DELETE FROM chat_history")
    suspend fun clearHistory()
}

// ==================== Database ====================

@Database(
    entities = [
        ProfileEntity::class,
        CompanyEntity::class,
        JobEntity::class,
        ApplicationEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JobAutomationDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun companyDao(): CompanyDao
    abstract fun jobDao(): JobDao
    abstract fun applicationDao(): ApplicationDao
    abstract fun chatDao(): ChatDao
    
    companion object {
        @Volatile
        private var INSTANCE: JobAutomationDatabase? = null
        
        fun getDatabase(context: android.content.Context): JobAutomationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JobAutomationDatabase::class.java,
                    "job_automation.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
