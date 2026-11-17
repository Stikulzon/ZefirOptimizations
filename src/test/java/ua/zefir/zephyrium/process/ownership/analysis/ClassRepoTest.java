package ua.zefir.zephyrium.process.ownership.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

class ClassRepoTest {
  private static final String PACKAGE = "com/example/typed";
  private static final String TYPED = PACKAGE + "/Typed";
  private static final String ITEM = PACKAGE + "/Item";
  private static final String BASE = PACKAGE + "/Base";
  private static final String LEAF = PACKAGE + "/Leaf";
  private static final String OBJECT = "java/lang/Object";
  private static final String IS = "is";
  private static final String IS_DESC = "(Ljava/lang/Object;)Z";
  private static final String HOLDER = "holder";
  private static final String HOLDER_DESC = "()Ljava/lang/Object;";
  private static final String NAME = "name";
  private static final String NAME_DESC = "()Ljava/lang/String;";
  private static final String CONSTANT = "base";

  @Test
  void resolvesDefaultMethodsInheritedThroughInterfaces() {
    ClassRepo repo = new ClassRepo(ClassRepoTest.class.getClassLoader(), BASE);
    repo.keepBytes(TYPED, typed());
    repo.keepBytes(ITEM, item());
    repo.keepBytes(BASE, base());
    repo.keepBytes(LEAF, leaf());
    String[] declaring = new String[1];

    MethodNode is = repo.findExactMethod(LEAF, IS, IS_DESC, declaring);
    assertNotNull(is);
    assertEquals(TYPED, declaring[0]);

    MethodNode name = repo.findExactMethod(LEAF, NAME, NAME_DESC, declaring);
    assertNotNull(name);
    assertEquals(BASE, declaring[0]);

    assertNull(repo.findExactMethod(LEAF, HOLDER, HOLDER_DESC, declaring));
  }

  private static byte[] typed() {
    ClassWriter writer = interfaceWriter(TYPED, new String[0]);
    writer
        .visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, HOLDER, HOLDER_DESC, null, null)
        .visitEnd();
    writer
        .visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, NAME, NAME_DESC, null, null)
        .visitEnd();
    MethodVisitor is = writer.visitMethod(Opcodes.ACC_PUBLIC, IS, IS_DESC, null, null);
    is.visitCode();
    is.visitVarInsn(Opcodes.ALOAD, 0);
    is.visitMethodInsn(Opcodes.INVOKEINTERFACE, TYPED, HOLDER, HOLDER_DESC, true);
    is.visitVarInsn(Opcodes.ALOAD, 1);
    is.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OBJECT, "equals", IS_DESC, false);
    is.visitInsn(Opcodes.IRETURN);
    is.visitMaxs(0, 0);
    is.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] item() {
    ClassWriter writer = interfaceWriter(ITEM, new String[] {TYPED});
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] base() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        BASE,
        null,
        OBJECT,
        new String[] {ITEM});
    MethodVisitor name = writer.visitMethod(Opcodes.ACC_PUBLIC, NAME, NAME_DESC, null, null);
    name.visitCode();
    name.visitLdcInsn(CONSTANT);
    name.visitInsn(Opcodes.ARETURN);
    name.visitMaxs(0, 0);
    name.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] leaf() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, LEAF, null, BASE, null);
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static ClassWriter interfaceWriter(String name, String[] interfaces) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        name,
        null,
        OBJECT,
        interfaces);
    return writer;
  }
}
