# WSMC for Spigot/Paper

让 Spigot/Paper 服务器支持 WebSocket 连接的插件。受 [rikka0w0/wsmc](https://github.com/rikka0w0/wsmc) 启发，适用于需要在 CDN 后面隐藏服务器的场景。

> 大部分 CDN 免费套餐不支持 TCP 代理，通过 WebSocket 方式可绕过此限制，有效防御 DDoS 攻击。

---

## 工作原理

```
MC 客户端 ──WebSocket──► WSMC 插件(25566) ──TCP──► localhost:25565(Spigot/Paper)
```

1. 客户端通过 `ws://` 或 `wss://` 连接到此插件
2. 插件完成 WebSocket 握手后，将数据转发到 **服务器自身的监听端口** (localhost)
3. 服务器看到的是来自 localhost 的标准 TCP 连接，正常处理登录和游戏逻辑
4. 所有 Bukkit 事件、权限、插件全部正常工作，无需额外配置

---

## 安装

### 要求

- Paper / Spigot **1.20.4+**
- Java **17+**（1.20.4+ 内置）

### 下载

从 [Releases](https://github.com/T-Ming-L/wsmc-spigot/releases/latest) 下载最新 `wsmc-spigot-x.x.x.jar`。

### 自行构建

```bash
git clone https://github.com/yourname/wsmc-spigot.git
cd wsmc-spigot
./gradlew build
```

### 部署

将 `build/libs/wsmc-spigot-X.X.X.jar` 放入服务器的 `plugins/` 目录，重启即可。

首次启动会自动在 `plugins/WSMC/wsmc.properties` 生成默认配置文件。

---

## 配置

配置文件位置：`plugins/WSMC/wsmc.properties`

| 属性                              | 类型    | 默认值            | 说明                                                                 |
| --------------------------------- | ------- | ----------------- | -------------------------------------------------------------------- |
| `wsmc.wsPort`                     | int     | `25566`           | WebSocket 监听端口                                                   |
| `wsmc.wsmcEndpoint`               | string  | 不设置            | WebSocket 路径，如 `/mc`。必须以 `/` 开头，区分大小写                |
| `wsmc.debug`                      | boolean | `false`           | 开启调试日志                                                         |
| `wsmc.dumpBytes`                  | boolean | `false`           | 打印原始 WebSocket 帧（需 `debug=true`）                             |
| `wsmc.maxFramePayloadLength`      | int     | `65536`           | 最大帧载荷长度。如 Netty 报错 "Max frame length exceeded" 时增大此值 |
| `wsmc.disableVanillaTCP`          | boolean | `false`           | 禁用原生 TCP 登录，仅允许 WebSocket 连接                             |
| `wsmc.spigotPort`                 | int     | `25565`           | 服务器自身的监听端口（插件连接 localhost 时使用）                    |
| `wsmc.proxyProtocol.enabled`      | boolean | `false`           | 启用 Proxy Protocol V2，传递真实客户端 IP 给服务器                   |
| `wsmc.proxyProtocol.realIpHeader` | string  | `X-Forwarded-For` | Proxy Protocol 使用的 HTTP 头（CDN 场景）                            |

---

## 客户端连接

安装此插件后，玩家可使用安装了 WSMC 客户端的格式连接：

```
# 不安全的 WebSocket
ws://your-server.com:25566/mc

# 安全的 WebSocket（wss）
wss://your-server.com:25566/mc

# 指定 SNI 和 HTTP Host（适用于 CDN 场景）
ws://host.com@1.2.3.4:25566/mc
wss://sni.com:host.com@1.2.3.4:25566/mc
```

---

## Proxy Protocol V2

在连接服务器前先发送 [Proxy Protocol V2](https://www.haproxy.org/download/2.9/doc/proxy-protocol.txt) 头部，将真实客户端 IP 透传给服务器。

### 配置步骤

**1. 在 `server.properties` 中启用：**

```properties
proxy-protocol=true
```

> Paper 1.20+ 支持此选项；Spigot 需要额外配置或使用 BungeeCord 模式。

**2. 在 `plugins/WSMC/wsmc.properties` 中启用：**

```properties
wsmc.proxyProtocol.enabled = true
wsmc.proxyProtocol.realIpHeader = X-Forwarded-For
```

### 数据流

```
Client → CDN [X-Forwarded-For: 1.2.3.4] → WSMC Plugin(25566)
  → [PPv2 Header: 1.2.3.4 → 127.0.0.1] → Spigot/Paper(25565)
  → [Server sees: 1.2.3.4]
```

服务器即可获取真实 `1.2.3.4` 而非 `127.0.0.1`。

---

## 与 WSMC Mod / Velocity 版对比

|          | WSMC Mod      | WSMC for Velocity | WSMC for Spigot/Paper |
| -------- | ------------- | ----------------- | --------------------- |
| 运行位置 | 游戏服务器内  | Velocity 代理     | Spigot/Paper 服务器内 |
| 适用场景 | 单服 / 无代理 | 群组服 / Velocity | 单服 / 无代理         |
| 后端兼容 | 直接处理      | 走 Velocity 转发  | 走本地服务器端口      |
| 配置方式 | JVM 系统属性  | `wsmc.properties` | `wsmc.properties`     |

---

## 常见问题

**Q: 和 Velocity 版的 WSMC 有什么区别？**
A: Velocity 版运行在代理端，支持群组服；Spigot/Paper 版直接运行在游戏服务器上，适合单服场景。

**Q: 走 CDN/WSS 后如何获取真实 IP？**
A: 配置 Proxy Protocol V2（见上方说明），CDN 会在 WebSocket 握手请求中添加 `X-Forwarded-For` 等 Header。

**Q: `disableVanillaTCP` 开启后管理员怎么连接？**
A: 管理员可以通过 WebSocket 连接，或者使用服务器控制台 / rcon 进行操作。

**Q: Netty 报错 "Max frame length exceeded"？**
A: 增大 `wsmc.maxFramePayloadLength` 配置项的值。

---

## 开源协议

MIT License

---

## 致谢

- [rikka0w0/wsmc](https://github.com/rikka0w0/wsmc) - 原始 WSMC 模组
- [PaperMC](https://papermc.io/) - 高性能 Minecraft 服务端
- [Netty](https://netty.io/) - 异步网络框架
