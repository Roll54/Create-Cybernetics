package com.perigrine3.createcybernetics.compat.corpse;

import com.perigrine3.createcybernetics.common.capabilities.PlayerCyberwareData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CorpseVisualSnapshotClientCache {

    private static final Map<UUID, CompoundTag> SNAPSHOTS = new ConcurrentHashMap<>();

    private CorpseVisualSnapshotClientCache() {
    }

    public static void put(UUID corpseOwnerUuid, CompoundTag snapshot) {
        if (corpseOwnerUuid == null) return;

        if (snapshot == null || snapshot.isEmpty()) {
            SNAPSHOTS.remove(corpseOwnerUuid);
            return;
        }

        SNAPSHOTS.put(corpseOwnerUuid, snapshot.copy());
    }

    public static CompoundTag get(UUID corpseOwnerUuid) {
        if (corpseOwnerUuid == null) return new CompoundTag();

        CompoundTag tag = SNAPSHOTS.get(corpseOwnerUuid);
        return tag == null ? new CompoundTag() : tag.copy();
    }

    public static void remove(UUID corpseOwnerUuid) {
        if (corpseOwnerUuid == null) return;
        SNAPSHOTS.remove(corpseOwnerUuid);
    }

    public static void applyToPlayer(Player visualPlayer, UUID corpseOwnerUuid) {
        if (visualPlayer == null) return;

        CompoundTag pd = visualPlayer.getPersistentData();
        pd.remove(PlayerCyberwareData.HOLO_SNAPSHOT_FLAG);
        pd.remove(PlayerCyberwareData.HOLO_SNAPSHOT_CYBERWARE);

        if (corpseOwnerUuid == null) return;

        CompoundTag snapshot = get(corpseOwnerUuid);
        if (snapshot.isEmpty()) return;

        pd.putBoolean(PlayerCyberwareData.HOLO_SNAPSHOT_FLAG, true);
        pd.put(PlayerCyberwareData.HOLO_SNAPSHOT_CYBERWARE, snapshot.copy());
    }
}