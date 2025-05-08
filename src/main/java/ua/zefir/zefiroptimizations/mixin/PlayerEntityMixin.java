package ua.zefir.zefiroptimizations.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
//    @Inject(method = "collideWithEntity", at = @At("HEAD"))
//    private void onCollideWithEntity(Entity entity, CallbackInfo ci) {
//        System.out.println("collideWithEntity " + entity.toString());
//    }
////
//    @Inject(method = "tick", at = @At("HEAD"))
//    private void onTick(CallbackInfo ci) {
//        System.out.println("player is ticking");
//    }
////
//    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getOtherEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
//    private List onCollideWithEntity(World instance, Entity entity, Box box, Operation<List> original) {
//        var newValue = instance.getOtherEntities(entity, box);
//        System.out.println("getOtherEntities in player: " + newValue);
//        return newValue;
//    }
}
