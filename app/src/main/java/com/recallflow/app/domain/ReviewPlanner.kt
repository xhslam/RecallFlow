package com.recallflow.app.domain

import com.recallflow.app.data.ItemStatus
import com.recallflow.app.data.ReviewKind
import com.recallflow.app.data.ReviewResult
import com.recallflow.app.data.ReviewScheduleEntity
import com.recallflow.app.data.ScheduleState
import com.recallflow.app.data.StudyItemEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** 固定的 RecallFlow 七轮主复习间隔。 */
object ReviewIntervals {
    val firstReview: Duration = Duration.ofMinutes(10)
    private val afterRound = mapOf(
        1 to Duration.ofDays(1),
        2 to Duration.ofDays(2),
        3 to Duration.ofDays(4),
        4 to Duration.ofDays(7),
        5 to Duration.ofDays(15),
        6 to Duration.ofDays(30)
    )

    fun nextIntervalAfter(round: Int): Duration? = afterRound[round]
}

class ReviewPlanner(private val zoneId: ZoneId = ZoneId.systemDefault()) {
    fun firstSchedule(itemId: String, planBatchId: String, firstLearnedAt: Instant): ReviewScheduleEntity =
        ReviewScheduleEntity(
            itemId = itemId,
            planBatchId = planBatchId,
            round = 1,
            kind = ReviewKind.MAIN,
            dueAt = firstLearnedAt.plus(ReviewIntervals.firstReview)
        )

    fun isOverdue(item: StudyItemEntity, now: Instant): Boolean {
        val next = item.nextReviewAt ?: return false
        val startOfToday = LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        return next < startOfToday && next <= now
    }

    fun priorityComparator(now: Instant): Comparator<StudyItemEntity> = Comparator { a, b ->
        val aOverdue = isOverdue(a, now)
        val bOverdue = isOverdue(b, now)
        when {
            aOverdue && !bOverdue -> -1
            !aOverdue && bOverdue -> 1
            else -> {
                val byDue = compareValues(a.nextReviewAt, b.nextReviewAt)
                if (byDue != 0) byDue else a.createdAt.compareTo(b.createdAt)
            }
        }
    }

    fun resolveCompletion(
        item: StudyItemEntity,
        currentSchedule: ReviewScheduleEntity,
        result: ReviewResult,
        completedAt: Instant = Instant.now()
    ): CompletionResolution {
        // UNKNOWN is reserved for neutral/manual history events. The review UI only emits FUZZY or MASTERED to advance.
        val actualAdvance = result == ReviewResult.FUZZY || result == ReviewResult.MASTERED
        return when {
            !actualAdvance -> {
                val remedial = ReviewScheduleEntity(
                    itemId = item.id,
                    planBatchId = item.activePlanBatchId,
                    round = currentSchedule.round,
                    kind = ReviewKind.REMEDIAL,
                    dueAt = completedAt.plus(ReviewIntervals.firstReview),
                    sourceEventId = currentSchedule.id
                )
                CompletionResolution(
                    updatedItem = item.copy(
                        lastReviewedAt = completedAt,
                        nextReviewAt = remedial.dueAt,
                        retryCount = item.retryCount + 1
                    ),
                    nextSchedule = remedial,
                    completedState = ScheduleState.COMPLETED
                )
            }
            currentSchedule.round >= 7 -> {
                CompletionResolution(
                    updatedItem = item.copy(
                        status = ItemStatus.REINFORCED,
                        lastReviewedAt = completedAt,
                        nextReviewAt = null,
                        currentRound = 7
                    ),
                    nextSchedule = null,
                    completedState = ScheduleState.COMPLETED
                )
            }
            else -> {
                val nextRound = currentSchedule.round + 1
                val interval = ReviewIntervals.nextIntervalAfter(currentSchedule.round)
                    ?: Duration.ZERO
                val next = ReviewScheduleEntity(
                    itemId = item.id,
                    planBatchId = item.activePlanBatchId,
                    round = nextRound,
                    kind = ReviewKind.MAIN,
                    dueAt = completedAt.plus(interval),
                    sourceEventId = currentSchedule.id
                )
                CompletionResolution(
                    updatedItem = item.copy(
                        status = ItemStatus.ACTIVE,
                        currentRound = nextRound,
                        lastReviewedAt = completedAt,
                        nextReviewAt = next.dueAt,
                        retryCount = if (currentSchedule.kind == ReviewKind.REMEDIAL) 0 else item.retryCount
                    ),
                    nextSchedule = next,
                    completedState = ScheduleState.COMPLETED
                )
            }
        }
    }

    fun restart(item: StudyItemEntity, startedAt: Instant = Instant.now()): RestartResolution {
        val batch = UUID.randomUUID().toString()
        val next = firstSchedule(item.id, batch, startedAt)
        return RestartResolution(
            updatedItem = item.copy(
                status = ItemStatus.ACTIVE,
                currentRound = 1,
                firstLearnedAt = startedAt,
                lastReviewedAt = null,
                nextReviewAt = next.dueAt,
                retryCount = 0,
                activePlanBatchId = batch
            ),
            firstSchedule = next
        )
    }
}

data class CompletionResolution(
    val updatedItem: StudyItemEntity,
    val nextSchedule: ReviewScheduleEntity?,
    val completedState: ScheduleState
)

data class RestartResolution(
    val updatedItem: StudyItemEntity,
    val firstSchedule: ReviewScheduleEntity
)
