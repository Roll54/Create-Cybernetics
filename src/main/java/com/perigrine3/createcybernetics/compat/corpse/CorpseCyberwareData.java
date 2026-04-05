package com.perigrine3.createcybernetics.compat.corpse;

import com.perigrine3.createcybernetics.common.capabilities.PlayerCyberwareData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public final class CorpseCyberwareData {

    private static final String NBT_SNAPSHOT = "Snapshot";

    private CompoundTag snapshot = new CompoundTag();

    public CorpseCyberwareData() {
    }

    public void captureFromPlayer(Player player, HolderLookup.Provider provider) {
        if (player == null) {
            snapshot = new CompoundTag();
            return;
        }
        snapshot = PlayerCyberwareData.createSnapshotTagFor(player, provider);
    }

    public void setSnapshot(CompoundTag tag) {
        snapshot = tag == null ? new CompoundTag() : tag.copy();
    }

    public CompoundTag getSnapshot() {
        return snapshot == null ? new CompoundTag() : snapshot.copy();
    }

    public boolean isEmpty() {
        return snapshot == null || snapshot.isEmpty();
    }

    public PlayerCyberwareData toPlayerCyberwareData(HolderLookup.Provider provider) {
        return PlayerCyberwareData.fromSnapshotTag(getSnapshot(), provider);
    }

    public CompoundTag serializeNBT() {
        CompoundTag out = new CompoundTag();
        out.put(NBT_SNAPSHOT, getSnapshot());
        return out;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            snapshot = new CompoundTag();
            return;
        }

        if (tag.contains(NBT_SNAPSHOT)) {
            snapshot = tag.getCompound(NBT_SNAPSHOT).copy();
        } else {
            snapshot = new CompoundTag();
        }
    }
}