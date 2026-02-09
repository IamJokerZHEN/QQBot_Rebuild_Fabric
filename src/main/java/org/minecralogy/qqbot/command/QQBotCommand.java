package org.minecralogy.qqbot.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import me.lucko.fabric.api.permissions.v0.Permissions;  // ✅ Fabric Permissions API
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    private static final Map<CommandSourceStack, CompletableFuture<String>> pendingInputs = new HashMap<>();
    private static final Gson gson = new Gson();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("qqbot")
                // ✅ 使用 Fabric Permissions API 检查权限
                // 参数：权限节点字符串，默认权限级别（2=管理员）
                .requires(source -> Permissions.check(source, "qqbot.command", 2))
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

    // ✅ 使用 Fabric Permissions API 进行权限检查
    // 可以为不同子命令设置不同的权限节点
    private static boolean hasPermission(CommandSourceStack source, int level) {
        return Permissions.check(source, "qqbot.command", level);
    }

    // 也可以为特定子命令设置更细粒度的权限
    private static boolean hasPermission(CommandSourceStack source, String permissionNode, int level) {
        return Permissions.check(source, permissionNode, level);
    }

    private static int updateConfigDirectly(CommandContext<CommandSourceStack> context, String key) {
        CommandSourceStack source = context.getSource();
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
                    source.sendFailure(Component.literal("未知的配置项"));
                    return 0;
            }

            updateConfig(key, value);
            source.sendSuccess(() -> Component.literal(key + " 已更新为: " + value), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("更新失败: " + e.getMessage()));
            return 0;
        }
    }

    public static int reconnect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            Bot.listener.close();
            Bot.sender.close();

            Bot.listener = new Listener(Bot.getListenerUri());
            Bot.sender = new Sender(Bot.getSenderUri());

            source.sendSuccess(() -> Component.literal("QQ Bot 重连成功"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("重连失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Config config = Bot.config;
        source.sendSuccess(() -> Component.literal("=== QQ Bot 状态 ==="), false);
        source.sendSuccess(() -> Component.literal("WebSocket连接状态: " +
                (Bot.listener.isConnected() ? "已连接" : "未连接")), false);
        source.sendSuccess(() -> Component.literal("当前URI: " + config.getUri()), false);
        source.sendSuccess(() -> Component.literal("服务器名称: " + config.getName()), false);
        return 1;
    }

    private static int showConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Config config = Bot.config;

        source.sendSuccess(() -> Component.literal("=== QQ Bot 配置 ==="), false);
        source.sendSuccess(() -> Component.literal("WebSocket URI: " + config.getUri()), false);
        source.sendSuccess(() -> Component.literal("名称: " + config.getName()), false);
        source.sendSuccess(() -> Component.literal("Token: " + config.getToken()), false);
        source.sendSuccess(() -> Component.literal("重连间隔: " + config.getReconnect_interval() + " 秒"), false);

        return 1;
    }

    private static int waitForInput(CommandContext<CommandSourceStack> context, String key) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("请在" + INPUT_TIMEOUT + "秒内在聊天框输入新的" + key + "值"), false);

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingInputs.put(source, future);

        future.orTimeout(INPUT_TIMEOUT, TimeUnit.SECONDS)
                .whenComplete((value, throwable) -> {
                    pendingInputs.remove(source);
                    if (throwable instanceof TimeoutException) {
                        source.sendFailure(Component.literal("输入超时，配置未更改"));
                    } else if (throwable != null) {
                        source.sendFailure(Component.literal("发生错误: " + throwable.getMessage()));
                    } else {
                        updateConfig(key, value);
                        source.sendSuccess(() -> Component.literal(key + "已更新为: " + value), true);
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
            Bot.config = config;

        } catch (Exception e) {
            Bot.LOGGER.error("Failed to update config", e);
        }
    }

    private static int testConnection(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            if (Bot.listener.isConnected()) {
                source.sendSuccess(() -> Component.literal("连接测试成功！"), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("连接未建立，请使用 /qqbot reconnect 重新连接"));
                return 0;
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("连接测试出错: " + e.getMessage()));
            return 0;
        }
    }

    private static int showLogs(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            List<String> logs = getLastLogs(LOG_LINES);
            source.sendSuccess(() -> Component.literal("=== 最新日志 ==="), false);
            logs.forEach(log -> source.sendSuccess(() -> Component.literal(log), false));
        } catch (Exception e) {
            source.sendFailure(Component.literal("无法读取日志: " + e.getMessage()));
        }

        return 1;
    }

    private static List<String> getLastLogs(int lines) {
        try {
            Path logPath = Paths.get("logs/latest.log");
            List<String> allLines = Files.readAllLines(logPath);
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