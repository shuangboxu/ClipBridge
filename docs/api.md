# ClipBridge API 说明

本文档先记录第一阶段已经落地的接口和统一响应约定。
后续随着认证、设备、同步、文件、分享模块逐步完成，再继续补充。

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
  - 失败时先使用 HTTP 状态码值，例如 `401`、`404`、`500`
- `message`
  - 对本次结果的简短说明
- `data`
  - 业务数据
  - 失败时通常省略
- `request_id`
  - 每个请求都会带一个请求 ID
  - 后续查日志和排错时可以直接用它定位

## 2. 当前阶段接口

### 2.1 健康检查

```http
GET /healthz
```

作用：

- 判断服务进程是否正常启动
- 顺手检查数据库是否可连通

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "service": "clipbridge-backend",
    "status": "ok",
    "database": "up",
    "time": "2026-05-22T11:30:00+08:00"
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

失败示例：

```json
{
  "code": 503,
  "message": "database is unavailable",
  "request_id": "8f6c99f1c2d14b77"
}
```

### 2.2 受保护示例接口

```http
GET /v1/system/profile
Authorization: Bearer <token>
```

作用：

- 用来验证第一阶段的鉴权中间件骨架是否生效
- 当前还不是正式的“当前账号信息接口”
- 后续会被真实的 `/v1/account/me` 等接口替代或吸收

成功示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "user_id": "11111111-1111-1111-1111-111111111111",
    "device_id": "22222222-2222-2222-2222-222222222222",
    "note": "当前接口仅用于验证第一阶段的鉴权中间件骨架，后续会替换为真实账号接口"
  },
  "request_id": "8f6c99f1c2d14b77"
}
```

未带 token 时的失败示例：

```json
{
  "code": 401,
  "message": "missing or invalid Authorization header",
  "request_id": "8f6c99f1c2d14b77"
}
```

token 无效时的失败示例：

```json
{
  "code": 401,
  "message": "invalid access token",
  "request_id": "8f6c99f1c2d14b77"
}
```

## 3. 当前阶段说明

第一阶段只完成基础骨架，不包含下面这些真实业务能力：

- 注册
- 登录
- Refresh Token 刷新
- 退出登录
- 真实账号信息读取
- 设备数据库校验
- 设备吊销校验

也就是说，当前的鉴权中间件是“能跑、能测、可继续扩展”的骨架，
它已经把 token 解析、身份注入、统一错误返回这些基础动作固定下来了。
