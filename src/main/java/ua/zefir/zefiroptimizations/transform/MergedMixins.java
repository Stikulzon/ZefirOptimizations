package ua.zefir.zefiroptimizations.transform;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class MergedMixins {
  public static final String DESCRIPTOR =
      "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
  public static final String MIXIN_KEY = "mixin";
  private static final byte[] MARKER = DESCRIPTOR.getBytes(StandardCharsets.US_ASCII);
  private static final int SKIPPED =
      ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

  public static boolean present(byte[] bytes) {
    return bytes != null && indexOf(bytes, MARKER) >= 0;
  }

  public static Set<String> of(byte[] bytes) {
    if (!present(bytes)) {
      return Set.of();
    }
    ClassNode node = new ClassNode();
    new ClassReader(bytes).accept(node, SKIPPED);
    Set<String> mixins = new HashSet<>();
    for (MethodNode method : node.methods) {
      collect(method.visibleAnnotations, mixins);
      collect(method.invisibleAnnotations, mixins);
    }
    return mixins;
  }

  private static void collect(List<AnnotationNode> annotations, Set<String> mixins) {
    if (annotations == null) {
      return;
    }
    for (AnnotationNode annotation : annotations) {
      if (!annotation.desc.equals(DESCRIPTOR) || annotation.values == null) {
        continue;
      }
      for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
        if (MIXIN_KEY.equals(annotation.values.get(i))
            && annotation.values.get(i + 1) instanceof String mixin) {
          mixins.add(mixin.replace('.', '/'));
        }
      }
    }
  }

  private static int indexOf(byte[] bytes, byte[] marker) {
    int last = bytes.length - marker.length;
    for (int start = 0; start <= last; start++) {
      if (matchesAt(bytes, marker, start)) {
        return start;
      }
    }
    return -1;
  }

  private static boolean matchesAt(byte[] bytes, byte[] marker, int start) {
    for (int i = 0; i < marker.length; i++) {
      if (bytes[start + i] != marker[i]) {
        return false;
      }
    }
    return true;
  }
}
