package ua.zefir.zefiroptimizations.mixin;

import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.entity.ai.brain.task.WorkStationCompetitionTask;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ua.zefir.zefiroptimizations.WorkStationCompetitionTaskModified;

import static ua.zefir.zefiroptimizations.Commands.isOptimizeVillagers;

@Mixin(WorkStationCompetitionTask.class)
public class WorkStationCompetitionTaskMixin {
//    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
//    private static void create(CallbackInfoReturnable<Task<VillagerEntity>> cir){
//        if(isOptimizeVillagers) {
//            cir.setReturnValue(WorkStationCompetitionTaskModified.create());
//        }
//    }
}
