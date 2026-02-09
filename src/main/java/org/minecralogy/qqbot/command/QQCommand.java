package org.minecralogy.qqbot.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import org.minecralogy.qqbot.Bot;
import net.minecraft.server.MinecraftServer;

import static net.minecraft.commands.Commands.literal;

public class QQCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("qq")
                .then(Commands.argument("message", MessageArgument.message()).executes(context -> {
                    // ✅ 使用 MessageArgument.getMessage
                    String m = MessageArgument.getMessage(context, "message").getString();
                    String msg = String.format("[%s] <%s> %s", Bot.config.getName(), context.getSource().getTextName(), m);
                    Bot.sender.sendSynchronousMessage(msg);
                    System.out.println(msg);

                    MinecraftServer server = context.getSource().getServer();
                    // ✅ 正确执行 say 命令
                    server.getCommands().performPrefixedCommand(
                            context.getSource(),
                            "say " + m
                    );
                    return 1;
                }))
        );
    }
}