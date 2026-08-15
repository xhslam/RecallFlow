package com.recallflow.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.recallflow.app.RecallFlowApplication
import com.recallflow.app.data.AppSettings
import com.recallflow.app.data.CategoryEntity
import com.recallflow.app.data.ItemStatus
import com.recallflow.app.data.ReviewResult
import com.recallflow.app.data.StudyItemEntity
import com.recallflow.app.data.TagEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RecallViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as RecallFlowApplication
    private val repository = app.repository
    private val scheduler = app.reminderScheduler
    private val settingsStore = app.settingsStore

    val uiState = combine(
        repository.observeLibraryItems(),
        repository.observeCategories(),
        repository.observeTags(),
        settingsStore.settings
    ) { items, categories, tags, settings ->
        HomeUiState(
            allItems = items,
            categories = categories,
            tags = tags,
            settings = settings,
            now = Instant.now()
        ).recalculate()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun createItem(
        title: String,
        content: String,
        categoryId: String?,
        tagIds: List<String>,
        firstLearnedAt: Instant = Instant.now(),
        onSaved: (StudyItemEntity) -> Unit = {}
    ) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val item = repository.createItem(title, content, content, categoryId, tagIds, firstLearnedAt)
        scheduler.rescheduleAll()
        onSaved(item)
    }

    fun complete(itemId: String, result: ReviewResult, onCompleted: () -> Unit = {}) = viewModelScope.launch {
        repository.completeCurrentReview(itemId, result)
        scheduler.rescheduleAll()
        onCompleted()
    }

    fun archive(itemId: String, archived: Boolean) = viewModelScope.launch {
        repository.archive(itemId, archived)
        scheduler.rescheduleAll()
    }

    fun restart(itemId: String) = viewModelScope.launch {
        repository.restartPlan(itemId)
        scheduler.rescheduleAll()
    }

    fun delete(itemId: String) = viewModelScope.launch {
        repository.deleteItem(itemId)
        scheduler.rescheduleAll()
    }

    fun addAttachment(itemId: String, uri: Uri) = viewModelScope.launch {
        app.attachmentStore.importUri(itemId, uri)?.let { attachment -> repository.addAttachment(attachment) }
    }

    fun addLink(itemId: String, url: String, title: String) = viewModelScope.launch {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            repository.addAttachment(app.attachmentStore.addLink(itemId, url, title))
        }
    }

    fun removeAttachment(attachmentId: String) = viewModelScope.launch {
        repository.removeAttachment(attachmentId)
    }

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        settingsStore.updateNotifications(enabled)
        scheduler.rescheduleAll()
    }

    fun saveQuietHours(start: String?, end: String?) = viewModelScope.launch {
        settingsStore.updateQuietHours(start, end)
        scheduler.rescheduleAll()
    }

    fun markOnboardingDone() = viewModelScope.launch { settingsStore.markOnboardingDone() }

    fun newCategory(name: String, parentId: String? = null) = viewModelScope.launch {
        if (name.isNotBlank()) repository.run { databaseUnsafeCategory(name, parentId) }
    }

    private suspend fun databaseUnsafeCategory(name: String, parentId: String?) {
        app.database.recallDao().upsertCategory(CategoryEntity(name = name.trim(), parentId = parentId))
    }

    fun newTag(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) app.database.recallDao().upsertTag(TagEntity(name = name.trim()))
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = RecallViewModel(application) as T
        }
    }
}

data class HomeUiState(
    val allItems: List<StudyItemEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val now: Instant = Instant.now(),
    val dueItems: List<StudyItemEntity> = emptyList(),
    val overdueItems: List<StudyItemEntity> = emptyList(),
    val todayItems: List<StudyItemEntity> = emptyList(),
    val completedToday: Int = 0,
    val nextReviewAt: Instant? = null
) {
    fun recalculate(): HomeUiState {
        val zone = ZoneId.systemDefault()
        val startToday = LocalDateTime.now(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val endToday = LocalDateTime.now(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        val due = allItems.filter { it.status == ItemStatus.ACTIVE && it.nextReviewAt?.isAfter(now) == false }
            .sortedWith(compareBy<StudyItemEntity> { it.nextReviewAt })
        val overdue = due.filter { it.nextReviewAt!! < startToday }
        val today = due.filter { it.nextReviewAt!! >= startToday && it.nextReviewAt!! < endToday }
        val scheduledToday = allItems.count { it.status == ItemStatus.ACTIVE && it.nextReviewAt != null && it.nextReviewAt!! >= startToday && it.nextReviewAt!! < endToday }
        return copy(
            dueItems = overdue + today,
            overdueItems = overdue,
            todayItems = today,
            completedToday = (scheduledToday - today.size).coerceAtLeast(0),
            nextReviewAt = allItems.filter { it.status == ItemStatus.ACTIVE }.mapNotNull { it.nextReviewAt }.filter { it.isAfter(now) }.minOrNull()
        )
    }
}
