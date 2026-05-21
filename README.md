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

## 部署约束

- 继续使用 `us2` 服务器作为网页端和后端部署目标
- 旧页面 `https://hy-us2.xushuangbo.top:18443/dashboard.html` 不能被影响
- 新项目应使用独立域名、独立目录、独立 nginx 配置、独立 systemd 服务
- 为了尽量降低对旧系统的影响，当前文档建议新网页端地址使用：
  - `https://clipbridge-us2.xushuangbo.top:18444`

## 当前状态

- 已完成项目说明文档初始化
- 已建立顶层模块目录
- 下一步按 `docs/roadmap.md` 进入正式开发
