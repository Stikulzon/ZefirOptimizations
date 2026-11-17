package ua.zefir.zephyrium.process.ownership.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

class PurityTest {
  private static final String OWNER = "com/example/Holder";
  private static final String FIELD = "value";
  private static final String OBJECT = "Ljava/lang/Object;";
  private static final String LONG = "J";
  private static final String ENTITY_ROOT = "net/minecraft/world/entity/Entity";
  private static final String MOB = "com/example/injected/TestMob";
  private static final String STORE = "com/example/injected/Store";
  private static final String CIR =
      "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
  private static final String MOB_ARG = "(L" + MOB + ";)Z";
  private static final String HANDLER_DESC = "(L" + CIR + ";)V";
  private static final String KEPT = "isKept";
  private static final String LEAKY = "isLeaky";
  private static final String TAG = "tag";
  private static final String TAMED = "isTamed";
  private static final String LEAKS = "leaks";

  @Test
  void recognisesMethodsThatOnlyReturnOneOfTheirFields() {
    FieldInsnNode field = Purity.getterField(getter(OBJECT, Opcodes.ARETURN, Opcodes.ACC_PUBLIC));
    assertNotNull(field);
    assertEquals(FIELD, field.name);
  }

  @Test
  void rejectsWideStaticAndLongerMethods() {
    assertNull(Purity.getterField(getter(LONG, Opcodes.LRETURN, Opcodes.ACC_PUBLIC)));
    assertNull(
        Purity.getterField(
            getter(OBJECT, Opcodes.ARETURN, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)));

    MethodNode longer = getter(OBJECT, Opcodes.ARETURN, Opcodes.ACC_PUBLIC);
    longer.instructions.insert(new InsnNode(Opcodes.NOP));
    assertNull(Purity.getterField(longer));

    MethodNode otherObject = new MethodNode(Opcodes.ACC_PUBLIC, "get", "()" + OBJECT, null, null);
    otherObject.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
    otherObject.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, FIELD, OBJECT));
    otherObject.instructions.add(new InsnNode(Opcodes.ARETURN));
    assertNull(Purity.getterField(otherObject));
  }

  @Test
  void injectedGettersStayReadableWhenTheirHandlersOnlyReadTheEntity() throws Exception {
    Map<String, byte[]> classes = Map.of(MOB, mob(), STORE, store());
    ClassLoader loader = new BytesResources(classes);
    ClassRepo repo = new ClassRepo(loader, ENTITY_ROOT);
    classes.forEach((name, bytes) -> repo.recordLoaded(name, bytes, true));
    Purity purity = new Purity(repo, McNames.load(loader));

    assertTrue(purity.isSnapshotReadable(MOB + '.' + KEPT + "()Z"));
    assertFalse(purity.isSnapshotReadable(MOB + '.' + LEAKY + "()Z"));
  }

  private static byte[] mob() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, MOB, null, ENTITY_ROOT, null);
    writer.visitField(Opcodes.ACC_PRIVATE, "kept", "Z", null, null).visitEnd();
    writer.visitField(Opcodes.ACC_PRIVATE, TAG, "I", null, null).visitEnd();
    injectedGetter(writer, KEPT, "handler$kept");
    injectedGetter(writer, LEAKY, "handler$leaky");
    handler(writer, "handler$kept", TAMED);
    handler(writer, "handler$leaky", LEAKS);
    MethodVisitor tag = writer.visitMethod(Opcodes.ACC_PUBLIC, TAG, "()I", null, null);
    tag.visitCode();
    tag.visitVarInsn(Opcodes.ALOAD, 0);
    tag.visitFieldInsn(Opcodes.GETFIELD, MOB, TAG, "I");
    tag.visitInsn(Opcodes.IRETURN);
    tag.visitMaxs(0, 0);
    tag.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static void injectedGetter(ClassWriter writer, String name, String handler) {
    MethodVisitor getter = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "()Z", null, null);
    getter.visitCode();
    getter.visitTypeInsn(Opcodes.NEW, CIR);
    getter.visitInsn(Opcodes.DUP);
    getter.visitLdcInsn(name);
    getter.visitInsn(Opcodes.ICONST_1);
    getter.visitVarInsn(Opcodes.ALOAD, 0);
    getter.visitFieldInsn(Opcodes.GETFIELD, MOB, "kept", "Z");
    getter.visitMethodInsn(Opcodes.INVOKESPECIAL, CIR, "<init>", "(Ljava/lang/String;ZZ)V", false);
    getter.visitVarInsn(Opcodes.ASTORE, 1);
    getter.visitVarInsn(Opcodes.ALOAD, 0);
    getter.visitVarInsn(Opcodes.ALOAD, 1);
    getter.visitMethodInsn(Opcodes.INVOKESPECIAL, MOB, handler, HANDLER_DESC, false);
    getter.visitVarInsn(Opcodes.ALOAD, 1);
    getter.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CIR, "getReturnValueZ", "()Z", false);
    getter.visitInsn(Opcodes.IRETURN);
    getter.visitMaxs(0, 0);
    getter.visitEnd();
  }

  private static void handler(ClassWriter writer, String name, String storeMethod) {
    MethodVisitor handler = writer.visitMethod(Opcodes.ACC_PRIVATE, name, HANDLER_DESC, null, null);
    handler.visitCode();
    handler.visitVarInsn(Opcodes.ALOAD, 1);
    handler.visitVarInsn(Opcodes.ALOAD, 0);
    handler.visitMethodInsn(Opcodes.INVOKESTATIC, STORE, storeMethod, MOB_ARG, false);
    handler.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
    handler.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, CIR, "setReturnValue", "(Ljava/lang/Object;)V", false);
    handler.visitInsn(Opcodes.RETURN);
    handler.visitMaxs(0, 0);
    handler.visitEnd();
  }

  private static byte[] store() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, STORE, null, "java/lang/Object", null);
    MethodVisitor tamed =
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, TAMED, MOB_ARG, null, null);
    tamed.visitCode();
    tamed.visitVarInsn(Opcodes.ALOAD, 0);
    tamed.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOB, TAG, "()I", false);
    tamed.visitInsn(Opcodes.IRETURN);
    tamed.visitMaxs(0, 0);
    tamed.visitEnd();
    MethodVisitor leaks =
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, LEAKS, MOB_ARG, null, null);
    leaks.visitCode();
    leaks.visitVarInsn(Opcodes.ALOAD, 0);
    leaks.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/util/Objects", "hashCode", "(Ljava/lang/Object;)I", false);
    leaks.visitInsn(Opcodes.IRETURN);
    leaks.visitMaxs(0, 0);
    leaks.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static final class BytesResources extends ClassLoader {
    private final Map<String, byte[]> classes;

    BytesResources(Map<String, byte[]> classes) {
      super(PurityTest.class.getClassLoader());
      this.classes = classes;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      byte[] bytes = classes.get(name.replace(".class", ""));
      return bytes != null ? new ByteArrayInputStream(bytes) : super.getResourceAsStream(name);
    }
  }

  private static MethodNode getter(String desc, int returnOpcode, int access) {
    MethodNode method = new MethodNode(access, "get", "()" + desc, null, null);
    method.instructions.add(new LabelNode());
    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, FIELD, desc));
    method.instructions.add(new InsnNode(returnOpcode));
    return method;
  }
}
