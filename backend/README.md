# ClipBridge Backend

这是 ClipBridge 第一阶段的后端基础骨架。

当前已经完成的范围：

- 服务启动入口
- 配置加载
- PostgreSQL 连接池
- SQL 迁移执行
- 健康检查接口
- 统一 JSON 响应格式
- 请求 ID 中间件
- 统一恢复中间件
- 统一鉴权中间件骨架
- 一个受保护的示例接口

## 目录说明

```text
backend/
├─ cmd/
│  ├─ server/       服务启动入口
│  └─ dev-token/    开发期生成测试 token 的小工具
├─ internal/
│  ├─ app/          应用装配层，负责把配置、数据库、鉴权拼起来
│  ├─ auth/         Access Token 生成与解析
│  ├─ config/       环境变量和 .env 加载
│  ├─ database/     PostgreSQL 连接与迁移
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

### 2. 受保护示例接口

```http
GET /v1/system/profile
Authorization: Bearer <token>
```

这个接口只是为了验证第一阶段的“鉴权中间件骨架”已经生效。
后续做真正的注册、登录、设备校验时，会在这个骨架上继续扩展。

## 生成测试 Token

当前还没做注册/登录接口，所以提供了一个开发期命令来生成测试 token：

```powershell
Set-Location D:\MyProject\ClipBridge\backend
go run ./cmd/dev-token
```

你也可以自定义用户 ID 和设备 ID：

```powershell
go run ./cmd/dev-token -user-id "11111111-1111-1111-1111-111111111111" -device-id "22222222-2222-2222-2222-222222222222"
```

拿到 token 后，就可以访问受保护接口：

```bash
curl -H "Authorization: Bearer <token>" http://127.0.0.1:18080/v1/system/profile
```

## 当前阶段的限制

- 还没有注册、登录、刷新令牌、退出登录
- 鉴权中间件当前只做 token 解析和基本声明校验
- 还没有接入“用户是否存在、设备是否已吊销”这类数据库校验

这些正好会在你路线图的下一步“认证与设备基础”里继续完成。

## 依赖说明

仓库里有一个 `third_party/gopkg.in/` 目录。

它不是业务代码，而是为了兼容当前开发环境里某些上游依赖对 `gopkg.in` 域名的访问限制，
临时放进仓库的本地模块替换目录。
如果你后面只是继续写业务功能，一般不需要改动这里面的内容。
