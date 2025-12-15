package org.minecralogy.qqbot.websocket;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.minecralogy.qqbot.BotCommandSource;
import org.minecralogy.qqbot.Utils;
import org.slf4j.Logger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.minecralogy.qqbot.Bot;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.PlayerManager;

/**
 * WebSocket客户端监听器类，继承自WebSocketClient，用于处理与WebSocket服务器的连接和通信
 */
public class Listener extends WebSocketClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Timer task = new Timer();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /**
     * 构造函数1，使用URI创建WebSocket客户端
     * @param serverUri WebSocket服务器的URI地址
     */
    @Deprecated
    public Listener(URI serverUri) {
        super(serverUri);
    }

    /**
     * 构造函数2，使用字符串形式的URI创建WebSocket客户端，并添加自定义请求头
     * @param serverUri WebSocket服务器的URI地址字符串
     * @throws URISyntaxException 如果URI格式不正确则抛出此异常
     */
    public Listener(String serverUri) throws URISyntaxException {
        super(new URI(serverUri));
        HashMap<String, String> headers = new HashMap<>();
        headers.put("name", Bot.config.getName());
        headers.put("token", Bot.config.getToken());
        this.addHeader("type", "Fabric");
        this.addHeader("info", Utils.encode(headers));
        LOGGER.info("[QQBOT][INFO]WEBSOCKET 正在连接，URI: {}", serverUri);
        LOGGER.info("[QQBOT][INFO]请求头信息: {}", headers);
        try {
            this.connectBlocking(5000, TimeUnit.MILLISECONDS);  // 5秒超时
        } catch (InterruptedException e) {
            LOGGER.error("[QQBOT][ERROR]连接被中断: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    public void connectWithTimeout(int timeout) throws Exception {
        this.connectBlocking(timeout, TimeUnit.MILLISECONDS);
    }


    public boolean isConnected() {
        return this.isOpen() && !this.isClosing();
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        LOGGER.info("[QQBOT][INFO][SUCCESS]WEBSOCKET 连接成功");
        LOGGER.info("[QQBOT][INFO]服务器响应: {}", serverHandshake.getHttpStatusMessage());
        task.cancel();
    }


    @Override
    public void onMessage(String s) {
        String msg = new String(s.getBytes(), StandardCharsets.UTF_8);
        msg = Utils.decode(msg);

        LOGGER.info("Received message: {}", msg);

        if (!Bot.isValidStrictly(msg, Package.class)) {
            LOGGER.warn("Not valid message: {}", msg);
            return;
        }

        Package message;
        try {
            message = new Gson().fromJson(msg, Package.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse message: {}", msg, e);
            return;
        }

        String response = null;
        HashMap<String, Object> responseMessage = new HashMap<>();
        boolean success = false;

        try {
            switch (message.type) {
                case "command":

                    MinecraftServer server = Bot.server;
                    BotCommandSource botCommandSource = new BotCommandSource(
                            server,
                            server.getOverworld() == null ? Vec3d.ZERO : new Vec3d(
                                    server.getOverworld().getLevelProperties().getSpawnPoint().getPos().getX(),
                                    server.getOverworld().getLevelProperties().getSpawnPoint().getPos().getY(),
                                    server.getOverworld().getLevelProperties().getSpawnPoint().getPos().getZ()
                            ),
                            Vec2f.ZERO,
                            server.getOverworld(),
                            4,
                            "Server",
                            Text.literal("Server"),
                            server,
                            null
                    );

                    server.getCommandManager().execute(
                            server.getCommandManager().getDispatcher().parse(message.data, botCommandSource),
                            message.data
                    );
                    response = botCommandSource.getResult();  // getResult()返回String
                    success = botCommandSource.isSuccess();
                    if (response.isEmpty()) {
                        response = "命令执行完成";
                    }
                    break;

                case "player_list":
                    PlayerManager playerManager = Bot.server.getPlayerManager();
                    List<ServerPlayerEntity> players = playerManager.getPlayerList();
                    List<String> playerNames = new ArrayList<>();
                    for(ServerPlayerEntity player : players) {
                        playerNames.add(player.getName().getString());
                    }
                    response = String.join(", ", playerNames).replace("\n", "");
                    success = true;
                    break;



                case "message":
                    Bot.server.sendMessage(Text.of(message.data));
                    response = "Message sent";
                    success = true;
                    break;
                case "server_occupation":
                    List<ServerPlayerEntity> onlinePlayers = Bot.server.getPlayerManager().getPlayerList();
                    response = String.valueOf(onlinePlayers.size());
                    success = true;
                    break;

                case "server_status":
                    response = "Server is running";
                    success = true;
                    break;
                    case "server_version":
                        response = Bot.server.getVersion();
                        success = true;
                        break;

                default:
                    LOGGER.warn("Unknown package type from bot: {}", message.type);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing message: {}", msg, e);
            success = false;
            response = e.getMessage();
        }

        responseMessage.put("success", success);
        responseMessage.put("data", response);

        try {
            String responseStr = Utils.encode(responseMessage);
            this.send(responseStr);
            LOGGER.info("Sent response to bot: {}", responseStr);
        } catch (Exception e) {
            LOGGER.error("Error sending response", e);
        }
    }


    @Override
    public void onClose(int i, String s, boolean b) {
        ReConnectedTask reConnectedTask = new ReConnectedTask();
        reConnectedTask.listener = this;
        task.schedule(reConnectedTask, 0, Bot.config.getReconnect_interval() * 1000L);
    }



    @Override
    public void onError(Exception e) {
        LOGGER.error("[QQBOT][INFO][ERROR]WEBSOCKET 连接失败");
        LOGGER.error("[QQBOT][INFO]错误类型: {}", e.getClass().getSimpleName());
        LOGGER.error("[QQBOT][INFO]错误详情: {}", e.getMessage());
        if (e.getCause() != null) {
            LOGGER.error("[QQBOT][INFO]根本原因: {}", e.getCause().getMessage());
        }
        e.printStackTrace();
        if (!this.isOpen()) {
            onClose(-1, e.getMessage(), false);
        }
    }


    private static class ReConnectedTask extends TimerTask {
        private Listener listener;

        private int retryCount = 0;
        private static final int MAX_RETRIES = 5;

        @Override
        public void run() {
            if (retryCount >= MAX_RETRIES) {
                LOGGER.error("Maximum retry attempts ({}) reached", MAX_RETRIES);
                return;
            }

            try {
                LOGGER.info("Attempting to reconnect... (Attempt {}/{})", retryCount + 1, MAX_RETRIES);
                listener.connectBlocking();
                retryCount = 0;
                LOGGER.info("Reconnected successfully");
            } catch (InterruptedException e) {
                LOGGER.error("Reconnection interrupted", e);
                retryCount++;
            } catch (Exception e) {
                LOGGER.error("Reconnection failed", e);
                retryCount++;
            }
        }
    }

}
