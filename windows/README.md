# ClipBridge Windows

这是 ClipBridge 的 Windows 桌面客户端，技术栈固定为：

- Java 21
- JavaFX
- Maven
- jpackage

当前功能范围对齐“已经落地的 Web + Android 能力”，明确排除 AI。

## 1. 本地运行

先确认本机已安装：

- JDK 21
- Maven 3.9+

在 `windows/` 目录执行：

```powershell
mvn javafx:run
```

如果只想先确认能编译：

```powershell
mvn -DskipTests compile
```

## 2. 测试与打包

运行单元测试：

```powershell
mvn test
```

打包可运行 fat jar：

```powershell
mvn -DskipTests package
```

生成 Windows 便携包 / 安装包：

```powershell
.\scripts\package-windows.ps1
```

常用参数：

```powershell
.\scripts\package-windows.ps1 -Version 1.0.0
.\scripts\package-windows.ps1 -PortableOnly
.\scripts\package-windows.ps1 -InstallerType inno
.\scripts\package-windows.ps1 -InstallerType jpackage
```

## 3. 本地状态文件

桌面端状态文件默认保存在：

```text
%APPDATA%\ClipBridge\windows-state.json
```

主要保存：

- 服务地址
- 用户名
- 当前设备 ID
- 当前设备名
- access token / refresh token
- 是否管理员
- 配额与带宽快照
- `lastAckSeq`
- 同步开关
- 开机自启
- 启动进托盘
- 分享规则

## 4. 托盘行为

如果系统支持托盘：

- 关闭主窗口时默认隐藏到托盘
- 托盘菜单支持 `显示主窗口`
- 托盘菜单支持 `立即同步`
- 托盘菜单支持 `开关同步`
- 托盘菜单支持 `退出`

`--start-in-tray` 启动参数会让应用启动后直接隐藏到托盘。

## 5. 开机自启

Windows 自启通过下面的注册表项实现：

```text
HKCU\Software\Microsoft\Windows\CurrentVersion\Run
```

开启自启时，写入的命令固定会带：

```text
--start-in-tray
```

这样开机启动不会直接弹出主窗口。

## 6. 当前边界

当前桌面端明确不做：

- AI 页面
- AI 接口
- 桌面内匿名公开取件页
- 历史删除
- 一键下线全部其他设备

这些边界和 `docs/roadmap.md`、`docs/ui-plan.md` 保持一致。
