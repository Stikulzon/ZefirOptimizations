/*!stonecutter-disabled
package ua.zefir.zephyrium.process.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelInvoker {
  @Invoker
  boolean invokeShouldDiscardEntity(Entity entity);
}

*/