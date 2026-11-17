package ua.zefir.zephyrium.process.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTickList;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ua.zefir.zephyrium.process.Processes;
import ua.zefir.zephyrium.process.neighbour.SectionIndex;
import ua.zefir.zephyrium.process.profile.Profiler;
import ua.zefir.zephyrium.process.world.MainQueue;
import ua.zefir.zephyrium.process.world.Players;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
  @Shadow @Final private List<ServerPlayer> players;

  @Unique private volatile List<ServerPlayer> zo$playersForProcesses = List.of();

  @Inject(method = "tick", at = @At("HEAD"))
  private void zo$beforeTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
    zo$playersForProcesses = List.copyOf(players);
    SectionIndex.nextTick();
    MainQueue.drain();
    for (ServerPlayer player : players) {
      Processes.republish(player);
    }
  }

  @Inject(method = "tick", at = @At("TAIL"))
  private void zo$afterTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
    MainQueue.drain();
  }

  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/entity/EntityTickList;forEach(Ljava/util/function/Consumer;)V"))
  private void zo$dispatchTicks(
      EntityTickList list, Consumer<Entity> action, Operation<Void> original) {
    DistanceManager tickets =
        ((ServerLevel) (Object) this).getChunkSource().chunkMap.getDistanceManager();
    Profiler.entityLoopStarted();
    try {
      original.call(
          list, (Consumer<Entity>) entity -> Processes.dispatchTick(entity, tickets, action));
    } finally {
      Profiler.entityLoopEnded();
    }
  }

  @WrapMethod(method = "addFreshEntity")
  private boolean zo$spawnEntity(Entity entity, Operation<Boolean> original) {
    if (Processes.inProcess()) {
      return (Boolean) Processes.callMain(self -> original.call(entity), null);
    }
    return original.call(entity);
  }

  @WrapMethod(
      method =
          "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V")
  private void zo$playSound(
      @Nullable Entity soundSource,
      double x,
      double y,
      double z,
      Holder<SoundEvent> sound,
      SoundSource category,
      float volume,
      float pitch,
      long seed,
      Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(
          () -> original.call(soundSource, x, y, z, sound, category, volume, pitch, seed));
      return;
    }
    original.call(soundSource, x, y, z, sound, category, volume, pitch, seed);
  }

  @WrapMethod(
      method =
          "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V")
  private void zo$playSoundFromEntity(
      @Nullable Entity soundSource,
      Entity entity,
      Holder<SoundEvent> sound,
      SoundSource category,
      float volume,
      float pitch,
      long seed,
      Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(
          () -> original.call(soundSource, entity, sound, category, volume, pitch, seed));
      return;
    }
    original.call(soundSource, entity, sound, category, volume, pitch, seed);
  }

  @WrapMethod(method = "levelEvent")
  private void zo$syncWorldEvent(
      @Nullable Entity eventSource, int eventId, BlockPos pos, int data, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(eventSource, eventId, pos, data));
      return;
    }
    original.call(eventSource, eventId, pos, data);
  }

  @WrapMethod(method = "gameEvent")
  private void zo$emitGameEvent(
      Holder<GameEvent> event,
      Vec3 emitterPos,
      GameEvent.Context emitter,
      Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(event, emitterPos, emitter));
      return;
    }
    original.call(event, emitterPos, emitter);
  }

  @WrapMethod(method = "broadcastEntityEvent")
  private void zo$sendEntityStatus(Entity entity, byte status, Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.castMain(() -> original.call(entity, status));
      return;
    }
    original.call(entity, status);
  }

  /*? if >=26.1 {*/
  @WrapMethod(
      method =
          "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V")
  private void zo$createExplosion(
      @Nullable Entity entity,
      @Nullable DamageSource damageSource,
      @Nullable ExplosionDamageCalculator behavior,
      double x,
      double y,
      double z,
      float power,
      boolean createFire,
      Level.ExplosionInteraction sourceType,
      ParticleOptions particle,
      ParticleOptions emitterParticle,
      net.minecraft.util.random.WeightedList<net.minecraft.core.particles.ExplosionParticleInfo>
          blockParticles,
      Holder<SoundEvent> soundEvent,
      Operation<Void> original) {
    if (Processes.inProcess()) {
      Processes.callMain(
          self -> {
            original.call(
                entity,
                damageSource,
                behavior,
                x,
                y,
                z,
                power,
                createFire,
                sourceType,
                particle,
                emitterParticle,
                blockParticles,
                soundEvent);
            return null;
          },
          null);
      return;
    }
    original.call(
        entity,
        damageSource,
        behavior,
        x,
        y,
        z,
        power,
        createFire,
        sourceType,
        particle,
        emitterParticle,
        blockParticles,
        soundEvent);
  }

  /*?} else {*/
    /*@WrapMethod(
      method =
          "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;")
  private net.minecraft.world.level.Explosion zo$createExplosion(
      @Nullable Entity entity,
      @Nullable DamageSource damageSource,
      @Nullable ExplosionDamageCalculator behavior,
      double x,
      double y,
      double z,
      float power,
      boolean createFire,
      Level.ExplosionInteraction sourceType,
      ParticleOptions particle,
      ParticleOptions emitterParticle,
      Holder<SoundEvent> soundEvent,
      Operation<net.minecraft.world.level.Explosion> original) {
    if (Processes.inProcess()) {
      return (net.minecraft.world.level.Explosion)
          Processes.callMain(
              self ->
                  original.call(
                      entity,
                      damageSource,
                      behavior,
                      x,
                      y,
                      z,
                      power,
                      createFire,
                      sourceType,
                      particle,
                      emitterParticle,
                      soundEvent),
              null);
    }
    return original.call(
        entity,
        damageSource,
        behavior,
        x,
        y,
        z,
        power,
        createFire,
        sourceType,
        particle,
        emitterParticle,
        soundEvent);
  }
  *//*?}*/

  @WrapMethod(method = "sendParticles(Lnet/minecraft/core/particles/ParticleOptions;DDDIDDDD)I")
  private int zo$spawnParticles(
      ParticleOptions particle,
      double x,
      double y,
      double z,
      int count,
      double deltaX,
      double deltaY,
      double deltaZ,
      double speed,
      Operation<Integer> original) {
    if (Processes.inProcess()) {
      Processes.castMain(
          () -> original.call(particle, x, y, z, count, deltaX, deltaY, deltaZ, speed));
      return 0;
    }
    return original.call(particle, x, y, z, count, deltaX, deltaY, deltaZ, speed);
  }

  @WrapMethod(method = "players()Ljava/util/List;")
  private List<ServerPlayer> zo$getPlayers(Operation<List<ServerPlayer>> original) {
    if (Processes.inProcess()) {
      return new ArrayList<>(zo$playersForProcesses);
    }
    return original.call();
  }

  @WrapMethod(method = "getPlayers(Ljava/util/function/Predicate;)Ljava/util/List;")
  private List<ServerPlayer> zo$getPlayers(
      Predicate<? super ServerPlayer> predicate, Operation<List<ServerPlayer>> original) {
    if (Processes.inProcess()) {
      return Players.matching(zo$playersForProcesses, predicate, Integer.MAX_VALUE);
    }
    return original.call(predicate);
  }

  @WrapMethod(method = "getPlayers(Ljava/util/function/Predicate;I)Ljava/util/List;")
  private List<ServerPlayer> zo$getPlayers(
      Predicate<? super ServerPlayer> predicate,
      int limit,
      Operation<List<ServerPlayer>> original) {
    if (Processes.inProcess()) {
      return Players.matching(zo$playersForProcesses, predicate, limit);
    }
    return original.call(predicate, limit);
  }
}
