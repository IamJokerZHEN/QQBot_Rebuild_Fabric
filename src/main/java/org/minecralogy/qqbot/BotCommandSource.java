package org.minecralogy.qqbot;

import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.permissions.PermissionSet;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class BotCommandSource extends CommandSourceStack {
    private String result = "";

    public String getResult() {
        if (result == null || result.isEmpty()) {
            return "";
        }
        return result.endsWith("\n") ?
                result.substring(0, result.length() - 1) :
                result;
    }

    boolean success = true;

    public boolean isSuccess() {
        return success;
    }

    CommandSource commandOutput;

    // ✅ 9个参数构造函数
    public BotCommandSource(CommandSource output, Vec3 pos, Vec2 rot, ServerLevel level,
                            PermissionSet permissionSet, String name,
                            Component displayName, MinecraftServer server, @Nullable Entity entity) {
        super(output, pos, rot, level, permissionSet, name, displayName, server, entity);
        this.commandOutput = output;
    }

    @Override
    public void sendChatMessage(net.minecraft.network.chat.OutgoingChatMessage message,
                                boolean filterMaskEnabled, net.minecraft.network.chat.ChatType.Bound params) {
        if (!this.isSilent()) {
            ServerPlayer serverPlayer = this.getPlayer();
            if (serverPlayer != null) {
                serverPlayer.sendChatMessage(message, filterMaskEnabled, params);
            } else {
                this.getOutput().sendSystemMessage(params.decorate(message.content()));
            }
        }
    }

    @Override
    public void sendSystemMessage(Component message) {
        result += message.getString() + "\n";
        if (!this.isSilent()) {
            ServerPlayer serverPlayer = this.getPlayer();
            if (serverPlayer != null) {
                serverPlayer.sendSystemMessage(message);
            } else {
                this.getOutput().sendSystemMessage(message);
            }
        }
    }

    @Override
    public void sendSuccess(Supplier<Component> feedbackSupplier, boolean broadcastToOps) {
        boolean bl = this.getOutput().acceptsSuccess() && !this.isSilent();
        boolean bl2 = broadcastToOps && this.getOutput().shouldInformAdmins() && !this.isSilent();
        Component component = feedbackSupplier.get();
        result += component.getString() + "\n";
        if (bl || bl2) {
            if (bl) {
                this.getOutput().sendSystemMessage(component);
            }
            if (bl2) {
                this.sendToOps(component);
            }
        }
    }

    @Override
    public void sendFailure(Component message) {
        success = false;
        result += message.getString() + "\n";
        if (this.getOutput().acceptsFailure() && !this.isSilent()) {
            MutableComponent mutableComponent = Component.empty().append(message).withStyle(ChatFormatting.RED);
            this.getOutput().sendSystemMessage(mutableComponent);
        }
    }

    private void sendToOps(Component message) {
        MutableComponent mutableComponent = Component.translatable("chat.type.admin",
                this.getDisplayName(), message).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        // ✅ 简化：直接发送给所有有权限的玩家，不检查 GameRules
        for (ServerPlayer serverPlayer : this.getServer().getPlayerList().getPlayers()) {
            if (serverPlayer != this.getOutput() &&
                    Permissions.check(serverPlayer, "minecraft.admin.command_feedback", 2)) {
                serverPlayer.sendSystemMessage(mutableComponent);
            }
        }

        if (this.getOutput() != this.getServer()) {
            this.getServer().sendSystemMessage(mutableComponent);
        }
    }

    public CommandSource getOutput() {
        return commandOutput;
    }
}