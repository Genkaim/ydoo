# ydoo

一个直接读取 Zepp / Amazfit 健康数据的安卓客户端。界面是扁平的 Codex 风格（Material 3 语义色 + 等宽数字，无渐变、无阴影、无模糊），深色/浅色跟随系统；底栏是可自定义顺序与显隐的悬浮胶囊；第四个页签是自带接入点设置的 AI 教练。

数据获取与登录方式参考 [ZeppBridge](https://github.com/lingcang728/ZeppBridge) 的「高级数据」通道：网页登录拿到 `apptoken`，再用 Zepp App 的请求头调用区域接口。不经过任何第三方服务器。

仓库：<https://github.com/Genkaim/ydoo>

---

## 功能

| 页签 | 内容 |
| --- | --- |
| 概览 | 顶部三环（睡眠 / Hybrid Charge / 训练负荷，环内无字）、今日活动、准备度、静息心率、PAI、最大摄氧量、压力、训练负荷、昨晚睡眠分期、Charge 当日曲线、体重、血氧、最近运动 |
| Hybrid Charge | 当前体力/精神/总分、当天曲线、近两周趋势、体力与精神分线、训练负荷 |
| 活动 | 步数/消耗/活动三环（环内无字）+ 今日心率 + 近 7 天步数 + 近 14 天距离与消耗 + 区间分布 + 运动列表 |
| AI 教练 | OpenAI 兼容聊天，流式输出、Markdown 与数据卡片；历史会话全部存档（右上角入口）；切到别的页签也不会中断 |

概览与 Charge 里的每一张卡片都可以点开，进入对应指标的详情页（独立 Activity，系统返回手势可退出）：

- 睡眠 → 分期时间线、夜间 HRV/心率/血氧、近 14 晚
- Charge → 当日曲线、近 14 天体力/精神
- 压力 → 当日曲线、四档分布、近 14 天
- 准备度 → 各项分数（HRV、静息心率、呼吸、皮温、体力、精神）与 14 天趋势
- 活动 → 目标进度、距离/消耗/活动分钟、14 天步数
- 训练负荷 → 合适区间、14 天负荷
- PAI → 当日/累计、三档分钟数、14 天
- 最大摄氧量 → 手表给的值 + 运动记录里的 VO2_max
- 体重 → 体重/BMI/体脂、趋势、最近记录
- 血氧 → 最近读数、平均/最低、曲线
- 运动 → 时长、距离、配速、心率、消耗、负荷、**GPS 轨迹**、云端明细全部字段

其它：

- **底栏自定义**：设置 → 底栏，或长按底栏。支持排序、隐藏、恢复默认（至少保留一个入口）。
- **全部数据**：设置 →「全部数据」按表列出这次同步拿到的每一个字段（含同步告警），任何取到的数据都有地方看。
- **AI 接入点自定义**：Base URL、API Key、模型、自定义请求头、连接测试。兼容 OpenAI、DeepSeek、Moonshot、One-API、Ollama、OpenCode 网关等；系统提示词内置，不开放修改。
- **AI 历史会话**：所有对话存档在本机 `ai-chats.json`，右上角历史入口可切换、删除、新建；失败的回答气泡里有「重试」。
- **每日 AI 摘要**：首页三环下面一段话，每天 5 点后第一次打开 App 自动生成，生成中显示呼吸动画，卡片右上角可手动重新生成。
- **数据时效**：除运动历史外，快照超过 3 小时就只显示 `--`，等这次同步拿到新数据再展示。
- **离线可看**：每次同步后把原始报文快照写到应用私有目录，下次打开先显示缓存结构，再后台刷新。

## 鉴权与数据获取

登录：

1. 应用内 WebView 打开 `https://watchface.zepp.com/`（与 ZeppBridge 相同的入口），让用户在页面里用 Zepp 邮箱/手机号登录（也支持页面提供的 Apple / 小米等网页登录；Google 在 WebView 里会被拒绝，按钮已隐藏）。
2. 页面每次加载完成后注入脚本，读取 `localStorage` / `sessionStorage` / `document.cookie`；同时由原生 `CookieManager` 取回 `.zepp.com` 与区域接口的 cookie。
3. `ZeppLogin` 先递归解析页面里的 `hm-user-login-info`（值本身还可能是 JSON 字符串或 URL 编码的 JSON）；随后按名字读取网页登录真正落库的 cookie：`apptoken`、`userid`，并从 `domains` cookie 的 `api-mifit` cname 得到区域主机提示。
4. 用 `/users/{userId}` 逐个探测候选区域主机（cn/cn2/huami/zepp/us/de/sg…），第一个能返回同一 `userId` 的主机被保存。

请求头（与 ZeppBridge 一致）：

```
apptoken: <token>
appname: com.huami.midong
appplatform: ios_phone
v: 2.0
vn: 10.2.5
cv: 1722_10.2.5
vb: 202604132257
lang: zh
country: CN
timezone: Asia/Shanghai
```

用到的只读接口：

| 用途 | 接口 |
| --- | --- |
| 个人资料 | `GET /users/{id}` |
| 手环明细（步数/睡眠/心率） | `GET /v1/data/band_data.json?query_type=detail&byteLength=8&device_type=0` |
| 准备度/HRV/静息心率 | `GET /users/{id}/events?eventType=readiness&subType=watch_score` |
| PAI | `GET /users/{id}/events?eventType=PaiHealthInfo` |
| 全天压力 | `GET /users/{id}/events?eventType=all_day_stress` |
| 血氧 | `GET /users/{id}/events?eventType=blood_oxygen` |
| Hybrid Charge | `GET /v2/users/me/events?eventType=Charge&subType=real_data` |
| 训练负荷 | `GET /v2/watch/users/{id}/WatchSportStatistics/SPORT_LOAD` |
| 体重 | `GET /users/{id}/members/-1/weightRecords`（时间参数是**秒**） |
| 运动 | `GET /v1/sport/run/history.json` |
| 运动明细（GPS） | `GET /v1/sport/run/detail.json?trackid=…&source=…` |

解析口径：

- `band_data` 的 `summary` 是 base64（可能是 gzip）的 JSON；`data_hr` 是 base64 的 1440 字节，每分钟一个心率值（20–240 有效）。
- 睡眠 `mode`：4 浅睡、5 深睡、8/11 REM、7 清醒；阶段偏移是「醒来日前一天本地零点起的分钟数」。
- Charge 的 `total=255` 是「手表没算出来」的哨兵值，会跳过；每日取最后一个有效样本。
- 运动明细的 `longitude_latitude` 是「首个绝对坐标 + 后续相对差值」，按累加还原；个别固件写成绝对坐标时用「点数更多、跨度更小」的一侧。
- **缺失就是缺失**：没有采样时字段为 `null`，界面显示 `--`，不会补 0，也不会用上一天的值顶替。

## 构建

```bash
./gradlew :app:assembleDebug     # 调试包
./gradlew :app:assembleRelease   # 发布包（用本机 debug 密钥签名，便于覆盖安装）
```

产物：`app/build/outputs/apk/debug/app-debug.apk` 与 `app/build/outputs/apk/release/app-release.apk`。

- Kotlin 2.3.0 / AGP 9.2.1 / Compose BOM 2025.09（Material 3 1.3.2）
- minSdk 26，targetSdk 36
- 扁平视觉：表面色 + 1dp 描边，无投影、无渐变；数字用等宽字体
- 深/浅色跟随系统（`isSystemInDarkTheme`），窗口背景在 `values` / `values-night` 各自声明
- 依赖了 `material-icons-extended`

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

32 个 JVM 单元测试：

- `HealthParserTest`：用真实接口响应的裁剪样本，校验步数、睡眠分期、Charge 哨兵值、准备度、PAI、压力、血氧、体重、运动、训练负荷的字段映射；另含 GPS 差值还原与无轨迹用例。
- `AiQueryTest`：校验 query 代码块/裸 JSON/字符串里的花括号/未闭合对象，防止再次出现正则解析崩溃。
- `StrainCalculatorTest`、`ZeppLoginTest`：Strain 映射与登录凭据提取。

`app/src/test/resources/*.json` 是从真实接口响应裁剪的样本，已把账号 `userId` 替换为占位值，不含昵称、头像或轨迹坐标。

## 已知边界

- 睡眠、步数、连续心率来自 `band_data` 二进制/半结构化报文，解析的是已验证字段；官方开放平台独有的数据没有接入。
- GPS 轨迹是没有底图的折线（不接地图瓦片服务），形状与起终点来自 `detail.json`；没有轨迹的运动明确显示「没有 GPS 坐标」。
- 写操作（改运动类型、发训练计划等）没有做，本应用只读。
- 网页登录是否顺畅取决于 Zepp 登录页当时的形态；如果它在这个 WebView 里一直空白，设置里提供「手动填写 App Token」：在电脑上用 ZeppBridge 登录后，从系统的凭据管理器读取 `apptoken` 与 user id 填入即可。
- AI 请求直接从手机发往用户填写的接入点；健康摘要只包含最近 7 天、且只发给你选择的服务。

## 隐私

- `apptoken` 与 AI 密钥保存在应用私有的 SharedPreferences 中，不进入日志、不写入导出文件。
- 健康数据只存在本机应用目录；唯一的对外请求是 Zepp 区域接口与你自己配置的 AI 接口。
- 明文 HTTP 只为你自己填写的 AI 接入点放开（`usesCleartextTraffic`），Zepp 接口全部走 HTTPS。

## 目录

```
app/src/main/java/com/genkaim/zeppglass/
├── MainActivity.kt
├── data/          ZeppApi, ZeppLogin, HealthParser, HealthStore, AiClient, AppSettings, 模型
└── ui/
    ├── ZgApp.kt            外壳、底栏、页面切换
    ├── components/         玻璃面板、底栏、图表、通用卡片
    ├── screens/            概览 / Charge / 睡眠 / AI / 登录 / 设置
    ├── sheets/             底栏自定义
    └── theme/              颜色与排版
```
