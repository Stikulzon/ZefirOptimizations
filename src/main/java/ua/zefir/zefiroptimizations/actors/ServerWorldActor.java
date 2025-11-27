package ua.zefir.zefiroptimizations.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import net.minecraft.server.world.ServerWorld;
import ua.zefir.zefiroptimizations.actors.messages.WorldMessages;
import ua.zefir.zefiroptimizations.mixin.ServerWorldAccessor;
import ua.zefir.zefiroptimizations.mixin.WorldAccessor;

import java.io.IOException;

public class ServerWorldActor extends AbstractBehavior<WorldMessages.WorldMessage> {

    private final ServerWorld world;

    public ServerWorldActor(ActorContext<WorldMessages.WorldMessage> context, ServerWorld world) {
        super(context);
        this.world = world;
    }

    public static Behavior<WorldMessages.WorldMessage> create(ServerWorld world) {
        return Behaviors.setup(context -> new ServerWorldActor(context, world));
    }

    @Override
    public Receive<WorldMessages.WorldMessage> createReceive() {
        return newReceiveBuilder()
                // Tick and Time
                .onMessage(WorldMessages.Tick.class, this::onTick)
                .onMessage(WorldMessages.TickTime.class, this::onTickTime)
                .onMessage(WorldMessages.TickSpawners.class, this::onTickSpawners)
                .onMessage(WorldMessages.TickChunk.class, this::onTickChunk)
                .onMessage(WorldMessages.TickIceAndSnow.class, this::onTickIceAndSnow)
                .onMessage(WorldMessages.IsInBlockTick.class, this::onIsInBlockTick)
                .onMessage(WorldMessages.GetTime.class, this::onGetTime)
                .onMessage(WorldMessages.GetTimeOfDay.class, this::onGetTimeOfDay)
                .onMessage(WorldMessages.SetTimeOfDay.class, this::onSetTimeOfDay)
                .onMessage(WorldMessages.GetTickManager.class, this::onGetTickManager)
                .onMessage(WorldMessages.GetTickOrder.class, this::onGetTickOrder)

                // Block and Chunk Operations
                .onMessage(WorldMessages.GetBlockState.class, this::onGetBlockState)
                .onMessage(WorldMessages.GetFluidState.class, this::onGetFluidState)
                .onMessage(WorldMessages.SetBlockState.class, this::onSetBlockState)
                .onMessage(WorldMessages.RemoveBlock.class, this::onRemoveBlock)
                .onMessage(WorldMessages.BreakBlock.class, this::onBreakBlock)
                .onMessage(WorldMessages.AddBlockBreakParticles.class, this::onAddBlockBreakParticles)
                .onMessage(WorldMessages.ScheduleBlockRerenderIfNeeded.class, this::onScheduleBlockRerenderIfNeeded)
                .onMessage(WorldMessages.IsInBuildLimit.class, this::onIsInBuildLimit)
                .onMessage(WorldMessages.CanSetBlock.class, this::onCanSetBlock)
                .onMessage(WorldMessages.IsDirectionSolid.class, this::onIsDirectionSolid)
                .onMessage(WorldMessages.IsTopSolid.class, this::onIsTopSolid)
                .onMessage(WorldMessages.GetWorldChunk.class, this::onGetWorldChunk)
                .onMessage(WorldMessages.GetChunk.class, this::onGetChunk)
                .onMessage(WorldMessages.GetChunkWithStatus.class, this::onGetChunkWithStatus)
                .onMessage(WorldMessages.GetChunkAsView.class, this::onGetChunkAsView)
                .onMessage(WorldMessages.IsChunkLoaded.class, this::onIsChunkLoaded)
                .onMessage(WorldMessages.ShouldTickBlocksInChunk.class, this::onShouldTickBlocksInChunk)
                .onMessage(WorldMessages.ShouldTickBlockPos.class, this::onShouldTickBlockPos)
                .onMessage(WorldMessages.ShouldTickChunkPos.class, this::onShouldTickChunkPos)
                .onMessage(WorldMessages.MarkDirty.class, this::onMarkDirty)
                .onMessage(WorldMessages.GetTopY.class, this::onGetTopY)
                .onMessage(WorldMessages.TestBlockState.class, this::onTestBlockState)
                .onMessage(WorldMessages.TestFluidState.class, this::onTestFluidState)

                // Neighbor Updates
                .onMessage(WorldMessages.UpdateListeners.class, this::onUpdateListeners)
                .onMessage(WorldMessages.UpdateNeighborsAlways.class, this::onUpdateNeighborsAlways)
                .onMessage(WorldMessages.UpdateNeighborsExcept.class, this::onUpdateNeighborsExcept)
                .onMessage(WorldMessages.UpdateNeighbor.class, this::onUpdateNeighbor)
                .onMessage(WorldMessages.UpdateNeighborState.class, this::onUpdateNeighborState)
                .onMessage(WorldMessages.UpdateNeighbors.class, this::onUpdateNeighbors)
                .onMessage(WorldMessages.UpdateComparators.class, this::onUpdateComparators)
                .onMessage(WorldMessages.ReplaceWithStateForNeighborUpdate.class, this::onReplaceWithStateForNeighborUpdate)

                // Entity Operations
                .onMessage(WorldMessages.SpawnEntity.class, this::onSpawnEntity)
                .onMessage(WorldMessages.TryLoadEntity.class, this::onTryLoadEntity)
                .onMessage(WorldMessages.OnDimensionChanged.class, this::onOnDimensionChanged)
                .onMessage(WorldMessages.OnPlayerConnected.class, this::onOnPlayerConnected)
                .onMessage(WorldMessages.OnPlayerRespawned.class, this::onOnPlayerRespawned)
                .onMessage(WorldMessages.SpawnNewEntityAndPassengers.class, this::onSpawnNewEntityAndPassengers)
                .onMessage(WorldMessages.UnloadEntities.class, this::onUnloadEntities)
                .onMessage(WorldMessages.RemovePlayer.class, this::onRemovePlayer)
                .onMessage(WorldMessages.ShouldTickEntity.class, this::onShouldTickEntity)
                .onMessage(WorldMessages.ShouldUpdatePostDeath.class, this::onShouldUpdatePostDeath)
                .onMessage(WorldMessages.TickEntity.class, this::onTickEntity)
                .onMessage(WorldMessages.TickEntityWithConsumer.class, this::onTickEntityWithConsumer)
                .onMessage(WorldMessages.SendEntityStatus.class, this::onSendEntityStatus)
                .onMessage(WorldMessages.SendEntityDamage.class, this::onSendEntityDamage)
                .onMessage(WorldMessages.GetEntityById.class, this::onGetEntityById)
                .onMessage(WorldMessages.GetEntityByUuid.class, this::onGetEntityByUuid)
                .onMessage(WorldMessages.GetDragonPart.class, this::onGetDragonPart)
                .onMessage(WorldMessages.GetOtherEntities.class, this::onGetOtherEntities)
                .onMessage(WorldMessages.GetEntitiesByType.class, this::onGetEntitiesByType)
                .onMessage(WorldMessages.GetEntitiesByTypePredicate.class, this::onGetEntitiesByTypePredicate)
                .onMessage(WorldMessages.CollectEntitiesByType.class, this::onCollectEntitiesByType)
                .onMessage(WorldMessages.CollectEntitiesByPredicate.class, this::onCollectEntitiesByPredicate)
                .onMessage(WorldMessages.CollectEntitiesByPredicateLimited.class, this::onCollectEntitiesByPredicateLimited)
                .onMessage(WorldMessages.GetPlayers.class, this::onGetPlayers)
                .onMessage(WorldMessages.GetPlayersPredicate.class, this::onGetPlayersPredicate)
                .onMessage(WorldMessages.GetPlayersPredicateLimited.class, this::onGetPlayersPredicateLimited)
                .onMessage(WorldMessages.GetRandomAlivePlayer.class, this::onGetRandomAlivePlayer)
                .onMessage(WorldMessages.GetAliveEnderDragons.class, this::onGetAliveEnderDragons)
                .onMessage(WorldMessages.IterateEntities.class, this::onIterateEntities)
                .onMessage(WorldMessages.GetEntityLookup.class, this::onGetEntityLookup)
                .onMessage(WorldMessages.LoadEntities.class, this::onLoadEntities)
                .onMessage(WorldMessages.AddEntities.class, this::onAddEntities)

                // Block Entity Operations
                .onMessage(WorldMessages.GetBlockEntity.class, this::onGetBlockEntity)
                .onMessage(WorldMessages.AddBlockEntity.class, this::onAddBlockEntity)
                .onMessage(WorldMessages.RemoveBlockEntity.class, this::onRemoveBlockEntity)
                .onMessage(WorldMessages.AddBlockEntityTicker.class, this::onAddBlockEntityTicker)
                .onMessage(WorldMessages.TickBlockEntities.class, this::onTickBlockEntities)

                // World State and Properties
                .onMessage(WorldMessages.Save.class, this::onSave)
                .onMessage(WorldMessages.Close.class, this::onClose)
                .onMessage(WorldMessages.IsSavingDisabled.class, this::onIsSavingDisabled)
                .onMessage(WorldMessages.GetLevelProperties.class, this::onGetLevelProperties)
                .onMessage(WorldMessages.GetGameRules.class, this::onGetGameRules)
                .onMessage(WorldMessages.GetPersistentStateManager.class, this::onGetPersistentStateManager)
                .onMessage(WorldMessages.GetRegistryManager.class, this::onGetRegistryManager)
                .onMessage(WorldMessages.GetEnabledFeatures.class, this::onGetEnabledFeatures)
                .onMessage(WorldMessages.GetDamageSources.class, this::onGetDamageSources)
                .onMessage(WorldMessages.GetChunkManager.class, this::onGetChunkManager)

                // Weather and Environment
                .onMessage(WorldMessages.SetWeather.class, this::onSetWeather)
                .onMessage(WorldMessages.ResetWeather.class, this::onResetWeather)
                .onMessage(WorldMessages.IsDay.class, this::onIsDay)
                .onMessage(WorldMessages.IsNight.class, this::onIsNight)
                .onMessage(WorldMessages.IsThundering.class, this::onIsThundering)
                .onMessage(WorldMessages.IsRaining.class, this::onIsRaining)
                .onMessage(WorldMessages.HasRain.class, this::onHasRain)
                .onMessage(WorldMessages.GetSkyAngleRadians.class, this::onGetSkyAngleRadians)
                .onMessage(WorldMessages.GetThunderGradient.class, this::onGetThunderGradient)
                .onMessage(WorldMessages.SetThunderGradient.class, this::onSetThunderGradient)
                .onMessage(WorldMessages.GetRainGradient.class, this::onGetRainGradient)
                .onMessage(WorldMessages.SetRainGradient.class, this::onSetRainGradient)
                .onMessage(WorldMessages.SetLightningTicksLeft.class, this::onSetLightningTicksLeft)
                .onMessage(WorldMessages.GetLightningPos.class, this::onGetLightningPos)

                // Lighting and Rendering
                .onMessage(WorldMessages.GetLightingProvider.class, this::onGetLightingProvider)
                .onMessage(WorldMessages.GetBrightness.class, this::onGetBrightness)
                .onMessage(WorldMessages.GetAmbientDarkness.class, this::onGetAmbientDarkness)
                .onMessage(WorldMessages.CalculateAmbientDarkness.class, this::onCalculateAmbientDarkness)

                // Events, Sounds and Particles
                .onMessage(WorldMessages.PlaySound.class, this::onPlaySound)
                .onMessage(WorldMessages.PlaySoundFromEntity.class, this::onPlaySoundFromEntity)
                .onMessage(WorldMessages.SyncGlobalEvent.class, this::onSyncGlobalEvent)
                .onMessage(WorldMessages.SyncWorldEvent.class, this::onSyncWorldEvent)
                .onMessage(WorldMessages.EmitGameEvent.class, this::onEmitGameEvent)
                .onMessage(WorldMessages.SpawnParticles.class, this::onSpawnParticles)
                .onMessage(WorldMessages.SpawnParticlesForPlayer.class, this::onSpawnParticlesForPlayer)
                .onMessage(WorldMessages.SetBlockBreakingInfo.class, this::onSetBlockBreakingInfo)
                .onMessage(WorldMessages.AddSyncedBlockEvent.class, this::onAddSyncedBlockEvent)

                // Explosions
                .onMessage(WorldMessages.CreateExplosionSimple.class, this::onCreateExplosionSimple)
                .onMessage(WorldMessages.CreateExplosionWithFire.class, this::onCreateExplosionWithFire)
                .onMessage(WorldMessages.CreateExplosionVec.class, this::onCreateExplosionVec)
                .onMessage(WorldMessages.CreateExplosion.class, this::onCreateExplosion)
                .onMessage(WorldMessages.CreateExplosionWithParticles.class, this::onCreateExplosionWithParticles)
                .onMessage(WorldMessages.CreateExplosionFull.class, this::onCreateExplosionFull)

                // Structures, Biomes and POIs
                .onMessage(WorldMessages.LocateStructure.class, this::onLocateStructure)
                .onMessage(WorldMessages.LocateBiome.class, this::onLocateBiome)
                .onMessage(WorldMessages.GetPointOfInterestStorage.class, this::onGetPointOfInterestStorage)
                .onMessage(WorldMessages.IsNearOccupiedPointOfInterest.class, this::onIsNearOccupiedPointOfInterest)
                .onMessage(WorldMessages.IsNearOccupiedPointOfInterestSection.class, this::onIsNearOccupiedPointOfInterestSection)
                .onMessage(WorldMessages.IsNearOccupiedPointOfInterestDistance.class, this::onIsNearOccupiedPointOfInterestDistance)
                .onMessage(WorldMessages.GetOccupiedPointOfInterestDistance.class, this::onGetOccupiedPointOfInterestDistance)
                .onMessage(WorldMessages.OnBlockChanged.class, this::onOnBlockChanged)
                .onMessage(WorldMessages.CacheStructures.class, this::onCacheStructures)

                // Miscellaneous
                .onMessage(WorldMessages.GetWorldBorder.class, this::onGetWorldBorder)
                .onMessage(WorldMessages.GetRandom.class, this::onGetRandom)
                .onMessage(WorldMessages.GetRandomPosInChunk.class, this::onGetRandomPosInChunk)
                .onMessage(WorldMessages.GetOrCreateRandom.class, this::onGetOrCreateRandom)
                .onMessage(WorldMessages.GetRandomSequences.class, this::onGetRandomSequences)
                .onMessage(WorldMessages.GetProfiler.class, this::onGetProfiler)
                .onMessage(WorldMessages.GetProfilerSupplier.class, this::onGetProfilerSupplier)
                .onMessage(WorldMessages.GetBiomeAccess.class, this::onGetBiomeAccess)
                .onMessage(WorldMessages.GetLocalDifficulty.class, this::onGetLocalDifficulty)
                .onMessage(WorldMessages.GetPathNodeTypeCache.class, this::onGetPathNodeTypeCache)
                .onMessage(WorldMessages.Disconnect.class, this::onDisconnect)
                .onMessage(WorldMessages.GetGeneratorStoredBiome.class, this::onGetGeneratorStoredBiome)
                .onMessage(WorldMessages.GetLogicalHeight.class, this::onGetLogicalHeight)
                .onMessage(WorldMessages.IsFlat.class, this::onIsFlat)
                .onMessage(WorldMessages.GetSeed.class, this::onGetSeed)
                .onMessage(WorldMessages.GetSeaLevel.class, this::onGetSeaLevel)

                // Server-Specific Features
                .onMessage(WorldMessages.SetEnderDragonFight.class, this::onSetEnderDragonFight)
                .onMessage(WorldMessages.GetEnderDragonFight.class, this::onGetEnderDragonFight)
                .onMessage(WorldMessages.ResetIdleTimeout.class, this::onResetIdleTimeout)
                .onMessage(WorldMessages.IsSleepingEnabled.class, this::onIsSleepingEnabled)
                .onMessage(WorldMessages.UpdateSleepingPlayers.class, this::onUpdateSleepingPlayers)
                .onMessage(WorldMessages.GetRaidManager.class, this::onGetRaidManager)
                .onMessage(WorldMessages.GetRaidAt.class, this::onGetRaidAt)
                .onMessage(WorldMessages.HasRaidAt.class, this::onHasRaidAt)
                .onMessage(WorldMessages.HandleInteraction.class, this::onHandleInteraction)
                .onMessage(WorldMessages.GetSpawnPos.class, this::onGetSpawnPos)
                .onMessage(WorldMessages.GetSpawnAngle.class, this::onGetSpawnAngle)
                .onMessage(WorldMessages.SetSpawnPos.class, this::onSetSpawnPos)
                .onMessage(WorldMessages.GetForcedChunks.class, this::onGetForcedChunks)
                .onMessage(WorldMessages.SetChunkForced.class, this::onSetChunkForced)
                .onMessage(WorldMessages.GetPortalForcer.class, this::onGetPortalForcer)

                // Data and Recipes
                .onMessage(WorldMessages.GetScoreboard.class, this::onGetScoreboard)
                .onMessage(WorldMessages.GetMapState.class, this::onGetMapState)
                .onMessage(WorldMessages.PutMapState.class, this::onPutMapState)
                .onMessage(WorldMessages.IncreaseAndGetMapId.class, this::onIncreaseAndGetMapId)
                .onMessage(WorldMessages.GetRecipeManager.class, this::onGetRecipeManager)
                .onMessage(WorldMessages.GetBrewingRecipeRegistry.class, this::onGetBrewingRecipeRegistry)

                // Debug and Diagnostics
                .onMessage(WorldMessages.GetDebugString.class, this::onGetDebugString)
                .onMessage(WorldMessages.AsString.class, this::onAsString)
//                .onMessage(WorldMessages.Dump.class, this::onDump)
                .onMessage(WorldMessages.AddDetailsToCrashReport.class, this::onAddDetailsToCrashReport)

                // Networking
                .onMessage(WorldMessages.SendPacket.class, this::onSendPacket)

                // Tick Schedulers
                .onMessage(WorldMessages.GetBlockTickScheduler.class, this::onGetBlockTickScheduler)
                .onMessage(WorldMessages.GetFluidTickScheduler.class, this::onGetFluidTickScheduler)
                .onMessage(WorldMessages.DisableTickSchedulers.class, this::onDisableTickSchedulers)

                // Fabric Attachment API
                .onMessage(WorldMessages.GetAttached.class, this::onGetAttached)
                .onMessage(WorldMessages.HasAttached.class, this::onHasAttached)
                .onMessage(WorldMessages.GetAttachedOrElse.class, this::onGetAttachedOrElse)
                .onMessage(WorldMessages.GetAttachedOrSet.class, this::onGetAttachedOrSet)
                .onMessage(WorldMessages.GetAttachedOrThrow.class, this::onGetAttachedOrThrow)
                .onMessage(WorldMessages.SetAttached.class, this::onSetAttached)
                .onMessage(WorldMessages.RemoveAttached.class, this::onRemoveAttached)
                .build();
    }


