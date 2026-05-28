# ClipBridge API 说明

本文档记录第一阶段当前已经落地的接口，范围覆盖：

- 健康检查
- 用户注册
- 用户登录
- Refresh Token 刷新
- 退出登录
- 当前账号信息
- 修改密码
- 设备列表
- 文本剪切板上传
- 历史记录查询
- 同步补拉
- ACK
- WebSocket 实时推送

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

- WebSocket 对浏览器额外兼容：
  - 原生浏览器 WebSocket 不能自定义 `Authorization` Header
  - 因此 `GET /v1/ws` 同时支持 `?access_token=<access_token>` 方式建连

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

### 3.6 修改密码

```http
POST /v1/account/password
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "current_password": "password123",
  "new_password": "new-password-456"
}
```

说明：

- `current_password` 必填
- `new_password` 长度要求 `8-128`
- 新密码不能和当前密码相同
- 修改成功后，当前设备继续保持登录
- 其他设备上的 refresh token 会被撤销，需要重新登录

成功返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "success": true,
    "user": {
      "id": "4c3a7db2-9fb3-4baf-9c83-7a467fdaf861",
      "username": "alice",
      "created_at": "2026-05-23T07:40:00Z",
      "updated_at": "2026-05-28T03:10:00Z"
    }
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

常见失败：

- `400 current_password is required`
- `400 password must be at least 8 characters`
- `400 new password must be different from current password`
- `401 current password is incorrect`

### 3.7 当前账号信息

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

### 3.8 设备列表

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

### 3.9 修改设备名

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

### 3.10 强制下线设备

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

### 3.11 上传文本剪切板

```http
POST /v1/clipboard/items
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "content_type": "text",
  "text_content": "hello from web"
}
```

说明：

- 当前阶段 `content_type` 只支持 `text`
- 服务端会为每个用户分配递增 `seq`
- 服务端会基于 `content_hash + 最近时间窗口` 做基础去重
- 若命中去重，接口会返回最近一条已有记录，并把 `deduplicated` 设为 `true`

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "item": {
      "id": "7d7f82a4-cfd7-4633-b6ad-4f040a6776d0",
      "seq": 12,
      "content_type": "text",
      "text_content": "hello from web",
      "content_hash": "4af4c0...",
      "origin_device_id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
      "is_current_device_origin": true,
      "created_at": "2026-05-25T01:20:00Z"
    },
    "deduplicated": false
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

常见失败：

- `400 only text clipboard items are supported`
- `400 text_content is required`
- `400 text_content must be at most 65536 bytes`

### 3.12 历史记录查询

```http
GET /v1/clipboard/items?limit=20&before_seq=120
Authorization: Bearer <access_token>
```

说明：

- 默认按 `seq DESC` 返回
- `before_seq` 为可选分页游标，表示“拉更旧的记录”
- 响应里会同时带上 `latest_seq` 和 `current_device_ack_seq`

### 3.13 同步补拉

```http
GET /v1/sync/pull?since_seq=8&limit=50
Authorization: Bearer <access_token>
```

说明：

- 补拉按 `seq ASC` 返回，方便客户端顺序处理
- `since_seq` 表示当前设备已经连续处理完成的最大序号
- 响应里的 `next_since_seq` 可直接作为下一次补拉或 ACK 候选值

### 3.14 ACK

```http
POST /v1/sync/ack
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "seq": 12
}
```

说明：

- ACK 会把当前设备的 `last_ack_seq` 推进到更大的值
- 服务端会自动用 `GREATEST` 保护，避免 ACK 倒退

### 3.15 WebSocket 实时推送

连接方式：

```http
GET /v1/ws?access_token=<access_token>
```

服务端事件：

- `sync.hello`
- `sync.heartbeat`
- `clipboard.new`
- `sync.acknowledged`

客户端事件：

- `sync.ping`
- `sync.ack`

`clipboard.new` 示例：

```json
{
  "type": "clipboard.new",
  "item": {
    "id": "7d7f82a4-cfd7-4633-b6ad-4f040a6776d0",
    "seq": 12,
    "content_type": "text",
    "text_content": "hello from web",
    "content_hash": "4af4c0...",
    "origin_device_id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
    "is_current_device_origin": false,
    "created_at": "2026-05-25T01:20:00Z"
  }
}
```

说明：

- 实时广播会自动排除源设备，避免回环推送到上传发起端
- `sync.heartbeat` 当前默认每 `20` 秒发送一次
- Web 端建议在检测到序号缺口时立刻回退到 `GET /v1/sync/pull`

### 3.16 受保护示例接口

```http
GET /v1/system/profile
Authorization: Bearer <access_token>
```

这个接口继续保留，用来快速确认 access token 和鉴权中间件是否正常。

## 4. 当前阶段边界

这一阶段已经完成：

- 登录、刷新、退出登录
- 当前账号信息与设备管理
- 文本上传、历史查询、补拉、ACK
- WebSocket 实时推送

当前仍未实现：

- 修改密码
- 文件同步
- 分享与管理员能力
