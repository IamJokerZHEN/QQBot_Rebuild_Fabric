package org.minecralogy.qqbot.websocket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import org.minecralogy.qqbot.Bot;
import org.minecralogy.qqbot.Utils;
import org.slf4j.Logger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sender类继承自WebSocketClient，用于处理与WebSocket服务器的通信
 * 包含连接管理、消息发送和接收、重连机制等功能
 */
public class Sender extends WebSocketClient {
    // 使用LogUtils获取Logger实例，用于日志记录
    private static final Logger LOGGER = LogUtils.getLogger();
    // 使用可重入锁和条件变量实现线程同步
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    // 存储接收到的消息
    private String message;
    // 定时器任务，用于实现重连机制
    Timer task;
    /**
     * 构造函数1：使用URI创建WebSocket客户端
     * @param serverUri WebSocket服务器的URI地址
     */
    public Sender(URI serverUri) {
        super(serverUri);
    }
    /**
     * 构造函数2：使用字符串形式的URI创建WebSocket客户端，并添加认证头信息
     * @param serverUri WebSocket服务器的URI字符串
     * @throws URISyntaxException 如果URI格式不正确
     */
    public Sender(String serverUri) throws URISyntaxException {
        super(new URI(serverUri));
        // 创建包含名称和令牌的头部信息
        HashMap<String, String> headers = new HashMap<>();
        headers.put("name", Bot.config.getName());
        headers.put("token", Bot.config.getToken());
        // 编码并添加头部信息
        this.addHeader("info", Utils.encode(headers));
    }

    /**
     * 重写父类方法，当WebSocket连接打开时调用
     * @param serverHandshake 服务器握手信息
     */
    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        LOGGER.info("Sender:Connected to server");
        this.sendServerStartup();
        task.cancel();
    }

    /**
     * 重写父类方法，当接收到服务器消息时调用
     * @param s 接收到的消息字符串
     */
    @Override
    public void onMessage(String s) {
        this.lock.lock();
        try {
            this.message = s;
            this.condition.signalAll(); // 唤醒所有等待的线程
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * 重写父类方法，当连接关闭时调用
     * @param i 关闭码
     * @param s 关闭原因
     * @param b 是否关闭
     */


    @Override
    public void onClose(int i, String s, boolean b) {
        ReConnectedTask reConnectedTask = new ReConnectedTask();
        reConnectedTask.sender = this;
        task.schedule(reConnectedTask, 0, Bot.config.getReconnect_interval() * 1000L);
    }


    /**
     * 重写父类方法，当发生错误时调用
     * @param e 异常对象
     */
    @Override
    public void onError(Exception e) {

        // 错误处理逻辑（当前为空）
    }

    /**
     * 内部类：实现重连任务的定时器任务
     */
    private static class ReConnectedTask extends TimerTask {
        private Sender sender;  // 修改字段名和类型
        private int retryCount = 0;
        private static final int MAX_RETRIES = 5;

        @Override
        public void run() {
            try {
                sender.connectBlocking();
            } catch (InterruptedException e) {
                LOGGER.error("Reconnection failed", e);
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 发送数据到服务器
     * @param event_type 事件类型
     * @param data 要发送的数据
     * @param waitResponse 是否等待响应
     * @return 如果发送成功且（不需要响应或收到成功响应）返回true，否则返回false
     */
    public boolean sendData(String event_type, Object data, Boolean waitResponse) {
        boolean responseReceived = false;
        HashMap<String, Object> messageData = new HashMap<>();
        messageData.put("data", data);
        messageData.put("type", event_type);
        try {
            this.send(Utils.encode(messageData));
        } catch (WebsocketNotConnectedException error) {
            LOGGER.warn("Can't send data");
            return false;
        }
        if (!waitResponse) return true;
        // 等待响应，最多等待5秒
        this.lock.lock();
        try {
            responseReceived = this.condition.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            this.lock.unlock();
        }
        if (!responseReceived) {
            LOGGER.warn("Wait response time out");
            return false;
        }
        // 解析并返回响应结果
        HashMap<String, ?> hm = (new Gson().fromJson(Utils.decode(this.message), new TypeToken<HashMap<String, Object>>() {}.getType()));
        return (boolean) hm.get("success");
    }
    /**
     * 发送服务器启动消息
     */
    public void sendServerStartup() {
        HashMap<String, Object> data = new HashMap<>();
        if (this.sendData("server_startup", data, true)) LOGGER.info("Send {} message success", "server start");
        else LOGGER.warn("Can't send {} message", "server start");
    }

    /**
     * 发送服务器关闭消息
     */
    // sendServerShutdown() - 服务器关闭时发送通知
    public void sendServerShutdown() {
        HashMap<String, Object> data = new HashMap<>();
        if (this.sendData("server_shutdown", data, true))
            LOGGER.info("Send {} message success", "server stop");
        else
            LOGGER.warn("Can't send {} message", "server stop");
    }

    // sendSynchronousMessage() - 发送同步消息
    public boolean sendSynchronousMessage(String message) {
        return this.sendData("message", message, true);
    }



    /**
     * 发送玩家离开消息
     * @param name 玩家名称
     */
    public void sendPlayerLeft(String name) {
        if (this.sendData("player_left", name, true)) LOGGER.info("Send {} message success", "player left");
        else LOGGER.warn("Can't send {} message", "player left");
    }

    /**
     * 发送玩家加入消息
     * @param name 玩家名称
     */
    public void sendPlayerJoined(String name) {
        if (this.sendData("player_joined", name, true)) LOGGER.info("Send {} message success", "player joined");
        else LOGGER.warn("Can't send {} message", "player joined");
    }

    /**
     * 发送玩家聊天消息
     * @param name 玩家名称
     * @param message 聊天内容
     */
    public void sendPlayerChat(String name, String message) {
        List<String> data = new ArrayList<>();
        data.add(name);
        data.add(message);
        this.sendData("player_chat", data, false);
        LOGGER.info("Send {} message success", "player");
    }

    /**
     * 发送玩家死亡消息
     * @param name 玩家名称
     * @param message 死亡信息
     */
    public void sendPlayerDeath(String name, String message) {
        List<String> data = new ArrayList<>();
        data.add(name);
        data.add(message);
        if (this.sendData("player_death", data, true)) LOGGER.info("Send {} message success", "player death");
        else LOGGER.warn("Can't send {} message", "player death");
    }

    /**
     * 发送同步消息

 * 该方法用于发送同步消息，消息内容作为参数传入
     * @param message 要发送的消息内容，类型为String
     * @return 发送是否成功，返回boolean类型，true表示成功，false表示失败
     */

    }

