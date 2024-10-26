package ua.zefir.zefiroptimizations.mixin;

import akka.actor.ActorRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.IArmorStandEntityAccess;
import ua.zefir.zefiroptimizations.actors.ZefirsActorMessages;

import java.util.function.Predicate;

@Mixin(ArmorStandEntity.class)
public abstract class ArmorStandEntityMixin extends LivingEntityMixin implements IArmorStandEntityAccess {
    @Final
    @Shadow
    private static Predicate<Entity> RIDEABLE_MINECART_PREDICATE;

    @Override
    public Predicate<Entity> zefiroptimizations$RIDEABLE_MINECART_PREDICATE() {
        return RIDEABLE_MINECART_PREDICATE;
    }

    @Inject(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At("RETURN"))
    private void init(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
        System.out.println("ArmorStand spawned");
        LivingEntity self = (LivingEntity) (Object) this;
        zefiroptimizations$setAsyncTicking(true);
        if (!world.isClient) {
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new ZefirsActorMessages.EntityCreated(self), ActorRef.noSender());
        }
    }
}
