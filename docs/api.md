# ClipBridge API 说明

本文档记录当前已经落地的接口，范围覆盖：

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
- 文件上传
- 文件列表查询
- 文件下载
- 文件重命名
- 文件删除
- 存储配额申请
- 上传/下载带宽申请
- 管理员申请
- 管理员设置
- 用户管理
- 申请审批
- 文本分享创建
- 文件分享创建
- 分享列表查询
- 分享撤销
- 公开取件页基础访问
- WebSocket 实时推送

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
      "is_admin": false,
      "storage_quota_bytes": 104857600,
      "upload_bandwidth_kbps": 2048,
      "download_bandwidth_kbps": 4096,
      "created_at": "2026-05-23T07:40:00Z",
      "updated_at": "2026-05-23T07:40:00Z"
    },
    "current_device_id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
    "storage_used_bytes": 12582912,
    "storage_free_bytes": 92274688,
    "limits": {
      "max_user_count": 200,
      "default_storage_quota_bytes": 104857600,
      "default_upload_bandwidth_kbps": 2048,
      "default_download_bandwidth_kbps": 4096,
      "max_user_upload_bandwidth_kbps": 10240,
      "max_user_download_bandwidth_kbps": 20480,
      "max_upload_file_bytes": 67108864,
      "allow_registration": false
    }
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

说明：

- `user` 是当前账号的最新角色与额度快照
- `storage_used_bytes` / `storage_free_bytes` 可直接用于额度概览
- `limits` 是当前系统全局限制快照，客户端可拿来展示默认值和上限

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

### 3.15 文件上传

```http
POST /v1/files
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

表单字段：

- `file`
  - 必填
  - 字段名固定就是 `file`

说明：

- 文件体走流式写入，不需要先整块读进内存
- 单文件默认上限是 `64MB`
- 服务端会记录：
  - 原始文件名
  - MIME 类型
  - 文件大小
  - SHA256
  - 来源设备 ID
  - 来源设备名快照

成功返回示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "file": {
      "id": "f3a6bc7b-6c33-4c08-a7f9-cf0dc7830b64",
      "original_name": "report.pdf",
      "content_type": "application/pdf",
      "size_bytes": 24576,
      "file_sha256": "7d8c0f...",
      "origin_device_id": "8a17fd87-50a2-4cd3-aabc-1d9d2c08f944",
      "origin_device_name": "Chrome on Windows",
      "created_at": "2026-05-31T09:20:00Z"
    }
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

常见失败：

- `400 multipart/form-data is required`
- `400 file field is required`
- `400 file name is required`
- `413 file is too large`

### 3.16 文件列表查询

```http
GET /v1/files?page=1&page_size=20
Authorization: Bearer <access_token>
```

说明：

- 按 `created_at DESC` 返回最新上传的文件
- `page` 和 `page_size` 都是可选
- `page_size` 默认 `20`，最大 `100`

响应里的 `summary` 结构：

- `total_files`
- `total_bytes`
- `max_upload_bytes`

### 3.17 文件下载

```http
GET /v1/files/{id}/download
Authorization: Bearer <access_token>
```

说明：

- 成功时直接返回文件二进制流
- `Content-Disposition` 会带上原始文件名
- 如果元数据存在但磁盘文件丢失，会返回 `404 file body not found`

### 3.18 文件重命名

```http
PATCH /v1/files/{id}
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "original_name": "new-report.pdf"
}
```

说明：

- 这里只改数据库里的显示名称
- 不会移动磁盘文件，也不会改 `stored_path`

### 3.19 文件删除

```http
DELETE /v1/files/{id}
Authorization: Bearer <access_token>
```

说明：

- 服务端会先删数据库记录，再尝试删磁盘文件
- 响应里的 `disk_removed=false` 表示记录删掉了，但磁盘文件清理失败，需要后续人工排查

### 3.20 文本分享创建

```http
POST /v1/shares/text
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体示例：

```json
{
  "text_content": "hello share",
  "never_expires": false,
  "expire_seconds": 86400,
  "burn_mode": "countdown",
  "burn_after_seconds": 300,
  "allow_copy_content": false,
  "is_encrypted": false
}
```

加密文本分享时：

- `is_encrypted=true`
- `password` 必填
- `encrypted_payload` 必填
- `encryption` 必填
- 明文 `text_content` 可留空

说明：

- `burn_mode` 支持：
  - `none`
  - `once`
  - `countdown`
