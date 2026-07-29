# android-app

Android 独立工程，使用 Kotlin、Jetpack Compose、AccessibilityService 和 Room。

当前开发里程碑已经包含：

- 大麦三种运行模式的任务配置页面。
- 固定目标场次、票档、价格、数量和安全上限校验。
- `ticket_task`、`run_log` Room 数据库及首版 schema。
- 仅订阅大麦包名的无障碍服务和稳定窗口页面节点快照。
- 带常驻状态和停止入口的前台运行服务。
- 通用运行状态机、动作去重/节流和安全上限。
- 左上角无障碍悬浮日志窗口，以及暂停/开始和停止控制窗口。
- 纯函数式 `PlatformAdapter` 契约、统一页面/动作模型和严格适配器注册表。
- 大麦 Android 页面识别、首次开售、提交弹窗、回流和收银台决策适配器。
- 绑定 `runId`、快照序号和 `actionId` 的受控节点执行器。
- 配置校验、快照调度、运行令牌、状态机、悬浮层和平台适配器契约测试。

无障碍服务采集页面快照，并且只执行经过两层校验的节点动作。页面事件会等待
180ms 稳定窗口；持续变化时最迟 800ms 生成一次快照。快照绑定本次 `runId`，
并限制为每个窗口最多 2,000 个节点、最大 50 层。执行器只使用
`AccessibilityNodeInfo.ACTION_CLICK` 和业务明确要求的全局返回，不使用坐标点击。

前台服务仅由配置页的用户操作启动，使用 `START_NOT_STICKY`，进程重启后不会
自动恢复历史任务。悬浮层使用 `TYPE_ACCESSIBILITY_OVERLAY` 拆成两个窗口：
最近四行日志窗口不接收触摸，控制窗口只覆盖两个 48dp 高的按钮。暂停会取消
待执行动作，恢复会请求新的页面快照，停止会清理当前运行令牌和窗口。

运行引擎当前已经提供：

- `WAIT_TARGET_APP`、`RUNNING`、用户暂停、安全暂停、订单锁定和停止转换。
- 暂停后取消 in-flight 动作；恢复后必须读取更高序号的新快照。
- 每个动作绑定 `runId` 和单调递增 `actionId`，过期回调会被拒绝。
- 相同页面指纹和动作的冷却、页面变化等待及有限重试。
- 最大运行时长、最大提交次数和连续动作失败上限。

大麦适配器已经接入受控执行链路。平台决策协调器先验证适配器返回的动作提案，
包括 `runId`、包名、顶层窗口、页面指纹和目标节点状态；无障碍服务在执行前还会
重新校验快照序号、页面证据、实时节点身份和悬浮窗口区域。节点点击只使用
`AccessibilityNodeInfo.ACTION_CLICK`，找不到可点击节点时安全暂停，不使用坐标。

## 构建

项目使用 Gradle Wrapper，要求 Android SDK Platform 37 和 JDK 17 至 26。

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
```

`connectedDebugAndroidTest` 需要已启动或已连接的 Android 设备。当前设备冒烟测试
会验证配置目标、安全上限、手动付款提示和“不会自动打开售票应用”的产品边界。

Release 签名通过仓库根目录下不纳入版本控制的 `keystore.properties` 配置。
字段模板见 `keystore.properties.example`；未配置时只会生成不可安装的
`app-release-unsigned.apk`。

实现依据：

- [Android 技术方案](docs/技术方案.md)
- [大麦 Android 业务逻辑](docs/大麦业务逻辑.md)
- [普通用户手机使用说明](docs/手机端用户使用说明.md)
- [手机端使用与测试指南](docs/手机端使用与测试指南.md)
- [普通用户手机使用说明（Word）](docs/手机端使用与测试指南.docx)
- [0.1.0 交付验收记录](docs/验收记录.md)
