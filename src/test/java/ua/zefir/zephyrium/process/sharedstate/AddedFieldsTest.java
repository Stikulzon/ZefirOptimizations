package ua.zefir.zephyrium.process.sharedstate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AddedFieldsTest {
  private static final String CHUNK_MAP = "net/minecraft/server/level/ChunkMap";
  private static final String BRAIN = "net/minecraft/world/entity/ai/Brain";
  private static final String ADDED = "mod$cache";
  private static final String VANILLA = "memories";
  private static final String PACKET = "net/minecraft/network/protocol/Packet";
  private static final String ROTATE_HEAD =
      "net/minecraft/network/protocol/game/ClientboundRotateHeadPacket";

  private final AddedFields added =
      new AddedFields(AddedFieldsTest.class.getClassLoader(), name -> false, Set.of(BRAIN, PACKET));

  @Test
  void fieldsAddedToSharedGameObjectsAreShared() {
    assertTrue(added.onSharedGameObject(CHUNK_MAP, ADDED));
  }

  @Test
  void fieldsAddedToObjectsOneEntityOwnsAreNotShared() {
    assertFalse(added.onSharedGameObject(BRAIN, ADDED));
  }

  @Test
  void fieldsAddedToPacketsAreNotShared() {
    assertFalse(added.onSharedGameObject(ROTATE_HEAD, ADDED));
  }

  @Test
  void vanillaFieldsAreNotAdded() {
    assertFalse(
        new AddedFields(AddedFieldsTest.class.getClassLoader(), name -> false, Set.of())
            .onSharedGameObject(BRAIN, VANILLA));
  }
}
