package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow protected abstract void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition);

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.getWorld().isClient) {
            ZefirOptimizations.getActorSystem()
                    .tell(new ZefirsActorMessages.EntityRemoved(self));
        }
    }

    @Redirect(method = "tickMovement",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;tickNewAi()V"
            )
    )
    private void redirectTickNewAi(LivingEntity self) {
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            CompletableFuture<Void> future = new CompletableFuture<>();

            ZefirOptimizations.SERVER.executeSync(() -> {
                ((LivingEntityAccessor) this).invokeTickNewAi();
                future.complete(null);
            });

            try {
                future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true) // Fucked up
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if(Thread.currentThread() != ZefirOptimizations.SERVER.getThread()) {
            ZefirOptimizations.getMainThreadActor().tell(
                    new ZefirsActorMessages.ApplyDamage(self, source, amount)
            );
            cir.setReturnValue(false); // TODO: Wrong behaviour
        }
    }

//    @Inject(method = "isClimbing", at = @At("HEAD"), cancellable = true)
//    private void onIsClimbing(CallbackInfoReturnable<Boolean> cir) {
////        System.out.println("isClimbing");
//        cir.setReturnValue(false);
//    }

    @Overwrite
    public boolean isClimbing() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockPos = this.getBlockPos();
//            this.getWorld().getBlockState(this.getBlockPos());
            WorldChunk worldChunk = this.getWorld().getChunk(ChunkSectionPos.getSectionCoord(blockPos.getX()), ChunkSectionPos.getSectionCoord(blockPos.getZ()));
            return false;
        }
    }

}