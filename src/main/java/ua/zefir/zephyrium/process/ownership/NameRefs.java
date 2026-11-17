package ua.zefir.zephyrium.process.ownership;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

@SuppressWarnings({"unused", "DataFlowIssue"})
public final class NameRefs {
  static Object entity() {
    return Entity.class;
  }

  static Object world() {
    return Level.class;
  }

  static Object dataTracker() {
    return SynchedEntityData.class;
  }

  static Object random() {
    return RandomSource.class;
  }

  static Object[] immutable() {
    return new Object[] {
      Vec3.class,
      Vec3i.class,
      BlockPos.class,
      AABB.class,
      ChunkPos.class,
      SectionPos.class,
      Vec2.class,
      EntityType.class,
      EntityDimensions.class,
      Pose.class,
      Identifier.class,
      ResourceKey.class,
      Holder.class,
      Holder.Reference.class,
      /*? if >=26.2 {*/
      net.minecraft.core.TypedInstance.class,
      /*?}*/
      EntityDataAccessor.class,
      BlockState.class,
      FluidState.class,
      Block.class,
      Fluid.class,
      ImmutableList.class,
      ImmutableSet.class,
      String.class,
      UUID.class,
      Integer.class,
      Long.class,
      Float.class,
      Double.class,
      Boolean.class,
      Byte.class,
      Short.class,
      Character.class,
      Number.class,
      Enum.class
    };
  }

  static Object[] entityOwned() {
    return new Object[] {
      Brain.class,
      Behavior.class,
      OneShot.class,
      Sensor.class,
      Goal.class,
      GoalSelector.class,
      PathNavigation.class,
      PathFinder.class,
      NodeEvaluator.class,
      MoveControl.class,
      LookControl.class,
      JumpControl.class,
      /*? if >=1.21.5 {*/
      net.minecraft.world.entity.EntityEquipment.class,
      /*?}*/
      EntityCollisionContext.class,
      Packet.class
    };
  }

  static Object[] pureStatic() {
    return new Object[] {
      Math.class,
      StrictMath.class,
      Mth.class,
      ChunkPos.class,
      BlockPos.class,
      SectionPos.class,
      Integer.class,
      Long.class,
      Float.class,
      Double.class,
      Boolean.class,
      Byte.class,
      Short.class,
      Character.class
    };
  }

  static void worldRandom(Level world) {
    Object random = world.random;
  }

  static void neverGuarded(Entity entity) {
    entity.getId();
  }

  static void dataTrackerGet(SynchedEntityData tracker) {
    tracker.get((EntityDataAccessor<Object>) null);
  }

  static void snapshotReadable(
      Entity entity, LivingEntity living, Mob mob, Player playerEntity, ServerPlayer player) {
    living.isPushable();
    living.isSleeping();
    living.onClimbable();
    entity.getInBlockState();
    entity.getTeam();
    entity.getIndirectPassengers();
    player.isSpectator();
    player.isCreative();
    living.getMainHandItem();
    living.getOffhandItem();
    playerEntity.getItemBySlot(EquipmentSlot.MAINHAND);
    playerEntity.getScoreboardName();
    playerEntity.getGameProfile();
    mob.getItemBySlot(EquipmentSlot.MAINHAND);
    /*? if >=26.2 {*/
    living.getItemBySlot(EquipmentSlot.MAINHAND);
    /*?}*/
    entity.getAddEntityPacket(null);
    living.getAttributes();
    mob.getLeashData();
    mob.requiresCustomPersistence();
  }

  static void untrackedCast(Entity entity, LivingEntity living) {
    entity.push((Entity) null);
    entity.push(0.0, 0.0, 0.0);
    entity.push((Vec3) null);
    entity.playSound(null, 0.0F, 0.0F);
    /*? if >=26.2 {*/
    living.knockback(0.0, 0.0, 0.0, null, 0.0F);
    living.knockback(0.0, 0.0, 0.0, null, 0.0F, false);
    /*?} else {*/
    /*living.knockback(0.0, 0.0, 0.0);
    *//*?}*/
  }

  static void castable(Entity entity, LivingEntity living) {
    entity.playerTouch(null);
    entity.push((Entity) null);
    entity.push(0.0, 0.0, 0.0);
    entity.push((Vec3) null);
    entity.setDeltaMovement((Vec3) null);
    entity.setDeltaMovement(0.0, 0.0, 0.0);
    entity.thunderHit(null, null);
    entity.playSound(null, 0.0F, 0.0F);
    /*? if >=26.2 {*/
    living.knockback(0.0, 0.0, 0.0, null, 0.0F);
    living.knockback(0.0, 0.0, 0.0, null, 0.0F, false);
    /*?} else {*/
    /*living.knockback(0.0, 0.0, 0.0);
    *//*?}*/
  }
}
