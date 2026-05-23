# ClipBridge

## 项目定位

- 项目类型：安卓课结课作业
- 开发目标：完整实现旧版“万能剪切板”的主要能力
- 代码目标：结构清晰、模块边界明确、便于初学者理解
- 开发顺序：优先联合开发后端、Android、Web；然后开发 Windows；HarmonyOS 最后再做

## 最终要覆盖的功能范围

- 用户注册、登录、刷新令牌、退出登录
- 多设备接入与设备管理
- 文本剪切板同步
- 剪切板历史记录查询、删除、补拉、ACK
- 文件中转上传、下载、删除、重命名
- 文本分享、文件分享、公开取件、过期与撤销
- 用户配额、带宽、管理员申请
- 管理员后台设置、用户管理、申请审批
- Android、Web、鸿蒙、Windows 多端接入
- AI 附加功能：文本整理、OCR、敏感信息提醒、智能标签、语义搜索

## 当前技术选型

- 后端：`Go + PostgreSQL`，采用模块化单体 + 分层架构，适合认证、同步、推送、文件传输这类并发网络服务。
- Android：`Kotlin + Jetpack Compose`，采用 `Single-Activity + Navigation + MVVM + Repository`，更适合状态驱动界面；同步核心放在 `Foreground Service` 中。
- 详细说明见 [docs/architecture.md](docs/architecture.md)。

## 当前目录规划

```text
ClipBridge/
├─ backend/      后端服务
├─ android/      Android 客户端
├─ web/          Web 管理端
├─ harmony/      HarmonyOS 客户端
├─ windows/      Windows 客户端
├─ docs/         项目文档
├─ AGENTS.md
└─ README.md
```

## 文档说明

- [docs/requirement.md](docs/requirement.md)：需求范围、功能边界、作业目标
- [docs/architecture.md](docs/architecture.md)：系统架构、模块拆分、部署约束
- [docs/api.md](docs/api.md)：接口设计说明、接口分组、请求与响应约定
- [docs/roadmap.md](docs/roadmap.md)：开发顺序、阶段计划、验收节点
- [docs/ui-plan.md](docs/ui-plan.md)：Android 和 Web 的页面布局、跳转规则、统一交互语义
- [web/README.md](web/README.md)：第 2 步最小 Web 端的本地运行方式与页面说明

## 部署约束

- 继续使用 `us2` 服务器作为网页端和后端部署目标
- 旧页面 `https://hy-us2.xushuangbo.top:18443/dashboard.html` 不能被影响
- 新项目应使用独立域名、独立目录、独立 nginx 配置、独立 systemd 服务
- 为了尽量降低对旧系统的影响，当前文档建议新网页端地址使用：
  - `https://clipbridge-us2.xushuangbo.top:18444`
