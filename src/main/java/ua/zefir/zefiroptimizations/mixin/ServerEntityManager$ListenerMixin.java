package ua.zefir.zefiroptimizations.mixin;

import akka.actor.typed.javadsl.AskPattern;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.world.entity.EntityHandler;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ServerEntityManagerMessages;
import ua.zefir.zefiroptimizations.data.ServerEntityManagerRef;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

@Mixin(targets = "net.minecraft.server.world.ServerEntityManager$Listener")
public class ServerEntityManager$ListenerMixin<T extends EntityLike> {
    @Shadow @Final ServerEntityManager manager;

    @Redirect(method = "updateEntityPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;entityLeftSection(JLnet/minecraft/world/entity/EntityTrackingSection;)V"))
    private void redirectEntityLeftSection(ServerEntityManager instance, long sectionPos, EntityTrackingSection<T> section) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.EntityLeftSection<>(sectionPos, section));
    }

    @Redirect(method = "updateLoadStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityHandler;updateLoadStatus(Ljava/lang/Object;)V", ordinal = 0))
    private <P> void redirectHandlerUpdateLoadStatus(EntityHandler instance, P t) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.HandlerUpdateLoadStatus<>(t));
    }

    @Redirect(method = "updateLoadStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityHandler;updateLoadStatus(Ljava/lang/Object;)V", ordinal = 1))
    private <P> void redirectHandlerUpdateLoadStatus2(EntityHandler instance, P t) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.HandlerUpdateLoadStatus<>(t));
    }

    @Redirect(method = "updateLoadStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;stopTracking(Lnet/minecraft/world/entity/EntityLike;)V"))
    private void redirectStopTracking$updateLoadStatus(ServerEntityManager instance, T entity) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.StopTracking<>(entity));
    }

    @Redirect(method = "updateLoadStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;startTracking(Lnet/minecraft/world/entity/EntityLike;)V"))
    private void redirectStartTracking$updateLoadStatus(ServerEntityManager instance, T entity) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.StartTracking<>(entity));
    }

    @Redirect(method = "updateLoadStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;stopTicking(Lnet/minecraft/world/entity/EntityLike;)V"))
    private void redirectStopTicking$updateLoadStatus(ServerEntityManager instance, T entity) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.StopTicking<>(entity));
    }

    @Redirect(method = "updateLoadStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;startTicking(Lnet/minecraft/world/entity/EntityLike;)V"))
    private void redirectStartTicking$updateLoadStatus(ServerEntityManager instance, T entity) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.StartTicking<>(entity));
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;stopTicking(Lnet/minecraft/world/entity/EntityLike;)V"))
    private void redirectStopTicking$remove(ServerEntityManager instance, T entity) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.StopTicking<>(entity));
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;stopTracking(Lnet/minecraft/world/entity/EntityLike;)V"))
    private void redirectStopTracking$remove(ServerEntityManager instance, T entity) {
        ((ServerEntityManagerRef) instance).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.StopTracking<>(entity));
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityHandler;destroy(Ljava/lang/Object;)V"))
    private <P> void redirectHandlerDestroy(EntityHandler instance, P t) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.HandlerDestroy<>(t));
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Ljava/util/Set;remove(Ljava/lang/Object;)Z"))
    private boolean redirectEntityUuidsRemove$remove(Set instance, Object o) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.EntityUuidsRemove((UUID) o));
        return false;
    }

    @Redirect(method = "remove", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerEntityManager;entityLeftSection(JLnet/minecraft/world/entity/EntityTrackingSection;)V"))
    private void redirectEntityLeftSection$remove(ServerEntityManager instance, long sectionPos, EntityTrackingSection<T> section) {
        ((ServerEntityManagerRef) manager).getEntityManagerActor()
                .tell(new ServerEntityManagerMessages.EntityLeftSection<>(sectionPos, section));
    }

    @Redirect(method = "updateEntityPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/SectionedEntityCache;getTrackingSection(J)Lnet/minecraft/world/entity/EntityTrackingSection;"))
    private EntityTrackingSection<T> redirectGetTrackingSection(SectionedEntityCache instance, long sectionPos) {
        CompletionStage<EntityTrackingSection<T>> resultFuture =
                AskPattern.ask(
                        ((ServerEntityManagerRef) manager).getEntityManagerActor(),
                        replyTo -> new ServerEntityManagerMessages.RequestCacheTrackingSection<>(sectionPos, replyTo),
                        ZefirOptimizations.timeout,
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            return resultFuture.toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerEntityManager actor", e);
        }
    }
}
