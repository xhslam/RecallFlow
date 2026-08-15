package com.recallflow.app.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.recallflow.app.R
import com.recallflow.app.data.CategoryEntity
import com.recallflow.app.data.ItemStatus
import com.recallflow.app.data.ReviewResult
import com.recallflow.app.data.StudyItemEntity
import com.recallflow.app.data.TagEntity
import com.recallflow.app.notifications.ReminderContract
import com.recallflow.app.ui.theme.Brick
import com.recallflow.app.ui.theme.InkMuted
import com.recallflow.app.ui.theme.OutlineSoft
import com.recallflow.app.ui.theme.Sage
import com.recallflow.app.ui.theme.SurfaceWarm
import com.recallflow.app.ui.theme.WarmWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private object Route {
    const val TODAY = "today"
    const val LIBRARY = "library"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    fun review(id: String) = "review/$id"
    fun detail(id: String) = "detail/$id"
}

private val ButtonShape = RoundedCornerShape(12.dp)
private val InputShape = RoundedCornerShape(10.dp)
private val NoteShape = RoundedCornerShape(12.dp)

@Composable
fun RecallFlowApp(
    viewModel: RecallViewModel,
    initialDestination: String?,
    initialItemId: String?
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var route by remember {
        mutableStateOf(
            if (initialDestination == "review" && initialItemId != null) Route.review(initialItemId) else Route.TODAY
        )
    }
    var showCreate by remember { mutableStateOf(false) }
    val isReview = route.startsWith("review/")

    if (!state.settings.onboardingDone) {
        Onboarding(onFinish = viewModel::markOnboardingDone)
        return
    }

    Scaffold(
        containerColor = WarmWhite,
        bottomBar = {
            if (!isReview) RecallBottomBar(route = route, onNavigate = { route = it })
        }
    ) { padding ->
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(110)) },
            label = "页面切换"
        ) { current ->
            when {
                current == Route.TODAY -> TodayScreen(
                    state = state,
                    onStart = { state.dueItems.firstOrNull()?.let { route = Route.review(it.id) } },
                    onOpen = { route = Route.detail(it) },
                    onBrowse = { route = Route.LIBRARY },
                    modifier = Modifier.padding(padding)
                )
                current == Route.LIBRARY -> LibraryScreen(
                    state = state,
                    onOpen = { route = Route.detail(it) },
                    onCreate = { showCreate = true },
                    modifier = Modifier.padding(padding)
                )
                current == Route.STATS -> StatisticsScreen(state, modifier = Modifier.padding(padding))
                current == Route.SETTINGS -> SettingsScreen(
                    state = state,
                    onNotifications = viewModel::setNotifications,
                    onQuietHours = viewModel::saveQuietHours,
                    modifier = Modifier.padding(padding)
                )
                current.startsWith("review/") -> ReviewScreen(
                    state = state,
                    itemId = current.removePrefix("review/"),
                    onBack = { route = Route.TODAY },
                    onDone = { id, result ->
                        viewModel.complete(id, result) {
                            val next = state.dueItems.firstOrNull { it.id != id }
                            route = next?.let { Route.review(it.id) } ?: Route.TODAY
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
                current.startsWith("detail/") -> ItemDetailScreen(
                    state = state,
                    itemId = current.removePrefix("detail/"),
                    onBack = { route = Route.LIBRARY },
                    onReview = { route = Route.review(it) },
                    onRestart = viewModel::restart,
                    onArchive = { viewModel.archive(it, true); route = Route.LIBRARY },
                    onDelete = { viewModel.delete(it); route = Route.LIBRARY },
                    onAddAttachment = viewModel::addAttachment,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (showCreate) {
        CreateItemDialog(
            categories = state.categories,
            tags = state.tags,
            onDismiss = { showCreate = false },
            onSave = { title, content, categoryId, tagIds, first ->
                viewModel.createItem(title, content, categoryId, tagIds, first) { showCreate = false }
            }
        )
    }
}

@Composable
private fun RecallBottomBar(route: String, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = WarmWhite) {
        val entries = listOf(
            Route.TODAY to Triple("今日复习", Icons.Outlined.CalendarMonth, "今日复习"),
            Route.LIBRARY to Triple("内容库", Icons.Outlined.CollectionsBookmark, "内容库"),
            Route.STATS to Triple("统计", Icons.Outlined.Assessment, "统计"),
            Route.SETTINGS to Triple("设置", Icons.Outlined.Settings, "设置")
        )
        entries.forEach { (target, spec) ->
            NavigationBarItem(
                selected = route == target,
                onClick = { onNavigate(target) },
                icon = { Icon(spec.second, contentDescription = spec.third) },
                label = { Text(spec.first) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Sage,
                    selectedTextColor = Sage,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = InkMuted,
                    unselectedTextColor = InkMuted
                )
            )
        }
    }
}

@Composable
private fun TodayScreen(
    state: HomeUiState,
    onStart: () -> Unit,
    onOpen: (String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val total = state.dueItems.size + state.completedToday
    val progress = if (total == 0) 0f else state.completedToday.toFloat() / total
    val hasDue = state.dueItems.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        item {
            Text("今日复习", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasDue) "先完成到期项目，再安排新的学习内容。" else "今天没有待复习项目。",
                color = InkMuted,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(32.dp))
        }

        item {
            Text(if (hasDue) "待完成" else "今日状态", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (hasDue) "${state.dueItems.size} 项" else "已完成",
                style = MaterialTheme.typography.headlineSmall
            )
            if (hasDue) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Sage,
                    trackColor = SurfaceWarm
                )
                Spacer(Modifier.height(8.dp))
                Text("已完成 ${state.completedToday} 项，共 $total 项", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.nextReviewAt?.let { "下一次复习：${formatTime(it)}" } ?: "添加内容后会自动安排复习。",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        if (hasDue) {
            item {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape) {
                    Text(if (state.overdueItems.isNotEmpty()) "处理逾期复习" else "开始复习")
                }
                Spacer(Modifier.height(32.dp))
            }
        } else {
            item {
                TextButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) {
                    Text("去内容库添加项目")
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        if (state.overdueItems.isNotEmpty()) {
            item {
                SectionLabel("已逾期 · ${state.overdueItems.size} 项", Brick)
                Spacer(Modifier.height(8.dp))
            }
            items(state.overdueItems, key = { it.id }) { item ->
                StudyItemRow(item, state.categories, overdue = true, onClick = { onOpen(item.id) })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        if (state.todayItems.isNotEmpty()) {
            item {
                SectionLabel("今日到期 · ${state.todayItems.size} 项")
                Spacer(Modifier.height(8.dp))
            }
            items(state.todayItems, key = { it.id }) { item ->
                StudyItemRow(item, state.categories, overdue = false, onClick = { onOpen(item.id) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color = Sage) {
    Text(text, color = color, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun StudyItemRow(
    item: StudyItemEntity,
    categories: List<CategoryEntity>,
    overdue: Boolean,
    onClick: () -> Unit
) {
    val category = categories.firstOrNull { it.id == item.categoryId }?.name ?: "未分类"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${item.title}，$category，第 ${item.currentRound} 轮" }
            .padding(vertical = 16.dp)
    ) {
        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(category, color = Sage, style = MaterialTheme.typography.bodyMedium)
            Text("第 ${item.currentRound} / 7 轮", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (overdue) "已逾期 · 原定 ${formatTime(item.nextReviewAt)}" else "今天到期 · ${formatTime(item.nextReviewAt)}",
            color = if (overdue) Brick else InkMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = OutlineSoft)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewScreen(
    state: HomeUiState,
    itemId: String,
    onBack: () -> Unit,
    onDone: (String, ReviewResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.allItems.firstOrNull { it.id == itemId }
    if (item == null) {
        EmptyRoute("该项目已不存在", onBack, modifier)
        return
    }
    Scaffold(
        modifier = modifier,
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("复习") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回今日复习")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text("第 ${item.currentRound} / 7 轮", color = Sage, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SurfaceWarm, NoteShape)
                    .padding(20.dp)
            ) {
                LazyColumn {
                    item {
                        Text(
                            if (item.richContent.isBlank()) "尚未添加笔记。请先回忆这个知识点，再选择掌握程度。" else item.richContent,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("附件可在项目详情中查看。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("回忆后选择当前掌握程度", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDone(item.id, ReviewResult.UNFAMILIAR) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = ButtonShape
                ) { Text("陌生") }
                OutlinedButton(
                    onClick = { onDone(item.id, ReviewResult.FUZZY) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = ButtonShape
                ) { Text("模糊") }
                Button(
                    onClick = { onDone(item.id, ReviewResult.MASTERED) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = ButtonShape
                ) { Text("掌握") }
            }
            Spacer(Modifier.height(8.dp))
            Text("陌生将在 10 分钟后加练；其余结果会进入下一节点。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreen(
    state: HomeUiState,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var onlyActive by remember { mutableStateOf(true) }
    val filtered = state.allItems.filter { item ->
        (!onlyActive || item.status == ItemStatus.ACTIVE) &&
            (query.isBlank() || item.title.contains(query, true) || item.plainTextIndex.contains(query, true))
    }

    Scaffold(
        modifier = modifier,
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("内容库") },
                actions = { if (state.allItems.isNotEmpty()) TextButton(onClick = onCreate) { Text("新增") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            if (state.allItems.isEmpty()) {
                item {
                    Spacer(Modifier.height(32.dp))
                    Text("还没有复习项目", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("添加知识点或笔记后，系统会自动安排七轮复习。", color = InkMuted, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape) {
                        Text("添加第一个项目")
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索标题或笔记") },
                        singleLine = true,
                        shape = InputShape
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = onlyActive, onClick = { onlyActive = true }, label = { Text("进行中") })
                        FilterChip(selected = !onlyActive, onClick = { onlyActive = false }, label = { Text("全部") })
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("${filtered.size} 项内容", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
                if (filtered.isEmpty()) {
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text("没有匹配的项目", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("修改关键词或切换筛选条件后再试。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    items(filtered, key = { it.id }) { item ->
                        StudyItemRow(
                            item = item,
                            categories = state.categories,
                            overdue = item.nextReviewAt?.isBefore(Instant.now()) == true,
                            onClick = { onOpen(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailScreen(
    state: HomeUiState,
    itemId: String,
    onBack: () -> Unit,
    onReview: (String) -> Unit,
    onRestart: (String) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddAttachment: (String, android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val item = state.allItems.firstOrNull { it.id == itemId }
    if (item == null) {
        EmptyRoute("该项目已不存在", onBack, modifier)
        return
    }
    var showDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val app = context.applicationContext as com.recallflow.app.RecallFlowApplication
    val attachments by app.repository.observeAttachments(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val events by app.repository.observeEvents(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val schedules by app.repository.observeSchedules(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAddAttachment(item.id, uri)
    }

    Scaffold(
        modifier = modifier,
        containerColor = WarmWhite,
        topBar = {
            TopAppBar(
                title = { Text("项目详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = "返回内容库") }
                },
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除项目", tint = Brick)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text("第 ${item.currentRound} / 7 轮 · ${statusText(item)}", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(32.dp))
            }
            item {
                DetailSection("笔记") {
                    Text(if (item.richContent.isBlank()) "未添加笔记" else item.richContent, style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                DetailSection("资料附件") {
                    OutlinedButton(
                        onClick = { attachmentPicker.launch(arrayOf("image/*", "application/pdf", "application/*")) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = ButtonShape
                    ) { Text("添加图片、PDF 或文件") }
                    Spacer(Modifier.height(12.dp))
                    if (attachments.isEmpty()) {
                        Text("暂未添加附件", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        attachments.forEach { attachment ->
                            Text(attachment.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                            HorizontalDivider(color = OutlineSoft)
                        }
                    }
                }
            }
            item {
                DetailSection("未来计划") {
                    Text(
                        item.nextReviewAt?.let { "下一次复习：${formatTime(it)}" } ?: "自动复习计划已完成",
                        color = InkMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    schedules.filter { it.state == com.recallflow.app.data.ScheduleState.PENDING }.take(7).forEach { schedule ->
                        Text("第 ${schedule.round} 轮 · ${formatTime(schedule.dueAt)}", color = InkMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
            item {
                DetailSection("完整历史") {
                    if (events.isEmpty()) {
                        Text("尚无完成记录", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        events.take(12).forEach { event ->
                            Text(
                                "第 ${event.round} 轮 · ${reviewResultText(event.result)} · ${formatTime(event.completedAt)}${if (event.wasOverdue) " · 逾期" else ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onReview(item.id) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape) {
                    Text("开始复习")
                }
                if (item.status == ItemStatus.REINFORCED) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onRestart(item.id) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = ButtonShape) {
                        Text("重新开始七轮计划")
                    }
                }
                if (item.status != ItemStatus.ARCHIVED) {
                    TextButton(onClick = { onArchive(item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("归档此项目")
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除项目？") },
            text = { Text("删除后将一并清除该项目的复习历史和本地附件。") },
            confirmButton = { TextButton(onClick = { onDelete(item.id); showDelete = false }) { Text("删除", color = Brick) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        content()
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = OutlineSoft)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsScreen(state: HomeUiState, modifier: Modifier = Modifier) {
    val reinforced = state.allItems.count { it.status == ItemStatus.REINFORCED }
    val overdue = state.allItems.count { it.status == ItemStatus.ACTIVE && it.nextReviewAt?.isBefore(Instant.now()) == true }
    val active = state.allItems.count { it.status == ItemStatus.ACTIVE }

    Scaffold(
        modifier = modifier,
        containerColor = WarmWhite,
        topBar = { TopAppBar(title = { Text("统计") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
                Text("当前概览", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                MetricGrid(
                    due = state.dueItems.size,
                    overdue = overdue,
                    reinforced = reinforced,
                    streak = if (state.completedToday > 0) "1 天" else "0 天"
                )
                Spacer(Modifier.height(32.dp))
            }
            item {
                Text("今日记录", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.completedToday > 0) "今天已完成 ${state.completedToday} 项复习。" else "完成第一次复习后，这里会显示当天记录。",
                    color = InkMuted,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = OutlineSoft)
                Spacer(Modifier.height(24.dp))
            }
            item {
                Text("内容概览", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                StatisticRow("进行中项目", "$active 项")
                StatisticRow("已巩固项目", "$reinforced 项")
                StatisticRow("分类数量", "${state.categories.size} 个", showDivider = false)
            }
        }
    }
}

@Composable
private fun MetricGrid(due: Int, overdue: Int, reinforced: Int, streak: String) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            MetricBlock("待处理", "$due", Modifier.weight(1f))
            MetricBlock("逾期项目", "$overdue", Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = OutlineSoft)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            MetricBlock("已巩固", "$reinforced", Modifier.weight(1f))
            MetricBlock("连续复习", streak, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = InkMuted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun StatisticRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = InkMuted, style = MaterialTheme.typography.bodyLarge)
    }
    if (showDivider) HorizontalDivider(color = OutlineSoft)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: HomeUiState,
    onNotifications: (Boolean) -> Unit,
    onQuietHours: (String?, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var start by remember(state.settings.quietStart) { mutableStateOf(state.settings.quietStart ?: "") }
    var end by remember(state.settings.quietEnd) { mutableStateOf(state.settings.quietEnd ?: "") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        modifier = modifier,
        containerColor = WarmWhite,
        topBar = { TopAppBar(title = { Text("设置") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = WarmWhite)) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
            item {
                SettingsSection("提醒") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("到期本地提醒", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text("相近到期任务会合并提醒。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(checked = state.settings.notificationsEnabled, onCheckedChange = onNotifications)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("检查通知权限") }
                    OutlinedButton(onClick = { sendTestNotification(context) }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = ButtonShape) {
                        Text("发送测试提醒")
                    }
                }
            }
            item {
                SettingsSection("每日免打扰") {
                    Text("这段时间内到期的任务会在结束后合并提醒。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(start, { start = it }, Modifier.weight(1f), label = { Text("开始") }, singleLine = true, shape = InputShape)
                        OutlinedTextField(end, { end = it }, Modifier.weight(1f), label = { Text("结束") }, singleLine = true, shape = InputShape)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onQuietHours(start.ifBlank { null }, end.ifBlank { null }) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = ButtonShape) {
                        Text("保存免打扰时段")
                    }
                }
            }
            item {
                SettingsSection("复习节点") {
                    Text("10 分钟、1 天、2 天、4 天、7 天、15 天、30 天。", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("为保持计划一致性，当前版本仅供查看。", color = InkMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SettingsSection("数据与隐私", showDivider = false) {
                    Text("学习记录和附件仅保存在本机。", color = InkMuted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, showDivider: Boolean = true, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        content()
        if (showDivider) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreateItemDialog(
    categories: List<CategoryEntity>,
    tags: List<TagEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, List<String>, Instant) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var firstLearnedAt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增复习内容") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题") }, singleLine = true, shape = InputShape) }
                item { RichTextEditor(content, { content = it }) }
                item {
                    Text("主分类", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("未分类") })
                        categories.take(4).forEach { category ->
                            FilterChip(selected = selectedCategory == category.id, onClick = { selectedCategory = category.id }, label = { Text(category.name) })
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    item {
                        Text("标签", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            tags.take(5).forEach { tag ->
                                FilterChip(
                                    selected = selectedTags.contains(tag.id),
                                    onClick = { selectedTags = if (selectedTags.contains(tag.id)) selectedTags - tag.id else selectedTags + tag.id },
                                    label = { Text(tag.name) }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        firstLearnedAt,
                        { firstLearnedAt = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("首次学习时间（可选 ISO）") },
                        supportingText = { Text("留空即使用现在；填写过去时间会立即生成逾期任务。") },
                        shape = InputShape
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val instant = runCatching { Instant.parse(firstLearnedAt) }.getOrNull() ?: Instant.now()
                    onSave(title, content, selectedCategory, selectedTags.toList(), instant)
                },
                enabled = title.isNotBlank(),
                shape = ButtonShape
            ) { Text("保存并安排复习") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RichTextEditor(value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("笔记", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("粗体" to "**", "标题" to "# ", "列表" to "- ", "引用" to "> ", "待办" to "- [ ] ", "公式" to "$$").forEach { (label, marker) ->
                FilterChip(
                    selected = false,
                    onClick = { onValueChange(value + if (value.isBlank()) marker else "\n$marker") },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        OutlinedTextField(
            value,
            onValueChange,
            Modifier.fillMaxWidth().height(150.dp),
            label = { Text("支持加粗、标题、列表、引用、待办与公式") },
            shape = InputShape
        )
    }
}

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val pages = listOf(
        "录入学习内容" to "保存标题、笔记、分类和资料，为每个知识点建立独立复习计划。",
        "按节点自动复习" to "系统会按 10 分钟、1 天、2 天、4 天、7 天、15 天和 30 天安排任务。",
        "在合适时刻提醒" to "开启本地提醒后，到期内容会进入今日复习，不需要账号或网络。"
    )
    Box(Modifier.fillMaxSize().background(WarmWhite).padding(28.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("RecallFlow", style = MaterialTheme.typography.displaySmall)
            Text("${step + 1} / ${pages.size}", color = InkMuted, style = MaterialTheme.typography.labelLarge)
            Text(pages[step].first, style = MaterialTheme.typography.headlineSmall)
            Text(pages[step].second, color = InkMuted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { if (step == pages.lastIndex) onFinish() else step++ }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = ButtonShape) {
                Text(if (step == pages.lastIndex) "开始使用" else "继续")
            }
            TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("跳过引导") }
        }
    }
}

@Composable
private fun EmptyRoute(message: String, onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onBack, shape = ButtonShape) { Text("返回") }
    }
}

private fun statusText(item: StudyItemEntity): String = when (item.status) {
    ItemStatus.ACTIVE -> "进行中"
    ItemStatus.REINFORCED -> "已巩固"
    ItemStatus.ARCHIVED -> "已归档"
}

private fun reviewResultText(result: ReviewResult): String = when (result) {
    ReviewResult.UNFAMILIAR -> "陌生"
    ReviewResult.FUZZY -> "模糊"
    ReviewResult.MASTERED -> "掌握"
    ReviewResult.UNKNOWN -> "手动记录"
}

private fun formatTime(instant: Instant?): String = instant?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")) ?: "未安排"

private fun sendTestNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(ReminderContract.CHANNEL_ID, ReminderContract.CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        )
    }
    val notification = NotificationCompat.Builder(context, ReminderContract.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_recallflow)
        .setContentTitle("RecallFlow 测试提醒")
        .setContentText("这是一条本地提醒测试通知。")
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(991_001, notification)
}