- `expire_seconds` 默认 `86400`
- `burn_mode=countdown` 时，`burn_after_seconds` 必须大于 `0`
- `allow_copy_content` 只影响公开文本取件页

### 3.21 文件分享创建

```http
POST /v1/shares/file
Authorization: Bearer <access_token>
Content-Type: multipart/form-data
```

表单字段：

- `file`
  - 必填
- `original_name`
  - 可选
  - 浏览器先加密文件时，建议额外带上原始文件名
- `original_content_type`
  - 可选
  - 浏览器先加密文件时，建议额外带上原始 MIME
- `never_expires`
- `expire_seconds`
- `burn_mode`
- `burn_after_seconds`
- `is_encrypted`
- `password`
- `encryption`

说明：

- 明文文件分享会直接保存原始文件体
- 加密文件分享建议由 Web 端先把文件加密成 `encrypted.bin` 后再上传
- 服务端仍会保存原始文件名和原始 MIME，便于公开页解密后恢复下载文件名

### 3.22 分享列表查询

```http
GET /v1/shares?page=1&page_size=20&status=active
Authorization: Bearer <access_token>
```

说明：

- `status` 可选：
  - `all`
  - `active`
  - `expired`
  - `consumed`
  - `revoked`
- 返回里会带：
  - `token`
  - `content_kind`
  - `status`
  - `is_encrypted`
  - `burn_mode`
  - `remaining_seconds`
  - 文本预览或文件元数据

### 3.23 分享撤销

```http
POST /v1/shares/{id}/revoke
Authorization: Bearer <access_token>
```

说明：

- 撤销后公开取件页会返回 `410 share is no longer available`
- 已撤销分享仍会保留在列表里，方便用户回看状态

### 3.24 公开取件页接口

元信息：

```http
GET /v1/public/shares/{token}/meta
```

公开取件：

```http
POST /v1/public/shares/{token}/content
Content-Type: application/json
```

请求体：

```json
{
  "password": "1234"
}
```

说明：

- 文本分享成功时返回 JSON
- 文件分享成功时直接返回文件流
- 加密文件分享会在响应头里额外返回解密元数据，便于浏览器解密后再触发下载
- 公开接口会按下面顺序判断是否还能访问：
  - 是否已撤销
  - 是否已焚毁
  - 是否已过期
  - 密码是否正确

常见失败：

- `401 invalid password`
- `404 share not found`
- `410 share is no longer available`

### 3.25 我的存储配额申请

创建申请：

```http
POST /v1/account/quota-requests
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "requested_quota_mb": 512,
  "reason": "需要同步更多课程资料"
}
```

查询我的申请记录：

```http
GET /v1/account/quota-requests?status=all
Authorization: Bearer <access_token>
```

说明：

- `requested_quota_mb` 单位固定是 `MB`
- `status` 可选：`all`、`pending`、`approved`、`rejected`
- 响应记录里的 `requested_quota_bytes` / `current_quota_bytes` 都是字节

常见失败：

- `400 request payload is invalid`
- `409 you already have a pending quota request`

### 3.26 我的带宽申请

创建申请：

```http
POST /v1/account/bandwidth-requests
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "requested_upload_kbps": 4096,
  "requested_download_kbps": 8192,
  "reason": "需要上传大文件和多端同步"
}
```

查询我的申请记录：

```http
GET /v1/account/bandwidth-requests?status=all
Authorization: Bearer <access_token>
```

说明：

- 上传和下载都使用 `Kbps`
- 响应记录会返回当前值、申请值、审核备注、审核人和审核时间

常见失败：

- `400 request payload is invalid`
- `409 you already have a pending bandwidth request`

### 3.27 我的管理员申请

创建申请：

```http
POST /v1/account/admin-requests
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "reason": "需要在手机端帮团队处理审批"
}
```

查询我的申请记录：

```http
GET /v1/account/admin-requests?status=all
Authorization: Bearer <access_token>
```

常见失败：

- `400 request payload is invalid`
- `409 you already have a pending admin request`
- `409 you are already an admin`

### 3.28 管理员系统设置

读取设置：

```http
GET /v1/admin/settings
Authorization: Bearer <access_token>
```

更新设置：

