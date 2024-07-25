package ua.zefir.zefiroptimizations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;

import java.util.List;
import java.util.Optional;

public class WorkStationCompetitionTaskModified {
    public static Task<VillagerEntity> create() {
        return TaskTriggerer.task(
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
                                            if (mob instanceof VillagerEntity villager && mob != entity) {
                                                if (villager.isAlive() && isUsingWorkStationAt(globalPos, poiType, villager)) {
                                                    moreExperiencedVillager = keepJobSiteForMoreExperiencedVillager(moreExperiencedVillager, villager);
                                                }
                                            }
                                        }
                                    }

                                    return true;
                                }
                        )
        );
    }

    private static VillagerEntity keepJobSiteForMoreExperiencedVillager(VillagerEntity first, VillagerEntity second) {
        if (first.getExperience() > second.getExperience()) {
            forgetJobSite(second);
            return first;
        } else {
            forgetJobSite(first);
            return second;
        }
    }

    private static void forgetJobSite(VillagerEntity villager) {
        Brain<VillagerEntity> brain = villager.getBrain();
        brain.forget(MemoryModuleType.JOB_SITE);
    }

    private static boolean isUsingWorkStationAt(GlobalPos pos, RegistryEntry<PointOfInterestType> poiType, VillagerEntity villager) {
        Optional<GlobalPos> optional = villager.getBrain().getOptionalRegisteredMemory(MemoryModuleType.JOB_SITE);
        return optional.isPresent() && pos.equals(optional.get()) && isCompletedWorkStation(poiType, villager.getVillagerData().getProfession());
    }

    private static boolean isCompletedWorkStation(RegistryEntry<PointOfInterestType> poiType, VillagerProfession profession) {
        return profession.heldWorkstation().test(poiType);
    }
}