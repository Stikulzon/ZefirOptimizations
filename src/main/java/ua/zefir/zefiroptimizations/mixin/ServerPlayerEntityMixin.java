package ua.zefir.zefiroptimizations.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ua.zefir.zefiroptimizations.ZefirOptimizations;
import ua.zefir.zefiroptimizations.actors.messages.ZefirsActorMessages;
import ua.zefir.zefiroptimizations.data.ServerPlayerEntityMixinInterface;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin extends PlayerEntity implements ServerPlayerEntityMixinInterface {

    public ServerPlayerEntityMixin(World world, BlockPos pos, float yaw, GameProfile gameProfile) {
        super(world, pos, yaw, gameProfile);
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Redirect(method = "playerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;tick()V"))
    private void onPlayerTick(PlayerEntity instance) {
        ZefirOptimizations.getActorSystem().tell(new ZefirsActorMessages.TickPlayer(instance));
    }

    @Override
    public void zefirOptimizations$callSuperTick(){
        super.tick();
    }
}
