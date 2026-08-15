# RecallFlow 原生 Android 架构说明

RecallFlow 采用**单模块、分层、离线优先**的 Kotlin Android 架构。UI 使用 Jetpack Compose 与单一状态容器，所有可变数据经由 Room 数据库、DataStore 设置和应用私有附件目录持久化；应用不包含网络层、账号层或后端依赖。

| 层级 | 主要组件 | 职责 |
|---|---|---|
| `ui` | Compose 页面、导航、ViewModel、主题、可访问组件 | 呈现状态、接收用户操作、请求权限与文件选择。 |
| `domain` | `ReviewPlanner`、统计计算、排序规则 | 固定七轮算法、陌生加练、逾期优先级和指标口径。 |
| `data` | Room 实体/DAO、`RecallRepository`、DataStore | 保持项目、分类、标签、历史、附件元数据与设置的一致性。 |
| `notifications` | Alarm 调度器、通知构建器、广播接收器 | 合并 15 分钟内任务、免打扰判断、取消与重排提醒、点击深链。 |
| `importexport` | ZIP/JSON/CSV 归档服务 | 本地完整备份、合并恢复、文本交换与预检。 |
| `platform` | 文件仓库、权限、时区/重启接收器 | 应用私有附件、系统权限、设备重启及时间变化后的重排。 |

## 数据一致性

项目完成操作使用单一 Room 事务：写入 `ReviewEvent`、更新 `StudyItem`、关闭当前 `ReviewSchedule`、创建下一条主轮或加练日程。事务成功后才调用通知重排器。通知批次在数据库中保存请求码和关联项目 ID，以便对计划编辑、删除、重启和权限变化进行幂等取消与重新排程。

## 固定节点

主轮以**实际完成时刻**为起点依次产生 1 天、2 天、4 天、7 天、15 天和 30 天后的任务；首次任务为首次学习时间后 10 分钟。选择“陌生”不推进主轮次，而创建一次 10 分钟后加练。第 7 轮成功完成后项目变为 `REINFORCED`，后续自动提醒停止。

## 通知策略

系统通知为本地通知。通知调度器会读取未完成任务，排除免打扰时段，然后按 15 分钟窗口合并为一个 `NotificationBatch`。每次计划或设置变更均先取消既有批次再写入新批次。设备启动、时区/系统时间变化及通知权限/精确定时访问变化后均进入相同的重排入口。若系统未允许精确定时，则退化为尽可能接近的非精确唤醒闹钟，数据库中的到期时刻仍是唯一业务事实来源。

## 依赖选择

| 需求 | 选型 |
|---|---|
| 原生 UI | Kotlin、Jetpack Compose、Material 3 |
| 数据库 | Room + KSP |
| 设置 | DataStore Preferences |
| 本地提醒 | AlarmManager、BroadcastReceiver、NotificationCompat |
| 图片 | Coil Compose；文件通过应用私有目录保存 |
| 附件预览 | 图片 Compose 预览、PDF `PdfRenderer`、链接 `WebView`、其他格式经系统兼容查看器兜底 |
| 备份 | Kotlin 序列化 JSON、`ZipOutputStream` / `ZipInputStream`、CSV 文本处理 |

应用最低 API 为 29，目标与编译 API 为 35。界面通过自有向量资源统一显示线性图标；不会使用 Emoji 作为功能图标或状态标识。

## 工程结构

```text
app/src/main/java/com/recallflow/app/
├── data/             # Room、DAO、仓库与备份数据 DTO
├── domain/           # 复习计划、统计、排序等纯业务规则
├── notifications/    # 闹钟、通知、启动/时区广播接收器
├── platform/         # 附件文件与权限辅助组件
├── ui/               # Compose 页面、状态、主题、导航
└── MainActivity.kt
```

该结构将核心算法与 Android 组件隔离，使七轮算法、合并规则、导入校验和统计口径可用本地单元测试验证。
