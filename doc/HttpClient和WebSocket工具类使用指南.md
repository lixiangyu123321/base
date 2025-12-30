# HttpClient 和 WebSocket 工具类使用指南

## 文档信息

- **创建日期**: 2025-12-30
- **工具类位置**: `com.lixiangyu.common.util.HttpClientUtil` 和 `com.lixiangyu.common.util.WebSocketUtil`
- **依赖**: Apache HttpClient 4.5.14、javax.websocket-api 1.1、Tyrus WebSocket Client 1.17

---

## HttpClientUtil 工具类

### 功能概述

`HttpClientUtil` 是基于 Apache HttpClient 封装的 HTTP 请求工具类，提供了以下功能：

- **GET 请求**：支持带参数、带请求头的 GET 请求
- **POST 请求**：支持 JSON、表单、文件上传的 POST 请求
- **PUT 请求**：支持 JSON 格式的 PUT 请求
- **DELETE 请求**：支持 DELETE 请求
- **超时配置**：支持自定义连接超时、读取超时、请求超时
- **请求头设置**：支持自定义请求头

### 使用示例

#### 1. GET 请求

```java
// 简单 GET 请求
String response = HttpClientUtil.doGet("http://example.com/api/data");

// 带参数的 GET 请求
Map<String, String> params = new HashMap<>();
params.put("page", "1");
params.put("size", "10");
String response = HttpClientUtil.doGet("http://example.com/api/data", null, params);

// 带请求头的 GET 请求
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer token123");
String response = HttpClientUtil.doGet("http://example.com/api/data", headers, null);
```

#### 2. POST 请求（JSON）

```java
// 简单 POST JSON 请求
String json = "{\"name\":\"test\",\"age\":18}";
String response = HttpClientUtil.doPostJson("http://example.com/api/users", json);

// 带请求头的 POST JSON 请求
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer token123");
String response = HttpClientUtil.doPostJson("http://example.com/api/users", json, headers);
```

#### 3. POST 请求（表单）

```java
// 简单 POST 表单请求
Map<String, String> params = new HashMap<>();
params.put("username", "admin");
params.put("password", "123456");
String response = HttpClientUtil.doPostForm("http://example.com/api/login", params);

// 带请求头的 POST 表单请求
Map<String, String> headers = new HashMap<>();
headers.put("Content-Type", "application/x-www-form-urlencoded");
String response = HttpClientUtil.doPostForm("http://example.com/api/login", params, headers);
```

#### 4. POST 请求（文件上传）

```java
// 文件上传
File file = new File("test.txt");
Map<String, String> params = new HashMap<>();
params.put("description", "测试文件");
String response = HttpClientUtil.doPostFile("http://example.com/api/upload", file, "test.txt", params);
```

#### 5. PUT 请求

```java
// PUT 请求
String json = "{\"id\":1,\"name\":\"updated\"}";
String response = HttpClientUtil.doPutJson("http://example.com/api/users/1", json);
```

#### 6. DELETE 请求

```java
// 简单 DELETE 请求
String response = HttpClientUtil.doDelete("http://example.com/api/users/1");

// 带请求头的 DELETE 请求
Map<String, String> headers = new HashMap<>();
headers.put("Authorization", "Bearer token123");
String response = HttpClientUtil.doDelete("http://example.com/api/users/1", headers);
```

#### 7. 自定义超时

```java
// 创建自定义超时的 HttpClient
CloseableHttpClient httpClient = HttpClientUtil.createHttpClient(3000, 5000, 5000);
// 注意：使用自定义 HttpClient 需要手动管理连接和关闭
```

### 异常处理

所有方法在发生异常时会抛出 `RuntimeException`，建议使用 try-catch 捕获：

```java
try {
    String response = HttpClientUtil.doGet("http://example.com/api/data");
    System.out.println(response);
} catch (RuntimeException e) {
    log.error("HTTP 请求失败", e);
}
```

---

## WebSocketUtil 工具类

### 功能概述

