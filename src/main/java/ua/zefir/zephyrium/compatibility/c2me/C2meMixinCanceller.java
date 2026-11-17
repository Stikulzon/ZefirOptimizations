package ua.zefir.zephyrium.compatibility.c2me;

import java.util.List;

public final class C2meMixinCanceller implements com.bawnorton.mixinsquared.api.MixinCanceller {
  @Override
  public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
    return mixinClassName.equals(
            "com.ishland.c2me.fixes.general.threading_issues.mixin.asynccatchers.MixinThreadedAnvilChunkStorage")
        || mixinClassName.equals(
            "com.ishland.c2me.fixes.worldgen.threading_issues.mixin.threading_detections.random_instances.MixinWorld");
  }
}
