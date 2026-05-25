# ClipBridge API 说明

本文档记录第一阶段当前已经落地的接口，范围覆盖：

- 健康检查
- 用户注册
- 用户登录
- Refresh Token 刷新
- 退出登录
- 当前账号信息
- 设备列表

后续随着文本同步、文件、分享、管理员能力逐步完成，再继续补充。

当前线上默认已关闭公开注册。
如果后续需要重新开放，需把 `AUTH_ALLOW_REGISTRATION` 改成 `true` 后重启服务。

## 1. 统一响应格式

所有接口都统一返回 JSON，外层结构固定如下：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "request_id": "8f6c99f1c2d14b77"
}
```

字段说明：

- `code`
  - 成功时固定为 `0`
  - 失败时先直接使用 HTTP 状态码值，例如 `400`、`401`、`409`、`500`
- `message`
  - 对本次结果的简短说明
- `data`
  - 业务数据
  - 失败时通常省略
- `request_id`
  - 每个请求都会带一个请求 ID
  - 后续查日志和排错时可以直接用它定位

## 2. 认证约定

### 2.1 Access Token

- 受保护接口统一通过请求头传递：

```http
Authorization: Bearer <access_token>
```

- 服务端会同时校验：
  - token 本身是否合法、是否过期
  - token 里的 `user_id` 是否真实存在
  - token 里的 `device_id` 是否真实存在且处于启用状态

### 2.2 Refresh Token

- Refresh Token 只通过接口 body 传递，不放在请求头里
- 刷新成功后会返回一组全新的 `access_token + refresh_token`
- 旧的 refresh token 会立刻失效

### 2.3 用户名与设备字段

- `username` 会先 `trim`，再统一转成小写
- `platform` 为空时，默认写成 `unknown`
- `device_name` 为空时，默认写成 `unnamed-device`
- 注册和登录成功后，都会自动创建一条设备记录

## 3. 接口清单

### 3.1 健康检查

```http
GET /healthz
```

作用：

- 判断服务进程是否正常启动
- 顺手检查数据库是否可连通

### 3.2 用户注册

```http
POST /v1/auth/register
Content-Type: application/json
```

请求体：

```json
{
  "username": "alice",
  "password": "password123",
  "platform": "android",
  "device_name": "Pixel 8"
}
```

说明：

- `username` 长度要求 `3-64`
- `password` 长度要求 `8-128`
- 注册成功后会直接返回用户、设备和 token
- 当 `AUTH_ALLOW_REGISTRATION=false` 时，该接口会直接返回 `403 registration is disabled`

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "user": {
      "id": "4c3a7db2-9fb3-4baf-9c83-7a467fdaf861",
      "username": "alice",
      "created_at": "2026-05-23T07:40:00Z",
      "updated_at": "2026-05-23T07:40:00Z"
    },
    "device": {
      "id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
      "platform": "android",
      "device_name": "Pixel 8",
      "last_seen_at": "2026-05-23T07:40:00Z",
      "is_active": true,
      "created_at": "2026-05-23T07:40:00Z"
    },
    "tokens": {
      "access_token": "<access_token>",
      "access_token_expires_at": "2026-05-23T09:40:00Z",
      "refresh_token": "<refresh_token>",
      "refresh_token_expires_at": "2026-06-22T07:40:00Z"
    }
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

常见失败：

- `400 username is required`
- `400 username must be at least 3 characters`
- `400 password must be at least 8 characters`
- `403 registration is disabled`
- `409 username already exists`

### 3.3 用户登录

```http
POST /v1/auth/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "alice",
  "password": "password123",
  "platform": "web",
  "device_name": "Chrome on Windows"
}
```

说明：

- 登录成功后同样会返回用户、设备和 token
- 每次成功登录都会登记当前设备

常见失败：

- `400 username is required`
- `400 password is required`
- `401 invalid username or password`

### 3.4 Refresh Token 刷新

```http
POST /v1/auth/refresh
Content-Type: application/json
```

请求体：

```json
{
  "refresh_token": "<refresh_token>"
}
```

成功返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "tokens": {
      "access_token": "<new_access_token>",
      "access_token_expires_at": "2026-05-23T09:50:00Z",
      "refresh_token": "<new_refresh_token>",
      "refresh_token_expires_at": "2026-06-22T07:50:00Z"
    }
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

常见失败：

- `400 refresh_token is required`
- `401 invalid refresh token`

### 3.5 退出登录

```http
POST /v1/auth/logout
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体可为空，也可以显式带上当前 refresh token：

