package org.minecralogy.qqbot.mixin;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.minecralogy.qqbot.Bot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 这是一个Mixin类，用于修改ServerPlayerEntity类的行为
 * 通过@Mixin注解将此类与ServerPlayerEntity类绑定
 */
@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    /**
     * 这是一个注入点，用于在ServerPlayerEntity的onDeath方法执行前插入自定义代码
     * @Inject注解表示这是一个方法注入点
     * method参数指定要注入的目标方法
     * at=@At("HEAD")表示在方法的开头位置注入
     */
    @Inject(method = "onDeath", at = @At("HEAD"))

    /**
     * 自定义的onDeath方法，当玩家死亡时触发
     * @param damageSource 造成死亡的伤害源
     * @param ci 回调信息，用于控制原方法的执行
     */
    public void onDeath(DamageSource damageSource, CallbackInfo ci) {
        // 调用Bot.sender的sendPlayerDeath方法
        // 传递玩家名称和死亡消息作为参数
        // ((ServerPlayerEntity)(Object)this)用于获取当前混入的实例
        // getName().getString()获取玩家名称
        // getDamageTracker().getDeathMessage().getString()获取死亡消息
        Bot.sender.sendPlayerDeath(((ServerPlayerEntity)(Object)this).getName().getString(), ((ServerPlayerEntity)(Object)this).getDamageTracker().getDeathMessage().getString());
    }
}
