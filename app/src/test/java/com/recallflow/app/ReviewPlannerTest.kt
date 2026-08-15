package com.recallflow.app

import com.recallflow.app.data.ItemStatus
import com.recallflow.app.data.ReviewKind
import com.recallflow.app.data.ReviewResult
import com.recallflow.app.data.ReviewScheduleEntity
import com.recallflow.app.data.StudyItemEntity
import com.recallflow.app.domain.ReviewPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ReviewPlannerTest {
    private val planner = ReviewPlanner(ZoneOffset.UTC)
    private val base = Instant.parse("2026-08-14T08:00:00Z")

    @Test
    fun firstReviewIsTenMinutesAfterLearning() {
        val item = StudyItemEntity(title = "测试项目", firstLearnedAt = base)
        val schedule = planner.firstSchedule(item.id, item.activePlanBatchId, base)
        assertEquals(base.plusSeconds(600), schedule.dueAt)
        assertEquals(1, schedule.round)
    }

    @Test
    fun unfamiliarCreatesTenMinuteRemedialWithoutAdvancingRound() {
        val item = StudyItemEntity(title = "测试项目", currentRound = 2, nextReviewAt = base)
        val due = ReviewScheduleEntity(itemId = item.id, planBatchId = item.activePlanBatchId, round = 2, kind = ReviewKind.MAIN, dueAt = base)
        val resolution = planner.resolveCompletion(item, due, ReviewResult.UNFAMILIAR, base)
        assertEquals(2, resolution.updatedItem.currentRound)
        assertEquals(1, resolution.updatedItem.retryCount)
        assertEquals(ReviewKind.REMEDIAL, resolution.nextSchedule?.kind)
        assertEquals(base.plusSeconds(600), resolution.nextSchedule?.dueAt)
    }

    @Test
    fun fuzzyAdvancesUsingActualCompletionTime() {
        val item = StudyItemEntity(title = "测试项目", currentRound = 1, nextReviewAt = base)
        val due = ReviewScheduleEntity(itemId = item.id, planBatchId = item.activePlanBatchId, round = 1, kind = ReviewKind.MAIN, dueAt = base)
        val completedLate = base.plusSeconds(7_200)
        val resolution = planner.resolveCompletion(item, due, ReviewResult.FUZZY, completedLate)
        assertEquals(2, resolution.updatedItem.currentRound)
        assertEquals(completedLate.plusSeconds(86_400), resolution.updatedItem.nextReviewAt)
    }

    @Test
    fun seventhRoundBecomesReinforced() {
        val item = StudyItemEntity(title = "测试项目", currentRound = 7, nextReviewAt = base)
        val due = ReviewScheduleEntity(itemId = item.id, planBatchId = item.activePlanBatchId, round = 7, kind = ReviewKind.MAIN, dueAt = base)
        val resolution = planner.resolveCompletion(item, due, ReviewResult.MASTERED, base)
        assertEquals(ItemStatus.REINFORCED, resolution.updatedItem.status)
        assertNull(resolution.updatedItem.nextReviewAt)
        assertNull(resolution.nextSchedule)
    }
}
