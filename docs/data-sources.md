# 数据来源与解析

应用内用 WebView 打开 `https://watchface.zepp.com/`（与 ZeppBridge 相同的入口），网页登录成功后读取会话，再用 Zepp App 的请求头调用区域接口。全程只读，不经过第三方服务器。

## 登录

1. 登录页每次加载完成、路由变化时以及每 0.6 秒轮询一次，读取 `localStorage` / `sessionStorage` / `document.cookie`，同时由原生 `CookieManager` 取回 `.zepp.com` 与区域接口的 cookie。
2. `ZeppLogin` 先递归解析页面里的 `hm-user-login-info`（值本身还可能是 JSON 字符串或 URL 编码的 JSON）；再按名字读取网页登录真正落库的 cookie：`apptoken`、`userid`，并从 `domains` cookie 的 `api-mifit` cname 得到区域主机提示。
3. 页面开始加载时先读一次 cookie，因为失效的网页会话会在页面脚本执行时被登出并删掉 cookie。
4. 用 `/users/{userId}` 逐个探测候选区域主机（cn / cn2 / huami / zepp / us / de / sg …），第一个返回同一 `userId` 的主机被保存。

支持的登录方式取决于 Zepp 登录页：邮箱密码、小米、Apple 等网页流程都可以；Google 在 Android WebView 里会返回 403 `disallowed_useragent`，按钮已隐藏。

## 请求头

与 Zepp App 一致：

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

## 只读接口

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
| 每日健康 | `GET /v2/users/me/events?eventType=DailyHealth&subType=summary` |
| 体重 | `GET /users/{id}/members/-1/weightRecords`（时间参数是**秒**） |
| 运动 | `GET /v1/sport/run/history.json` |
| 运动明细（GPS） | `GET /v1/sport/run/detail.json?trackid=…&source=…` |

## 解析口径

- `band_data` 的 `summary` 是 base64（可能是 gzip）的 JSON；`data_hr` 是 base64 的 1440 字节，每分钟一个心率值（20–240 有效）。
- 睡眠 `mode`：4 浅睡、5 深睡、8/11 REM、7 清醒；阶段偏移是「醒来日前一天本地零点起的分钟数」。
- Charge 的 `total=255` 是「手表没算出来」的哨兵值，会跳过；每个采样按自己的日历日归并，每日取最后一个有效样本。
- 运动明细的 `longitude_latitude` 是「首个绝对坐标 + 后续相对差值」，按累加还原；个别固件写成绝对坐标时用「点数更多、跨度更小」的一侧。
- 缺失就是缺失：没有采样时字段为 `null`，界面显示 `--`，不会补 0，也不会用上一天的值顶替。
- 快照超过 3 小时按 `--` 展示（运动历史除外），等这次同步拿到新数据再显示。

## 负荷的计算

每日负荷 = `21 × (1 − e^(−load/90))`，其中

- `load = (心率冲量 + 步数项 + 脑力项) × 恢复系数`
- 心率冲量按 Banister TRIMP 逐分钟累计，只有超过 30% 心率储备的时间计入；最大心率优先用 Tanaka 公式，缺生日时取历史读数的 95 分位
- 步数项 = `步数 / 1000 × 2`
- 脑力项 = 0–100 的脑力负荷 × 0.25（满值 25，约一次中等训练的三分之一）
- 恢复系数：HRV 与基线比 `1 + 0.30 × (1 − ratio)`（限 0.85–1.30），静息心率每高 1 bpm `× 1.02`（限 0.90–1.25），乘积限 0.80–1.35；缺数据时恒为 1.00

它是本机估算，不是手表给出的分数，界面上也这样说明。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

`app/src/test/resources/*.json` 是从真实接口响应裁剪的样本，账号 `userId` 已替换为占位值，不含昵称、头像或轨迹坐标。
