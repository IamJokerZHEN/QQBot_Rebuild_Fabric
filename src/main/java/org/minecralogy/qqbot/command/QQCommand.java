package org.minecralogy.qqbot.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.network.message.SentMessage;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.minecralogy.qqbot.Bot;
import net.minecraft.server.MinecraftServer;

import static net.minecraft.server.command.CommandManager.literal;

public class QQCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("qq")
                .then(CommandManager.argument("message", MessageArgumentType.message()).executes(context -> {
                    MessageArgumentType.getSignedMessage(context, "message", message -> {
                        String m = SentMessage.of(message).content().getString();
                        String msg = String.format("[%s] <%s> %s", Bot.config.getName(), context.getSource().getName(), m);
                        Bot.sender.sendSynchronousMessage(msg);
                        System.out.println(msg);

                        // 修复：通过ServerCommandSource获取服务器
                        MinecraftServer server = context.getSource().getServer();
                        // 修复：使用execute方法代替executeWithPrefix
                        server.getCommandManager().execute(
                                server.getCommandManager().getDispatcher().parse("say " + m, context.getSource()),
                                "say " + m
                        );
                    });
                    return 1;
                }))
        );
    }
}
