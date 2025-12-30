package com.lixiangyu.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket 工具类
 * 提供 WebSocket 客户端连接、发送消息、接收消息等功能
 *
 * @author lixiangyu
 */
@Slf4j
public class WebSocketUtil {

    /**
     * WebSocket 客户端端点
     */
    @ClientEndpoint
    public static class WebSocketClient {

        private Session session;
        private URI serverUri;
        private Consumer<String> onMessageCallback;
        private Consumer<String> onErrorCallback;
        private Runnable onOpenCallback;
        private Runnable onCloseCallback;
        private String lastMessage;
        private CountDownLatch messageLatch;
        private Exception lastError;

        /**
         * 连接 WebSocket 服务器
         *
         * @param serverUri 服务器 URI
         * @throws Exception 连接异常
         */
        public void connect(URI serverUri) throws Exception {
            this.serverUri = serverUri;
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, serverUri);
        }

        /**
         * 连接 WebSocket 服务器（带超时）
         *
         * @param serverUri 服务器 URI
         * @param timeout   超时时间（秒）
         * @throws Exception 连接异常
         */
        public void connect(URI serverUri, int timeout) throws Exception {
            this.serverUri = serverUri;
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxSessionIdleTimeout(timeout * 1000L);
            container.connectToServer(this, serverUri);
        }

        /**
         * 连接打开时调用
         *
         * @param session WebSocket 会话
         */
        @OnOpen
        public void onOpen(Session session) {
            this.session = session;
            log.info("WebSocket 连接已打开，URI: {}", serverUri);
            if (onOpenCallback != null) {
                try {
                    onOpenCallback.run();
                } catch (Exception e) {
                    log.error("执行 onOpen 回调失败", e);
                }
            }
        }

        /**
         * 接收文本消息时调用
         *
         * @param message 消息内容
         */
        @OnMessage
        public void onMessage(String message) {
            log.debug("收到 WebSocket 消息: {}", message);
            this.lastMessage = message;
            if (messageLatch != null) {
                messageLatch.countDown();
            }
            if (onMessageCallback != null) {
                try {
                    onMessageCallback.accept(message);
                } catch (Exception e) {
                    log.error("执行 onMessage 回调失败", e);
                }
            }
        }

        /**
         * 接收二进制消息时调用
         *
         * @param bytes 二进制数据
         */
        @OnMessage
        public void onMessage(ByteBuffer bytes) {
            log.debug("收到 WebSocket 二进制消息，长度: {}", bytes.remaining());
            String message = new String(bytes.array(), bytes.position(), bytes.remaining());
            onMessage(message);
        }

        /**
         * 连接关闭时调用
         *
         * @param session WebSocket 会话
         * @param reason 关闭原因
         */
        @OnClose
        public void onClose(Session session, CloseReason reason) {
            log.info("WebSocket 连接已关闭，URI: {}, 原因: {}", serverUri, reason);
            this.session = null;
            if (onCloseCallback != null) {
                try {
                    onCloseCallback.run();
                } catch (Exception e) {
                    log.error("执行 onClose 回调失败", e);
                }
            }
        }

        /**
         * 发生错误时调用
         *
         * @param session WebSocket 会话
         * @param error   错误信息
         */
        @OnError
        public void onError(Session session, Throwable error) {
            log.error("WebSocket 发生错误，URI: {}", serverUri, error);
            this.lastError = new Exception("WebSocket 错误: " + error.getMessage(), error);
            if (onErrorCallback != null) {
                try {
                    onErrorCallback.accept(error.getMessage());
                } catch (Exception e) {
                    log.error("执行 onError 回调失败", e);
                }
            }
        }

        /**
         * 发送文本消息
         *
         * @param message 消息内容
         * @throws IOException IO 异常
         */
        public void sendMessage(String message) throws IOException {
            if (session == null || !session.isOpen()) {
                throw new IllegalStateException("WebSocket 连接未打开");
            }
            session.getBasicRemote().sendText(message);
            log.debug("发送 WebSocket 消息: {}", message);
        }

        /**
         * 发送二进制消息
         *
         * @param bytes 二进制数据
         * @throws IOException IO 异常
         */
        public void sendMessage(ByteBuffer bytes) throws IOException {
            if (session == null || !session.isOpen()) {
                throw new IllegalStateException("WebSocket 连接未打开");
            }
            session.getBasicRemote().sendBinary(bytes);
            log.debug("发送 WebSocket 二进制消息，长度: {}", bytes.remaining());
        }

        /**
         * 同步发送消息并等待响应
         *
         * @param message 消息内容
         * @param timeout 超时时间（秒）
         * @return 响应消息
         * @throws Exception 异常
         */
        public String sendMessageAndWait(String message, int timeout) throws Exception {
            messageLatch = new CountDownLatch(1);
            lastMessage = null;
            sendMessage(message);
            boolean received = messageLatch.await(timeout, TimeUnit.SECONDS);
            if (!received) {
                throw new Exception("等待 WebSocket 响应超时");
            }
            return lastMessage;
        }

