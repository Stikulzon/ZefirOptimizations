package ua.zefir.zefiroptimizations.actors.messages;

import akka.actor.typed.ActorRef;
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
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.crash.CrashReport;
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
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.tick.TickManager;
import net.minecraft.world.tick.WorldTickScheduler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Defines all possible messages that can be sent to a ServerWorldActor.
 * Each message corresponds to a public method in the World or ServerWorld class.
 */
public interface WorldMessages {
    interface WorldMessage {}

    //<editor-fold desc="Tick and Time">
    record Tick(BooleanSupplier shouldKeepTicking) implements WorldMessage {}
    record TickTime() implements WorldMessage {}
    record TickSpawners(boolean spawnMonsters, boolean spawnAnimals) implements WorldMessage {}
    record TickChunk(WorldChunk chunk, int randomTickSpeed) implements WorldMessage {}
    record TickIceAndSnow(BlockPos pos) implements WorldMessage {}
    record IsInBlockTick(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetTime(ActorRef<Long> replyTo) implements WorldMessage {}
    record GetTimeOfDay(ActorRef<Long> replyTo) implements WorldMessage {}
    record SetTimeOfDay(long timeOfDay) implements WorldMessage {}
    record GetTickManager(ActorRef<TickManager> replyTo) implements WorldMessage {}
    record GetTickOrder(ActorRef<Long> replyTo) implements WorldMessage {}
    //</editor-fold>

    //<editor-fold desc="Block and Chunk Operations">
    record GetBlockState(BlockPos pos, ActorRef<BlockState> replyTo) implements WorldMessage {}
    record GetFluidState(BlockPos pos, ActorRef<FluidState> replyTo) implements WorldMessage {}
    record SetBlockState(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record RemoveBlock(BlockPos pos, boolean move, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record BreakBlock(BlockPos pos, boolean drop, @Nullable Entity breakingEntity, int maxUpdateDepth, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record AddBlockBreakParticles(BlockPos pos, BlockState state) implements WorldMessage {}
    record ScheduleBlockRerenderIfNeeded(BlockPos pos, BlockState old, BlockState updated) implements WorldMessage {}
    record IsInBuildLimit(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record CanSetBlock(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsDirectionSolid(BlockPos pos, Entity entity, Direction direction, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsTopSolid(BlockPos pos, Entity entity, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetWorldChunk(BlockPos pos, ActorRef<WorldChunk> replyTo) implements WorldMessage {}
    record GetChunk(int i, int j, ActorRef<WorldChunk> replyTo) implements WorldMessage {}
    record GetChunkWithStatus(int chunkX, int chunkZ, ChunkStatus leastStatus, boolean create, ActorRef<Chunk> replyTo) implements WorldMessage {}
    record GetChunkAsView(int chunkX, int chunkZ, ActorRef<BlockView> replyTo) implements WorldMessage {}
    record IsChunkLoaded(long chunkPos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record ShouldTickBlocksInChunk(long chunkPos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record ShouldTickBlockPos(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record ShouldTickChunkPos(ChunkPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record MarkDirty(BlockPos pos) implements WorldMessage {}
    record GetTopY(Heightmap.Type heightmap, int x, int z, ActorRef<Integer> replyTo) implements WorldMessage {}
    record TestBlockState(BlockPos pos, Predicate<BlockState> state, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record TestFluidState(BlockPos pos, Predicate<FluidState> state, ActorRef<Boolean> replyTo) implements WorldMessage {}

    // Neighbor Updates
    record UpdateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags) implements WorldMessage {}
    record UpdateNeighborsAlways(BlockPos pos, Block sourceBlock) implements WorldMessage {}
    record UpdateNeighborsExcept(BlockPos pos, Block sourceBlock, Direction direction) implements WorldMessage {}
    record UpdateNeighbor(BlockPos pos, Block sourceBlock, BlockPos sourcePos) implements WorldMessage {}
    record UpdateNeighborState(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean toNotify) implements WorldMessage {}
    record UpdateNeighbors(BlockPos pos, Block block) implements WorldMessage {}
    record UpdateComparators(BlockPos pos, Block block) implements WorldMessage {}
    record ReplaceWithStateForNeighborUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) implements WorldMessage {}

    // Entity Operations
    record SpawnEntity(Entity entity, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record TryLoadEntity(Entity entity, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record OnDimensionChanged(Entity entity) implements WorldMessage {}
    record OnPlayerConnected(ServerPlayerEntity player) implements WorldMessage {}
    record OnPlayerRespawned(ServerPlayerEntity player) implements WorldMessage {}
    record SpawnNewEntityAndPassengers(Entity entity, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record UnloadEntities(WorldChunk chunk) implements WorldMessage {}
    record RemovePlayer(ServerPlayerEntity player, Entity.RemovalReason reason) implements WorldMessage {}
    record ShouldTickEntity(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record ShouldUpdatePostDeath(Entity entity, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record TickEntity(Entity entity) implements WorldMessage {}
    record TickEntityWithConsumer<T extends Entity> (Consumer<T> tickConsumer, T entity) implements WorldMessage {}
    record SendEntityStatus(Entity entity, byte status) implements WorldMessage {}
    record SendEntityDamage(Entity entity, DamageSource damageSource) implements WorldMessage {}
    record GetEntityById(int id, ActorRef<Entity> replyTo) implements WorldMessage {}
    record GetEntityByUuid(UUID uuid, ActorRef<Entity> replyTo) implements WorldMessage {}
    record GetDragonPart(int id, ActorRef<Entity> replyTo) implements WorldMessage {}
    record GetOtherEntities(@Nullable Entity except, Box box, Predicate<? super Entity> predicate, ActorRef<List<Entity>> replyTo) implements WorldMessage {}
    record GetEntitiesByType <T extends Entity> (TypeFilter<Entity, T> filter, Box box, Predicate<? super T> predicate, ActorRef<List<T>> replyTo) implements WorldMessage {}
    record GetEntitiesByTypePredicate <T extends Entity> (TypeFilter<Entity, T> filter, Predicate<? super T> predicate, ActorRef<List<? extends T>> replyTo) implements WorldMessage {}
    record CollectEntitiesByType <T extends Entity> (TypeFilter<Entity, T> filter, Box box, Predicate<? super T> predicate, List<? super T> result, int limit) implements WorldMessage {}
    record CollectEntitiesByPredicate <T extends Entity> (TypeFilter<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result) implements WorldMessage {}
    record CollectEntitiesByPredicateLimited <T extends Entity> (TypeFilter<Entity, T> filter, Predicate<? super T> predicate, List<? super T> result, int limit) implements WorldMessage {}
    record GetPlayers(ActorRef<List<ServerPlayerEntity>> replyTo) implements WorldMessage {}
    record GetPlayersPredicate(Predicate<? super ServerPlayerEntity> predicate, ActorRef<List<ServerPlayerEntity>> replyTo) implements WorldMessage {}
    record GetPlayersPredicateLimited(Predicate<? super ServerPlayerEntity> predicate, int limit, ActorRef<List<ServerPlayerEntity>> replyTo) implements WorldMessage {}
    record GetRandomAlivePlayer(ActorRef<ServerPlayerEntity> replyTo) implements WorldMessage {}
    record GetAliveEnderDragons(ActorRef<List<? extends EnderDragonEntity>> replyTo) implements WorldMessage {}
    record IterateEntities(ActorRef<Iterable<Entity>> replyTo) implements WorldMessage {}
    record GetEntityLookup(ActorRef<EntityLookup<Entity>> replyTo) implements WorldMessage {}
    record LoadEntities(List<Entity> entities) implements WorldMessage {}
    record AddEntities(List<Entity> entities) implements WorldMessage {}

    // Block Entity Operations
    record GetBlockEntity(BlockPos pos, ActorRef<BlockEntity> replyTo) implements WorldMessage {}
    record AddBlockEntity(BlockEntity blockEntity) implements WorldMessage {}
    record RemoveBlockEntity(BlockPos pos) implements WorldMessage {}
    record AddBlockEntityTicker(BlockEntityTickInvoker ticker) implements WorldMessage {}
    record TickBlockEntities() implements WorldMessage {}

    // World State and Properties
    record Save(ProgressListener progressListener, boolean flush, boolean savingDisabled) implements WorldMessage {}
    record Close() implements WorldMessage {}
    record IsSavingDisabled(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetLevelProperties(ActorRef<WorldProperties> replyTo) implements WorldMessage {}
    record GetGameRules(ActorRef<GameRules> replyTo) implements WorldMessage {}
    record GetPersistentStateManager(ActorRef<PersistentStateManager> replyTo) implements WorldMessage {}
    record GetRegistryManager(ActorRef<DynamicRegistryManager> replyTo) implements WorldMessage {}
    record GetEnabledFeatures(ActorRef<FeatureSet> replyTo) implements WorldMessage {}
    record GetDamageSources(ActorRef<DamageSources> replyTo) implements WorldMessage {}
    record GetChunkManager(ActorRef<ServerChunkManager> replyTo) implements WorldMessage {}
    record GetDimension(ActorRef<DimensionType> replyTo) implements WorldMessage {}
    record GetDimensionEntry(ActorRef<RegistryEntry<DimensionType>> replyTo) implements WorldMessage {}

    // Weather and Environment
    record SetWeather(int clearDuration, int rainDuration, boolean raining, boolean thundering) implements WorldMessage {}
    record ResetWeather() implements WorldMessage {}
    record IsDay(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsNight(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsThundering(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsRaining(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record HasRain(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetSkyAngleRadians(float tickDelta, ActorRef<Float> replyTo) implements WorldMessage {}
    record GetThunderGradient(float delta, ActorRef<Float> replyTo) implements WorldMessage {}
    record SetThunderGradient(float thunderGradient) implements WorldMessage {}
    record GetRainGradient(float delta, ActorRef<Float> replyTo) implements WorldMessage {}
    record SetRainGradient(float rainGradient) implements WorldMessage {}
    record SetLightningTicksLeft(int lightningTicksLeft) implements WorldMessage {}
    record GetLightningPos(BlockPos pos, ActorRef<BlockPos> replyTo) implements WorldMessage {}

    // Lighting and Rendering
    record GetLightingProvider(ActorRef<LightingProvider> replyTo) implements WorldMessage {}
    record GetBrightness(Direction direction, boolean shaded, ActorRef<Float> replyTo) implements WorldMessage {}
    record GetAmbientDarkness(ActorRef<Integer> replyTo) implements WorldMessage {}
    record CalculateAmbientDarkness() implements WorldMessage {}

    // Events, Sounds and Particles
    record PlaySound(@Nullable PlayerEntity source, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) implements WorldMessage {}
    record PlaySoundFromEntity(@Nullable PlayerEntity source, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed) implements WorldMessage {}
    record SyncGlobalEvent(int eventId, BlockPos pos, int data) implements WorldMessage {}
    record SyncWorldEvent(@Nullable PlayerEntity player, int eventId, BlockPos pos, int data) implements WorldMessage {}
    record EmitGameEvent(RegistryEntry<GameEvent> event, Vec3d emitterPos, GameEvent.Emitter emitter) implements WorldMessage {}
    record SpawnParticles <T extends ParticleEffect> (T particle, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed, ActorRef<Integer> replyTo) implements WorldMessage {}
    record SpawnParticlesForPlayer <T extends ParticleEffect> (ServerPlayerEntity viewer, T particle, boolean force, double x, double y, double z, int count, double deltaX, double deltaY, double deltaZ, double speed, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record SetBlockBreakingInfo(int entityId, BlockPos pos, int progress) implements WorldMessage {}
    record AddSyncedBlockEvent(BlockPos pos, Block block, int type, int data) implements WorldMessage {}

    // Explosions
    record CreateExplosionSimple(Entity entity, double x, double y, double z, float power, World.ExplosionSourceType explosionSourceType, ActorRef<Explosion> replyTo) implements WorldMessage {}
    record CreateExplosionWithFire(Entity entity, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, ActorRef<Explosion> replyTo) implements WorldMessage {}
    record CreateExplosionVec(Entity entity, DamageSource damageSource, ExplosionBehavior behavior, Vec3d pos, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, ActorRef<Explosion> replyTo) implements WorldMessage {}
    record CreateExplosion(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, ActorRef<Explosion> replyTo) implements WorldMessage {}
    record CreateExplosionWithParticles(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, ParticleEffect particle, ParticleEffect emitterParticle, RegistryEntry<SoundEvent> soundEvent, ActorRef<Explosion> replyTo) implements WorldMessage {}
    record CreateExplosionFull(@Nullable Entity entity, @Nullable DamageSource damageSource, @Nullable ExplosionBehavior behavior, double x, double y, double z, float power, boolean createFire, World.ExplosionSourceType explosionSourceType, boolean particles, ParticleEffect particle, ParticleEffect emitterParticle, RegistryEntry<SoundEvent> soundEvent, ActorRef<Explosion> replyTo) implements WorldMessage {}

    // Structures, Biomes and POIs
    record LocateStructure(TagKey<Structure> structureTag, BlockPos pos, int radius, boolean skipReferencedStructures, ActorRef<BlockPos> replyTo) implements WorldMessage {}
    record LocateBiome(Predicate<RegistryEntry<Biome>> predicate, BlockPos pos, int radius, int horizontalBlockCheckInterval, int verticalBlockCheckInterval, ActorRef<Pair<BlockPos, RegistryEntry<Biome>>> replyTo) implements WorldMessage {}
    record GetPointOfInterestStorage(ActorRef<PointOfInterestStorage> replyTo) implements WorldMessage {}
    record IsNearOccupiedPointOfInterest(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsNearOccupiedPointOfInterestSection(ChunkSectionPos sectionPos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record IsNearOccupiedPointOfInterestDistance(BlockPos pos, int maxDistance, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetOccupiedPointOfInterestDistance(ChunkSectionPos pos, ActorRef<Integer> replyTo) implements WorldMessage {}
    record OnBlockChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock) implements WorldMessage {}
    record CacheStructures(Chunk chunk) implements WorldMessage {}

    // Miscellaneous
    record GetWorldBorder(ActorRef<WorldBorder> replyTo) implements WorldMessage {}
    record GetRandom(ActorRef<Random> replyTo) implements WorldMessage {}
    record GetRandomPosInChunk(int x, int y, int z, int i, ActorRef<BlockPos> replyTo) implements WorldMessage {}
    record GetOrCreateRandom(Identifier id, ActorRef<Random> replyTo) implements WorldMessage {}
    record GetRandomSequences(ActorRef<RandomSequencesState> replyTo) implements WorldMessage {}
    record GetProfiler(ActorRef<Profiler> replyTo) implements WorldMessage {}
    record GetProfilerSupplier(ActorRef<Supplier<Profiler>> replyTo) implements WorldMessage {}
    record GetBiomeAccess(ActorRef<BiomeAccess> replyTo) implements WorldMessage {}
    record GetLocalDifficulty(BlockPos pos, ActorRef<LocalDifficulty> replyTo) implements WorldMessage {}
    record GetPathNodeTypeCache(ActorRef<PathNodeTypeCache> replyTo) implements WorldMessage {}
    record Disconnect() implements WorldMessage {}
    record GetGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ, ActorRef<RegistryEntry<Biome>> replyTo) implements WorldMessage {}
    record GetLogicalHeight(ActorRef<Integer> replyTo) implements WorldMessage {}
    record IsFlat(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetSeed(ActorRef<Long> replyTo) implements WorldMessage {}
    record GetSeaLevel(ActorRef<Integer> replyTo) implements WorldMessage {}

    // Server-Specific Features
    record SetEnderDragonFight(@Nullable EnderDragonFight enderDragonFight) implements WorldMessage {}
    record GetEnderDragonFight(ActorRef<EnderDragonFight> replyTo) implements WorldMessage {}
    record ResetIdleTimeout() implements WorldMessage {}
    record IsSleepingEnabled(ActorRef<Boolean> replyTo) implements WorldMessage {}
    record UpdateSleepingPlayers() implements WorldMessage {}
    record GetRaidManager(ActorRef<RaidManager> replyTo) implements WorldMessage {}
    record GetRaidAt(BlockPos pos, ActorRef<Raid> replyTo) implements WorldMessage {}
    record HasRaidAt(BlockPos pos, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record HandleInteraction(net.minecraft.entity.EntityInteraction interaction, Entity entity, net.minecraft.entity.InteractionObserver observer) implements WorldMessage {}
    record GetSpawnPos(ActorRef<BlockPos> replyTo) implements WorldMessage {}
    record GetSpawnAngle(ActorRef<Float> replyTo) implements WorldMessage {}
    record SetSpawnPos(BlockPos pos, float angle) implements WorldMessage {}
    record GetForcedChunks(ActorRef<LongSet> replyTo) implements WorldMessage {}
    record SetChunkForced(int x, int z, boolean forced, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetPortalForcer(ActorRef<net.minecraft.world.dimension.PortalForcer> replyTo) implements WorldMessage {}

    // Data and Recipes
    record GetScoreboard(ActorRef<ServerScoreboard> replyTo) implements WorldMessage {}
    record GetMapState(MapIdComponent id, ActorRef<MapState> replyTo) implements WorldMessage {}
    record PutMapState(MapIdComponent id, MapState state) implements WorldMessage {}
    record IncreaseAndGetMapId(ActorRef<MapIdComponent> replyTo) implements WorldMessage {}
    record GetRecipeManager(ActorRef<RecipeManager> replyTo) implements WorldMessage {}
    record GetBrewingRecipeRegistry(ActorRef<BrewingRecipeRegistry> replyTo) implements WorldMessage {}

    // Debug and Diagnostics
    record GetDebugString(ActorRef<String> replyTo) implements WorldMessage {}
    record AsString(ActorRef<String> replyTo) implements WorldMessage {}
//    record Dump(Path path) {}
    record AddDetailsToCrashReport(CrashReport report) implements WorldMessage {}

    // Networking
    record SendPacket(Packet<?> packet) implements WorldMessage {}

    // Tick Schedulers
    record GetBlockTickScheduler(ActorRef<WorldTickScheduler<Block>> replyTo) implements WorldMessage {}
    record GetFluidTickScheduler(ActorRef<WorldTickScheduler<Fluid>> replyTo) implements WorldMessage {}
    record DisableTickSchedulers(WorldChunk chunk) implements WorldMessage {}

    // Fabric Attachment API
    record GetAttached<A>(AttachmentType<A> type, ActorRef<@Nullable A> replyTo) implements WorldMessage {}
    record HasAttached(AttachmentType<?> type, ActorRef<Boolean> replyTo) implements WorldMessage {}
    record GetAttachedOrElse<A>(AttachmentType<A> type, @Nullable A fallback, ActorRef<@Nullable A> replyTo) implements WorldMessage {}
    record GetAttachedOrSet<A>(AttachmentType<A> type, A value, ActorRef<A> replyTo) implements WorldMessage {}
    record GetAttachedOrThrow<A>(AttachmentType<A> type, ActorRef<A> replyTo) implements WorldMessage {}
    record SetAttached<A>(AttachmentType<A> type, @Nullable A value, ActorRef<@Nullable A> replyTo) implements WorldMessage {}
    record RemoveAttached<A>(AttachmentType<A> type, ActorRef<@Nullable A> replyTo) implements WorldMessage {}

}