`WebSocketUtil` 是基于 Java WebSocket API 封装的 WebSocket 客户端工具类，提供了以下功能：

- **WebSocket 连接**：支持连接 WebSocket 服务器
- **消息发送**：支持发送文本和二进制消息
- **消息接收**：支持接收文本和二进制消息
- **回调机制**：支持连接打开、消息接收、错误处理、连接关闭的回调
- **同步等待**：支持发送消息后同步等待响应
- **连接管理**：支持管理多个 WebSocket 连接

### 使用示例

#### 1. 简单连接和发送消息

```java
// 连接 WebSocket 服务器
WebSocketUtil.WebSocketClient client = WebSocketUtil.connect("ws://example.com/websocket");

// 等待连接建立
Thread.sleep(1000);

// 发送消息
client.sendMessage("Hello, WebSocket!");

// 关闭连接
client.close();
```

#### 2. 带回调的连接

```java
// 连接并设置回调
WebSocketUtil.WebSocketClient client = WebSocketUtil.connect(
    "ws://example.com/websocket",
    message -> {
        // 收到消息时的回调
        System.out.println("收到消息: " + message);
    },
    error -> {
        // 发生错误时的回调
        System.err.println("错误: " + error);
    }
);

// 设置连接打开时的回调
client.setOnOpenCallback(() -> {
    System.out.println("连接已打开");
});

// 设置连接关闭时的回调
client.setOnCloseCallback(() -> {
    System.out.println("连接已关闭");
});
```

#### 3. 同步发送消息并等待响应

```java
// 连接
WebSocketUtil.WebSocketClient client = WebSocketUtil.connect("ws://example.com/websocket");
Thread.sleep(1000);

// 发送消息并等待响应（超时时间 5 秒）
String response = client.sendMessageAndWait("请求数据", 5);
System.out.println("收到响应: " + response);
```

#### 4. 发送二进制消息

```java
// 连接
WebSocketUtil.WebSocketClient client = WebSocketUtil.connect("ws://example.com/websocket");
Thread.sleep(1000);

// 发送二进制消息
byte[] data = "Hello, Binary!".getBytes();
ByteBuffer buffer = ByteBuffer.wrap(data);
client.sendMessage(buffer);
```

#### 5. 使用连接管理器

```java
// 获取全局连接管理器
WebSocketUtil.WebSocketClientManager manager = WebSocketUtil.getManager();

// 创建并连接客户端
URI uri = URI.create("ws://example.com/websocket");
WebSocketUtil.WebSocketClient client1 = manager.createClient("client1", uri);
WebSocketUtil.WebSocketClient client2 = manager.createClient("client2", uri);

// 获取客户端
WebSocketUtil.WebSocketClient client = manager.getClient("client1");

// 发送消息
client.sendMessage("Hello from client1");

// 移除客户端
manager.removeClient("client1");

// 关闭所有客户端
manager.closeAll();
```

#### 6. 自定义超时连接

```java
// 连接并设置超时时间（秒）
WebSocketUtil.WebSocketClient client = WebSocketUtil.connect("ws://example.com/websocket", 10);
```

### 异常处理

WebSocket 操作可能抛出 `IOException` 或其他异常，建议使用 try-catch 捕获：

```java
try {
    WebSocketUtil.WebSocketClient client = WebSocketUtil.connect("ws://example.com/websocket");
    client.sendMessage("Hello");
} catch (Exception e) {
    log.error("WebSocket 操作失败", e);
}
```

---

## 完整示例

### 示例 1：HTTP 客户端调用 RESTful API

```java
@Service
public class UserService {
    
    private static final String API_BASE_URL = "http://example.com/api";
    
    public String getUser(Long id) {
        String url = API_BASE_URL + "/users/" + id;
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        return HttpClientUtil.doGet(url, headers, null);
    }
    
    public String createUser(String userJson) {
        String url = API_BASE_URL + "/users";
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        return HttpClientUtil.doPostJson(url, userJson, headers);
    }
    
    public String updateUser(Long id, String userJson) {
        String url = API_BASE_URL + "/users/" + id;
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        return HttpClientUtil.doPutJson(url, userJson, headers);
    }
    
    public String deleteUser(Long id) {
        String url = API_BASE_URL + "/users/" + id;
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer token123");
        return HttpClientUtil.doDelete(url, headers);
    }
}
```

