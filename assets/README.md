# ClipBridge 视觉资产清单

这份清单用于统一管理当前项目的品牌图和插画图，避免后续 Android、Web 接页面时各自从不同目录随意取图，造成风格和命名混乱。

## 目录结构

```text
assets/
├─ brand/                  品牌与应用基础图
│  ├─ app-icon.png
│  ├─ app-icon-black.png
│  ├─ favicon.ico
│  ├─ splash-screen.png
│  └─ splash-screen.webp
└─ illustrations/
   ├─ p0/                  第一轮立即会用到的插画
   └─ p1/                  第一轮后半段和第二轮会逐步用到的插画

assets/illustrations-webp/
├─ p0/                     Web 端优先可用的轻量版插画
└─ p1/
```

## 当前结论

当前 `icon/` 目录中已经存在的 P0、P1 插画整体风格较统一：

- 都采用蓝青色、发光、半透明玻璃感的 3D 工具风格
- 与我们之前确定的“轻量工具台 + 稳定管理感”方向一致
- 适合先作为 Android 和 Web 第一轮接入的正式资源

因此本次不强行重画，而是先整理并作为当前可用版本保留。

## 品牌图

### `assets/brand/app-icon.png`

- 用途：主 Logo、应用图标主参考
- 来源：`icon/icon.png`

### `assets/brand/app-icon-black.png`

- 用途：单色版 Logo、浅色背景下的极简场景
- 来源：`icon/icon-black.png`

### `assets/brand/favicon.ico`

- 用途：Web favicon
- 来源：`icon/icon.ico`

### `assets/brand/splash-screen.png`

- 用途：启动图占位
- 来源：`icon/splash_screen.png`

## P0 插画

### `assets/illustrations/p0/auth-hero.png`

- 页面：登录 / 注册页
- 端：Android、Web
- 说明：认证页主视觉图，强调“多端接入同一云剪切板”
- 来源：`icon/auth-hero.png`

### `assets/illustrations/p0/empty-history.png`

- 页面：历史记录空状态
- 端：Android、Web
- 说明：强调“等待第一条同步内容”
- 来源：`icon/empty-history.png`

### `assets/illustrations/p0/empty-devices.png`

- 页面：设备管理空状态 / 设备较少时的轻空状态
- 端：Android、Web
- 说明：强调“多设备围绕同一账号连接”
- 来源：`icon/empty-devices.png`

### `assets/illustrations/p0/state-server-offline.png`

- 页面：服务地址不可用、网络异常、后端离线态
- 端：Android、Web
- 说明：强调“桥接断开 / 服务不可达”
- 来源：`icon/state-server-offline.png`

## P1 插画

### `assets/illustrations/p1/dashboard-hero.png`

- 页面：Dashboard / 首页顶部
- 端：Web 优先，Android 可裁切用于首页卡片
- 说明：强调“控制台概览 + 同步中心”
- 来源：`icon/dashboard-hero.png`

### `assets/illustrations/p1/empty-files.png`

- 页面：文件中心空状态
- 端：Android、Web
- 说明：强调“文件中转与上传”
- 来源：`icon/empty-files.png`

### `assets/illustrations/p1/empty-shares.png`

- 页面：分享管理空状态
- 端：Android、Web
- 说明：强调“分享链接与跨端传播”
- 来源：`icon/empty-shares.png`

### `assets/illustrations/p1/empty-requests.png`

- 页面：申请记录空状态
- 端：Web 优先，Android 预留
- 说明：强调“账号申请 / 审批记录”
- 来源：`icon/empty-requests.png`

## 格式说明

这批插画当前统一保留为 `PNG`，原因是：

- 这些图本质上是偏位图质感的 AI / 插画风格资源
- 直接强转成 `SVG` 并不会得到真正可维护的矢量稿
- Android 和 Web 第一轮接入都可以直接稳定使用 `PNG`

同时本次额外导出了一套 `WebP` 轻量版，放在：

- `assets/illustrations-webp/`
- `assets/brand/splash-screen.webp`

建议使用方式：

- Android：优先使用 `PNG`
- Web：优先使用 `WebP`
- 如果后续要做进一步裁切、抠图、二次加工，优先从 `PNG` 源文件开始

如果后面某张图需要：

- 无限缩放
- 主题色程序化修改
- 与矢量图标系统完全统一

再单独重绘成真正的矢量 `SVG` 会更合适。

## 原始素材说明

原始出图目前仍保留在 `icon/` 目录，作为来源素材保底。
后续页面代码统一优先引用 `assets/` 目录下的整理版，不再直接引用 `icon/` 根目录。