        /**
         * 关闭连接
         *
         * @throws IOException IO 异常
         */
        public void close() throws IOException {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }

        /**
         * 检查连接是否打开
         *
         * @return 是否打开
         */
        public boolean isOpen() {
            return session != null && session.isOpen();
        }

        /**
         * 获取会话
         *
         * @return WebSocket 会话
         */
        public Session getSession() {
            return session;
        }

        // Getter 和 Setter 方法
        public void setOnMessageCallback(Consumer<String> onMessageCallback) {
            this.onMessageCallback = onMessageCallback;
        }

        public void setOnErrorCallback(Consumer<String> onErrorCallback) {
            this.onErrorCallback = onErrorCallback;
        }

        public void setOnOpenCallback(Runnable onOpenCallback) {
            this.onOpenCallback = onOpenCallback;
        }

        public void setOnCloseCallback(Runnable onCloseCallback) {
            this.onCloseCallback = onCloseCallback;
        }

        public Exception getLastError() {
            return lastError;
        }
    }

    /**
     * WebSocket 客户端管理器
     */
    public static class WebSocketClientManager {

        private final Map<String, WebSocketClient> clients = new ConcurrentHashMap<>();

        /**
         * 创建并连接 WebSocket 客户端
         *
         * @param key       客户端标识
         * @param serverUri 服务器 URI
         * @return WebSocket 客户端
         * @throws Exception 连接异常
         */
        public WebSocketClient createClient(String key, URI serverUri) throws Exception {
            WebSocketClient client = new WebSocketClient();
            client.connect(serverUri);
            clients.put(key, client);
            return client;
        }

        /**
         * 创建并连接 WebSocket 客户端（带超时）
         *
         * @param key       客户端标识
         * @param serverUri 服务器 URI
         * @param timeout   超时时间（秒）
         * @return WebSocket 客户端
         * @throws Exception 连接异常
         */
        public WebSocketClient createClient(String key, URI serverUri, int timeout) throws Exception {
            WebSocketClient client = new WebSocketClient();
            client.connect(serverUri, timeout);
            clients.put(key, client);
            return client;
        }

        /**
         * 获取客户端
         *
         * @param key 客户端标识
         * @return WebSocket 客户端
         */
        public WebSocketClient getClient(String key) {
            return clients.get(key);
        }

        /**
         * 移除客户端
         *
         * @param key 客户端标识
         * @throws IOException IO 异常
         */
        public void removeClient(String key) throws IOException {
            WebSocketClient client = clients.remove(key);
            if (client != null) {
                client.close();
            }
        }

        /**
         * 关闭所有客户端
         *
         * @throws IOException IO 异常
         */
        public void closeAll() throws IOException {
            for (WebSocketClient client : clients.values()) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error("关闭 WebSocket 客户端失败", e);
                }
            }
            clients.clear();
        }
    }

    /**
     * 全局 WebSocket 客户端管理器
     */
    private static final WebSocketClientManager manager = new WebSocketClientManager();

    /**
     * 创建并连接 WebSocket 客户端
     *
     * @param url 服务器 URL
     * @return WebSocket 客户端
     * @throws Exception 连接异常
     */
    public static WebSocketClient connect(String url) throws Exception {
        URI uri = URI.create(url);
        WebSocketClient client = new WebSocketClient();
        client.connect(uri);
        return client;
    }

    /**
     * 创建并连接 WebSocket 客户端（带超时）
     *
     * @param url     服务器 URL
     * @param timeout 超时时间（秒）
     * @return WebSocket 客户端
     * @throws Exception 连接异常
     */
    public static WebSocketClient connect(String url, int timeout) throws Exception {
        URI uri = URI.create(url);
        WebSocketClient client = new WebSocketClient();
        client.connect(uri, timeout);
        return client;
    }

    /**
     * 创建并连接 WebSocket 客户端（带回调）
     *
     * @param url             服务器 URL
     * @param onMessageCallback 消息回调
     * @param onErrorCallback   错误回调
     * @return WebSocket 客户端
     * @throws Exception 连接异常
     */
    public static WebSocketClient connect(String url, Consumer<String> onMessageCallback, Consumer<String> onErrorCallback) throws Exception {
        WebSocketClient client = connect(url);
        client.setOnMessageCallback(onMessageCallback);
        client.setOnErrorCallback(onErrorCallback);
        return client;
    }

    /**
     * 获取全局管理器
     *
     * @return WebSocket 客户端管理器
     */
    public static WebSocketClientManager getManager() {
        return manager;
    }
}

