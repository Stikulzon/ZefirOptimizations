package ua.zefir.zefiroptimizations.process.sharedstate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class SharedStateInstrumenterTest {
  private static final String MOD = "example";
  private static final String PACKAGE = "com/example/shared";
  private static final String FOREIGN = PACKAGE + "/Foreign";
  private static final String MANAGER = PACKAGE + "/Manager";
  private static final String MANAGER_TYPE = "L" + MANAGER + ";";
  private static final String PLAIN = "com/other/Plain";
  private static final String RANDOM = "net/minecraft/util/RandomSource";
  private static final String MAP = "Ljava/util/Map;";
  private static final String MAP_OWNER = "java/util/Map";
  private static final String HASH_MAP = "java/util/HashMap";
  private static final String VOID = "()V";
  private static final String INT = "()I";
  private static final String SIZE_OF = "(Ljava/util/Map;)I";
  private static final String ROUTED_PREFIX = "zo$sr$";
  private static final String SYNC_PREFIX = "zo$sync$";
  private static final String SITE_PREFIX = "zo$ss$";
  private static final int TICKS = 3;
  private static final int SYNC_VALUE = 7;
  private static final String KEY = PACKAGE + "/Key";
  private static final String KEY_TYPE = "L" + KEY + ";";
  private static final String KEY_IMPL = PACKAGE + "/KeyImpl";
  private static final String HOLDER = PACKAGE + "/Holder";
  private static final String KEYS = PACKAGE + "/Keys";
  private static final String KEYS_IMPL = PACKAGE + "/KeysImpl";
  private static final String KEY_FACTORY = "(Ljava/lang/String;)" + KEY_TYPE;
  private static final String STRING_CONSTRUCTOR = "(Ljava/lang/String;)V";
  private static final String KEY_NAME = "connection";
  private static final String GAME = "net/minecraft/server/level/Tracker";
  private static final String GAME_MIXIN = PACKAGE + "/TrackerMixin";
  private static final String ADDED_LIST = "mod$scheduled";
  private static final String FASTUTIL_LIST = "it/unimi/dsi/fastutil/objects/ObjectArrayList";
  private static final String SCHEDULE = "mod$schedule";
  private static final String LAZY_FIELD = "mod$data";
  private static final String LAZY_GETTER = "mod$getData";
  private static final String ATTACHED_FIELD = "mod$attached";
  private static final String ON_CREATE = "mod$onCreate";
  private static final String REATTACH = "mod$reattach";
  private static final String TAKES_OBJECT = "(Ljava/lang/Object;)V";
  private static final String RARELY_SHARES = "rarelyShares";
  private static final String RARELY_SHARES_DESC = "(I)I";
  private static final String TAIL_PREFIX = "zo$tail$";
  private static final int SHARED_ARGUMENT = 5;
  private static final String MIXIN_MERGED =
      "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";

  private static final ModCode CODE = new ModCode(Map.of(PACKAGE, MOD));

  private static SharedStateInstrumenter instrumenter(Map<String, byte[]> classes) {
    ClassLoader loader = new ResourceLoader(classes);
    StaticTypes types = new StaticTypes(CODE, loader, RANDOM);
    AddedFields added = new AddedFields(loader, name -> false, Set.of());
    return new SharedStateInstrumenter(
        CODE, types, new SharedAccess(CODE, types, added, new ReadCalls(RANDOM), loader), true);
  }

  @Test
  void readsOnlyOfMutableStaticsAreUnsafe() {
    StaticTypes types =
        new StaticTypes(CODE, SharedStateInstrumenterTest.class.getClassLoader(), RANDOM);

    assertTrue(types.unsafeToRead(MAP));
    assertTrue(types.unsafeToRead("[I"));
    assertTrue(types.unsafeToRead("Lit/unimi/dsi/fastutil/ints/Int2IntOpenHashMap;"));
    assertTrue(types.unsafeToRead("L" + RANDOM + ";"));
    assertTrue(types.unsafeToRead(MANAGER_TYPE));
    assertTrue(types.mayChange("Ljava/lang/Object;"));
    assertFalse(types.unsafeToRead("I"));
    assertFalse(types.mayChange("I"));
    assertFalse(types.unsafeToRead("Ljava/lang/String;"));
    assertFalse(types.unsafeToRead("Ljava/util/concurrent/ConcurrentHashMap;"));
    assertFalse(types.unsafeToRead("Lcom/google/common/collect/ImmutableList;"));
    assertFalse(types.unsafeToRead("Lnet/minecraft/world/item/Item;"));
  }

  @Test
  void finalStaticsHoldingImmutableValuesAreSafeToRead() {
    ClassLoader loader =
        new ResourceLoader(
            Map.of(
                KEY, keyInterface(),
                KEY_IMPL, keyImpl(),
                HOLDER, holder(),
                KEYS_IMPL, keysImpl(),
                KEYS, keys()));
    StaticTypes types = new StaticTypes(CODE, loader, RANDOM);

    assertTrue(types.unsafeToRead(KEY_TYPE));
    assertFalse(types.unsafeStatic(KEYS_IMPL, "CONNECTION", KEY_TYPE));
    assertFalse(types.unsafeStatic(KEYS, "CONNECTION", KEY_TYPE));
    assertFalse(types.unsafeStatic(KEYS, "CAST", KEY_TYPE));
    assertTrue(types.unsafeStatic(KEYS, "MUTABLE", KEY_TYPE));
    assertTrue(types.unsafeStatic(KEYS, "REASSIGNED", KEY_TYPE));
    assertTrue(types.unsafeStatic(KEYS, "UNKNOWN", KEY_TYPE));
  }

  @Test
  void routesMethodsThatTouchSharedStateAndLeavesReadsAlone() {
    byte[] original = foreignClass(FOREIGN);

    ClassNode node = read(instrumenter(Map.of(FOREIGN, original)).instrument(FOREIGN, original));

    assertTrue(startsWithRoute(method(node, "tick")));
    assertTrue(startsWithRoute(method(node, "useCache")));
    assertFalse(startsWithRoute(method(node, "readFlag")));
    assertFalse(startsWithRoute(method(node, "readCache")));
    assertFalse(startsWithRoute(method(node, "publishCopy")));
    assertFalse(startsWithRoute(method(node, "passesToReader")));
    assertFalse(startsWithRoute(method(node, "<clinit>")));
    assertTrue(calls(method(node, "<init>"), SITE_PREFIX));
    assertTrue(node.methods.stream().anyMatch(m -> m.name.startsWith(ROUTED_PREFIX)));
  }

  @Test
  void splitsSynchronizedMethodsSoRoutingHappensBeforeTheMonitor() {
    byte[] original = foreignClass(FOREIGN);

    ClassNode node = read(instrumenter(Map.of(FOREIGN, original)).instrument(FOREIGN, original));

    MethodNode wrapper = method(node, "sync");
    MethodNode copy =
        node.methods.stream().filter(m -> m.name.startsWith(SYNC_PREFIX)).findFirst().orElseThrow();
    assertEquals(0, wrapper.access & Opcodes.ACC_SYNCHRONIZED);
    assertTrue(startsWithRoute(wrapper));
    assertTrue((copy.access & Opcodes.ACC_SYNCHRONIZED) != 0);
  }

  @Test
  void routedClassVerifiesAndBehavesAsBeforeOffProcesses() throws Exception {
    byte[] original = foreignClass(FOREIGN);
    byte[] routed = instrumenter(Map.of(FOREIGN, original)).instrument(FOREIGN, original);

    Class<?> loaded = new BytesLoader().define(FOREIGN, routed);
    Object instance = loaded.getConstructor().newInstance();
    for (int i = 0; i < TICKS; i++) {
      loaded.getMethod("tick").invoke(instance);
    }
    loaded.getMethod("sync").invoke(null);

    assertEquals(SYNC_VALUE, loaded.getField("synced").getInt(null));
    assertEquals(TICKS, loaded.getField("count").getInt(null));
    assertEquals(0, loaded.getMethod("useCache").invoke(instance));
  }

  @Test
  void routesMixinMethodsThatFillListsTheyAddedToGameObjects() {
    byte[] merged = gameClass(true);

    ClassNode node = read(instrumenter(Map.of(GAME, gameClass(false))).instrument(GAME, merged));

    assertTrue(startsWithRoute(method(node, SCHEDULE)));
  }

  @Test
  void routesLazyInitialisersOnlyWhileTheirFieldIsUnset() throws Exception {
    byte[] routed = instrumenter(Map.of(GAME, gameClass(false))).instrument(GAME, gameClass(true));

    List<AbstractInsnNode> code = new java.util.ArrayList<>();
    for (AbstractInsnNode insn : method(read(routed), LAZY_GETTER).instructions) {
      if (insn.getOpcode() >= 0) {
        code.add(insn);
      }
    }
    assertEquals(Opcodes.GETFIELD, code.get(1).getOpcode());
    assertEquals(Opcodes.IFNONNULL, code.get(2).getOpcode());
    assertTrue(
        code.get(3) instanceof MethodInsnNode call && call.name.equals(SharedStateGuard.ROUTE));

    Class<?> loaded = new BytesLoader().define(GAME, routed);
    Object instance = loaded.getConstructor().newInstance();
    Object first = loaded.getMethod(LAZY_GETTER).invoke(instance);
    assertSame(first, loaded.getMethod(LAZY_GETTER).invoke(instance));
  }

  @Test
  void leavesFieldsAddedToObjectsUnderConstructionAlone() throws Exception {
    byte[] routed = instrumenter(Map.of(GAME, gameClass(false))).instrument(GAME, gameClass(true));

    ClassNode node = read(routed);
    assertFalse(startsWithRoute(method(node, ON_CREATE)));
    assertFalse(calls(method(node, "<init>"), SITE_PREFIX));
    assertTrue(startsWithRoute(method(node, REATTACH)));
    Class<?> loaded = new BytesLoader().define(GAME, routed);
    Field attached = loaded.getDeclaredField(ATTACHED_FIELD);
    attached.setAccessible(true);
    assertEquals("created", attached.get(loaded.getConstructor().newInstance()));
  }

  @Test
  void routesOnlyThePartOfAMethodThatTouchesSharedState() throws Exception {
    byte[] original = foreignClass(FOREIGN);
    byte[] routed = instrumenter(Map.of(FOREIGN, original)).instrument(FOREIGN, original);

    MethodNode method = method(read(routed), RARELY_SHARES);
    assertFalse(startsWithRoute(method));
    assertTrue(calls(method, SharedStateGuard.ROUTE));

    Class<?> loaded = new BytesLoader().define(FOREIGN, routed);
    Object instance = loaded.getConstructor().newInstance();
    java.lang.reflect.Method rarelyShares = loaded.getMethod(RARELY_SHARES, int.class);
    assertEquals(0, rarelyShares.invoke(instance, 0));
    assertEquals(0, loaded.getField("count").getInt(null));
    assertEquals(SHARED_ARGUMENT + 1, rarelyShares.invoke(instance, SHARED_ARGUMENT));
    assertEquals(1, loaded.getField("count").getInt(null));

    java.lang.reflect.Method tail =
        java.util.Arrays.stream(loaded.getDeclaredMethods())
            .filter(m -> m.getName().startsWith(TAIL_PREFIX))
            .findFirst()
            .orElseThrow();
    tail.setAccessible(true);
    assertEquals(SHARED_ARGUMENT + 1, tail.invoke(instance, SHARED_ARGUMENT, 1L));
    assertEquals(2, loaded.getField("count").getInt(null));
  }

  @Test
  void leavesCodeOutsideModsWithoutMixinsAlone() {
    byte[] original = foreignClass(PLAIN);

    assertSame(original, instrumenter(Map.of()).instrument(PLAIN, original));
  }

  private static ClassNode read(byte[] bytes) {
    ClassNode node = new ClassNode();
    new ClassReader(bytes).accept(node, 0);
    return node;
  }

  private static boolean startsWithRoute(MethodNode method) {
    for (AbstractInsnNode insn : method.instructions) {
      if (insn.getOpcode() < 0) {
        continue;
      }
      return insn instanceof MethodInsnNode call
          && call.owner.equals(SharedStateGuard.INTERNAL_NAME)
          && call.name.equals(SharedStateGuard.ROUTE);
    }
    return false;
  }

  private static boolean calls(MethodNode method, String prefix) {
    for (AbstractInsnNode insn : method.instructions) {
      if (insn instanceof MethodInsnNode call && call.name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static MethodNode method(ClassNode node, String name) {
    return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
  }

  private static byte[] foreignClass(String name) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
    writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "count", "I", null, null).visitEnd();
    writer
        .visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "synced", "I", null, null)
        .visitEnd();
    writer.visitField(Opcodes.ACC_STATIC, "cache", MAP, null, null).visitEnd();
    writer
        .visitField(Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "published", MAP, null, null)
        .visitEnd();
    writer.visitField(Opcodes.ACC_STATIC, "manager", MANAGER_TYPE, null, null).visitEnd();

    MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", VOID, null, null);
    init.visitCode();
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", VOID, false);
    init.visitFieldInsn(Opcodes.GETSTATIC, name, "cache", MAP);
    init.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP_OWNER, "clear", VOID, true);
    init.visitInsn(Opcodes.RETURN);
    init.visitMaxs(0, 0);
    init.visitEnd();

    MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", VOID, null, null);
    clinit.visitCode();
    clinit.visitTypeInsn(Opcodes.NEW, HASH_MAP);
    clinit.visitInsn(Opcodes.DUP);
    clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, HASH_MAP, "<init>", VOID, false);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, name, "cache", MAP);
    clinit.visitInsn(Opcodes.RETURN);
    clinit.visitMaxs(0, 0);
    clinit.visitEnd();

    MethodVisitor tick = writer.visitMethod(Opcodes.ACC_PUBLIC, "tick", VOID, null, null);
    tick.visitCode();
    tick.visitFieldInsn(Opcodes.GETSTATIC, name, "count", "I");
    tick.visitInsn(Opcodes.ICONST_1);
    tick.visitInsn(Opcodes.IADD);
    tick.visitFieldInsn(Opcodes.PUTSTATIC, name, "count", "I");
    tick.visitInsn(Opcodes.RETURN);
    tick.visitMaxs(0, 0);
    tick.visitEnd();

    MethodVisitor rarely =
        writer.visitMethod(Opcodes.ACC_PUBLIC, RARELY_SHARES, RARELY_SHARES_DESC, null, null);
    rarely.visitCode();
    Label shares = new Label();
    rarely.visitInsn(Opcodes.LCONST_1);
    rarely.visitVarInsn(Opcodes.LSTORE, 2);
    rarely.visitVarInsn(Opcodes.ILOAD, 1);
    rarely.visitJumpInsn(Opcodes.IFNE, shares);
    rarely.visitInsn(Opcodes.ICONST_0);
    rarely.visitInsn(Opcodes.IRETURN);
    rarely.visitLabel(shares);
    rarely.visitFieldInsn(Opcodes.GETSTATIC, name, "count", "I");
    rarely.visitInsn(Opcodes.ICONST_1);
    rarely.visitInsn(Opcodes.IADD);
    rarely.visitFieldInsn(Opcodes.PUTSTATIC, name, "count", "I");
    rarely.visitVarInsn(Opcodes.ILOAD, 1);
    rarely.visitVarInsn(Opcodes.LLOAD, 2);
    rarely.visitInsn(Opcodes.L2I);
    rarely.visitInsn(Opcodes.IADD);
    rarely.visitInsn(Opcodes.IRETURN);
    rarely.visitMaxs(0, 0);
    rarely.visitEnd();

    MethodVisitor useCache = writer.visitMethod(Opcodes.ACC_PUBLIC, "useCache", INT, null, null);
    useCache.visitCode();
    useCache.visitFieldInsn(Opcodes.GETSTATIC, name, "cache", MAP);
    useCache.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP_OWNER, "clear", VOID, true);
    useCache.visitInsn(Opcodes.ICONST_0);
    useCache.visitInsn(Opcodes.IRETURN);
    useCache.visitMaxs(0, 0);
    useCache.visitEnd();

    MethodVisitor publish = writer.visitMethod(Opcodes.ACC_PUBLIC, "publishCopy", VOID, null, null);
    publish.visitCode();
    publish.visitTypeInsn(Opcodes.NEW, HASH_MAP);
    publish.visitInsn(Opcodes.DUP);
    publish.visitFieldInsn(Opcodes.GETSTATIC, name, "cache", MAP);
    publish.visitMethodInsn(Opcodes.INVOKESPECIAL, HASH_MAP, "<init>", "(Ljava/util/Map;)V", false);
    publish.visitFieldInsn(Opcodes.PUTSTATIC, name, "published", MAP);
    publish.visitInsn(Opcodes.RETURN);
    publish.visitMaxs(0, 0);
    publish.visitEnd();

    MethodVisitor readCache = writer.visitMethod(Opcodes.ACC_PUBLIC, "readCache", INT, null, null);
    readCache.visitCode();
    readCache.visitFieldInsn(Opcodes.GETSTATIC, name, "cache", MAP);
    readCache.visitMethodInsn(Opcodes.INVOKEINTERFACE, MAP_OWNER, "size", INT, true);
    readCache.visitInsn(Opcodes.IRETURN);
    readCache.visitMaxs(0, 0);
    readCache.visitEnd();

    MethodVisitor readFlag = writer.visitMethod(Opcodes.ACC_PUBLIC, "readFlag", INT, null, null);
    readFlag.visitCode();
    readFlag.visitFieldInsn(Opcodes.GETSTATIC, name, "manager", MANAGER_TYPE);
    readFlag.visitFieldInsn(Opcodes.GETFIELD, MANAGER, "flag", "I");
    readFlag.visitInsn(Opcodes.IRETURN);
    readFlag.visitMaxs(0, 0);
    readFlag.visitEnd();

    MethodVisitor sizeOf =
        writer.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "sizeOf", SIZE_OF, null, null);
    sizeOf.visitCode();
    sizeOf.visitInsn(Opcodes.ICONST_0);
    sizeOf.visitInsn(Opcodes.IRETURN);
    sizeOf.visitMaxs(0, 0);
    sizeOf.visitEnd();

    MethodVisitor passes =
        writer.visitMethod(Opcodes.ACC_PUBLIC, "passesToReader", INT, null, null);
    passes.visitCode();
    passes.visitFieldInsn(Opcodes.GETSTATIC, name, "cache", MAP);
    passes.visitMethodInsn(Opcodes.INVOKESTATIC, name, "sizeOf", SIZE_OF, false);
    passes.visitInsn(Opcodes.IRETURN);
    passes.visitMaxs(0, 0);
    passes.visitEnd();

    MethodVisitor sync =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
            "sync",
            VOID,
            null,
            null);
    sync.visitCode();
    sync.visitIntInsn(Opcodes.BIPUSH, SYNC_VALUE);
    sync.visitFieldInsn(Opcodes.PUTSTATIC, name, "synced", "I");
    sync.visitInsn(Opcodes.RETURN);
    sync.visitMaxs(0, 0);
    sync.visitEnd();

    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] keyInterface() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        KEY,
        null,
        "java/lang/Object",
        null);
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] keyImpl() {
    return keyClass(KEY_IMPL, Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);
  }

  private static byte[] holder() {
    return keyClass(HOLDER, Opcodes.ACC_PUBLIC);
  }

  private static byte[] keyClass(String name, int fieldAccess) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V21,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
        name,
        null,
        "java/lang/Object",
        new String[] {KEY});
    writer.visitField(fieldAccess, "id", "Ljava/lang/String;", null, null).visitEnd();
    MethodVisitor init =
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", STRING_CONSTRUCTOR, null, null);
    init.visitCode();
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", VOID, false);
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitVarInsn(Opcodes.ALOAD, 1);
    init.visitFieldInsn(Opcodes.PUTFIELD, name, "id", "Ljava/lang/String;");
    init.visitInsn(Opcodes.RETURN);
    init.visitMaxs(0, 0);
    init.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] keysImpl() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, KEYS_IMPL, null, "java/lang/Object", null);
    writer
        .visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "CONNECTION",
            KEY_TYPE,
            null,
            null)
        .visitEnd();
    MethodVisitor factory =
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "key", KEY_FACTORY, null, null);
    factory.visitCode();
    factory.visitTypeInsn(Opcodes.NEW, KEY_IMPL);
    factory.visitInsn(Opcodes.DUP);
    factory.visitVarInsn(Opcodes.ALOAD, 0);
    factory.visitMethodInsn(Opcodes.INVOKESPECIAL, KEY_IMPL, "<init>", STRING_CONSTRUCTOR, false);
    factory.visitInsn(Opcodes.ARETURN);
    factory.visitMaxs(0, 0);
    factory.visitEnd();
    MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", VOID, null, null);
    clinit.visitCode();
    clinit.visitLdcInsn(KEY_NAME);
    clinit.visitMethodInsn(Opcodes.INVOKESTATIC, KEYS_IMPL, "key", KEY_FACTORY, false);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, KEYS_IMPL, "CONNECTION", KEY_TYPE);
    clinit.visitInsn(Opcodes.RETURN);
    clinit.visitMaxs(0, 0);
    clinit.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] keys() {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, KEYS, null, "java/lang/Object", null);
    int constant = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
    for (String field : List.of("CONNECTION", "CAST", "MUTABLE", "UNKNOWN")) {
      writer.visitField(constant, field, KEY_TYPE, null, null).visitEnd();
    }
    writer
        .visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "REASSIGNED", KEY_TYPE, null, null)
        .visitEnd();
    MethodVisitor clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", VOID, null, null);
    clinit.visitCode();
    clinit.visitFieldInsn(Opcodes.GETSTATIC, KEYS_IMPL, "CONNECTION", KEY_TYPE);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, KEYS, "CONNECTION", KEY_TYPE);
    clinit.visitFieldInsn(Opcodes.GETSTATIC, KEYS_IMPL, "CONNECTION", KEY_TYPE);
    clinit.visitTypeInsn(Opcodes.CHECKCAST, KEY);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, KEYS, "CAST", KEY_TYPE);
    clinit.visitTypeInsn(Opcodes.NEW, HOLDER);
    clinit.visitInsn(Opcodes.DUP);
    clinit.visitLdcInsn(KEY_NAME);
    clinit.visitMethodInsn(Opcodes.INVOKESPECIAL, HOLDER, "<init>", STRING_CONSTRUCTOR, false);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, KEYS, "MUTABLE", KEY_TYPE);
    clinit.visitFieldInsn(Opcodes.GETSTATIC, KEYS_IMPL, "CONNECTION", KEY_TYPE);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, KEYS, "REASSIGNED", KEY_TYPE);
    clinit.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/System",
        "getProperties",
        "()Ljava/util/Properties;",
        false);
    clinit.visitTypeInsn(Opcodes.CHECKCAST, KEY);
    clinit.visitFieldInsn(Opcodes.PUTSTATIC, KEYS, "UNKNOWN", KEY_TYPE);
    clinit.visitInsn(Opcodes.RETURN);
    clinit.visitMaxs(0, 0);
    clinit.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static byte[] gameClass(boolean withMixin) {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, GAME, null, "java/lang/Object", null);
    MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", VOID, null, null);
    init.visitCode();
    init.visitVarInsn(Opcodes.ALOAD, 0);
    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", VOID, false);
    if (withMixin) {
      init.visitVarInsn(Opcodes.ALOAD, 0);
      init.visitLdcInsn("created");
      init.visitMethodInsn(Opcodes.INVOKESPECIAL, GAME, ON_CREATE, TAKES_OBJECT, false);
    }
    init.visitInsn(Opcodes.RETURN);
    init.visitMaxs(0, 0);
    init.visitEnd();
    if (withMixin) {
      writer.visitField(Opcodes.ACC_PRIVATE, ATTACHED_FIELD, "Ljava/lang/Object;", null, null);
      attach(writer, Opcodes.ACC_PRIVATE, ON_CREATE);
      attach(writer, Opcodes.ACC_PUBLIC, REATTACH);
      writer
          .visitField(Opcodes.ACC_PRIVATE, ADDED_LIST, "L" + FASTUTIL_LIST + ";", null, null)
          .visitEnd();
      MethodVisitor schedule =
          writer.visitMethod(Opcodes.ACC_PUBLIC, SCHEDULE, "(Ljava/lang/Object;)V", null, null);
      AnnotationVisitor merged = schedule.visitAnnotation(MIXIN_MERGED, true);
      merged.visit("mixin", GAME_MIXIN.replace('/', '.'));
      merged.visitEnd();
      schedule.visitCode();
      schedule.visitVarInsn(Opcodes.ALOAD, 0);
      schedule.visitFieldInsn(Opcodes.GETFIELD, GAME, ADDED_LIST, "L" + FASTUTIL_LIST + ";");
      schedule.visitVarInsn(Opcodes.ALOAD, 1);
      schedule.visitMethodInsn(
          Opcodes.INVOKEVIRTUAL, FASTUTIL_LIST, "add", "(Ljava/lang/Object;)Z", false);
      schedule.visitInsn(Opcodes.POP);
      schedule.visitInsn(Opcodes.RETURN);
      schedule.visitMaxs(0, 0);
      schedule.visitEnd();
      String list = "L" + FASTUTIL_LIST + ";";
      writer.visitField(Opcodes.ACC_PRIVATE, LAZY_FIELD, list, null, null).visitEnd();
      MethodVisitor lazy =
          writer.visitMethod(Opcodes.ACC_PUBLIC, LAZY_GETTER, "()" + list, null, null);
      AnnotationVisitor lazyMerged = lazy.visitAnnotation(MIXIN_MERGED, true);
      lazyMerged.visit("mixin", GAME_MIXIN.replace('/', '.'));
      lazyMerged.visitEnd();
      lazy.visitCode();
      Label set = new Label();
      lazy.visitVarInsn(Opcodes.ALOAD, 0);
      lazy.visitFieldInsn(Opcodes.GETFIELD, GAME, LAZY_FIELD, list);
      lazy.visitJumpInsn(Opcodes.IFNONNULL, set);
      lazy.visitVarInsn(Opcodes.ALOAD, 0);
      lazy.visitTypeInsn(Opcodes.NEW, FASTUTIL_LIST);
      lazy.visitInsn(Opcodes.DUP);
      lazy.visitMethodInsn(Opcodes.INVOKESPECIAL, FASTUTIL_LIST, "<init>", VOID, false);
      lazy.visitFieldInsn(Opcodes.PUTFIELD, GAME, LAZY_FIELD, list);
      lazy.visitLabel(set);
      lazy.visitVarInsn(Opcodes.ALOAD, 0);
      lazy.visitFieldInsn(Opcodes.GETFIELD, GAME, LAZY_FIELD, list);
      lazy.visitInsn(Opcodes.ARETURN);
      lazy.visitMaxs(0, 0);
      lazy.visitEnd();
    }
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static void attach(ClassWriter writer, int access, String name) {
    MethodVisitor method = writer.visitMethod(access, name, TAKES_OBJECT, null, null);
    AnnotationVisitor merged = method.visitAnnotation(MIXIN_MERGED, true);
    merged.visit("mixin", GAME_MIXIN.replace('/', '.'));
    merged.visitEnd();
    method.visitCode();
    method.visitVarInsn(Opcodes.ALOAD, 0);
    method.visitVarInsn(Opcodes.ALOAD, 1);
    method.visitFieldInsn(Opcodes.PUTFIELD, GAME, ATTACHED_FIELD, "Ljava/lang/Object;");
    method.visitInsn(Opcodes.RETURN);
    method.visitMaxs(0, 0);
    method.visitEnd();
  }

  private static final class ResourceLoader extends ClassLoader {
    private final Map<String, byte[]> classes;

    ResourceLoader(Map<String, byte[]> classes) {
      super(SharedStateInstrumenterTest.class.getClassLoader());
      this.classes = classes;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
      for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
        if (name.equals(entry.getKey() + ".class")) {
          return new ByteArrayInputStream(entry.getValue());
        }
      }
      return super.getResourceAsStream(name);
    }
  }

  private static final class BytesLoader extends ClassLoader {
    BytesLoader() {
      super(SharedStateInstrumenterTest.class.getClassLoader());
    }

    Class<?> define(String internalName, byte[] bytes) {
      return defineClass(internalName.replace('/', '.'), bytes, 0, bytes.length);
    }
  }
}
