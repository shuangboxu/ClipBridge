# ClipBridge Backend

这是 ClipBridge 第一阶段的后端服务。

当前已经完成的范围：

- 服务启动入口
- 配置加载
- PostgreSQL 连接池
- SQL 迁移执行
- 健康检查接口
- 统一 JSON 响应格式
- 请求 ID 中间件
- 统一恢复中间件
- 真实鉴权中间件
- 用户注册
- 用户登录
- Refresh Token 刷新
- 退出登录
- 登录时自动登记设备
- 当前账号信息接口
- 设备列表接口
- 修改设备名接口
- 强制下线设备接口

## 目录说明

```text
backend/
├─ cmd/
│  ├─ server/       服务启动入口
│  └─ dev-token/    开发期生成测试 token 的小工具
├─ internal/
│  ├─ app/          应用装配层，负责把配置、数据库、鉴权拼起来
│  ├─ auth/         账号认证、密码处理、Token 与认证业务层
│  ├─ config/       环境变量和 .env 加载
│  ├─ database/     PostgreSQL 连接与迁移
│  ├─ id/           UUID 生成等基础 ID 工具
│  └─ httpapi/      路由、处理中间件、响应格式、处理器
└─ .env.example
```

## 启动前准备

1. 准备一个 PostgreSQL 数据库。
2. 在数据库里创建用户和库，例如：

```sql
CREATE USER clipbridge WITH PASSWORD 'clipbridge';
CREATE DATABASE clipbridge OWNER clipbridge;
```

3. 复制配置文件：

```bash
cp .env.example .env
```

4. 按需修改 `.env`，尤其是：

- `DATABASE_URL`
- `JWT_SECRET`
- `REFRESH_TOKEN_TTL_HOURS`

## 启动服务

如果你在 Windows 的 PowerShell 里操作：

```powershell
Set-Location D:\MyProject\ClipBridge\backend
go mod tidy
go run ./cmd/server
```

如果你当前和我一样在 WSL 终端里，但 Go 只装在 Windows：

```bash
powershell.exe -NoProfile -Command "Set-Location 'D:\MyProject\ClipBridge\backend'; go mod tidy; go run ./cmd/server"
```

默认监听地址是：

```text
http://127.0.0.1:18080
```

## 当前可用接口

### 1. 健康检查

```http
GET /healthz
```

这个接口会顺手检查数据库是否可连通。

### 2. 认证接口

```http
POST /v1/auth/register
POST /v1/auth/login
POST /v1/auth/refresh
POST /v1/auth/logout
```

说明：

- 当前默认关闭公开注册，只有把 `AUTH_ALLOW_REGISTRATION=true` 写进环境变量后才会放开
- 注册和登录成功后都会自动登记当前设备
- `refresh` 会返回新的 access token 和新的 refresh token
- `logout` 需要携带 access token，body 里的 `refresh_token` 可选

### 3. 当前账号信息

```http
GET /v1/account/me
Authorization: Bearer <access_token>
```

### 4. 设备列表

```http
GET /v1/devices
Authorization: Bearer <access_token>
```

### 5. 设备管理接口

```http
PATCH /v1/devices
POST /v1/devices/offline
```

说明：

- `PATCH /v1/devices` 用于修改设备名
- `POST /v1/devices/offline` 用于强制下线指定设备，并直接删除这条设备记录

### 6. 受保护示例接口

```http
GET /v1/system/profile
Authorization: Bearer <access_token>
```

这个接口会继续保留，方便开发时快速确认 access token 和鉴权中间件是否正常工作。

## 生成测试 Token

当前仍然保留开发期命令来生成测试 token：

```powershell
Set-Location D:\MyProject\ClipBridge\backend
go run ./cmd/dev-token
```

你也可以自定义用户 ID 和设备 ID：

```powershell
go run ./cmd/dev-token -user-id "11111111-1111-1111-1111-111111111111" -device-id "22222222-2222-2222-2222-222222222222"
```

注意：

- 现在鉴权中间件已经会去数据库校验用户和设备是否存在
- 所以这个命令生成的 token，只有在对应 `user_id` 和 `device_id` 真实存在时，才能访问受保护接口

拿到 token 后，就可以访问受保护接口：

```bash
curl -H "Authorization: Bearer <token>" http://127.0.0.1:18080/v1/system/profile
```

## 当前阶段的限制

- 还没有修改密码接口
- 还没有文本同步、历史记录、ACK、补拉接口
- 还没有 WebSocket 实时同步

这些会在路线图后续步骤继续补上。

## 依赖说明

仓库里有一个 `third_party/gopkg.in/` 目录。

它不是业务代码，而是为了兼容当前开发环境里某些上游依赖对 `gopkg.in` 域名的访问限制，
临时放进仓库的本地模块替换目录。
如果你后面只是继续写业务功能，一般不需要改动这里面的内容。
