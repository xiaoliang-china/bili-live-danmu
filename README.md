# bili-live-danmu

轻量级哔哩哔哩直播弹幕 Java 库。通过 WebSocket 连接直播间，接收并解析弹幕、表情、礼物、大航海、醒目留言和互动消息。

> **当前版本：1.1.0** · Java 17+ · MIT License
>
> 本库为 [鼠鼠弹幕重置版](../README.md) 的弹幕后端，也可单独接入任意 Java 项目。

---

## ✨ 功能特性

- **实时连接**：接入 B 站直播弹幕 WebSocket，完成鉴权、心跳保活与二进制协议解包
- **消息分类**：内置弹幕、表情、礼物、大航海、醒目留言（SC）、互动（进场 / 关注 / 分享等）处理器
- **游客或登录**：支持匿名连接，也可携带 Cookie 以登录态进入直播间
- **发送弹幕**：提供 HTTP 发弹幕接口（需要用户 Cookie）
- **可扩展**：通过 `MessageHandler` 自行处理任意 `cmd`，不必改库代码
- **轻量依赖**：运行时只需 Gson 与 Java-WebSocket；日志走 SLF4J（可选）

---

## 📋 环境要求

| 项目 | 说明 |
| --- | --- |
| Java | 17 或更高 |
| 构建 | Gradle（仓库自带 Wrapper） |
| Gson | 2.11.0（`api` 依赖，会传递给调用方） |
| Java-WebSocket | 1.5.7（`api` 依赖） |
| SLF4J | 可选。本库以 `compileOnly` 引用，宿主项目自行提供实现即可看到日志 |

默认弹幕服务器：

```
wss://broadcastlv.chat.bilibili.com:2245/sub
```

可通过 `new DanmuClient(URI)` 换成其它节点。

---

## 📦 安装

本库目前随鼠鼠弹幕仓库以 Gradle 子项目形式提供，尚未发布到 Maven Central。任选一种接入方式即可。

### 作为 Gradle 子项目（推荐）

```gradle
include ':bili-live-danmu'
project(':bili-live-danmu').projectDir = file('bili-live-danmu')

dependencies {
    implementation project(':bili-live-danmu')
}
```

### 使用构建产物

在本目录执行：

```bash
# Windows
gradlew.bat clean build

# Linux / macOS
./gradlew clean build
```

产物位于 `build/libs/`：

| 文件 | 说明 |
| --- | --- |
| `bili-live-danmu-1.1.0.jar` | 主包 |
| `bili-live-danmu-1.1.0-sources.jar` | 源码 |
| `bili-live-danmu-1.1.0-javadoc.jar` | Javadoc |

再在业务项目中引入 JAR，并自行添加传递依赖：

```gradle
dependencies {
    implementation files('libs/bili-live-danmu-1.1.0.jar')
    implementation 'com.google.code.gson:gson:2.11.0'
    implementation 'org.java-websocket:Java-WebSocket:1.5.7'
}
```

坐标（若自行发布到本地 / 私有仓库）：

```
com.xiaoliang:bili-live-danmu:1.1.0
```

---

## 🚀 快速开始

下面是一份可运行的最小示例：连接指定房间，打印弹幕与礼物，60 秒后断开。

```java
import com.xiaoliang.bili.live.danmu.Auth;
import com.xiaoliang.bili.live.danmu.ConnectionListener;
import com.xiaoliang.bili.live.danmu.DanmuClient;
import com.xiaoliang.bili.live.danmu.handler.DanmuHandler;
import com.xiaoliang.bili.live.danmu.handler.GiftHandler;

public class Example {
    public static void main(String[] args) throws Exception {
        DanmuClient client = new DanmuClient();

        client.setListener(new ConnectionListener() {
            @Override
            public void onOpen() {
                System.out.println("已连接");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                System.out.println("已断开: " + reason + " (code=" + code + ")");
            }

            @Override
            public void onError(Exception ex) {
                ex.printStackTrace();
            }
        });

        client.addHandler(new DanmuHandler(danmu ->
                System.out.println(danmu.user.name + ": " + danmu.body)));
        client.addHandler(new GiftHandler(gift ->
                System.out.println(gift.user.name + " 赠送 " + gift.name + " x" + gift.num)));

        // Auth.create() 会发起 HTTP 请求，请勿在 UI / 渲染线程调用
        Auth auth = Auth.create(24256088L);   // 换成真实房间号；短号亦可
        client.connect(auth);

        Thread.sleep(60_000);
        client.disconnect();
    }
}
```

