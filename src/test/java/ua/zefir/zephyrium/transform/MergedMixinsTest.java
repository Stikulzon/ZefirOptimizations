package ua.zefir.zephyrium.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class MergedMixinsTest {
  private static final String TARGET = "net/minecraft/server/level/Target";
  private static final String MIXIN = "com/example/mixin/TargetMixin";
  private static final String METHOD = "mod$tick";
  private static final String VOID = "()V";

  @Test
  void readsTheMixinsMergedIntoAClass() {
    byte[] merged = target(true);

    assertTrue(MergedMixins.present(merged));
    assertEquals(Set.of(MIXIN), MergedMixins.of(merged));
  }

  @Test
  void findsNothingInClassesWithoutMixins() {
    byte[] plain = target(false);

    assertFalse(MergedMixins.present(plain));
    assertEquals(Set.of(), MergedMixins.of(plain));
  }

  private static byte[] target(boolean merged) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, TARGET, null, "java/lang/Object", null);
    MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, METHOD, VOID, null, null);
    if (merged) {
      AnnotationVisitor annotation = method.visitAnnotation(MergedMixins.DESCRIPTOR, true);
      annotation.visit(MergedMixins.MIXIN_KEY, MIXIN.replace('/', '.'));
      annotation.visitEnd();
    }
    method.visitCode();
    method.visitInsn(Opcodes.RETURN);
    method.visitMaxs(0, 0);
    method.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }
}
