package org.minecralogy.qqbot.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.minecralogy.qqbot.Bot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerListMixin {
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    public void onPlayerConnect(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        Bot.sender.sendPlayerJoined(player.getName().getString());
    }
}