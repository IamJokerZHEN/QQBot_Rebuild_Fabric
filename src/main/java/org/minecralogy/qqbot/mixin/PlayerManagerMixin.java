package org.minecralogy.qqbot.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.minecralogy.qqbot.Bot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {
/**
 * 注解说明：这是一个通过@Inject注入的方法，用于在玩家连接时执行自定义逻辑
 * method = "onPlayerConnect" - 指定要注入的目标方法名为"onPlayerConnect"
 * at = @At("HEAD") - 表示在目标方法的开始处(HEAD)注入代码
 *
 * @param connection 客户端连接对象，包含网络连接相关信息
 * @param player 服务器玩家实体对象，包含玩家相关信息
 * @param clientData 已连接客户端的数据对象，包含客户端相关信息
 * @param ci 回调信息对象，用于控制方法执行流程
 */
    @Inject(method = "onPlayerConnect", at = @At("HEAD"))
    public void onPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData clientData, CallbackInfo ci) { // 当有玩家连接到服务器时触发此方法
        Bot.sender.sendPlayerJoined(player.getName().getString()); // 通过Bot发送玩家加入消息，消息内容为玩家名称字符串
    }
}
