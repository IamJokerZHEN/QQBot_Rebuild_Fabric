package org.minecralogy.qqbot.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import org.minecralogy.qqbot.Bot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"))
    public void onDeath(DamageSource damageSource, CallbackInfo ci) {
        Bot.sender.sendPlayerDeath(((ServerPlayer)(Object)this).getName().getString(), ((ServerPlayer)(Object)this).getCombatTracker().getDeathMessage().getString());
    }
}