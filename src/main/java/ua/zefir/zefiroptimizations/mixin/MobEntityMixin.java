package ua.zefir.zefiroptimizations.mixin;

import akka.actor.ActorRef;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.ZefirsActorMessages;
import ua.zefir.zefiroptimizations.actors.IAsyncTickingLivingEntity;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin extends LivingEntityMixin {
    public MobEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(EntityType<? extends LivingEntity> entityType, World world, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!world.isClient) {
            ZefirOptimizations.getAsyncTickManager()
                    .tell(new ZefirsActorMessages.EntityCreated(self), ActorRef.noSender());
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;tick()V", shift = At.Shift.AFTER), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if(Thread.currentThread() == ZefirOptimizations.SERVER.getThread()) {
            ci.cancel();
        }
    }


    @Inject(method = "loot", at = @At("HEAD"), cancellable = true)
    private void onLoot(ItemEntity item, CallbackInfo ci) {
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            ZefirOptimizations.getMainThreadActor().tell(
                    new ZefirsActorMessages.LootItemEntity((LivingEntity) (Object) this, item),
                    ActorRef.noSender()
            );
            ci.cancel();
        }
    }
}