### 示例 2：WebSocket 客户端实时通信

```java
@Component
public class WebSocketClientService {
    
    private WebSocketUtil.WebSocketClient client;
    
    @PostConstruct
    public void init() {
        try {
            // 连接 WebSocket 服务器
            client = WebSocketUtil.connect(
                "ws://example.com/websocket",
                this::onMessage,
                this::onError
            );
            
            // 设置连接打开时的回调
            client.setOnOpenCallback(() -> {
                log.info("WebSocket 连接已打开");
                // 连接成功后发送认证消息
                try {
                    client.sendMessage("{\"type\":\"auth\",\"token\":\"token123\"}");
                } catch (IOException e) {
                    log.error("发送认证消息失败", e);
                }
            });
            
            // 设置连接关闭时的回调
            client.setOnCloseCallback(() -> {
                log.info("WebSocket 连接已关闭");
                // 可以在这里实现重连逻辑
            });
        } catch (Exception e) {
            log.error("WebSocket 连接失败", e);
        }
    }
    
    private void onMessage(String message) {
        log.info("收到 WebSocket 消息: {}", message);
        // 处理收到的消息
    }
    
    private void onError(String error) {
        log.error("WebSocket 错误: {}", error);
    }
    
    public void sendMessage(String message) {
        if (client != null && client.isOpen()) {
            try {
                client.sendMessage(message);
            } catch (IOException e) {
                log.error("发送 WebSocket 消息失败", e);
            }
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                log.error("关闭 WebSocket 连接失败", e);
            }
        }
    }
}
```

---

## 注意事项

### HttpClientUtil

1. **连接管理**：每次请求都会创建新的 HttpClient，请求完成后自动关闭。如果需要复用连接，可以创建自定义的 HttpClient。
2. **超时设置**：默认连接超时 5 秒，读取超时 10 秒，请求超时 10 秒。可以根据实际需求调整。
3. **异常处理**：所有方法在发生异常时会抛出 `RuntimeException`，需要适当处理。
4. **请求头**：如果不设置请求头，POST JSON 请求会自动设置 `Content-Type: application/json`，POST 表单请求会自动设置 `Content-Type: application/x-www-form-urlencoded`。

### WebSocketUtil

1. **连接状态**：发送消息前需要确保连接已打开，可以使用 `client.isOpen()` 检查。
2. **线程安全**：WebSocket 客户端不是线程安全的，多线程访问时需要注意同步。
3. **异常处理**：WebSocket 操作可能抛出 `IOException` 或其他异常，需要适当处理。
4. **连接管理**：使用连接管理器可以方便地管理多个 WebSocket 连接。
5. **超时设置**：连接超时时间需要在连接时设置，无法在连接后修改。

---

## 依赖说明

### Maven 依赖

工具类已自动添加以下依赖到 `common/pom.xml`：

```xml
<!-- Apache HttpClient -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.14</version>
</dependency>

<!-- Apache HttpClient Fluent API -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>fluent-hc</artifactId>
    <version>4.5.14</version>
</dependency>

<!-- Apache HttpMime（用于文件上传） -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpmime</artifactId>
    <version>4.5.14</version>
</dependency>

<!-- WebSocket API -->
<dependency>
    <groupId>javax.websocket</groupId>
    <artifactId>javax.websocket-api</artifactId>
    <version>1.1</version>
</dependency>

<!-- Tyrus WebSocket 实现 -->
<dependency>
    <groupId>org.glassfish.tyrus</groupId>
    <artifactId>tyrus-client</artifactId>
    <version>1.17</version>
</dependency>
```

---

**文档版本**: 1.0  
**最后更新**: 2025-12-30