登录态连接（部分互动信息更完整）：

```java
String cookie = "DedeUserID=...; buvid3=...; bili_jct=...; SESSDATA=...";
Auth auth = Auth.create(24256088L, cookie);
client.connect(auth);
```

`connect()` 本身是异步的：调用返回后 WebSocket 仍在握手。连接成功会回调 `ConnectionListener.onOpen()`，可用 `isOpen()` 查询当前状态。

---

## 📖 API 说明

包名：`com.xiaoliang.bili.live.danmu`

### DanmuClient

核心客户端。一个实例对应一条弹幕长连接。

| 方法 | 说明 |
| --- | --- |
| `DanmuClient()` | 使用默认弹幕服务器 |
| `DanmuClient(URI serverUri)` | 指定 WebSocket 地址 |
| `void connect(Auth auth)` | 建立连接并发送鉴权包。若已有连接，会先关闭旧连接再连新的 |
| `void disconnect()` | 停止心跳并关闭连接 |
| `boolean isOpen()` | 当前 WebSocket 是否处于打开状态 |
| `void setListener(ConnectionListener listener)` | 设置连接状态回调 |
| `void addHandler(MessageHandler handler)` | 注册消息处理器，可重复添加多个 |
| `void removeHandler(MessageHandler handler)` | 按引用移除处理器 |
| `static void send(String cookie, String roomId, String message)` | HTTP 发送弹幕，需 Cookie 中含 `bili_jct` |

消息分发规则：服务器推送的 `SEND_SMS_REPLY` 包会被解析为 `Message`，再依次询问每个 handler 的 `canHandle()`；返回 `true` 的 handler 都会执行 `handle()`（不是互斥的）。

连接实现上的几个细节：

- 应用层每 **30 秒** 发送一次心跳。B 站弹幕服务器不响应 WebSocket Ping/Pong，因此库关闭了 Java-WebSocket 自带的丢线检测，避免 1～3 分钟后被误判为断线（关闭码 1006）。
- 心跳任务内部吞掉发送异常，避免一次失败导致后续心跳被调度器静默取消。
- `connect()` 替换旧连接时，会忽略旧连接迟到的 `onClose` / `onError`，避免误触发业务侧重连或停掉新连接的心跳。
- **库本身不自动重连**。断线后需要业务侧在 `onClose` / `onError` 里自行重连（鼠鼠弹幕模组就是这样做的）。

### Auth

进入直播间所需的鉴权信息。通常用工厂方法创建，不必手填字段。

```java
// 游客
Auth.create(long roomid)

// 登录用户（Cookie 中至少要有 DedeUserID、buvid3）
Auth.create(long roomid, String cookie)
```

两个工厂方法都会：

1. 拉取 WBI 密钥并对参数签名
2. 请求 `getDanmuInfo` 拿到弹幕 `token`
3. 若接口仍返回 `room_id`，优先使用真实房间号（兼容短号）；否则回退到传入的房间号

HTTP 层带超时与重试：连接超时 5 秒、请求超时 8 秒，遇 `IOException` 最多重试 3 次、间隔 1 秒。

| 字段 | 含义 |
| --- | --- |
| `uid` | 用户 UID，游客为 `0` |
| `roomid` | 用于鉴权的房间号 |
| `buvid` | 设备标识；游客由库生成 |
| `key` | 弹幕 token |
| `protover` | 固定为 `2`（Body 使用 zlib） |
| `platform` | 固定为 `web` |
| `type` | 固定为 `2` |

也可直接 `new Auth(roomid, uid, buvid, key)`，一般用于测试或自行拿到 token 的场景。

> `Auth.create()` 是阻塞的，内部有多段 HTTP。GUI / 游戏渲染线程请放到后台线程调用，否则会卡死界面。

### ConnectionListener

```java
public interface ConnectionListener {
    void onOpen();
    void onClose(int code, String reason, boolean remote);
    void onError(Exception ex);
}
```

| 回调 | 时机 |
| --- | --- |
| `onOpen` | WebSocket 握手成功，即将发送鉴权包并启动心跳 |
| `onClose` | 连接关闭。`remote == true` 表示对端关闭 |
| `onError` | 连接过程出现异常 |

### Message 与 MessageHandler

