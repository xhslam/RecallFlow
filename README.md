# RecallFlow

RecallFlow 是一款面向学生备考者的**本地优先 Android 复习提醒应用**。它将学习项目、固定七轮复习计划、本地到期通知和复习历史保存在设备本地，无需账号、后端或网络服务。

## 已实现能力

| 模块 | 实现内容 |
|---|---|
| 今日复习 | 默认首页展示今日待办、逾期任务、完成进度、下一次复习时间，并提供连续“开始复习”入口。 |
| 内容管理 | 项目保存标题、笔记、主分类、标签、创建时间、首次学习时间和状态；支持归档、删除、搜索和分类/标签数据结构。 |
| 复习算法 | 固定节点为学习后 10 分钟、1 天、2 天、4 天、7 天、15 天、30 天；陌生会安排 10 分钟加练，模糊/掌握推进轮次，第七轮结束后标记已巩固。 |
| 离线数据 | Room 数据库存储项目、分类、标签、附件元数据、日程、历史事件与通知批次；DataStore 保存提醒和免打扰偏好。 |
| 本地提醒 | 支持 Android 13 通知权限、免打扰窗口、15 分钟任务合并、精确定时可用时的精确闹钟降级、设备启动/时区/系统时间变化后的重排。 |
| 复习界面 | 提供陌生、模糊、掌握三个大按钮；使用暖白、苔绿、浅灰的低饱和度卡片风格，无 Emoji 图标。 |
| 统计界面 | 提供今日/本周/本月切换、待处理/逾期/已巩固/连续复习摘要和趋势呈现框架。 |
| 首次引导与设置 | 提供三页本地功能引导、通知权限测试、测试提醒、免打扰时段和固定节点说明。 |

## 工程要求

| 项目 | 值 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose / Material 3 |
| 最低 Android 版本 | Android 10（API 29） |
| 编译 / 目标 API | API 35 |
| 包名 | `com.recallflow.app` |
| JDK | 17 |
| 推荐 Gradle | 8.9 |

## 使用 Android Studio 构建

1. 在 Android Studio 中选择 **Open**，打开 `RecallFlow` 工程目录。
2. 使用 JDK 17，并确保 Android SDK 中已安装 Android 35 平台与对应 Build Tools。
3. 首次同步时，Android Studio 会解析项目依赖；若未自动识别 SDK，请在 `local.properties` 添加：

   ```properties
   sdk.dir=C\:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
   ```

4. 选择模拟器或 Android 10 及以上真机，执行 **Run**；或使用本机 Gradle 8.9：

   ```powershell
   gradle -p . :app:assembleDebug
   ```

5. 调试 APK 输出位置为：

   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

## 验证记录

在用户本机的 Android 环境中已完成以下验证：

| 验证项 | 结果 |
|---|---|
| `:app:kspDebugKotlin` | 通过 |
| `:app:assembleDebug` | 通过 |
| `:app:testDebugUnitTest` | 通过 |

单元测试覆盖固定首轮 10 分钟、陌生加练不推进轮次、按实际完成时间推进，以及第七轮完成后巩固四项关键规则。

## 项目文件

| 文件 | 作用 |
|---|---|
| `PRD.md` | 已确认产品需求与验收标准。 |
| `ARCHITECTURE.md` | 分层架构、数据一致性、提醒与存储策略。 |
| `app/src/main/java/` | Kotlin 源码。 |
| `app/src/test/` | 复习算法单元测试。 |

> 应用数据保留在设备本地。通知授权、精确定时访问、通知渠道开关均受用户和 Android 系统设置控制；即使提醒受限，Room 中的到期计划仍是业务状态的唯一来源。
