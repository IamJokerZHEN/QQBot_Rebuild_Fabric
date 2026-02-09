package org.minecralogy.qqbot;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.minecralogy.qqbot.command.QQCommand;
import org.minecralogy.qqbot.command.QQBotCommand;
import org.minecralogy.qqbot.websocket.Listener;
import org.minecralogy.qqbot.websocket.Sender;
import org.slf4j.Logger;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
/**
 * Bot类实现了ModInitializer接口，是模组的初始化类
 * 负责配置文件的加载、命令注册、服务器生命周期事件的监听以及WebSocket连接的建立
 */
public class Bot implements ModInitializer {
    // 配置对象，用于存储从配置文件中读取的配置信息
    public static Config config;
    // Minecraft服务器实例，用于获取服务器相关信息
    public static MinecraftServer server = null;
    // WebSocket监听器，用于接收来自机器人的消息
    public static Listener listener;
    // WebSocket发送器，用于向机器人发送消息
    public static Sender sender;
    // 日志记录器，用于记录模组的运行信息
    public static final Logger LOGGER = LogUtils.getLogger();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * 检查JSON字符串是否可以严格转换为指定类型的对象
     * @param json 待检查的JSON字符串
     * @param type 目标类型
     * @return 如果可以转换返回true，否则返回false
     */
    public static <T> boolean isValidStrictly(String json, Class<T> type) {
        try {
            new Gson().getAdapter(type).fromJson(json);
            return true;
        } catch (JsonSyntaxException | IOException e) {
            return false;
        }
    }

    public static String getListenerUri() {
        return config.uri + "/websocket/minecraft";
    }

    public static String getSenderUri() {
        return config.uri + "/websocket/bot";
    }

    /**
     * 模组初始化方法，在模组加载时调用
     * 负责命令注册、服务器生命周期事件监听、配置文件加载和WebSocket连接建立
     */
    @Override
    public void onInitialize() {
        // 注册QQ命令

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            QQCommand.register(dispatcher);QQBotCommand.register(dispatcher);   });
            // 注册服务器启动事件监听器
        ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> server = minecraftServer);
        // 注册服务器停止事件监听器

        // 获取配置文件路径
        String path = FabricLoader.getInstance().getConfigDir().resolve("qq_bot.json").toString();
        LOGGER.info("The bot config: {}", path);
        File file = new File(path);

        // 如果文件不存在或内容无效，创建新配置文件
        if (!file.exists() || !file.canRead() || file.isDirectory()) {
            LOGGER.info("Creating new configuration file");
            try {
                // 创建默认配置
                String defaultConfig = """
                    {
                      "uri": "ws://localhost:6457/",
                      "name": "JMS",
                      "token": "niggerniggerJOKERZHEN",
                      "reconnect_interval": 5
                    }
                    """;

                // 写入文件
                Files.write(file.toPath(), defaultConfig.getBytes());
            } catch (IOException e) {
                LOGGER.error("Failed to create configuration file", e);
                throw new RuntimeException(e);
            }
        }

        // 读取配置文件内容
        try (InputStream inputStream = new FileInputStream(file)) {
            String conf = new String(inputStream.readAllBytes());
            if (!isValidStrictly(conf, Config.class)) {
                throw new RuntimeException("Invalid configuration file format");
            }
            config = new Gson().fromJson(conf, Config.class);
        } catch (IOException e) {
            LOGGER.error("Failed to read configuration file", e);
            throw new RuntimeException(e);
        }

        // 处理URI末尾的斜杠
        if (config.uri.endsWith("/")) {
            config.uri = config.uri.substring(0, config.uri.length() - 1);
        }

        try {
            // 创建WebSocket连接

            String baseUri = config.getUri();
            listener = new Listener(baseUri + "/websocket/minecraft");
            sender = new Sender(baseUri + "/websocket/bot");
        } catch (URISyntaxException e) {
            LOGGER.error("Invalid URI format", e);
            throw new RuntimeException(e);
        }
    }
}