```java
public class Message {
    public String cmd;          // 如 DANMU_MSG、SEND_GIFT
    public JsonElement data;    // 多数 cmd 的载荷
    public JsonElement info;    // DANMU_MSG 使用 info 数组
}

public interface MessageHandler {
    boolean canHandle(Message message);
    void handle(Message message);
}
```

内置 handler 覆盖常见 cmd；未识别的 cmd 会被忽略。若要处理点赞、房间状态等，自己实现 `MessageHandler` 即可。

---

## 🧩 内置处理器

均在 `com.xiaoliang.bili.live.danmu.handler`，构造时传入 `Consumer<T>`。

| 类 | 匹配的 cmd | 回调类型 | 说明 |
| --- | --- | --- | --- |
| `DanmuHandler` | `DANMU_MSG`（文本） | `Danmu` | 普通弹幕。与表情通过 `info[0][12]` 区分 |
| `EomjiHandler` | `DANMU_MSG`（表情） | `Emoji` | 表情弹幕，含图片 URL。类名沿用历史拼写 |
| `GiftHandler` | `SEND_GIFT` | `Gift` | 投喂礼物 |
| `GuardHandler` | `USER_TOAST_MSG` | `Guard` | 舰长 / 提督 / 总督开通或续费 |
| `SuperChatHandler` | `SUPER_CHAT_MESSAGE` | `SuperChat` | 醒目留言 |
| `InteractiveHandler` | `INTERACT_WORD*` | `Interactive` | 进场、关注、分享等。新协议字段缺失时会尝试解析 `pb` |

完整接入示例：

```java
import com.xiaoliang.bili.live.danmu.handler.*;
import com.xiaoliang.bili.live.danmu.model.Interactive;

client.addHandler(new DanmuHandler(danmu -> { /* ... */ }));
client.addHandler(new EomjiHandler(emoji -> { /* ... */ }));
client.addHandler(new GiftHandler(gift -> { /* ... */ }));
client.addHandler(new GuardHandler(guard -> { /* ... */ }));
client.addHandler(new SuperChatHandler(sc -> { /* ... */ }));
client.addHandler(new InteractiveHandler(iv -> {
    System.out.println(Interactive.typeName(iv.type) + " " + iv.user.name);
}));
```

自定义 cmd 示例：

```java
client.addHandler(new MessageHandler() {
    @Override
    public boolean canHandle(Message message) {
        return "LIKE_INFO_V3_CLICK".equals(message.cmd);
    }

    @Override
    public void handle(Message message) {
        System.out.println("点赞: " + message.data);
    }
});
```

---

## 🧱 数据模型

均在 `com.xiaoliang.bili.live.danmu.model`，字段为 public，便于直接读取。

### User

几乎所有事件都带用户信息。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `uid` | `String` | 用户 UID |
| `name` | `String` | 昵称 |
| `fansMedal` | `FansMedal` | 粉丝勋章，可能为 `null` |
| `guardLevel` | `int` | `0` 无 · `1` 总督 · `2` 提督 · `3` 舰长 |

`FansMedal`：`name`（勋章名）、`level`（等级）。

### 各事件字段

**Danmu**（弹幕）

| 字段 | 说明 |
| --- | --- |
| `user` | 发送者 |
| `body` | 文本内容 |

**Emoji**（表情弹幕）

| 字段 | 说明 |
| --- | --- |
| `user` | 发送者 |
| `body` | 表情名称 / 文本 |
| `uri` | 表情图片 URL |

**Gift**（礼物）

| 字段 | 说明 |
| --- | --- |
| `id` | 礼物 ID |
| `user` | 赠送者 |
| `name` | 礼物名称 |
| `num` | 数量 |
| `price` | 金额，**单位：元**。金瓜子为 `total_coin / 1000`，银瓜子（免费礼物）为 `0` |

**Guard**（大航海）

| 字段 | 说明 |
| --- | --- |
| `id` | 对应礼物 ID |
| `user` | 开通者 |
| `name` | 角色名（舰长 / 提督 / 总督） |
| `level` | 与 `user.guardLevel` 相同 |
| `num` | 开通数量 |
| `unit` | 单位，如「月」 |
| `price` | 金额，**单位：元**（接口分为 `price / 1000`） |

**SuperChat**（醒目留言）

| 字段 | 说明 |
| --- | --- |
| `id` | SC ID |
| `user` | 发送者 |
| `body` | 文本 |
| `time` | 展示持续秒数 |
| `price` | 金额，**单位：元** |

