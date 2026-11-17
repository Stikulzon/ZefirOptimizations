package ua.zefir.zefiroptimizations.process.ownership.analysis;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class Bytecode {
  static final String GUARDS = "ua/zefir/zefiroptimizations/process/Guards";
  public static final String INVOCATION = "ua/zefir/zefiroptimizations/process/Invocation";
  static final String OBJECT = "java/lang/Object";
  static final Type SAM = Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;");
  static final Handle METAFACTORY =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          "java/lang/invoke/LambdaMetafactory",
          "metafactory",
          "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
              + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
              + "Ljava/lang/invoke/CallSite;",
          false);

  public static MethodInsnNode guard(String name, String desc) {
    return new MethodInsnNode(Opcodes.INVOKESTATIC, GUARDS, name, desc, false);
  }

  public static InvokeDynamicInsnNode invocation(
      Type[] captured,
      String implOwner,
      String implName,
      String implDesc,
      boolean ownerIsInterface) {
    StringBuilder desc = new StringBuilder("(");
    for (Type type : captured) {
      desc.append(type.getDescriptor());
    }
    desc.append(")L").append(INVOCATION).append(';');
    return new InvokeDynamicInsnNode(
        "invoke",
        desc.toString(),
        METAFACTORY,
        SAM,
        new Handle(Opcodes.H_INVOKESTATIC, implOwner, implName, implDesc, ownerIsInterface),
        SAM);
  }

  public static void loadAll(InsnList insns, Type[] types, int firstSlot) {
    int slot = firstSlot;
    for (Type type : types) {
      insns.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot));
      slot += type.getSize();
    }
  }

  public static Type[] append(Type[] types, Type last) {
    Type[] result = new Type[types.length + 1];
    System.arraycopy(types, 0, result, 0, types.length);
    result[types.length] = last;
    return result;
  }

  public static int size(Type[] types) {
    int size = 0;
    for (Type type : types) {
      size += type.getSize();
    }
    return size;
  }

  public static void box(InsnList insns, Type type) {
    String wrapper = wrapper(type);
    if (type.getSort() == Type.VOID) {
      insns.add(new InsnNode(Opcodes.ACONST_NULL));
    } else if (wrapper != null) {
      insns.add(
          new MethodInsnNode(
              Opcodes.INVOKESTATIC,
              wrapper,
              "valueOf",
              "(" + type.getDescriptor() + ")L" + wrapper + ";",
              false));
    }
  }

  public static void unbox(InsnList insns, Type type) {
    String wrapper = wrapper(type);
    if (type.getSort() == Type.VOID) {
      insns.add(new InsnNode(Opcodes.POP));
    } else if (wrapper != null) {
      insns.add(new TypeInsnNode(Opcodes.CHECKCAST, wrapper));
      insns.add(
          new MethodInsnNode(
              Opcodes.INVOKEVIRTUAL,
              wrapper,
              type.getClassName() + "Value",
              "()" + type.getDescriptor(),
              false));
    } else if (!type.getInternalName().equals(OBJECT)) {
      insns.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
    }
  }

  private static String wrapper(Type type) {
    return switch (type.getSort()) {
      case Type.BOOLEAN -> "java/lang/Boolean";
      case Type.BYTE -> "java/lang/Byte";
      case Type.CHAR -> "java/lang/Character";
      case Type.SHORT -> "java/lang/Short";
      case Type.INT -> "java/lang/Integer";
      case Type.FLOAT -> "java/lang/Float";
      case Type.LONG -> "java/lang/Long";
      case Type.DOUBLE -> "java/lang/Double";
      default -> null;
    };
  }

  public static InsnNode returnInsn(Type type) {
    return new InsnNode(type.getOpcode(Opcodes.IRETURN));
  }

  public static Object frameType(Type type) {
    return switch (type.getSort()) {
      case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.INTEGER;
      case Type.FLOAT -> Opcodes.FLOAT;
      case Type.LONG -> Opcodes.LONG;
      case Type.DOUBLE -> Opcodes.DOUBLE;
      case Type.ARRAY -> type.getDescriptor();
      default -> type.getInternalName();
    };
  }

  public static FrameNode frame(Object... locals) {
    return new FrameNode(Opcodes.F_NEW, locals.length, locals, 0, new Object[0]);
  }

  public static Object[] locals(String owner, boolean instance, Type[] arguments) {
    List<Object> locals = new ArrayList<>();
    if (instance) {
      locals.add(owner);
    }
    for (Type argument : arguments) {
      locals.add(frameType(argument));
    }
    return locals.toArray();
  }
}
