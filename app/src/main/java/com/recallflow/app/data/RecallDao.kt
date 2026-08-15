package com.recallflow.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RecallDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: StudyItemEntity)

    @Update suspend fun updateItem(item: StudyItemEntity)
    @Delete suspend fun deleteItem(item: StudyItemEntity)

    @Query("SELECT * FROM study_items WHERE id = :id LIMIT 1")
    suspend fun itemById(id: String): StudyItemEntity?
    @Query("SELECT * FROM study_items ORDER BY createdAt ASC")
    suspend fun allItems(): List<StudyItemEntity>

    @Query("SELECT * FROM study_items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<StudyItemEntity?>

    @Query("SELECT * FROM study_items WHERE status != 'ARCHIVED' ORDER BY CASE WHEN nextReviewAt IS NULL THEN 1 ELSE 0 END, nextReviewAt ASC, createdAt DESC")
    fun observeLibraryItems(): Flow<List<StudyItemEntity>>

    @Query("SELECT * FROM study_items WHERE status = 'ACTIVE' AND nextReviewAt IS NOT NULL AND nextReviewAt <= :instant ORDER BY nextReviewAt ASC, createdAt ASC")
    fun observeDueItems(instant: Instant): Flow<List<StudyItemEntity>>

    @Query("SELECT * FROM study_items WHERE status = 'ACTIVE' AND nextReviewAt IS NOT NULL AND nextReviewAt <= :instant ORDER BY nextReviewAt ASC, createdAt ASC")
    suspend fun dueItems(instant: Instant): List<StudyItemEntity>

    @Query("SELECT * FROM study_items WHERE status = 'ACTIVE' AND nextReviewAt IS NOT NULL ORDER BY nextReviewAt ASC")
    suspend fun allScheduledActiveItems(): List<StudyItemEntity>

    @Query("SELECT * FROM study_items WHERE status = 'ACTIVE' AND nextReviewAt IS NOT NULL AND nextReviewAt > :start AND nextReviewAt <= :end ORDER BY nextReviewAt ASC")
    suspend fun activeItemsDueBetween(start: Instant, end: Instant): List<StudyItemEntity>

    @Query("SELECT COUNT(*) FROM study_items WHERE status = 'ACTIVE' AND nextReviewAt IS NOT NULL AND nextReviewAt <= :instant")
    fun observeDueCount(instant: Instant): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT * FROM categories") suspend fun allCategories(): List<CategoryEntity>
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCategory(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTag(tag: TagEntity)
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeTags(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags") suspend fun allTags(): List<TagEntity>
    @Query("DELETE FROM tags WHERE id = :id") suspend fun deleteTag(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceItemTags(refs: List<StudyItemTagCrossRef>)
    @Query("DELETE FROM study_item_tags WHERE itemId = :itemId") suspend fun clearItemTags(itemId: String)
    @Query("SELECT tagId FROM study_item_tags WHERE itemId = :itemId") suspend fun tagIdsForItem(itemId: String): List<String>
    @Query("SELECT t.* FROM tags t INNER JOIN study_item_tags r ON t.id = r.tagId WHERE r.itemId = :itemId ORDER BY t.name COLLATE NOCASE")
    fun observeTagsForItem(itemId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachment(attachment: AttachmentEntity)
    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY createdAt ASC")
    fun observeAttachments(itemId: String): Flow<List<AttachmentEntity>>
    @Query("SELECT * FROM attachments WHERE itemId = :itemId") suspend fun attachmentsForItem(itemId: String): List<AttachmentEntity>
    @Query("DELETE FROM attachments WHERE id = :id") suspend fun deleteAttachment(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ReviewScheduleEntity)
    @Update suspend fun updateSchedule(schedule: ReviewScheduleEntity)
    @Query("SELECT * FROM review_schedules WHERE itemId = :itemId AND state = 'PENDING' ORDER BY dueAt ASC")
    suspend fun pendingSchedulesForItem(itemId: String): List<ReviewScheduleEntity>
    @Query("SELECT * FROM review_schedules WHERE itemId = :itemId ORDER BY dueAt ASC")
    fun observeSchedulesForItem(itemId: String): Flow<List<ReviewScheduleEntity>>
    @Query("UPDATE review_schedules SET state = 'CANCELLED' WHERE itemId = :itemId AND state = 'PENDING'")
    suspend fun cancelPendingSchedules(itemId: String)
    @Query("UPDATE review_schedules SET state = 'COMPLETED' WHERE id = :id")
    suspend fun markScheduleComplete(id: String)
    @Query("SELECT * FROM review_schedules WHERE itemId = :itemId AND state = 'PENDING' AND dueAt <= :now ORDER BY dueAt ASC LIMIT 1")
    suspend fun currentSchedule(itemId: String, now: Instant): ReviewScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewEvent(event: ReviewEventEntity)
    @Query("SELECT * FROM review_events WHERE itemId = :itemId ORDER BY completedAt DESC")
    fun observeEventsForItem(itemId: String): Flow<List<ReviewEventEntity>>
    @Query("SELECT * FROM review_events WHERE completedAt >= :start AND completedAt < :end ORDER BY completedAt ASC")
    suspend fun eventsBetween(start: Instant, end: Instant): List<ReviewEventEntity>
    @Query("SELECT * FROM review_events ORDER BY completedAt ASC")
    suspend fun allEvents(): List<ReviewEventEntity>
    @Query("SELECT * FROM review_schedules ORDER BY dueAt ASC")
    suspend fun allSchedules(): List<ReviewScheduleEntity>
    @Query("SELECT * FROM attachments ORDER BY createdAt ASC")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotificationBatch(batch: NotificationBatchEntity)
    @Query("SELECT * FROM notification_batches WHERE invalidatedAt IS NULL")
    suspend fun activeNotificationBatches(): List<NotificationBatchEntity>
    @Query("SELECT * FROM notification_batches WHERE id = :id LIMIT 1")
    suspend fun notificationBatchById(id: String): NotificationBatchEntity?
    @Query("DELETE FROM notification_batches") suspend fun clearNotificationBatches()

    @Transaction
    suspend fun replaceTags(itemId: String, tagIds: List<String>) {
        clearItemTags(itemId)
        if (tagIds.isNotEmpty()) replaceItemTags(tagIds.distinct().map { StudyItemTagCrossRef(itemId, it) })
    }

    @Transaction
    suspend fun deleteItemAndRelations(item: StudyItemEntity) {
        deleteItem(item)
    }
}