```json
{
  "refresh_token": "<refresh_token>"
}
```

说明：

- 如果 body 里带了 `refresh_token`，服务端会撤销这一个 token
- 如果 body 为空，服务端会撤销当前设备下的全部 refresh token
- 返回成功后，客户端应立即清理本地 token

### 3.6 当前账号信息

```http
GET /v1/account/me
Authorization: Bearer <access_token>
```

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "user": {
      "id": "4c3a7db2-9fb3-4baf-9c83-7a467fdaf861",
      "username": "alice",
      "created_at": "2026-05-23T07:40:00Z",
      "updated_at": "2026-05-23T07:40:00Z"
    },
    "current_device_id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944"
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

### 3.7 设备列表

```http
GET /v1/devices
Authorization: Bearer <access_token>
```

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "devices": [
      {
        "id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
        "platform": "android",
        "device_name": "Pixel 8",
        "last_seen_at": "2026-05-23T07:40:00Z",
        "is_active": true,
        "created_at": "2026-05-23T07:40:00Z"
      },
      {
        "id": "fe34dbd8-08e4-4e77-98de-e4ad7672d2a2",
        "platform": "web",
        "device_name": "Chrome on Windows",
        "last_seen_at": "2026-05-23T07:35:00Z",
        "is_active": true,
        "created_at": "2026-05-23T07:35:00Z"
      }
    ]
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

### 3.8 修改设备名

```http
PATCH /v1/devices
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "device_id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
  "device_name": "Office Chrome"
}
```

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "device": {
      "id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
      "platform": "web",
      "device_name": "Office Chrome",
      "last_seen_at": "2026-05-23T07:40:00Z",
      "is_active": true,
      "created_at": "2026-05-23T07:35:00Z"
    }
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

常见失败：

- `400 device_id is required`
- `400 device_name is required`
- `400 device_name must be at most 128 characters`
- `404 device not found`

### 3.9 强制下线设备

```http
POST /v1/devices/offline
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "device_id": "fe34dbd8-08e4-4e77-98de-e4ad7672d2a2"
}
```

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "device": {
      "id": "fe34dbd8-08e4-4e77-98de-e4ad7672d2a2",
      "platform": "web",
      "device_name": "Chrome on Windows",
      "last_seen_at": "2026-05-23T07:35:00Z",
      "is_active": false,
      "created_at": "2026-05-23T07:35:00Z"
    },
    "current_device_forced_offline": false
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

说明：

- 设备被强制下线后，会直接从数据库删除该设备记录
- 同一台设备名下的 refresh token 会随着设备删除一起失效
- 接口返回的 `device` 是删除前的设备快照，方便客户端提示用户
- 如果下线的是当前设备，`current_device_forced_offline` 会返回 `true`

常见失败：

- `400 device_id is required`
- `404 device not found`

### 3.10 受保护示例接口

```http
GET /v1/system/profile
Authorization: Bearer <access_token>
```

这个接口继续保留，用来快速确认 access token 和鉴权中间件是否正常。

## 4. 当前阶段边界

这一阶段已经完成登录链路和基础设备读取能力，但还没有实现：

- 一键下线其他设备
- 修改密码
- 文本同步上传
- 历史记录查询
- ACK 与补拉
- WebSocket 实时推送
