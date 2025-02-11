package ua.zefir.zefiroptimizations.mixin;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import akka.util.Timeout;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import scala.concurrent.Await;
import scala.concurrent.Future;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.ZefirsActorMessages;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Mixin(ServerWorld.class)
public class ServerWorldMixin  {
//    private Timeout timeout = Timeout.create(Duration.ofSeconds(10));
//
//    @Redirect(method = "method_31420", at = @At(
//            value = "INVOKE",
//            target = "Lnet/minecraft/entity/Entity;isRemoved()Z"
//    ))
//    private boolean onTickMovement(Entity instance) {
//        if(!(instance instanceof PlayerEntity)) {
//            Future<Object> future = Patterns.ask(ZefirOptimizations.getMainThreadActor(), false, timeout);
//            try {
//                Boolean result = (Boolean) Await.result(future, timeout.duration());
//                return result;
//            } catch (TimeoutException | InterruptedException ex){
//                throw new RuntimeException("Entity response timeout: " + ex);
//            }
//        }
//        return instance.isRemoved();
//    }
}
