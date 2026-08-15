package com.recallflow.app.data

import androidx.room.withTransaction
import com.recallflow.app.domain.ReviewPlanner
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

class RecallRepository(
    private val database: RecallDatabase,
    private val planner: ReviewPlanner = ReviewPlanner()
) {
    private val dao = database.recallDao()

    fun observeLibraryItems(): Flow<List<StudyItemEntity>> = dao.observeLibraryItems()
    fun observeItem(itemId: String): Flow<StudyItemEntity?> = dao.observeItem(itemId)
    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()
    fun observeTags(): Flow<List<TagEntity>> = dao.observeTags()
    fun observeAttachments(itemId: String): Flow<List<AttachmentEntity>> = dao.observeAttachments(itemId)
    fun observeItemTags(itemId: String): Flow<List<TagEntity>> = dao.observeTagsForItem(itemId)
    fun observeSchedules(itemId: String): Flow<List<ReviewScheduleEntity>> = dao.observeSchedulesForItem(itemId)
    fun observeEvents(itemId: String): Flow<List<ReviewEventEntity>> = dao.observeEventsForItem(itemId)

    suspend fun createItem(
        title: String,
        richContent: String,
        plainTextIndex: String,
        categoryId: String?,
        tagIds: List<String>,
        firstLearnedAt: Instant,
        timezoneId: String = ZoneId.systemDefault().id
    ): StudyItemEntity = database.withTransaction {
        val draft = StudyItemEntity(
            title = title.trim(),
            richContent = richContent,
            plainTextIndex = plainTextIndex,
            categoryId = categoryId,
            firstLearnedAt = firstLearnedAt,
            nextReviewAt = firstLearnedAt.plus(java.time.Duration.ofMinutes(10)),
            timezoneId = timezoneId
        )
        dao.upsertItem(draft)
        dao.replaceTags(draft.id, tagIds)
        dao.insertSchedule(planner.firstSchedule(draft.id, draft.activePlanBatchId, firstLearnedAt))
        draft
    }

    suspend fun updateItem(item: StudyItemEntity, tagIds: List<String>) = database.withTransaction {
        dao.updateItem(item.copy(title = item.title.trim(), plainTextIndex = item.plainTextIndex))
        dao.replaceTags(item.id, tagIds)
    }

    suspend fun addAttachment(attachment: AttachmentEntity) = dao.upsertAttachment(attachment)
    suspend fun removeAttachment(id: String) = dao.deleteAttachment(id)

    suspend fun completeCurrentReview(
        itemId: String,
        result: ReviewResult,
        completedAt: Instant = Instant.now(),
        note: String? = null
    ): StudyItemEntity? = database.withTransaction {
        val item = dao.itemById(itemId) ?: return@withTransaction null
        val schedule = dao.currentSchedule(itemId, completedAt) ?: return@withTransaction item
        val wasOverdue = planner.isOverdue(item, completedAt)
        val event = ReviewEventEntity(
            itemId = itemId,
            planBatchId = item.activePlanBatchId,
            round = schedule.round,
            kind = schedule.kind,
            scheduledAt = schedule.dueAt,
            completedAt = completedAt,
            result = result,
            wasOverdue = wasOverdue,
            note = note
        )
        val resolution = planner.resolveCompletion(item, schedule, result, completedAt)
        dao.insertReviewEvent(event)
        dao.markScheduleComplete(schedule.id)
        dao.updateItem(resolution.updatedItem)
        resolution.nextSchedule?.let { nextSchedule -> dao.insertSchedule(nextSchedule) }
        resolution.updatedItem
    }

    suspend fun archive(itemId: String, archived: Boolean) = database.withTransaction {
        val item = dao.itemById(itemId) ?: return@withTransaction
        if (archived) {
            dao.cancelPendingSchedules(itemId)
            dao.updateItem(item.copy(status = ItemStatus.ARCHIVED, archivedAt = Instant.now(), nextReviewAt = null))
        } else {
            val restart = planner.restart(item)
            dao.updateItem(restart.updatedItem.copy(archivedAt = null))
            dao.insertSchedule(restart.firstSchedule)
        }
    }

    suspend fun deleteItem(itemId: String) = database.withTransaction {
        val item = dao.itemById(itemId)
        if (item != null) dao.deleteItemAndRelations(item)
    }

    suspend fun restartPlan(itemId: String, startedAt: Instant = Instant.now()): StudyItemEntity? = database.withTransaction {
        val item = dao.itemById(itemId) ?: return@withTransaction null
        dao.cancelPendingSchedules(itemId)
        val restart = planner.restart(item, startedAt)
        dao.updateItem(restart.updatedItem)
        dao.insertSchedule(restart.firstSchedule)
        restart.updatedItem
    }

    suspend fun dueItems(now: Instant = Instant.now()): List<StudyItemEntity> =
        dao.dueItems(now).sortedWith(planner.priorityComparator(now))

    suspend fun allScheduledItems(): List<StudyItemEntity> = dao.allScheduledActiveItems()
    suspend fun currentItem(itemId: String): StudyItemEntity? = dao.itemById(itemId)
    suspend fun allCategories(): List<CategoryEntity> = dao.allCategories()
    suspend fun allTags(): List<TagEntity> = dao.allTags()
    suspend fun eventsBetween(start: Instant, end: Instant): List<ReviewEventEntity> = dao.eventsBetween(start, end)
}
