package ua.zefir.zefiroptimizations.mixin;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.collection.PackedIntegerArray;
import net.minecraft.util.collection.PaletteStorage;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.stream.LongStream;


@Mixin(PalettedContainer.class)
public abstract class PalettedContainerMixin<T> implements PaletteResizeListener<T>, ReadableContainer<T> {

    @Shadow @Final private PalettedContainer.PaletteProvider paletteProvider;
    @Shadow @Final private PaletteResizeListener<T> dummyListener;
    @Shadow private volatile PalettedContainer.Data<T> data;
    @Shadow private PalettedContainer.Data<T> getCompatibleData(@Nullable PalettedContainer.Data<T> previousData, int bits) { return null; }
    @Shadow protected abstract void set(int index, T value);
    @Shadow protected abstract T swap(int index, T value);
    @Shadow private static <T> void applyEach(int[] is, IntUnaryOperator applier) {}

//    /**
//     * @author Zefir
//     * @reason Thread-safe implementation without locks
//     */
//    @Overwrite
//    public int onResize(int i, T object) {
//        PalettedContainer.Data<T> data = this.data;
//        PalettedContainer.Data<T> data2 = this.getCompatibleData(data, i);
//        data2.importFrom(data.palette, data.storage);
//        this.data = data2;
//        return data2.palette.index(object);
//    }

    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public T swap(int x, int y, int z, T value) {
        return this.swap(this.paletteProvider.computeIndex(x, y, z), value);
    }

    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public void set(int x, int y, int z, T value) {
        this.set(this.paletteProvider.computeIndex(x, y, z), value);
    }

    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public void forEachValue(Consumer<T> action) {
        Palette<T> palette = this.data.palette.copy();
        PaletteStorage storage = this.data.storage.copy();
        IntSet intSet = new IntArraySet();
        storage.forEach(intSet::add);
        intSet.forEach(id -> action.accept(palette.get(id)));
    }

    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public void readPacket(PacketByteBuf buf) {
        int i = buf.readByte();
        PalettedContainer.Data<T> data = this.getCompatibleData(null, i);
        Objects.requireNonNull(data).palette.readPacket(buf);
        buf.readLongArray(data.storage.getData());
        this.data = data;
    }

    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public void writePacket(PacketByteBuf buf) {
        PalettedContainer.Data<T> data = this.data.copy();
        data.writePacket(buf);
    }

    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public ReadableContainer.Serialized<T> serialize(IndexedIterable<T> idList, PalettedContainer.PaletteProvider paletteProvider) {
        PalettedContainer.Data<T> data = this.data.copy();
        BiMapPalette<T> biMapPalette = new BiMapPalette<>(idList, data.storage.getElementBits(), this.dummyListener);
        int i = paletteProvider.getContainerSize();
        int[] is = new int[i];
        data.storage.writePaletteIndices(is);
        applyEach(is, id -> biMapPalette.index(data.palette.get(id)));
        int j = paletteProvider.getBits(idList, biMapPalette.getSize());
        Optional<LongStream> optional;
        if (j != 0) {
            PackedIntegerArray packedIntegerArray = new PackedIntegerArray(j, i, is);
            optional = Optional.of(Arrays.stream(packedIntegerArray.getData()));
        } else {
            optional = Optional.empty();
        }

        return new ReadableContainer.Serialized<>(biMapPalette.getElements(), optional);
    }


    /**
     * @author Zefir
     * @reason Thread-safe implementation without locks
     */
    @Overwrite
    public void count(PalettedContainer.Counter<T> counter) {
        // Creating a copy of the palette and data for thread-safety.
        Palette<T> palette = this.data.palette.copy();
        PaletteStorage storage = this.data.storage.copy();
        if (palette.getSize() == 1) {
            counter.accept(palette.get(0), storage.getSize());
        } else {
            Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
            storage.forEach(key -> int2IntOpenHashMap.addTo(key, 1));
            int2IntOpenHashMap.int2IntEntrySet().forEach(entry -> counter.accept(palette.get(entry.getIntKey()), entry.getIntValue()));
        }
    }
}
