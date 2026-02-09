package org.minecralogy.qqbot.websocket;
import net.minecraft.server.permissions.PermissionSet;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
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
import net.minecraft.server.players.PlayerList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Listener extends WebSocketClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Timer task = new Timer();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Deprecated
    public Listener(URI serverUri) {
        super(serverUri);
    }

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
            this.connectBlocking(5000, TimeUnit.MILLISECONDS);
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

    // ✅ 使用反射创建 PermissionSet
    // 将返回类型改为 PermissionSet
    // ✅ 修改返回类型为 PermissionSet
    private PermissionSet createPermissionSet(int level) {
        try {
            Class<?> permissionSetClass = Class.forName("net.minecraft.server.permissions.PermissionSet");

            // 1.21.11 中，level 4 使用 ALL_PERMISSIONS
            if (level >= 4) {
                try {
                    return (PermissionSet) permissionSetClass.getField("ALL_PERMISSIONS").get(null);
                } catch (Exception e) {
                    LOGGER.warn("无法获取 ALL_PERMISSIONS，尝试 of 方法");
                }
            }

            // 尝试 of(int) 方法（1.21.11 推荐方式）
            try {
                Method ofMethod = permissionSetClass.getMethod("of", int.class);
                return (PermissionSet) ofMethod.invoke(null, level);
            } catch (NoSuchMethodException e) {
                // 尝试 from(int) 方法（旧版兼容）
                Method fromMethod = permissionSetClass.getMethod("from", int.class);
                return (PermissionSet) fromMethod.invoke(null, level);
            }

        } catch (Exception e) {
            LOGGER.error("反射创建 PermissionSet 失败: {}", e.getMessage());
            // 返回默认权限集
            return null;
        }
    }

    @Override
    public void onMessage(String s) {
        String msg = new String(s.getBytes(), StandardCharsets.UTF_8);
        msg = Utils.decode(msg);

        LOGGER.warn("Received message: {}", msg);

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

                    // ✅ 使用反射创建 PermissionSet
                    PermissionSet permissionSet = PermissionSet.ALL_PERMISSIONS;
                    // 如果反射失败，使用 null（可能构造函数能接受 null）
                    if (permissionSet == null) {
                        LOGGER.warn("PermissionSet 创建失败，尝试使用 null");
                    }

                    BotCommandSource botCommandSource = new BotCommandSource(
                            server,
                            new Vec3(0, 0, 0),  // 简化位置
                            Vec2.ZERO,
                            server.overworld(),
                            permissionSet ,  // 可能是 null，让构造函数处理
                            "Server",
                            Component.literal("Server"),
                            server,
                            null
                    );

                    server.getCommands().performPrefixedCommand(
                            botCommandSource,
                            message.data
                    );

                    response = botCommandSource.getResult();
                    success = botCommandSource.isSuccess();
                    if (response.isEmpty()) {
                        response = "命令执行完成";
                    }
                    break;

                case "player_list":
                    PlayerList playerList = Bot.server.getPlayerList();
                    List<ServerPlayer> players = playerList.getPlayers();
                    List<String> playerNames = new ArrayList<>();
                    for (ServerPlayer player : players) {
                        playerNames.add(player.getName().getString());
                    }
                    response = String.join(", ", playerNames).replace("\n", "");
                    success = true;
                    break;

                case "message":
                    Bot.server.sendSystemMessage(Component.literal(message.data));
                    response = "Message sent";
                    success = true;
                    break;

                case "server_occupation":
                    List<ServerPlayer> onlinePlayers = Bot.server.getPlayerList().getPlayers();
                    response = String.valueOf(onlinePlayers.size());
                    success = true;
                    break;

                case "server_status":
                    response = "Server is running";
                    success = true;
                    break;

                case "server_version":
                    response = Bot.server.getServerVersion();
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
            LOGGER.warn("Sent response to bot: {}", responseStr);
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
                this.cancel();
                return;
            }
            retryCount++;

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