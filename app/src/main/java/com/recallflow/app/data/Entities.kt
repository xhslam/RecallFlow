package com.recallflow.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.Instant
import java.util.UUID

enum class ItemStatus { ACTIVE, REINFORCED, ARCHIVED }
enum class AttachmentType { IMAGE, PDF, DOCUMENT, LINK }
enum class ReviewKind { MAIN, REMEDIAL, MANUAL, RESTART }
enum class ReviewResult { UNKNOWN, UNFAMILIAR, FUZZY, MASTERED }
enum class ScheduleState { PENDING, COMPLETED, CANCELLED }

@Entity(
    tableName = "categories",
    indices = [Index("parentId")]
)
data class CategoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val colorToken: String = "sage",
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now()
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorToken: String = "mist",
    val createdAt: Instant = Instant.now()
)

@Entity(tableName = "study_items", indices = [Index("categoryId"), Index("nextReviewAt"), Index("status")])
data class StudyItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val richContent: String = "",
    val plainTextIndex: String = "",
    val categoryId: String? = null,
    val createdAt: Instant = Instant.now(),
    val firstLearnedAt: Instant = Instant.now(),
    val status: ItemStatus = ItemStatus.ACTIVE,
    val currentRound: Int = 1,
    val lastReviewedAt: Instant? = null,
    val nextReviewAt: Instant? = null,
    val timezoneId: String = java.time.ZoneId.systemDefault().id,
    val retryCount: Int = 0,
    val archivedAt: Instant? = null,
    val activePlanBatchId: String = UUID.randomUUID().toString()
)

@Entity(
    tableName = "study_item_tags",
    primaryKeys = ["itemId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(entity = StudyItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class StudyItemTagCrossRef(val itemId: String, val tagId: String)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = StudyItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")]
)
data class AttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val type: AttachmentType,
    val displayName: String,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val sizeBytes: Long = 0L,
    val url: String? = null,
    val createdAt: Instant = Instant.now()
)

@Entity(
    tableName = "review_schedules",
    indices = [Index("itemId"), Index("dueAt"), Index("state"), Index("planBatchId")]
)
data class ReviewScheduleEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val planBatchId: String,
    val round: Int,
    val kind: ReviewKind,
    val dueAt: Instant,
    val state: ScheduleState = ScheduleState.PENDING,
    val sourceEventId: String? = null,
    val createdAt: Instant = Instant.now()
)

@Entity(
    tableName = "review_events",
    indices = [Index("itemId"), Index("completedAt"), Index("planBatchId")]
)
data class ReviewEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val planBatchId: String,
    val round: Int,
    val kind: ReviewKind,
    val scheduledAt: Instant? = null,
    val completedAt: Instant = Instant.now(),
    val result: ReviewResult = ReviewResult.UNKNOWN,
    val wasOverdue: Boolean = false,
    val note: String? = null
)

@Entity(tableName = "notification_batches", indices = [Index("triggerAt")])
data class NotificationBatchEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val triggerAt: Instant,
    val itemIdsCsv: String,
    val requestCode: Int,
    val scheduled: Boolean = false,
    val invalidatedAt: Instant? = null
)

class Converters {
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    @TypeConverter fun itemStatusToString(value: ItemStatus?): String? = value?.name
    @TypeConverter fun stringToItemStatus(value: String?): ItemStatus? = value?.let(ItemStatus::valueOf)
    @TypeConverter fun attachmentTypeToString(value: AttachmentType?): String? = value?.name
    @TypeConverter fun stringToAttachmentType(value: String?): AttachmentType? = value?.let(AttachmentType::valueOf)
    @TypeConverter fun reviewKindToString(value: ReviewKind?): String? = value?.name
    @TypeConverter fun stringToReviewKind(value: String?): ReviewKind? = value?.let(ReviewKind::valueOf)
    @TypeConverter fun reviewResultToString(value: ReviewResult?): String? = value?.name
    @TypeConverter fun stringToReviewResult(value: String?): ReviewResult? = value?.let(ReviewResult::valueOf)
    @TypeConverter fun scheduleStateToString(value: ScheduleState?): String? = value?.name
    @TypeConverter fun stringToScheduleState(value: String?): ScheduleState? = value?.let(ScheduleState::valueOf)
}
