package org.minecralogy.qqbot.command;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.minecralogy.qqbot.Bot;
import org.minecralogy.qqbot.Config;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.minecralogy.qqbot.websocket.Listener;
import org.minecralogy.qqbot.websocket.Sender;
import java.util.stream.Collectors;

public class QQBotCommand {
    private static final Path CONFIG_PATH = Paths.get("config/qq_bot.json");
    private static final int LOG_LINES = 15;
    private static final int INPUT_TIMEOUT = 60;
    private static final Map<ServerCommandSource, CompletableFuture<String>> pendingInputs = new HashMap<>();
    private static final Gson gson = new Gson();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("qqbot")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(QQBotCommand::showInfo)
                .then(literal("reconnect")
                        .executes(QQBotCommand::reconnect)
                )
                .then(literal("config")
                        .executes(QQBotCommand::showConfig)
                        .then(literal("uri")
                                .then(argument("value", string())
                                        .executes(context -> updateConfigDirectly(context, "uri")))
                        )
                        .then(literal("name")
                                .then(argument("value", string())
                                        .executes(context -> updateConfigDirectly(context, "name")))
                        )
                        .then(literal("token")
                                .then(argument("value", string())
                                        .executes(context -> updateConfigDirectly(context, "token")))
                        )
                        .then(literal("reconnect_interval")
                                .then(argument("value", integer())
                                        .executes(context -> updateConfigDirectly(context, "reconnect_interval")))
                        )
                )
                .then(literal("test")
                        .executes(QQBotCommand::testConnection)
                )
                .then(literal("info")
                        .executes(QQBotCommand::showInfo)
                )
                .then(literal("log")
                        .executes(QQBotCommand::showLogs)
                )
        );
    }

    private static int updateConfigDirectly(CommandContext<ServerCommandSource> context, String key) {
        ServerCommandSource source = context.getSource();
        String value;

        try {
            switch (key) {
                case "uri":
                case "name":
                case "token":
                    value = getString(context, "value");
                    break;
                case "reconnect_interval":
                    value = String.valueOf(getInteger(context, "value"));
                    break;
                default:
                    source.sendError(Text.literal("未知的配置项"));
                    return 0;
            }

            updateConfig(key, value);
            source.sendFeedback(() -> Text.literal(key + " 已更新为: " + value), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("更新失败: " + e.getMessage()));
            return 0;
        }
    }

    public static int reconnect(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            Bot.listener.close();
            Bot.sender.close();

            Bot.listener = new Listener(Bot.getListenerUri());
            Bot.sender = new Sender(Bot.getSenderUri());

            source.sendFeedback(() -> Text.literal("QQ Bot 重连成功"), true);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("重连失败: " + e.getMessage()));
            return 0;
        }
    }


    private static int showInfo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Config config = Bot.config;
        source.sendFeedback(() -> Text.literal("=== QQ Bot 状态 ==="), false);
        source.sendFeedback(() -> Text.literal("WebSocket连接状态: " +
                (Bot.listener.isConnected() ? "已连接" : "未连接")), false);  // 使用 isConnected()
        source.sendFeedback(() -> Text.literal("当前URI: " + config.getUri()), false);
        source.sendFeedback(() -> Text.literal("服务器名称: " + config.getName()), false);
        return 1;
    }


    private static int showConfig(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Config config = Bot.config;

        source.sendFeedback(() -> Text.literal("=== QQ Bot 配置 ==="), false);
        source.sendFeedback(() -> Text.literal("WebSocket URI: " + config.getUri()), false);
        source.sendFeedback(() -> Text.literal("名称: " + config.getName()), false);
        source.sendFeedback(() -> Text.literal("Token: " + config.getToken()), false);
        source.sendFeedback(() -> Text.literal("重连间隔: " + config.getReconnect_interval() + " 秒"), false);

        return 1;
    }

    private static int waitForInput(CommandContext<ServerCommandSource> context, String key) {
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("请在" + INPUT_TIMEOUT + "秒内在聊天框输入新的" + key + "值"), false);

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingInputs.put(source, future);

        future.orTimeout(INPUT_TIMEOUT, TimeUnit.SECONDS)
                .whenComplete((value, throwable) -> {
                    pendingInputs.remove(source);
                    if (throwable instanceof TimeoutException) {
                        source.sendError(Text.literal("输入超时，配置未更改"));
                    } else if (throwable != null) {
                        source.sendError(Text.literal("发生错误: " + throwable.getMessage()));
                    } else {
                        updateConfig(key, value);
                        source.sendFeedback(() -> Text.literal(key + "已更新为: " + value), true);
                    }
                });

        return 1;
    }

    private static void updateConfig(String key, String value) {
        try {
            String content = Files.readString(CONFIG_PATH);
            Config config = gson.fromJson(content, Config.class);

            switch (key) {
                case "uri":
                    config.uri = value;
                    break;
                case "name":
                    config.name = value;
                    break;
                case "token":
                    config.token = value;
                    break;
                case "reconnect_interval":
                    config.reconnect_interval = Integer.parseInt(value);
                    break;
            }

            String newContent = gson.toJson(config);
            Files.writeString(CONFIG_PATH, newContent);

            // 更新运行时的配置
            Bot.config = config;

        } catch (Exception e) {
            Bot.LOGGER.error("Failed to update config", e);
        }
    }

    private static int testConnection(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        try {
            if (Bot.listener.isConnected()) {
                source.sendFeedback(() -> Text.literal("连接测试成功！"), true);
                return 1;
            } else {
                source.sendError(Text.literal("连接未建立，请使用 /qqbot reconnect 重新连接"));
                return 0;
            }
        } catch (Exception e) {
            source.sendError(Text.literal("连接测试出错: " + e.getMessage()));
            return 0;
        }
    }


    private static int showLogs(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();

        try {
            List<String> logs = getLastLogs(LOG_LINES);
            source.sendFeedback(() -> Text.literal("=== 最新日志 ==="), false);
            logs.forEach(log -> source.sendFeedback(() -> Text.literal(log), false));
        } catch (Exception e) {
            source.sendError(Text.literal("无法读取日志: " + e.getMessage()));
        }

        return 1;
    }

    private static List<String> getLastLogs(int lines) {
        try {
            Path logPath = Paths.get("logs/latest.log");
            List<String> allLines = Files.readAllLines(logPath);
            // 过滤包含[QQBOT]的日志行
            List<String> qqbotLogs = allLines.stream()
                    .filter(line -> line.contains("[QQBOT]"))
                    .collect(Collectors.toList());
            int size = qqbotLogs.size();
            return qqbotLogs.subList(Math.max(0, size - lines), size);
        } catch (Exception e) {
            Bot.LOGGER.error("Failed to read log file", e);
            return List.of();
        }
    }
}
