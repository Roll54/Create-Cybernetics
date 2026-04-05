package com.perigrine3.createcybernetics.compat.corpse;

import com.perigrine3.createcybernetics.ConfigValues;
import com.perigrine3.createcybernetics.CreateCybernetics;
import com.perigrine3.createcybernetics.api.CyberwareSlot;
import com.perigrine3.createcybernetics.api.ICyberwareItem;
import com.perigrine3.createcybernetics.api.InstalledCyberware;
import com.perigrine3.createcybernetics.common.capabilities.ModAttachments;
import com.perigrine3.createcybernetics.common.capabilities.PlayerCyberwareData;
import com.perigrine3.createcybernetics.common.surgery.DefaultOrgans;
import com.perigrine3.createcybernetics.common.surgery.RobosurgeonSlotMap;
import com.perigrine3.createcybernetics.item.ModItems;
import com.perigrine3.createcybernetics.item.generic.XPCapsuleItem;
import com.perigrine3.createcybernetics.util.ModTags;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CorpseCompat {

    public static final String CORPSE_MODID = "corpse";

    private static final String ROOT_TAG = CreateCybernetics.MODID + "_corpse_cyberware";
    private static final String ITEMS_TAG = "Items";
    private static final String DATA_TAG = "CorpseCyberwareData";

    public static final int CYBERWARE_SLOT_COUNT = 80;

    private static final Map<UUID, PendingCorpseData> PENDING = new ConcurrentHashMap<>();

    private CorpseCompat() {
    }

    public static void init() {
        if (isLoaded()) {
            NeoForge.EVENT_BUS.register(new CorpseCompat());
        }
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(CORPSE_MODID);
    }

    public static boolean capturePlayerDeathForCorpse(ServerPlayer player) {
        if (!isLoaded()) return false;
        if (player.level().isClientSide) return false;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return false;
        if (ConfigValues.KEEP_CYBERWARE) return false;

        PlayerCyberwareData data = player.getData(ModAttachments.CYBERWARE);
        if (data == null) return false;

        HolderLookup.Provider provider = player.registryAccess();

        CorpseCyberwareData corpseData = new CorpseCyberwareData();
        corpseData.captureFromPlayer(player, provider);

        NonNullList<ItemStack> stored = buildStoredCyberwareList(player, data);

        PENDING.put(player.getUUID(), new PendingCorpseData(corpseData, stored));

        return true;
    }

    public static void syncPendingCorpseVisualSnapshotOnDeath(ServerPlayer player) {
        if (player == null) return;
        if (player.level().isClientSide) return;

        PendingCorpseData pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        if (pending.data() == null || pending.data().isEmpty()) return;

        CorpseVisualSnapshotPayload payload =
                new CorpseVisualSnapshotPayload(player.getUUID(), pending.data().getSnapshot());

        PacketDistributor.sendToPlayer(player, payload);
        PacketDistributor.sendToPlayersTrackingEntity(player, payload);

        CreateCybernetics.LOGGER.info(
                "[corpse compat] synced pending corpse visual snapshot on death; corpseOwnerUuid={}",
                player.getUUID()
        );
    }

    @SubscribeEvent
    public void onCorpseJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof CorpseEntity corpse)) return;

        tryApplyPendingToCorpse(corpse);
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Entity target = event.getTarget();
        if (!(target instanceof CorpseEntity corpse)) return;

        ensureCorpseCyberwareLoaded(corpse);
        syncCorpseVisualSnapshotTo(player, corpse);
    }

    public static void ensureCorpseCyberwareLoaded(CorpseEntity corpse) {
        if (corpse == null) return;

        if (!hasStoredCorpseCyberwareData(corpse)) {
            tryApplyPendingToCorpse(corpse);
        }
    }

    private static void tryApplyPendingToCorpse(CorpseEntity corpse) {
        if (corpse == null) return;

        Optional<UUID> corpseOwnerUuid = corpse.getCorpseUUID();
        if (corpseOwnerUuid.isEmpty()) return;

        UUID ownerUuid = corpseOwnerUuid.get();
        PendingCorpseData pending = PENDING.get(ownerUuid);
        if (pending == null) return;

        writeCorpseCyberwareItems(corpse, pending.items());
        setStoredCorpseCyberwareData(corpse, pending.data());

        if (hasStoredCorpseCyberwareData(corpse)) {
            PENDING.remove(ownerUuid);

            CreateCybernetics.LOGGER.info(
                    "[corpse compat] applied pending corpse cyberware to corpse; corpseOwnerUuid={}, corpseEntityUuid={}",
                    ownerUuid,
                    corpse.getUUID()
            );
        }
    }

    private static void syncCorpseVisualSnapshotTo(ServerPlayer player, CorpseEntity corpse) {
        if (player == null || corpse == null) return;

        Optional<UUID> corpseOwnerUuid = corpse.getCorpseUUID();
        if (corpseOwnerUuid.isEmpty()) return;

        CorpseCyberwareData data = getStoredCorpseCyberwareData(corpse);
        if (data.isEmpty()) return;

        PacketDistributor.sendToPlayer(
                player,
                new CorpseVisualSnapshotPayload(corpseOwnerUuid.get(), data.getSnapshot())
        );
    }

    public static boolean hasCyberware(CorpseEntity corpse) {
        for (ItemStack stack : getCorpseCyberwareItems(corpse)) {
            if (!stack.isEmpty()) return true;
        }
        return false;
    }

    public static NonNullList<ItemStack> getCorpseCyberwareItems(CorpseEntity corpse) {
        NonNullList<ItemStack> items = NonNullList.withSize(CYBERWARE_SLOT_COUNT, ItemStack.EMPTY);

        CompoundTag persistent = corpse.getPersistentData();
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return items;
        }

        CompoundTag root = persistent.getCompound(ROOT_TAG);
        if (!root.contains(ITEMS_TAG, Tag.TAG_COMPOUND)) {
            return items;
        }

        ContainerHelper.loadAllItems(root.getCompound(ITEMS_TAG), items, corpse.registryAccess());
        return items;
    }

    public static void writeCorpseCyberwareItems(CorpseEntity corpse, NonNullList<ItemStack> items) {
        CompoundTag persistent = corpse.getPersistentData();
        CompoundTag root = persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? persistent.getCompound(ROOT_TAG)
                : new CompoundTag();

        CompoundTag itemsTag = new CompoundTag();
        ContainerHelper.saveAllItems(itemsTag, items, corpse.registryAccess());

        root.put(ITEMS_TAG, itemsTag);
        persistent.put(ROOT_TAG, root);
    }

    public static boolean hasStoredCorpseCyberwareData(CorpseEntity corpse) {
        CompoundTag persistent = corpse.getPersistentData();
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) return false;

        CompoundTag root = persistent.getCompound(ROOT_TAG);
        return root.contains(DATA_TAG, Tag.TAG_COMPOUND)
                && !root.getCompound(DATA_TAG).isEmpty();
    }

    public static CorpseCyberwareData getStoredCorpseCyberwareData(CorpseEntity corpse) {
        CorpseCyberwareData out = new CorpseCyberwareData();

        if (corpse == null) {
            return out;
        }

        CompoundTag persistent = corpse.getPersistentData();
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag root = persistent.getCompound(ROOT_TAG);
        if (!root.contains(DATA_TAG, Tag.TAG_COMPOUND)) {
            return out;
        }

        out.deserializeNBT(root.getCompound(DATA_TAG));
        return out;
    }

    public static void setStoredCorpseCyberwareData(CorpseEntity corpse, CorpseCyberwareData data) {
        if (corpse == null || data == null) return;

        CompoundTag persistent = corpse.getPersistentData();
        CompoundTag root = persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)
                ? persistent.getCompound(ROOT_TAG)
                : new CompoundTag();

        root.put(DATA_TAG, data.serializeNBT());
        persistent.put(ROOT_TAG, root);
    }

    private static NonNullList<ItemStack> buildStoredCyberwareList(ServerPlayer player, PlayerCyberwareData data) {
        NonNullList<ItemStack> stored = NonNullList.withSize(CYBERWARE_SLOT_COUNT, ItemStack.EMPTY);

        boolean hadCorticalStack = hasCorticalStackInstalled(player);
        int xpPoints = hadCorticalStack ? getTotalXpPoints(player) : 0;
        boolean capsuleStored = false;

        for (CyberwareSlot slot : CyberwareSlot.values()) {
            int mappedSize = RobosurgeonSlotMap.mappedSize(slot);

            for (int i = 0; i < mappedSize; i++) {
                InstalledCyberware installed = data.get(slot, i);
                ItemStack installedStack = installed != null && installed.getItem() != null
                        ? installed.getItem()
                        : ItemStack.EMPTY;

                ItemStack def = DefaultOrgans.get(slot, i);
                if (def == null) def = ItemStack.EMPTY;

                ItemStack effective = !installedStack.isEmpty() ? installedStack : def;
                if (effective.isEmpty()) continue;
                if (!shouldDropInstalledOnDeath(effective, slot)) continue;

                if (ModItems.BRAINUPGRADES_CORTICALSTACK != null
                        && effective.is(ModItems.BRAINUPGRADES_CORTICALSTACK.get())
                        && hadCorticalStack
                        && !capsuleStored) {
                    ItemStack capsule = XPCapsuleItem.makeCapsule(player.getGameProfile().getName(), xpPoints);
                    putFirstEmpty(stored, capsule);
                    capsuleStored = true;
                }

                putFirstEmpty(stored, effective.copy());
            }
        }

        for (int i = 0; i < PlayerCyberwareData.CHIPWARE_SLOT_COUNT; i++) {
            ItemStack st = data.getChipwareStack(i);
            if (st == null || st.isEmpty()) continue;
            if (!st.is(ModTags.Items.DATA_SHARDS)) continue;

            ItemStack drop = st.copy();
            drop.setCount(1);
            putFirstEmpty(stored, drop);
        }

        for (int i = 0; i < PlayerCyberwareData.CYBERDECK_SLOT_COUNT; i++) {
            ItemStack st = data.getCyberdeckStack(i);
            if (st == null || st.isEmpty()) continue;
            if (!st.is(ModTags.Items.QUICKHACK_SHARDS)) continue;

            ItemStack drop = st.copy();
            drop.setCount(1);
            putFirstEmpty(stored, drop);
        }

        return stored;
    }

    private static void putFirstEmpty(NonNullList<ItemStack> items, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copy());
                return;
            }
        }
    }

    private static boolean shouldDropInstalledOnDeath(ItemStack installedStack, CyberwareSlot slot) {
        if (installedStack.isEmpty()) return false;
        if (installedStack.getItem() instanceof ICyberwareItem cw) {
            return cw.dropsOnDeath(installedStack, slot);
        }
        return true;
    }

    private static boolean hasCorticalStackInstalled(ServerPlayer player) {
        PlayerCyberwareData data = player.getData(ModAttachments.CYBERWARE);
        if (data == null) return false;

        for (CyberwareSlot slot : CyberwareSlot.values()) {
            InstalledCyberware[] arr = data.getAll().get(slot);
            if (arr == null) continue;

            for (InstalledCyberware inst : arr) {
                if (inst == null) continue;
                ItemStack st = inst.getItem();
                if (st == null || st.isEmpty()) continue;

                if (ModItems.BRAINUPGRADES_CORTICALSTACK != null
                        && st.is(ModItems.BRAINUPGRADES_CORTICALSTACK.get())) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int totalXpForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    private static int getTotalXpPoints(Player player) {
        int level = player.experienceLevel;
        int base = totalXpForLevel(level);
        int toNext = player.getXpNeededForNextLevel();
        int within = Math.round(player.experienceProgress * (float) toNext);
        return Math.max(0, base + within);
    }

    private record PendingCorpseData(CorpseCyberwareData data, NonNullList<ItemStack> items) {
    }
}