    private Behavior<WorldMessages.WorldMessage> onTick(WorldMessages.Tick msg) {
        this.world.tick(msg.shouldKeepTicking());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTickTime(WorldMessages.TickTime msg) {
        ((ServerWorldAccessor) this.world).invokeTickTime();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTickSpawners(WorldMessages.TickSpawners msg) {
        this.world.tickSpawners(msg.spawnMonsters(), msg.spawnAnimals());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTickChunk(WorldMessages.TickChunk msg) {
        this.world.tickChunk(msg.chunk(), msg.randomTickSpeed());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTickIceAndSnow(WorldMessages.TickIceAndSnow msg) {
        this.world.tickIceAndSnow(msg.pos());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsInBlockTick(WorldMessages.IsInBlockTick msg) {
        msg.replyTo().tell(this.world.isInBlockTick());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetTime(WorldMessages.GetTime msg) {
        msg.replyTo().tell(this.world.getTime());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetTimeOfDay(WorldMessages.GetTimeOfDay msg) {
        msg.replyTo().tell(this.world.getTimeOfDay());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetTimeOfDay(WorldMessages.SetTimeOfDay msg) {
        this.world.setTimeOfDay(msg.timeOfDay());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetTickManager(WorldMessages.GetTickManager msg) {
        msg.replyTo().tell(this.world.getTickManager());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetTickOrder(WorldMessages.GetTickOrder msg) {
        msg.replyTo().tell(this.world.getTickOrder());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetBlockState(WorldMessages.GetBlockState msg) {
        msg.replyTo().tell(this.world.getBlockState(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetFluidState(WorldMessages.GetFluidState msg) {
        msg.replyTo().tell(this.world.getFluidState(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetBlockState(WorldMessages.SetBlockState msg) {
        msg.replyTo().tell(this.world.setBlockState(msg.pos(), msg.state(), msg.flags(), msg.maxUpdateDepth()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onRemoveBlock(WorldMessages.RemoveBlock msg) {
        msg.replyTo().tell(this.world.removeBlock(msg.pos(), msg.move()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onBreakBlock(WorldMessages.BreakBlock msg) {
        msg.replyTo().tell(this.world.breakBlock(msg.pos(), msg.drop(), msg.breakingEntity(), msg.maxUpdateDepth()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onAddBlockBreakParticles(WorldMessages.AddBlockBreakParticles msg) {
        this.world.addBlockBreakParticles(msg.pos(), msg.state());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onScheduleBlockRerenderIfNeeded(WorldMessages.ScheduleBlockRerenderIfNeeded msg) {
        this.world.scheduleBlockRerenderIfNeeded(msg.pos(), msg.old(), msg.updated());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsInBuildLimit(WorldMessages.IsInBuildLimit msg) {
        msg.replyTo().tell(this.world.isInBuildLimit(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCanSetBlock(WorldMessages.CanSetBlock msg) {
        msg.replyTo().tell(this.world.canSetBlock(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsDirectionSolid(WorldMessages.IsDirectionSolid msg) {
        msg.replyTo().tell(this.world.isDirectionSolid(msg.pos(), msg.entity(), msg.direction()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsTopSolid(WorldMessages.IsTopSolid msg) {
        msg.replyTo().tell(this.world.isTopSolid(msg.pos(), msg.entity()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetWorldChunk(WorldMessages.GetWorldChunk msg) {
        msg.replyTo().tell(this.world.getWorldChunk(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetChunk(WorldMessages.GetChunk msg) {
        msg.replyTo().tell(this.world.getChunk(msg.i(), msg.j()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetChunkWithStatus(WorldMessages.GetChunkWithStatus msg) {
        msg.replyTo().tell(this.world.getChunk(msg.chunkX(), msg.chunkZ(), msg.leastStatus(), msg.create()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetChunkAsView(WorldMessages.GetChunkAsView msg) {
        msg.replyTo().tell(this.world.getChunkAsView(msg.chunkX(), msg.chunkZ()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsChunkLoaded(WorldMessages.IsChunkLoaded msg) {
        msg.replyTo().tell(this.world.isChunkLoaded(msg.chunkPos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onShouldTickBlocksInChunk(WorldMessages.ShouldTickBlocksInChunk msg) {
        msg.replyTo().tell(this.world.shouldTickBlocksInChunk(msg.chunkPos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onShouldTickBlockPos(WorldMessages.ShouldTickBlockPos msg) {
        msg.replyTo().tell(this.world.shouldTickBlockPos(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onShouldTickChunkPos(WorldMessages.ShouldTickChunkPos msg) {
        msg.replyTo().tell(this.world.shouldTick(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onMarkDirty(WorldMessages.MarkDirty msg) {
        this.world.markDirty(msg.pos());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetTopY(WorldMessages.GetTopY msg) {
        msg.replyTo().tell(this.world.getTopY(msg.heightmap(), msg.x(), msg.z()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTestBlockState(WorldMessages.TestBlockState msg) {
        msg.replyTo().tell(this.world.testBlockState(msg.pos(), msg.state()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTestFluidState(WorldMessages.TestFluidState msg) {
        msg.replyTo().tell(this.world.testFluidState(msg.pos(), msg.state()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateListeners(WorldMessages.UpdateListeners msg) {
        this.world.updateListeners(msg.pos(), msg.oldState(), msg.newState(), msg.flags());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateNeighborsAlways(WorldMessages.UpdateNeighborsAlways msg) {
        this.world.updateNeighborsAlways(msg.pos(), msg.sourceBlock());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateNeighborsExcept(WorldMessages.UpdateNeighborsExcept msg) {
        this.world.updateNeighborsExcept(msg.pos(), msg.sourceBlock(), msg.direction());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateNeighbor(WorldMessages.UpdateNeighbor msg) {
        this.world.updateNeighbor(msg.pos(), msg.sourceBlock(), msg.sourcePos());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateNeighborState(WorldMessages.UpdateNeighborState msg) {
        this.world.updateNeighbor(msg.state(), msg.pos(), msg.sourceBlock(), msg.sourcePos(), msg.toNotify());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateNeighbors(WorldMessages.UpdateNeighbors msg) {
        this.world.updateNeighbors(msg.pos(), msg.block());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateComparators(WorldMessages.UpdateComparators msg) {
        this.world.updateComparators(msg.pos(), msg.block());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onReplaceWithStateForNeighborUpdate(WorldMessages.ReplaceWithStateForNeighborUpdate msg) {
        this.world.replaceWithStateForNeighborUpdate(msg.direction(), msg.neighborState(), msg.pos(), msg.neighborPos(), msg.flags(), msg.maxUpdateDepth());
        return this;
    }

    // Entity Operation Handlers
    private Behavior<WorldMessages.WorldMessage> onSpawnEntity(WorldMessages.SpawnEntity msg) {
        msg.replyTo().tell(this.world.spawnEntity(msg.entity()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTryLoadEntity(WorldMessages.TryLoadEntity msg) {
        msg.replyTo().tell(this.world.tryLoadEntity(msg.entity()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onOnDimensionChanged(WorldMessages.OnDimensionChanged msg) {
        this.world.onDimensionChanged(msg.entity());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onOnPlayerConnected(WorldMessages.OnPlayerConnected msg) {
        this.world.onPlayerConnected(msg.player());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onOnPlayerRespawned(WorldMessages.OnPlayerRespawned msg) {
        this.world.onPlayerRespawned(msg.player());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSpawnNewEntityAndPassengers(WorldMessages.SpawnNewEntityAndPassengers msg) {
        msg.replyTo().tell(this.world.spawnNewEntityAndPassengers(msg.entity()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUnloadEntities(WorldMessages.UnloadEntities msg) {
        this.world.unloadEntities(msg.chunk());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onRemovePlayer(WorldMessages.RemovePlayer msg) {
        this.world.removePlayer(msg.player(), msg.reason());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onShouldTickEntity(WorldMessages.ShouldTickEntity msg) {
        msg.replyTo().tell(this.world.shouldTickEntity(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onShouldUpdatePostDeath(WorldMessages.ShouldUpdatePostDeath msg) {
        msg.replyTo().tell(this.world.shouldUpdatePostDeath(msg.entity()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTickEntity(WorldMessages.TickEntity msg) {
        this.world.tickEntity(msg.entity());
        return this;
    }

    private <T extends net.minecraft.entity.Entity> Behavior<WorldMessages.WorldMessage> onTickEntityWithConsumer(WorldMessages.TickEntityWithConsumer<T> msg) {
        this.world.tickEntity(msg.tickConsumer(), msg.entity());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSendEntityStatus(WorldMessages.SendEntityStatus msg) {
        this.world.sendEntityStatus(msg.entity(), msg.status());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSendEntityDamage(WorldMessages.SendEntityDamage msg) {
        this.world.sendEntityDamage(msg.entity(), msg.damageSource());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetEntityById(WorldMessages.GetEntityById msg) {
        msg.replyTo().tell(this.world.getEntityById(msg.id()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetEntityByUuid(WorldMessages.GetEntityByUuid msg) {
        msg.replyTo().tell(this.world.getEntity(msg.uuid()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetDragonPart(WorldMessages.GetDragonPart msg) {
        msg.replyTo().tell(this.world.getDragonPart(msg.id()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetOtherEntities(WorldMessages.GetOtherEntities msg) {
        msg.replyTo().tell(this.world.getOtherEntities(msg.except(), msg.box(), msg.predicate()));
        return this;
    }

    private <T extends net.minecraft.entity.Entity> Behavior<WorldMessages.WorldMessage> onGetEntitiesByType(WorldMessages.GetEntitiesByType<T> msg) {
        msg.replyTo().tell(this.world.getEntitiesByType(msg.filter(), msg.box(), msg.predicate()));
        return this;
    }

    private <T extends net.minecraft.entity.Entity> Behavior<WorldMessages.WorldMessage> onGetEntitiesByTypePredicate(WorldMessages.GetEntitiesByTypePredicate<T> msg) {
        msg.replyTo().tell(this.world.getEntitiesByType(msg.filter(), msg.predicate()));
        return this;
    }

    private <T extends net.minecraft.entity.Entity> Behavior<WorldMessages.WorldMessage> onCollectEntitiesByType(WorldMessages.CollectEntitiesByType<T> msg) {
        this.world.collectEntitiesByType(msg.filter(), msg.box(), msg.predicate(), msg.result(), msg.limit());
        return this;
    }

    private <T extends net.minecraft.entity.Entity> Behavior<WorldMessages.WorldMessage> onCollectEntitiesByPredicate(WorldMessages.CollectEntitiesByPredicate<T> msg) {
        this.world.collectEntitiesByType(msg.filter(), msg.predicate(), msg.result());
        return this;
    }

    private <T extends net.minecraft.entity.Entity> Behavior<WorldMessages.WorldMessage> onCollectEntitiesByPredicateLimited(WorldMessages.CollectEntitiesByPredicateLimited<T> msg) {
        this.world.collectEntitiesByType(msg.filter(), msg.predicate(), msg.result(), msg.limit());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPlayers(WorldMessages.GetPlayers msg) {
        msg.replyTo().tell(this.world.getPlayers());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPlayersPredicate(WorldMessages.GetPlayersPredicate msg) {
        msg.replyTo().tell(this.world.getPlayers(msg.predicate()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPlayersPredicateLimited(WorldMessages.GetPlayersPredicateLimited msg) {
        msg.replyTo().tell(this.world.getPlayers(msg.predicate(), msg.limit()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRandomAlivePlayer(WorldMessages.GetRandomAlivePlayer msg) {
        msg.replyTo().tell(this.world.getRandomAlivePlayer());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetAliveEnderDragons(WorldMessages.GetAliveEnderDragons msg) {
        msg.replyTo().tell(this.world.getAliveEnderDragons());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIterateEntities(WorldMessages.IterateEntities msg) {
        msg.replyTo().tell(this.world.iterateEntities());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetEntityLookup(WorldMessages.GetEntityLookup msg) {
        msg.replyTo().tell(((ServerWorldAccessor) this.world).invokeGetEntityLookup());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onLoadEntities(WorldMessages.LoadEntities msg) {
        this.world.loadEntities(msg.entities().stream());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onAddEntities(WorldMessages.AddEntities msg) {
        this.world.addEntities(msg.entities().stream());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetBlockEntity(WorldMessages.GetBlockEntity msg) {
        msg.replyTo().tell(this.world.getBlockEntity(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onAddBlockEntity(WorldMessages.AddBlockEntity msg) {
        this.world.addBlockEntity(msg.blockEntity());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onRemoveBlockEntity(WorldMessages.RemoveBlockEntity msg) {
        this.world.removeBlockEntity(msg.pos());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onAddBlockEntityTicker(WorldMessages.AddBlockEntityTicker msg) {
        this.world.addBlockEntityTicker(msg.ticker());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onTickBlockEntities(WorldMessages.TickBlockEntities msg) {
        ((WorldAccessor) this.world).invokeTickBlockEntities();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSave(WorldMessages.Save msg) {
        this.world.save(msg.progressListener(), msg.flush(), msg.savingDisabled());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onClose(WorldMessages.Close msg) {
        try {
            this.world.close();
        } catch (IOException e) {
            getContext().getLog().error("Failed to close world", e);
        }
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsSavingDisabled(WorldMessages.IsSavingDisabled msg) {
        msg.replyTo().tell(this.world.isSavingDisabled());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetLevelProperties(WorldMessages.GetLevelProperties msg) {
        msg.replyTo().tell(this.world.getLevelProperties());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetGameRules(WorldMessages.GetGameRules msg) {
        msg.replyTo().tell(this.world.getGameRules());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPersistentStateManager(WorldMessages.GetPersistentStateManager msg) {
        msg.replyTo().tell(this.world.getPersistentStateManager());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRegistryManager(WorldMessages.GetRegistryManager msg) {
        msg.replyTo().tell(this.world.getRegistryManager());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetEnabledFeatures(WorldMessages.GetEnabledFeatures msg) {
        msg.replyTo().tell(this.world.getEnabledFeatures());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetDamageSources(WorldMessages.GetDamageSources msg) {
        msg.replyTo().tell(this.world.getDamageSources());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetChunkManager(WorldMessages.GetChunkManager msg) {
        msg.replyTo().tell(this.world.getChunkManager());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetWeather(WorldMessages.SetWeather msg) {
        this.world.setWeather(msg.clearDuration(), msg.rainDuration(), msg.raining(), msg.thundering());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onResetWeather(WorldMessages.ResetWeather msg) {
        this.world.resetWeather();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsDay(WorldMessages.IsDay msg) {
        msg.replyTo().tell(this.world.isDay());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsNight(WorldMessages.IsNight msg) {
        msg.replyTo().tell(this.world.isNight());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsThundering(WorldMessages.IsThundering msg) {
        msg.replyTo().tell(this.world.isThundering());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsRaining(WorldMessages.IsRaining msg) {
        msg.replyTo().tell(this.world.isRaining());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onHasRain(WorldMessages.HasRain msg) {
        msg.replyTo().tell(this.world.hasRain(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetSkyAngleRadians(WorldMessages.GetSkyAngleRadians msg) {
        msg.replyTo().tell(this.world.getSkyAngleRadians(msg.tickDelta()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetThunderGradient(WorldMessages.GetThunderGradient msg) {
        msg.replyTo().tell(this.world.getThunderGradient(msg.delta()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetThunderGradient(WorldMessages.SetThunderGradient msg) {
        this.world.setThunderGradient(msg.thunderGradient());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRainGradient(WorldMessages.GetRainGradient msg) {
        msg.replyTo().tell(this.world.getRainGradient(msg.delta()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetRainGradient(WorldMessages.SetRainGradient msg) {
        this.world.setRainGradient(msg.rainGradient());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetLightningTicksLeft(WorldMessages.SetLightningTicksLeft msg) {
        this.world.setLightningTicksLeft(msg.lightningTicksLeft());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetLightningPos(WorldMessages.GetLightningPos msg) {
        msg.replyTo().tell(((ServerWorldAccessor) this.world).invokeGetLightningPos(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetLightingProvider(WorldMessages.GetLightingProvider msg) {
        msg.replyTo().tell(this.world.getLightingProvider());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetBrightness(WorldMessages.GetBrightness msg) {
        msg.replyTo().tell(this.world.getBrightness(msg.direction(), msg.shaded()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetAmbientDarkness(WorldMessages.GetAmbientDarkness msg) {
        msg.replyTo().tell(this.world.getAmbientDarkness());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCalculateAmbientDarkness(WorldMessages.CalculateAmbientDarkness msg) {
        this.world.calculateAmbientDarkness();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onPlaySound(WorldMessages.PlaySound msg) {
        this.world.playSound(msg.source(), msg.x(), msg.y(), msg.z(), msg.sound(), msg.category(), msg.volume(), msg.pitch(), msg.seed());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onPlaySoundFromEntity(WorldMessages.PlaySoundFromEntity msg) {
        this.world.playSoundFromEntity(msg.source(), msg.entity(), msg.sound(), msg.category(), msg.volume(), msg.pitch(), msg.seed());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSyncGlobalEvent(WorldMessages.SyncGlobalEvent msg) {
        this.world.syncGlobalEvent(msg.eventId(), msg.pos(), msg.data());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSyncWorldEvent(WorldMessages.SyncWorldEvent msg) {
        this.world.syncWorldEvent(msg.player(), msg.eventId(), msg.pos(), msg.data());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onEmitGameEvent(WorldMessages.EmitGameEvent msg) {
        this.world.emitGameEvent(msg.event(), msg.emitterPos(), msg.emitter());
        return this;
    }

    private <T extends net.minecraft.particle.ParticleEffect> Behavior<WorldMessages.WorldMessage> onSpawnParticles(WorldMessages.SpawnParticles<T> msg) {
        msg.replyTo().tell(this.world.spawnParticles(msg.particle(), msg.x(), msg.y(), msg.z(), msg.count(), msg.deltaX(), msg.deltaY(), msg.deltaZ(), msg.speed()));
        return this;
    }

    private <T extends net.minecraft.particle.ParticleEffect> Behavior<WorldMessages.WorldMessage> onSpawnParticlesForPlayer(WorldMessages.SpawnParticlesForPlayer<T> msg) {
        msg.replyTo().tell(this.world.spawnParticles(msg.viewer(), msg.particle(), msg.force(), msg.x(), msg.y(), msg.z(), msg.count(), msg.deltaX(), msg.deltaY(), msg.deltaZ(), msg.speed()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetBlockBreakingInfo(WorldMessages.SetBlockBreakingInfo msg) {
        this.world.setBlockBreakingInfo(msg.entityId(), msg.pos(), msg.progress());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onAddSyncedBlockEvent(WorldMessages.AddSyncedBlockEvent msg) {
        this.world.addSyncedBlockEvent(msg.pos(), msg.block(), msg.type(), msg.data());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCreateExplosionSimple(WorldMessages.CreateExplosionSimple msg) {
        msg.replyTo().tell(this.world.createExplosion(msg.entity(), msg.x(), msg.y(), msg.z(), msg.power(), msg.explosionSourceType()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCreateExplosionWithFire(WorldMessages.CreateExplosionWithFire msg) {
        msg.replyTo().tell(this.world.createExplosion(msg.entity(), msg.x(), msg.y(), msg.z(), msg.power(), msg.createFire(), msg.explosionSourceType()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCreateExplosionVec(WorldMessages.CreateExplosionVec msg) {
        msg.replyTo().tell(this.world.createExplosion(msg.entity(), msg.damageSource(), msg.behavior(), msg.pos(), msg.power(), msg.createFire(), msg.explosionSourceType()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCreateExplosion(WorldMessages.CreateExplosion msg) {
        msg.replyTo().tell(this.world.createExplosion(msg.entity(), msg.damageSource(), msg.behavior(), msg.x(), msg.y(), msg.z(), msg.power(), msg.createFire(), msg.explosionSourceType()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCreateExplosionWithParticles(WorldMessages.CreateExplosionWithParticles msg) {
        msg.replyTo().tell(this.world.createExplosion(msg.entity(), msg.damageSource(), msg.behavior(), msg.x(), msg.y(), msg.z(), msg.power(), msg.createFire(), msg.explosionSourceType(), msg.particle(), msg.emitterParticle(), msg.soundEvent()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCreateExplosionFull(WorldMessages.CreateExplosionFull msg) {
        msg.replyTo().tell(this.world.createExplosion(msg.entity(), msg.damageSource(), msg.behavior(), msg.x(), msg.y(), msg.z(), msg.power(), msg.createFire(), msg.explosionSourceType(), msg.particles(), msg.particle(), msg.emitterParticle(), msg.soundEvent()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onLocateStructure(WorldMessages.LocateStructure msg) {
        msg.replyTo().tell(this.world.locateStructure(msg.structureTag(), msg.pos(), msg.radius(), msg.skipReferencedStructures()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onLocateBiome(WorldMessages.LocateBiome msg) {
        msg.replyTo().tell(this.world.locateBiome(msg.predicate(), msg.pos(), msg.radius(), msg.horizontalBlockCheckInterval(), msg.verticalBlockCheckInterval()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPointOfInterestStorage(WorldMessages.GetPointOfInterestStorage msg) {
        msg.replyTo().tell(this.world.getPointOfInterestStorage());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsNearOccupiedPointOfInterest(WorldMessages.IsNearOccupiedPointOfInterest msg) {
        msg.replyTo().tell(this.world.isNearOccupiedPointOfInterest(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsNearOccupiedPointOfInterestSection(WorldMessages.IsNearOccupiedPointOfInterestSection msg) {
        msg.replyTo().tell(this.world.isNearOccupiedPointOfInterest(msg.sectionPos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsNearOccupiedPointOfInterestDistance(WorldMessages.IsNearOccupiedPointOfInterestDistance msg) {
        msg.replyTo().tell(this.world.isNearOccupiedPointOfInterest(msg.pos(), msg.maxDistance()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetOccupiedPointOfInterestDistance(WorldMessages.GetOccupiedPointOfInterestDistance msg) {
        msg.replyTo().tell(this.world.getOccupiedPointOfInterestDistance(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onOnBlockChanged(WorldMessages.OnBlockChanged msg) {
        this.world.onBlockChanged(msg.pos(), msg.oldBlock(), msg.newBlock());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onCacheStructures(WorldMessages.CacheStructures msg) {
        this.world.cacheStructures(msg.chunk());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetWorldBorder(WorldMessages.GetWorldBorder msg) {
        msg.replyTo().tell(this.world.getWorldBorder());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRandom(WorldMessages.GetRandom msg) {
        msg.replyTo().tell(this.world.getRandom());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRandomPosInChunk(WorldMessages.GetRandomPosInChunk msg) {
        msg.replyTo().tell(this.world.getRandomPosInChunk(msg.x(), msg.y(), msg.z(), msg.i()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetOrCreateRandom(WorldMessages.GetOrCreateRandom msg) {
        msg.replyTo().tell(this.world.getOrCreateRandom(msg.id()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRandomSequences(WorldMessages.GetRandomSequences msg) {
        msg.replyTo().tell(this.world.getRandomSequences());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetProfiler(WorldMessages.GetProfiler msg) {
        msg.replyTo().tell(this.world.getProfiler());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetProfilerSupplier(WorldMessages.GetProfilerSupplier msg) {
        msg.replyTo().tell(this.world.getProfilerSupplier());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetBiomeAccess(WorldMessages.GetBiomeAccess msg) {
        msg.replyTo().tell(this.world.getBiomeAccess());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetLocalDifficulty(WorldMessages.GetLocalDifficulty msg) {
        msg.replyTo().tell(this.world.getLocalDifficulty(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPathNodeTypeCache(WorldMessages.GetPathNodeTypeCache msg) {
        msg.replyTo().tell(this.world.getPathNodeTypeCache());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onDisconnect(WorldMessages.Disconnect msg) {
        this.world.disconnect();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetGeneratorStoredBiome(WorldMessages.GetGeneratorStoredBiome msg) {
        msg.replyTo().tell(this.world.getGeneratorStoredBiome(msg.biomeX(), msg.biomeY(), msg.biomeZ()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetLogicalHeight(WorldMessages.GetLogicalHeight msg) {
        msg.replyTo().tell(this.world.getLogicalHeight());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsFlat(WorldMessages.IsFlat msg) {
        msg.replyTo().tell(this.world.isFlat());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetSeed(WorldMessages.GetSeed msg) {
        msg.replyTo().tell(this.world.getSeed());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetSeaLevel(WorldMessages.GetSeaLevel msg) {
        msg.replyTo().tell(this.world.getSeaLevel());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetEnderDragonFight(WorldMessages.SetEnderDragonFight msg) {
        this.world.setEnderDragonFight(msg.enderDragonFight());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetEnderDragonFight(WorldMessages.GetEnderDragonFight msg) {
        msg.replyTo().tell(this.world.getEnderDragonFight());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onResetIdleTimeout(WorldMessages.ResetIdleTimeout msg) {
        this.world.resetIdleTimeout();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIsSleepingEnabled(WorldMessages.IsSleepingEnabled msg) {
        msg.replyTo().tell(this.world.isSleepingEnabled());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onUpdateSleepingPlayers(WorldMessages.UpdateSleepingPlayers msg) {
        this.world.updateSleepingPlayers();
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRaidManager(WorldMessages.GetRaidManager msg) {
        msg.replyTo().tell(this.world.getRaidManager());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRaidAt(WorldMessages.GetRaidAt msg) {
        msg.replyTo().tell(this.world.getRaidAt(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onHasRaidAt(WorldMessages.HasRaidAt msg) {
        msg.replyTo().tell(this.world.hasRaidAt(msg.pos()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onHandleInteraction(WorldMessages.HandleInteraction msg) {
        this.world.handleInteraction(msg.interaction(), msg.entity(), msg.observer());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetSpawnPos(WorldMessages.GetSpawnPos msg) {
        msg.replyTo().tell(this.world.getSpawnPos());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetSpawnAngle(WorldMessages.GetSpawnAngle msg) {
        msg.replyTo().tell(this.world.getSpawnAngle());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetSpawnPos(WorldMessages.SetSpawnPos msg) {
        this.world.setSpawnPos(msg.pos(), msg.angle());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetForcedChunks(WorldMessages.GetForcedChunks msg) {
        msg.replyTo().tell(this.world.getForcedChunks());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSetChunkForced(WorldMessages.SetChunkForced msg) {
        msg.replyTo().tell(this.world.setChunkForced(msg.x(), msg.z(), msg.forced()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetPortalForcer(WorldMessages.GetPortalForcer msg) {
        msg.replyTo().tell(this.world.getPortalForcer());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetScoreboard(WorldMessages.GetScoreboard msg) {
        msg.replyTo().tell(this.world.getScoreboard());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetMapState(WorldMessages.GetMapState msg) {
        msg.replyTo().tell(this.world.getMapState(msg.id()));
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onPutMapState(WorldMessages.PutMapState msg) {
        this.world.putMapState(msg.id(), msg.state());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onIncreaseAndGetMapId(WorldMessages.IncreaseAndGetMapId msg) {
        msg.replyTo().tell(this.world.increaseAndGetMapId());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetRecipeManager(WorldMessages.GetRecipeManager msg) {
        msg.replyTo().tell(this.world.getRecipeManager());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetBrewingRecipeRegistry(WorldMessages.GetBrewingRecipeRegistry msg) {
        msg.replyTo().tell(this.world.getBrewingRecipeRegistry());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetDebugString(WorldMessages.GetDebugString msg) {
        msg.replyTo().tell(this.world.getDebugString());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onAsString(WorldMessages.AsString msg) {
        msg.replyTo().tell(this.world.asString());
        return this;
    }

//    private Behavior<WorldMessages.WorldMessage> onDump(WorldMessages.Dump msg) {
//        try {
//            this.world.dump(msg.path());
//        } catch (IOException e) {
//            getContext().getLog().error("Failed to dump world", e);
//        }
//        return this;
//    }

    private Behavior<WorldMessages.WorldMessage> onAddDetailsToCrashReport(WorldMessages.AddDetailsToCrashReport msg) {
        this.world.addDetailsToCrashReport(msg.report());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onSendPacket(WorldMessages.SendPacket msg) {
        this.world.sendPacket(msg.packet());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetBlockTickScheduler(WorldMessages.GetBlockTickScheduler msg) {
        msg.replyTo().tell(this.world.getBlockTickScheduler());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onGetFluidTickScheduler(WorldMessages.GetFluidTickScheduler msg) {
        msg.replyTo().tell(this.world.getFluidTickScheduler());
        return this;
    }

    private Behavior<WorldMessages.WorldMessage> onDisableTickSchedulers(WorldMessages.DisableTickSchedulers msg) {
        this.world.disableTickSchedulers(msg.chunk());
        return this;
    }
    //</editor-fold>

    //<editor-fold desc="Fabric Attachment API Handlers">
    private <A> Behavior<WorldMessages.WorldMessage> onGetAttached(WorldMessages.GetAttached<A> msg) {
        msg.replyTo().tell(this.world.getAttached(msg.type()));
        return this;
    }
    private Behavior<WorldMessages.WorldMessage> onHasAttached(WorldMessages.HasAttached msg) {
        msg.replyTo().tell(this.world.hasAttached(msg.type()));
        return this;
    }
    private <A> Behavior<WorldMessages.WorldMessage> onGetAttachedOrElse(WorldMessages.GetAttachedOrElse<A> msg) {
        msg.replyTo().tell(this.world.getAttachedOrElse(msg.type(), msg.fallback()));
        return this;
    }
    private <A> Behavior<WorldMessages.WorldMessage> onGetAttachedOrSet(WorldMessages.GetAttachedOrSet<A> msg) {
        msg.replyTo().tell(this.world.getAttachedOrSet(msg.type(), msg.value()));
        return this;
    }
    private <A> Behavior<WorldMessages.WorldMessage> onGetAttachedOrThrow(WorldMessages.GetAttachedOrThrow<A> msg) {
        msg.replyTo().tell(this.world.getAttachedOrThrow(msg.type()));
        return this;
    }
    private <A> Behavior<WorldMessages.WorldMessage> onSetAttached(WorldMessages.SetAttached<A> msg) {
        msg.replyTo().tell(this.world.setAttached(msg.type(), msg.value()));
        return this;
    }
    private <A> Behavior<WorldMessages.WorldMessage> onRemoveAttached(WorldMessages.RemoveAttached<A> msg) {
        msg.replyTo().tell(this.world.removeAttached(msg.type()));
        return this;
    }
}