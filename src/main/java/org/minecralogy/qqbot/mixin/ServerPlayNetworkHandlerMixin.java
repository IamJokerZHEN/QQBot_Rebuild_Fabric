package org.minecralogy.qqbot.mixin;

import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.minecralogy.qqbot.Bot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    void onDisconneted(DisconnectionDetails info, CallbackInfo ci) {
        Bot.sender.sendPlayerLeft(((ServerGamePacketListenerImpl)(Object)this).player.getName().getString());
    }

    @Inject(method = "handleChat", at = @At("HEAD"))
    public void onChatMessage(ServerboundChatPacket packet, CallbackInfo ci) {
        Bot.sender.sendPlayerChat(((ServerGamePacketListenerImpl)(Object)this).player.getName().getString(), packet.message());
    }
}