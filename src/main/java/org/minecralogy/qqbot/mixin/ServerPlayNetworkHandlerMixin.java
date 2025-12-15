package org.minecralogy.qqbot.mixin;

import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.minecralogy.qqbot.Bot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    void onDisconneted(DisconnectionInfo info, CallbackInfo ci) {
        Bot.sender.sendPlayerLeft(((ServerPlayNetworkHandler)(Object)this).player.getName().getString());
    }
/**
 * 注入方法：在onChatMessage方法的HEAD位置注入代码
 * 该方法用于处理玩家聊天消息
 *
 * @param packet 聊天消息数据包，包含聊天内容等信息
 * @param ci 回调信息，用于控制方法的执行流程
 */
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    public void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
    // 获取当前玩家名称并发送聊天消息
    // 通过强制转换获取ServerPlayNetworkHandler中的player对象
    // 调用Bot.sender的sendPlayerChat方法发送玩家聊天消息
       Bot.sender.sendPlayerChat(((ServerPlayNetworkHandler)(Object)this).player.getName().getString(), packet.chatMessage());
    }
}