```http
PUT /v1/admin/settings
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体示例：

```json
{
  "max_user_count": 200,
  "default_storage_quota_mb": 100,
  "default_upload_bandwidth_kbps": 2048,
  "default_download_bandwidth_kbps": 4096,
  "max_user_upload_bandwidth_kbps": 10240,
  "max_user_download_bandwidth_kbps": 20480,
  "max_upload_file_mb": 64,
  "allow_registration": false
}
```

成功响应里的 `data` 结构：

```json
{
  "settings": {
    "max_user_count": 200,
    "default_storage_quota_bytes": 104857600,
    "default_upload_bandwidth_kbps": 2048,
    "default_download_bandwidth_kbps": 4096,
    "max_user_upload_bandwidth_kbps": 10240,
    "max_user_download_bandwidth_kbps": 20480,
    "max_upload_file_bytes": 67108864,
    "allow_registration": false,
    "updated_at": "2026-06-01T10:00:00Z"
  },
  "current_user_count": 12
}
```

说明：

- 请求里的存储和文件大小单位是 `MB`
- 响应里的存储和文件大小字段统一返回字节

常见失败：

- `400 request payload is invalid`
- `403 admin permission is required`

### 3.29 管理员用户管理

用户列表：

```http
GET /v1/admin/users
Authorization: Bearer <access_token>
```

更新用户：

```http
PATCH /v1/admin/users/{id}
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体示例：

```json
{
  "storage_quota_mb": 512,
  "upload_bandwidth_kbps": 4096,
  "download_bandwidth_kbps": 8192,
  "is_admin": true
}
```

删除用户：

```http
DELETE /v1/admin/users/{id}
Authorization: Bearer <access_token>
```

用户列表每条记录会返回：

- `is_admin`
- `storage_quota_bytes`
- `storage_used_bytes`
- `storage_free_bytes`
- `upload_bandwidth_kbps`
- `download_bandwidth_kbps`
- `has_pending_quota_request`
- `has_pending_bandwidth_request`
- `has_pending_admin_request`
- `last_active_at`
- `created_at`
- `updated_at`

常见失败：

- `400 request payload is invalid`
- `403 admin permission is required`
- `404 resource not found`
- `409 cannot remove the last admin`

### 3.30 管理员审批存储配额申请

待审批列表：

```http
GET /v1/admin/quota-requests?status=pending
Authorization: Bearer <access_token>
```

批准：

```http
POST /v1/admin/quota-requests/{id}/approve
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "approved_quota_mb": 512,
  "review_note": "按业务需求上调"
}
```

拒绝：

```http
POST /v1/admin/quota-requests/{id}/reject
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "review_note": "当前配额已足够"
}
```

常见失败：

- `403 admin permission is required`
- `404 resource not found`
- `409 request is not pending`

### 3.31 管理员审批带宽申请

待审批列表：

```http
GET /v1/admin/bandwidth-requests?status=pending
Authorization: Bearer <access_token>
```

批准：

```http
POST /v1/admin/bandwidth-requests/{id}/approve
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "approved_upload_kbps": 4096,
  "approved_download_kbps": 8192,
  "review_note": "按申请值通过"
}
```

拒绝：

```http
POST /v1/admin/bandwidth-requests/{id}/reject
Authorization: Bearer <access_token>
Content-Type: application/json
```

请求体：

```json
{
  "review_note": "当前带宽策略不允许继续上调"
}
```

常见失败：

- `403 admin permission is required`
- `404 resource not found`
- `409 request is not pending`

### 3.32 管理员审批管理员申请

待审批列表：

```http
GET /v1/admin/admin-requests?status=pending
Authorization: Bearer <access_token>
```

批准：

```http
POST /v1/admin/admin-requests/{id}/approve
Authorization: Bearer <access_token>
Content-Type: application/json
```

拒绝：

```http
POST /v1/admin/admin-requests/{id}/reject
Authorization: Bearer <access_token>
Content-Type: application/json
```

这两个接口的请求体都只有一个字段：

```json
{
  "review_note": "同意在移动端参与日常管理"
}
```

常见失败：

- `403 admin permission is required`
- `404 resource not found`
- `409 request is not pending`

### 3.33 WebSocket 实时推送

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

### 3.34 受保护示例接口

```http
GET /v1/system/profile
Authorization: Bearer <access_token>
```

这个接口继续保留，用来快速确认 access token 和鉴权中间件是否正常。

## 4. 当前阶段边界

这一阶段已经完成：

- 登录、刷新、退出登录
- 当前账号信息与设备管理
- 配额申请、带宽申请、管理员申请
- 管理员设置、用户管理、审批
- 文本上传、历史查询、补拉、ACK
- 文件上传、列表、下载、重命名、删除
- 文本分享、文件分享、公开取件、撤销、过期与焚毁
- WebSocket 实时推送
