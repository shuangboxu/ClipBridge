# ClipBridge Lite

这是一个极简版 Windows 桌面客户端。

当前只保留：

- 登录
- 开关同步
- 开关“同步文件”
- 打开网页端历史页
- 退出登录

## 本地运行

```powershell
mvn javafx:run
```

## 编译

```powershell
mvn -DskipTests compile
```

## 测试

```powershell
mvn test
```

## 打包

```powershell
.\scripts\package-windows.ps1 -PortableOnly
```
