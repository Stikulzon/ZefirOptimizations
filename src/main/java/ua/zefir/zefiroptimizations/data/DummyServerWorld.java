package ua.zefir.zefiroptimizations.data;

import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AskPattern;
import akka.japi.function.Function;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.PathNodeTypeCache;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.map.MapState;
import net.minecraft.network.packet.Packet;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.village.raid.Raid;
import net.minecraft.village.raid.RaidManager;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.BlockEntityTickInvoker;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.PortalForcer;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.spawner.SpecialSpawner;
import net.minecraft.world.tick.TickManager;
import net.minecraft.world.tick.WorldTickScheduler;
import org.jetbrains.annotations.Nullable;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.WorldMessages;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DummyServerWorld extends ServerWorld {
    private final ActorRef<WorldMessages.WorldMessage> worldActor;
    private final ServerWorld realWorld;

    public DummyServerWorld(ServerWorld realWorld,
                            MinecraftServer server,
                            Executor workerExecutor,
                            LevelStorage.Session session,
                            ServerWorldProperties properties,
                            RegistryKey<World> worldKey,
                            DimensionOptions dimensionOptions,
                            WorldGenerationProgressListener worldGenerationProgressListener,
                            boolean debugWorld,
                            long seed,
                            List<SpecialSpawner> spawners,
                            boolean shouldTickTime,
                            @Nullable RandomSequencesState randomSequencesState) {
        super(server,
                workerExecutor,
                session,
                properties,
                worldKey,
                dimensionOptions,
                worldGenerationProgressListener,
                debugWorld,
                seed,
                spawners,
                shouldTickTime,
                randomSequencesState);

        this.realWorld = realWorld;

        CompletionStage<ZefirsActorMessages.ResponseWorldActorRef> resultFuture =
                AskPattern.ask(
                        ZefirOptimizations.getActorSystem(),
                        replyTo -> new ZefirsActorMessages.ServerWorldCreated(realWorld, realWorld.getRegistryKey(), replyTo),
                        ZefirOptimizations.timeout,
                        ZefirOptimizations.getActorSystem().scheduler());
        try {
            this.worldActor = resultFuture.toCompletableFuture().get().worldActor();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting the result from ServerWorld actor", e);
        }
    }


    private <T> T ask(Function<ActorRef<T>, WorldMessages.WorldMessage> messageProvider) {
        try {
            return AskPattern.ask(
                    this.worldActor,
                    messageProvider,
                    ZefirOptimizations.timeout,
                    ZefirOptimizations.getActorSystem().scheduler()
            ).toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Error getting result from ServerWorld actor", e);
        }
    }

    //<editor-fold desc="ServerWorld Overrides">
    @Override
    public void setEnderDragonFight(@Nullable EnderDragonFight enderDragonFight) {
        this.worldActor.tell(new WorldMessages.SetEnderDragonFight(enderDragonFight));
    }

    @Override
    public void setWeather(int clearDuration, int rainDuration, boolean raining, boolean thundering) {
        this.worldActor.tell(new WorldMessages.SetWeather(clearDuration, rainDuration, raining, thundering));
    }

    @Override
    public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ) {
        return ask(replyTo -> new WorldMessages.GetGeneratorStoredBiome(biomeX, biomeY, biomeZ, replyTo));
    }

    @Override
    public void tick(BooleanSupplier shouldKeepTicking) {
        this.worldActor.tell(new WorldMessages.Tick(shouldKeepTicking));
    }

    @Override
    public boolean shouldTickBlocksInChunk(long chunkPos) {
        return ask(replyTo -> new WorldMessages.ShouldTickBlocksInChunk(chunkPos, replyTo));
    }

    @Override
    protected void tickTime() {
        this.worldActor.tell(new WorldMessages.TickTime());
    }

    @Override
    public void setTimeOfDay(long timeOfDay) {
        this.worldActor.tell(new WorldMessages.SetTimeOfDay(timeOfDay));
    }

    @Override
    public void tickSpawners(boolean spawnMonsters, boolean spawnAnimals) {
        this.worldActor.tell(new WorldMessages.TickSpawners(spawnMonsters, spawnAnimals));
    }

    @Override
    public void tickChunk(WorldChunk chunk, int randomTickSpeed) {
        this.worldActor.tell(new WorldMessages.TickChunk(chunk, randomTickSpeed));
    }

    @Override
    public void tickIceAndSnow(BlockPos pos) {
        this.worldActor.tell(new WorldMessages.TickIceAndSnow(pos));
    }

    @Override
    protected BlockPos getLightningPos(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetLightningPos(pos, replyTo));
    }

    @Override
    public boolean isInBlockTick() {
        return ask(WorldMessages.IsInBlockTick::new);
    }

    @Override
    public boolean isSleepingEnabled() {
        return ask(WorldMessages.IsSleepingEnabled::new);
    }

    @Override
    public void updateSleepingPlayers() {
        this.worldActor.tell(new WorldMessages.UpdateSleepingPlayers());
    }

    @Override
    public ServerScoreboard getScoreboard() {
        return ask(WorldMessages.GetScoreboard::new);
    }

    @Override
    public void resetWeather() {
        this.worldActor.tell(new WorldMessages.ResetWeather());
    }

    @Override
    public void resetIdleTimeout() {
        this.worldActor.tell(new WorldMessages.ResetIdleTimeout());
    }

    @Override
    public void tickEntity(Entity entity) {
        this.worldActor.tell(new WorldMessages.TickEntity(entity));
    }

    @Override
    public void save(@Nullable net.minecraft.util.ProgressListener progressListener, boolean flush, boolean savingDisabled) {
        this.worldActor.tell(new WorldMessages.Save(progressListener, flush, savingDisabled));
    }

    @Override
    public <T extends Entity> List<? extends T> getEntitiesByType(TypeFilter<Entity, T> filter, Predicate<? super T> predicate) {
        return ask(replyTo -> new WorldMessages.GetEntitiesByTypePredicate(filter, predicate, replyTo));
    }

    @Override
    public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result) {
        this.worldActor.tell(new WorldMessages.CollectEntitiesByPredicate(filter, predicate, result));
    }

    @Override
    public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result, int limit) {
        this.worldActor.tell(new WorldMessages.CollectEntitiesByPredicateLimited(filter, predicate, result, limit));
    }

    @Override
    public List<? extends EnderDragonEntity> getAliveEnderDragons() {
        return ask(WorldMessages.GetAliveEnderDragons::new);
    }

    @Override
    public List<ServerPlayerEntity> getPlayers(Predicate<? super ServerPlayerEntity> predicate) {
        return ask(replyTo -> new WorldMessages.GetPlayersPredicate(predicate, replyTo));
    }

    @Override
    public List<ServerPlayerEntity> getPlayers(Predicate<? super ServerPlayerEntity> predicate, int limit) {
        return ask(replyTo -> new WorldMessages.GetPlayersPredicateLimited(predicate, limit, replyTo));
    }

    @Nullable
    @Override
    public ServerPlayerEntity getRandomAlivePlayer() {
        return ask(WorldMessages.GetRandomAlivePlayer::new);
    }

    @Override
    public boolean spawnEntity(Entity entity) {
        return ask(replyTo -> new WorldMessages.SpawnEntity(entity, replyTo));
    }

    @Override
    public boolean tryLoadEntity(Entity entity) {
        return ask(replyTo -> new WorldMessages.TryLoadEntity(entity, replyTo));
    }

    @Override
    public void onDimensionChanged(Entity entity) {
        this.worldActor.tell(new WorldMessages.OnDimensionChanged(entity));
    }

    @Override
    public void onPlayerConnected(ServerPlayerEntity player) {
        this.worldActor.tell(new WorldMessages.OnPlayerConnected(player));
    }

    @Override
    public void onPlayerRespawned(ServerPlayerEntity player) {
        this.worldActor.tell(new WorldMessages.OnPlayerRespawned(player));
    }

    @Override
    public boolean spawnNewEntityAndPassengers(Entity entity) {
        return ask(replyTo -> new WorldMessages.SpawnNewEntityAndPassengers(entity, replyTo));
    }

    @Override
    public void unloadEntities(WorldChunk chunk) {
        this.worldActor.tell(new WorldMessages.UnloadEntities(chunk));
    }

    @Override
    public void removePlayer(ServerPlayerEntity player, Entity.RemovalReason reason) {
        this.worldActor.tell(new WorldMessages.RemovePlayer(player, reason));
    }

    @Override
    public void setBlockBreakingInfo(int entityId, BlockPos pos, int progress) {
        this.worldActor.tell(new WorldMessages.SetBlockBreakingInfo(entityId, pos, progress));
    }

    @Override
    public void playSound(@Nullable PlayerEntity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
        this.worldActor.tell(new WorldMessages.PlaySound(source, x, y, z, sound, category, volume, pitch, seed));
    }

    @Override
    public void playSoundFromEntity(@Nullable PlayerEntity source, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) {
        this.worldActor.tell(new WorldMessages.PlaySoundFromEntity(source, entity, sound, category, volume, pitch, seed));
    }

    @Override
    public void syncGlobalEvent(int eventId, BlockPos pos, int data) {
        this.worldActor.tell(new WorldMessages.SyncGlobalEvent(eventId, pos, data));
    }

    @Override
    public void syncWorldEvent(@Nullable PlayerEntity player, int eventId, BlockPos pos, int data) {
        this.worldActor.tell(new WorldMessages.SyncWorldEvent(player, eventId, pos, data));
    }

    @Override
    public int getLogicalHeight() {
        return ask(WorldMessages.GetLogicalHeight::new);
    }

    @Override
    public void emitGameEvent(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter) {
        this.worldActor.tell(new WorldMessages.EmitGameEvent(event, emitterPos, emitter));
    }

    @Override
    public void updateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
        this.worldActor.tell(new WorldMessages.UpdateListeners(pos, oldState, newState, flags));
    }

    @Override
    public void updateNeighborsAlways(BlockPos pos, Block sourceBlock) {
        this.worldActor.tell(new WorldMessages.UpdateNeighborsAlways(pos, sourceBlock));
    }

    @Override
    public void updateNeighborsExcept(BlockPos pos, Block sourceBlock, Direction direction) {
        this.worldActor.tell(new WorldMessages.UpdateNeighborsExcept(pos, sourceBlock, direction));
    }

    @Override
    public void updateNeighbor(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {
        this.worldActor.tell(new WorldMessages.UpdateNeighbor(pos, sourceBlock, sourcePos));
    }

    @Override
    public void updateNeighbor(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        this.worldActor.tell(new WorldMessages.UpdateNeighborState(state, pos, sourceBlock, sourcePos, notify));
    }

    @Override
    public ServerChunkManager getChunkManager() {
        return ask(WorldMessages.GetChunkManager::new);
    }

    @Override
    public void addSyncedBlockEvent(BlockPos pos, Block block, int type, int data) {
        this.worldActor.tell(new WorldMessages.AddSyncedBlockEvent(pos, block, type, data));
    }

    @Override
    public WorldTickScheduler<Block> getBlockTickScheduler() {
        return ask(WorldMessages.GetBlockTickScheduler::new);
    }

    @Override
    public WorldTickScheduler<Fluid> getFluidTickScheduler() {
        return ask(WorldMessages.GetFluidTickScheduler::new);
    }

    @Override
    public PortalForcer getPortalForcer() {
        return ask(WorldMessages.GetPortalForcer::new);
    }

    @Override
    public <T extends ParticleEffect> boolean spawnParticles(ServerPlayerEntity viewer, T particle, boolean force, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed) {
        return ask(replyTo -> new WorldMessages.SpawnParticlesForPlayer(viewer, particle, force, x, y, z, count, deltaX, deltaY, deltaZ, speed, replyTo));
    }

    @Nullable
    @Override
    public Entity getDragonPart(int id) {
        return ask(replyTo -> new WorldMessages.GetDragonPart(id, replyTo));
    }

    @Nullable
    @Override
    public BlockPos locateStructure(net.minecraft.registry.tag.TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipReferencedStructures) {
        return ask(replyTo -> new WorldMessages.LocateStructure(structureTag, pos, radius, skipReferencedStructures, replyTo));
    }

    @Nullable
    @Override
    public Pair<BlockPos, RegistryEntry<Biome>> locateBiome(Predicate<RegistryEntry<Biome>> predicate, BlockPos pos, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval) {
        return ask(replyTo -> new WorldMessages.LocateBiome(predicate, pos, radius, horizontalBlockCheckInterval, verticalBlockCheckInterval, replyTo));
    }

    @Override
    public PersistentStateManager getPersistentStateManager() {
        return ask(WorldMessages.GetPersistentStateManager::new);
    }

    @Override
    public void setSpawnPos(BlockPos pos, float angle) {
        this.worldActor.tell(new WorldMessages.SetSpawnPos(pos, angle));
    }

    @Override
    public LongSet getForcedChunks() {
        return ask(WorldMessages.GetForcedChunks::new);
    }

    @Override
    public boolean setChunkForced(int x, int z, boolean forced) {
        return ask(replyTo -> new WorldMessages.SetChunkForced(x, z, forced, replyTo));
    }

    @Override
    public List<ServerPlayerEntity> getPlayers() {
        return ask(WorldMessages.GetPlayers::new);
    }

    @Override
    public void onBlockChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock) {
        this.worldActor.tell(new WorldMessages.OnBlockChanged(pos, oldBlock, newBlock));
    }

    @Override
    public PointOfInterestStorage getPointOfInterestStorage() {
        return ask(WorldMessages.GetPointOfInterestStorage::new);
    }

    @Override
    public boolean isNearOccupiedPointOfInterest(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.IsNearOccupiedPointOfInterest(pos, replyTo));
    }

    @Override
    public boolean isNearOccupiedPointOfInterest(ChunkSectionPos sectionPos) {
        return ask(replyTo -> new WorldMessages.IsNearOccupiedPointOfInterestSection(sectionPos, replyTo));
    }

    @Override
    public boolean isNearOccupiedPointOfInterest(BlockPos pos, int maxDistance) {
        return ask(replyTo -> new WorldMessages.IsNearOccupiedPointOfInterestDistance(pos, maxDistance, replyTo));
    }

    @Override
    public int getOccupiedPointOfInterestDistance(ChunkSectionPos pos) {
        return ask(replyTo -> new WorldMessages.GetOccupiedPointOfInterestDistance(pos, replyTo));
    }

    @Override
    public RaidManager getRaidManager() {
        return ask(WorldMessages.GetRaidManager::new);
    }

    @Nullable
    @Override
    public Raid getRaidAt(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetRaidAt(pos, replyTo));
    }

    @Override
    public boolean hasRaidAt(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.HasRaidAt(pos, replyTo));
    }

    @Override
    public void handleInteraction(net.minecraft.entity.EntityInteraction interaction, Entity entity, net.minecraft.entity.InteractionObserver observer) {
        this.worldActor.tell(new WorldMessages.HandleInteraction(interaction, entity, observer));
    }

    @Override
    public void dump(Path path) throws IOException {
//        this.worldActor.tell(new WorldMessages.Dump(path));
        this.realWorld.dump(path);
    }

    @Override
    public void updateNeighbors(BlockPos pos, Block block) {
        this.worldActor.tell(new WorldMessages.UpdateNeighbors(pos, block));
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded) {
        return ask(replyTo -> new WorldMessages.GetBrightness(direction, shaded, replyTo));
    }

    @Override
    public Iterable<Entity> iterateEntities() {
        return ask(WorldMessages.IterateEntities::new);
    }

    @Override
    public boolean isFlat() {
        return ask(WorldMessages.IsFlat::new);
    }

    @Override
    public long getSeed() {
        return ask(WorldMessages.GetSeed::new);
    }

    @Nullable
    @Override
    public EnderDragonFight getEnderDragonFight() {
        return ask(WorldMessages.GetEnderDragonFight::new);
    }

    @Override
    public ServerWorld toServerWorld() {
        return this;
    }

    @Override
    public String getDebugString() {
        return ask(WorldMessages.GetDebugString::new);
    }

    @Override
    protected EntityLookup<Entity> getEntityLookup() {
        return ask(WorldMessages.GetEntityLookup::new);
    }

    @Override
    public void loadEntities(Stream<Entity> entities) {
        this.worldActor.tell(new WorldMessages.LoadEntities(entities.toList()));
    }

    @Override
    public void addEntities(Stream<Entity> entities) {
        this.worldActor.tell(new WorldMessages.AddEntities(entities.toList()));
    }

    @Override
    public void disableTickSchedulers(WorldChunk chunk) {
        this.worldActor.tell(new WorldMessages.DisableTickSchedulers(chunk));
    }

    @Override
    public void cacheStructures(Chunk chunk) {
        this.worldActor.tell(new WorldMessages.CacheStructures(chunk));
    }

    @Override
    public PathNodeTypeCache getPathNodeTypeCache() {
        return ask(WorldMessages.GetPathNodeTypeCache::new);
    }

    @Override
    public String asString() {
        return ask(WorldMessages.AsString::new);
    }

    @Override
    public boolean isChunkLoaded(long chunkPos) {
        return ask(replyTo -> new WorldMessages.IsChunkLoaded(chunkPos, replyTo));
    }

    @Override
    public boolean shouldTickEntity(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.ShouldTickEntity(pos, replyTo));
    }

    @Override
    public boolean shouldTick(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.ShouldTickBlockPos(pos, replyTo));
    }

    @Override
    public boolean shouldTick(ChunkPos pos) {
        return ask(replyTo -> new WorldMessages.ShouldTickChunkPos(pos, replyTo));
    }

    @Override
    public net.minecraft.resource.featuretoggle.FeatureSet getEnabledFeatures() {
        return ask(WorldMessages.GetEnabledFeatures::new);
    }

    @Override
    public BrewingRecipeRegistry getBrewingRecipeRegistry() {
        return ask(WorldMessages.GetBrewingRecipeRegistry::new);
    }

    @Override
    public Random getOrCreateRandom(Identifier id) {
        return ask(replyTo -> new WorldMessages.GetOrCreateRandom(id, replyTo));
    }

    @Override
    public net.minecraft.util.math.random.RandomSequencesState getRandomSequences() {
        return ask(WorldMessages.GetRandomSequences::new);
    }

    @Override
    public boolean isInBuildLimit(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.IsInBuildLimit(pos, replyTo));
    }

    @Override
    public WorldChunk getWorldChunk(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetWorldChunk(pos, replyTo));
    }

    @Override
    public WorldChunk getChunk(int i, int j) {
        return ask(replyTo -> new WorldMessages.GetChunk(i, j, replyTo));
    }

    @Nullable
    @Override
    public Chunk getChunk(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create) {
        return ask(replyTo -> new WorldMessages.GetChunkWithStatus(chunkX, chunkZ, leastStatus, create, replyTo));
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState state, int flags) {
        return this.setBlockState(pos, state, flags, 512);
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        return ask(replyTo -> new WorldMessages.SetBlockState(pos, state, flags, maxUpdateDepth, replyTo));
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean move) {
        return ask(replyTo -> new WorldMessages.RemoveBlock(pos, move, replyTo));
    }

    @Override
    public boolean breakBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth) {
        return ask(replyTo -> new WorldMessages.BreakBlock(pos, drop, breakingEntity, maxUpdateDepth, replyTo));
    }

    @Override
    public void addBlockBreakParticles(BlockPos pos, BlockState state) {
        this.worldActor.tell(new WorldMessages.AddBlockBreakParticles(pos, state));
    }

    @Override
    public boolean setBlockState(BlockPos pos, BlockState state) {
        return this.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    @Override
    public void scheduleBlockRerenderIfNeeded(BlockPos pos, BlockState old, BlockState updated) {
        this.worldActor.tell(new WorldMessages.ScheduleBlockRerenderIfNeeded(pos, old, updated));
    }

    @Override
    public void replaceWithStateForNeighborUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        this.worldActor.tell(new WorldMessages.ReplaceWithStateForNeighborUpdate(direction, neighborState, pos, neighborPos, flags, maxUpdateDepth));
    }

    @Override
    public int getTopY(Heightmap.Type heightmap, int x, int z) {
        return ask(replyTo -> new WorldMessages.GetTopY(heightmap, x, z, replyTo));
    }

    @Override
    public LightingProvider getLightingProvider() {
        return ask(WorldMessages.GetLightingProvider::new);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetBlockState(pos, replyTo));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetFluidState(pos, replyTo));
    }

    @Override
    public boolean isDay() {
        return ask(WorldMessages.IsDay::new);
    }

    @Override
    public boolean isNight() {
        return ask(WorldMessages.IsNight::new);
    }

    @Override
    public float getSkyAngleRadians(float tickDelta) {
        return ask(replyTo -> new WorldMessages.GetSkyAngleRadians(tickDelta, replyTo));
    }

    @Override
    public void addBlockEntityTicker(BlockEntityTickInvoker ticker) {
        this.worldActor.tell(new WorldMessages.AddBlockEntityTicker(ticker));
    }

    @Override
    protected void tickBlockEntities() {
        this.worldActor.tell(new WorldMessages.TickBlockEntities());
    }

    @Override
    public <T extends Entity> void tickEntity(Consumer<T> tickConsumer, T entity) {
        this.worldActor.tell(new WorldMessages.TickEntityWithConsumer(tickConsumer, entity));
    }

    @Override
    public boolean shouldUpdatePostDeath(Entity entity) {
        return ask(replyTo -> new WorldMessages.ShouldUpdatePostDeath(entity, replyTo));
    }

    @Override
    public boolean shouldTickBlockPos(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.ShouldTickBlockPos(pos, replyTo));
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, double x, double y, double z, float power, World.ExplosionSourceType explosionSourceType) {
        return ask(replyTo -> new WorldMessages.CreateExplosionSimple(entity, x, y, z, power, explosionSourceType, replyTo));
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType) {
        return ask(replyTo -> new WorldMessages.CreateExplosionWithFire(entity, x, y, z, power, createFire, explosionSourceType, replyTo));
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, Vec3d pos, float power, boolean createFire, World.ExplosionSourceType explosionSourceType) {
        return ask(replyTo -> new WorldMessages.CreateExplosionVec(entity, damageSource, behavior, pos, power, createFire, explosionSourceType, replyTo));
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType) {
        return ask(replyTo -> new WorldMessages.CreateExplosion(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, replyTo));
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, ParticleEffect particle, ParticleEffect emitterParticle, RegistryEntry<SoundEvent> soundEvent) {
        return ask(replyTo -> new WorldMessages.CreateExplosionWithParticles(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, particle, emitterParticle, soundEvent, replyTo));
    }

    @Override
    public Explosion createExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, boolean particles, ParticleEffect particle, ParticleEffect emitterParticle, RegistryEntry<SoundEvent> soundEvent) {
        return ask(replyTo -> new WorldMessages.CreateExplosionFull(entity, damageSource, behavior, x, y, z, power, createFire, explosionSourceType, particles, particle, emitterParticle, soundEvent, replyTo));
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetBlockEntity(pos, replyTo));
    }

    @Override
    public void addBlockEntity(BlockEntity blockEntity) {
        this.worldActor.tell(new WorldMessages.AddBlockEntity(blockEntity));
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
        this.worldActor.tell(new WorldMessages.RemoveBlockEntity(pos));
    }

    @Override
    public boolean canSetBlock(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.CanSetBlock(pos, replyTo));
    }

    @Override
    public boolean isDirectionSolid(BlockPos pos, Entity entity, Direction direction) {
        return ask(replyTo -> new WorldMessages.IsDirectionSolid(pos, entity, direction, replyTo));
    }

    @Override
    public boolean isTopSolid(BlockPos pos, Entity entity) {
        return ask(replyTo -> new WorldMessages.IsTopSolid(pos, entity, replyTo));
    }

    @Override
    public void calculateAmbientDarkness() {
        this.worldActor.tell(new WorldMessages.CalculateAmbientDarkness());
    }

    @Override
    public BlockPos getSpawnPos() {
        return ask(WorldMessages.GetSpawnPos::new);
    }

    @Override
    public float getSpawnAngle() {
        return ask(WorldMessages.GetSpawnAngle::new);
    }

    @Override
    public void close() throws IOException {
        this.worldActor.tell(new WorldMessages.Close());
    }

    @Nullable
    @Override
    public BlockView getChunkAsView(int chunkX, int chunkZ) {
        return ask(replyTo -> new WorldMessages.GetChunkAsView(chunkX, chunkZ, replyTo));
    }

    @Override
    public List<Entity> getOtherEntities(@Nullable Entity except, Box box, Predicate<? super Entity> predicate) {
        return ask(replyTo -> new WorldMessages.GetOtherEntities(except, box, predicate, replyTo));
    }

    @Override
    public <T extends Entity> List<T> getEntitiesByType(TypeFilter<Entity, T> filter, Box box, Predicate<? super T> predicate) {
        return ask(replyTo -> new WorldMessages.GetEntitiesByType(filter, box, predicate, replyTo));
    }

    @Override
    public <T extends Entity> void collectEntitiesByType(TypeFilter<Entity, T> filter, Box box, Predicate<? super T> predicate, List<? super T> result, int limit) {
        this.worldActor.tell(new WorldMessages.CollectEntitiesByType(filter, box, predicate, result, limit));
    }

    @Nullable
    @Override
    public Entity getEntityById(int id) {
        return ask(replyTo -> new WorldMessages.GetEntityById(id, replyTo));
    }

    @Override
    public void markDirty(BlockPos pos) {
        this.worldActor.tell(new WorldMessages.MarkDirty(pos));
    }

    @Override
    public int getSeaLevel() {
        return ask(WorldMessages.GetSeaLevel::new);
    }

    @Override
    public void disconnect() {
        this.worldActor.tell(new WorldMessages.Disconnect());
    }

    @Override
    public long getTime() {
        return ask(WorldMessages.GetTime::new);
    }

    @Override
    public long getTimeOfDay() {
        return ask(WorldMessages.GetTimeOfDay::new);
    }

    @Override
    public void sendEntityStatus(Entity entity, byte status) {
        this.worldActor.tell(new WorldMessages.SendEntityStatus(entity, status));
    }

    @Override
    public void sendEntityDamage(Entity entity, DamageSource damageSource) {
        this.worldActor.tell(new WorldMessages.SendEntityDamage(entity, damageSource));
    }

    @Override
    public WorldProperties getLevelProperties() {
        return ask(WorldMessages.GetLevelProperties::new);
    }

    @Override
    public GameRules getGameRules() {
        return ask(WorldMessages.GetGameRules::new);
    }

    @Override
    public TickManager getTickManager() {
        return ask(WorldMessages.GetTickManager::new);
    }

    @Override
    public float getThunderGradient(float delta) {
        return ask(replyTo -> new WorldMessages.GetThunderGradient(delta, replyTo));
    }

    @Override
    public void setThunderGradient(float thunderGradient) {
        this.worldActor.tell(new WorldMessages.SetThunderGradient(thunderGradient));
    }

    @Override
    public float getRainGradient(float delta) {
        return ask(replyTo -> new WorldMessages.GetRainGradient(delta, replyTo));
    }

    @Override
    public void setRainGradient(float rainGradient) {
        this.worldActor.tell(new WorldMessages.SetRainGradient(rainGradient));
    }

    @Override
    public boolean isThundering() {
        return ask(WorldMessages.IsThundering::new);
    }

    @Override
    public boolean isRaining() {
        return ask(WorldMessages.IsRaining::new);
    }

    @Override
    public boolean hasRain(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.HasRain(pos, replyTo));
    }

    @Nullable
    @Override
    public MapState getMapState(MapIdComponent id) {
        return ask(replyTo -> new WorldMessages.GetMapState(id, replyTo));
    }

    @Override
    public void putMapState(MapIdComponent id, MapState state) {
        this.worldActor.tell(new WorldMessages.PutMapState(id, state));
    }

    @Override
    public MapIdComponent increaseAndGetMapId() {
        return ask(WorldMessages.IncreaseAndGetMapId::new);
    }

    @Override
    public CrashReportSection addDetailsToCrashReport(CrashReport report) {
//        this.worldActor.tell(new WorldMessages.AddDetailsToCrashReport(report));
//        return report.getSystemDetailsSection();
        return this.realWorld.addDetailsToCrashReport(report);
    }

    @Override
    public void updateComparators(BlockPos pos, Block block) {
        this.worldActor.tell(new WorldMessages.UpdateComparators(pos, block));
    }

    @Override
    public LocalDifficulty getLocalDifficulty(BlockPos pos) {
        return ask(replyTo -> new WorldMessages.GetLocalDifficulty(pos, replyTo));
    }

    @Override
    public int getAmbientDarkness() {
        return ask(WorldMessages.GetAmbientDarkness::new);
    }

    @Override
    public void setLightningTicksLeft(int lightningTicksLeft) {
        this.worldActor.tell(new WorldMessages.SetLightningTicksLeft(lightningTicksLeft));
    }

    @Override
    public WorldBorder getWorldBorder() {
        return ask(WorldMessages.GetWorldBorder::new);
    }

    @Override
    public void sendPacket(Packet<?> packet) {
        this.worldActor.tell(new WorldMessages.SendPacket(packet));
    }

    @Override
    public DimensionType getDimension() {
        return ask(WorldMessages.GetDimension::new);
    }

    @Override
    public RegistryEntry<DimensionType> getDimensionEntry() {
        return ask(WorldMessages.GetDimensionEntry::new);
    }

    @Override
    public Random getRandom() {
        return ask(WorldMessages.GetRandom::new);
    }

    @Override
    public boolean testBlockState(BlockPos pos, Predicate<BlockState> state) {
        return ask(replyTo -> new WorldMessages.TestBlockState(pos, state, replyTo));
    }

    @Override
    public boolean testFluidState(BlockPos pos, Predicate<FluidState> state) {
        return ask(replyTo -> new WorldMessages.TestFluidState(pos, state, replyTo));
    }

    @Override
    public RecipeManager getRecipeManager() {
        return ask(WorldMessages.GetRecipeManager::new);
    }

    @Override
    public BlockPos getRandomPosInChunk(int x, int y, int z, int i) {
        return ask(replyTo -> new WorldMessages.GetRandomPosInChunk(x, y, z, i, replyTo));
    }

    @Override
    public boolean isSavingDisabled() {
        return ask(WorldMessages.IsSavingDisabled::new);
    }

    @Override
    public Profiler getProfiler() {
        return ask(WorldMessages.GetProfiler::new);
    }

    @Override
    public Supplier<Profiler> getProfilerSupplier() {
        return ask(WorldMessages.GetProfilerSupplier::new);
    }

    @Override
    public BiomeAccess getBiomeAccess() {
        return ask(WorldMessages.GetBiomeAccess::new);
    }

    @Override
    public long getTickOrder() {
        return ask(WorldMessages.GetTickOrder::new);
    }

    @Override
    public DynamicRegistryManager getRegistryManager() {
        return ask(WorldMessages.GetRegistryManager::new);
    }

    @Override
    public DamageSources getDamageSources() {
        return ask(WorldMessages.GetDamageSources::new);
    }

    // AttachmentTarget methods (from Fabric API)
    @Override
    public <A> @Nullable A getAttached(AttachmentType<A> type) {
        return ask(replyTo -> new WorldMessages.GetAttached(type, replyTo));
    }

    @Override
    public boolean hasAttached(AttachmentType<?> type) {
        return ask(replyTo -> new WorldMessages.HasAttached(type, replyTo));
    }

    @Override
    public <A> @Nullable A getAttachedOrElse(AttachmentType<A> type, @Nullable A fallback) {
        return ask(replyTo -> new WorldMessages.GetAttachedOrElse(type, fallback, replyTo));
    }

//    @Override
//    public <A> @Nullable A getAttachedOrCreate(AttachmentType<A> type, Supplier<A> initializer) {
//        return ask(replyTo -> new WorldMessages.GetAttachedOrElse(type, fallback, replyTo));
//    }

//    @Override
//    public <A> @Nullable A getAttachedOrCreate(AttachmentType<A> type) {
//        return ask(replyTo -> new WorldMessages.GetAttachedOrElse(type, fallback, replyTo));
//    }

    @Override
    public <A> A getAttachedOrSet(AttachmentType<A> type, A value) {
        return ask(replyTo -> new WorldMessages.GetAttachedOrSet(type, value, replyTo));
    }

    @Override
    public <A> A getAttachedOrThrow(AttachmentType<A> type) {
        return ask(replyTo -> new WorldMessages.GetAttachedOrThrow(type, replyTo));
    }

    @Override
    public <A> @Nullable A setAttached(AttachmentType<A> type, @Nullable A value) {
        return ask(replyTo -> new WorldMessages.SetAttached(type, value, replyTo));
    }

    @Override
    public <A> @Nullable A removeAttached(AttachmentType<A> type) {
        return ask(replyTo -> new WorldMessages.RemoveAttached(type, replyTo));
    }
}