**Interactive**（互动）

| 字段 | 说明 |
| --- | --- |
| `user` | 用户 |
| `type` | 见下表 |

| 常量 | 值 | 含义 |
| --- | --- | --- |
| `Interactive.ENTER` | 1 | 进入直播间 |
| `Interactive.FOLLOW` | 2 | 关注 |
| `Interactive.SHARE` | 3 | 分享 |
| `Interactive.SPECIAL_FOLLOW` | 4 | 特别关注 |
| `Interactive.MUTUAL_FOLLOW` | 5 | 互粉 |

`Interactive.typeName(int)` 可将数字转为 `ENTER` / `FOLLOW` 等字符串。

---

## 💬 发送弹幕

```java
DanmuClient.send(cookie, String.valueOf(roomId), "你好");
```

- 需要登录 Cookie，且包含 CSRF 令牌 `bili_jct`
- 走 `https://api.live.bilibili.com/msg/send`
- HTTP 非 200 时抛出 `IOException`
- **使用 Cookie 有账号与风控风险**，仅建议个人调试使用

---

## 🏗 工作流程

```
Auth.create(roomId[, cookie])
        │
        ├─ 拉取 WBI 密钥并签名
        └─ getDanmuInfo → token + 真实房间号
                │
                ▼
DanmuClient.connect(auth)
        │
        ├─ WebSocket 握手
        ├─ 发送 AUTH 包
        ├─ 每 30s 发送 HEARTBEAT
        └─ 收到 SEND_SMS_REPLY
                │
                ▼
        Gson → Message
                │
                ▼
        已注册的 MessageHandler
                │
                ├─ DanmuHandler / EomjiHandler
                ├─ GiftHandler / GuardHandler
                ├─ SuperChatHandler / InteractiveHandler
                └─ 你自己的 Handler
```

底层数据包由 `Packet` 处理：16 字节头 + Body；`version = 2` 时先 zlib 解压再递归拆包。业务代码一般不需要直接使用该类。

---

## 🔧 构建与测试

在 `bili-live-danmu` 目录下：

```bash
# 运行单元测试
gradlew.bat test          # Windows
./gradlew test            # Linux / macOS

# 指定测试类
./gradlew test --tests DanmuHandlerTest

# 构建 JAR（含 sources / javadoc）
./gradlew clean build
```

测试报告：`build/reports/tests/test/index.html`。

连接真实直播间的集成测试默认跳过，需设置环境变量后才会跑：

| 变量 | 说明 |
| --- | --- |
| `BILI_TEST_ROOM_ID` | 房间号（必填才会执行） |
| `BILI_TEST_COOKIE` | 可选，登录 Cookie |
| `BILI_TEST_DURATION_SECONDS` | 监听时长，默认 `90` |
| `BILI_TEST_RAW_MODE` | 原始 cmd 打印模式 |

更细的测试说明见 [TESTING.md](TESTING.md)。

---

## ⚠️ 注意事项

1. **协议非官方承诺**。B 站直播弹幕接口可能随时变更，本库按当前公开协议实现，不保证长期兼容。
2. **不要在主线程做 `Auth.create()`**。签名与 `getDanmuInfo` 都是同步 HTTP。
3. **没有内置重连**。请在 `onClose` / `onError` 中自行决定是否、何时再次 `connect()`。再次 `connect()` 是安全的，会先拆掉旧连接。
4. **Cookie 属于敏感凭据**。不要写入版本库或日志；发送弹幕可能触发风控。
5. **日志**。本库使用 SLF4J，宿主未绑定实现时不会打印日志。
6. **`EomjiHandler`** 是历史类名（Emoji 的拼写），请按此名称引用，不要自行改成 `EmojiHandler`。

---

## 📁 源码结构

```
com.xiaoliang.bili.live.danmu
├── DanmuClient          连接、心跳、消息分发、发送弹幕
├── Auth                 鉴权与 getDanmuInfo
├── ConnectionListener   连接回调
├── Message / MessageHandler
├── Packet               二进制协议
├── User                 用户与粉丝勋章
├── BiliWbiSign          WBI 签名
├── BiliHttp             带超时与重试的 HTTP
├── handler/             内置消息处理器
└── model/               Danmu / Emoji / Gift / Guard / SuperChat / Interactive
```

---

## 📄 许可证

本项目使用 [MIT](LICENSE) 许可证。

作者 / 维护：XiaoLiang LiQing
