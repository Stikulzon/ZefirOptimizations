package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.entity.ai.brain.task.WorkStationCompetitionTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(WorkStationCompetitionTask.class)
public class WorkStationCompetitionTaskMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void create(CallbackInfoReturnable<Task<VillagerEntity>> cir){
        cir.setReturnValue(TaskTriggerer.task(
                context -> context.group(context.queryMemoryValue(MemoryModuleType.JOB_SITE), context.queryMemoryValue(MemoryModuleType.MOBS))
                        .apply(
                                context,
                                (jobSite, mobs) -> (world, entity, time) -> {
                                    GlobalPos globalPos = context.getValue(jobSite);
                                    Optional<RegistryEntry<PointOfInterestType>> poiTypeOpt = world.getPointOfInterestStorage().getType(globalPos.pos());

                                    if (poiTypeOpt.isPresent()) {
                                        List<LivingEntity> villagerEntities = context.getValue(mobs);
                                        RegistryEntry<PointOfInterestType> poiType = poiTypeOpt.get();

                                        VillagerEntity moreExperiencedVillager = entity;

                                        for (LivingEntity mob : villagerEntities) {
                                            if (mob instanceof VillagerEntity && mob != entity) {
                                                VillagerEntity villager = (VillagerEntity) mob;
                                                if (villager.isAlive() && isUsingWorkStationAt(globalPos, poiType, villager)) {
                                                    moreExperiencedVillager = keepJobSiteForMoreExperiencedVillager(moreExperiencedVillager, villager);
                                                }
                                            }
                                        }
                                    }

                                    return true;
                                }
                        )
        ));
    }

    @Unique
    private static VillagerEntity keepJobSiteForMoreExperiencedVillager(VillagerEntity first, VillagerEntity second) {
        if (first.getExperience() > second.getExperience()) {
            forgetJobSite(second);
            return first;
        } else {
            forgetJobSite(first);
            return second;
        }
    }

    @Unique
    private static void forgetJobSite(VillagerEntity villager) {
        Brain<VillagerEntity> brain = villager.getBrain();
        brain.forget(MemoryModuleType.JOB_SITE);
    }

    @Unique
    private static boolean isUsingWorkStationAt(GlobalPos pos, RegistryEntry<PointOfInterestType> poiType, VillagerEntity villager) {
        Optional<GlobalPos> optional = villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
        return optional.isPresent() && pos.equals(optional.get()) && isCompletedWorkStation(poiType, villager.getVillagerData().getProfession());
    }

    @Unique
    private static boolean isCompletedWorkStation(RegistryEntry<PointOfInterestType> poiType, VillagerProfession profession) {
        return profession.heldWorkstation().test(poiType);
    }
}
