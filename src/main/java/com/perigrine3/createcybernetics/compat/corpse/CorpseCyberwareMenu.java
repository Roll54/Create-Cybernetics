package com.perigrine3.createcybernetics.compat.corpse;

import com.perigrine3.createcybernetics.CreateCybernetics;
import de.maxhenkel.corpse.entities.CorpseEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CorpseCyberwareMenu extends AbstractContainerMenu {

    public static final int COLUMNS = 10;
    public static final int ROWS = 8;

    private final Container cyberwareInventory;
    private final int corpseEntityId;

    public CorpseCyberwareMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(
                ModCorpseCompatMenus.CORPSE_CYBERWARE.get(),
                id,
                playerInventory,
                buf.readInt(),
                buf.readBoolean()
        );

        CreateCybernetics.LOGGER.info(
                "[corpse compat] client ctor hit; id={}, corpseEntityId={}",
                id,
                this.corpseEntityId
        );
    }

    public CorpseCyberwareMenu(MenuType<?> type, int id, Inventory playerInventory, CorpseEntity corpse, boolean editable) {
        this(type, id, playerInventory, corpse != null ? corpse.getId() : -1, editable);

        CreateCybernetics.LOGGER.info(
                "[corpse compat] server ctor hit; id={}, corpse={}, editable={}",
                id,
                corpse,
                editable
        );
    }

    private CorpseCyberwareMenu(MenuType<?> type, int id, Inventory playerInventory, int corpseEntityId, boolean editable) {
        super(type, id);
        this.corpseEntityId = corpseEntityId;

        CorpseEntity corpse = resolveCorpse(playerInventory.player, corpseEntityId);
        this.cyberwareInventory = corpse != null
                ? new CorpseCyberwareInventory(corpse)
                : new SimpleContainer(CorpseCompat.CYBERWARE_SLOT_COUNT);

        CreateCybernetics.LOGGER.info(
                "[corpse compat] common ctor hit; id={}, corpseEntityId={}, resolvedCorpse={}, editable={}, inventoryType={}",
                id,
                corpseEntityId,
                corpse,
                editable,
                this.cyberwareInventory.getClass().getName()
        );

        int startX = 7;
        int startY = 18;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int index = col + row * COLUMNS;
                addSlot(new Slot(cyberwareInventory, index, startX + col * 18, startY + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        int playerInvY = startY + ROWS * 18 + 14;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 15 + col * 18, playerInvY + row * 18));
            }
        }

        int hotbarY = playerInvY + 58;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 15 + col * 18, hotbarY));
        }

        CreateCybernetics.LOGGER.info(
                "[corpse compat] menu slot setup finished; totalSlots={}, corpseSlots={}",
                this.slots.size(),
                CorpseCompat.CYBERWARE_SLOT_COUNT
        );
    }

    private static CorpseEntity resolveCorpse(Player player, int entityId) {
        if (player == null || player.level() == null || entityId < 0) {
            CreateCybernetics.LOGGER.info(
                    "[corpse compat] resolveCorpse failed; player={}, levelPresent={}, entityId={}",
                    player,
                    player != null && player.level() != null,
                    entityId
            );
            return null;
        }

        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof CorpseEntity corpse) {
            CreateCybernetics.LOGGER.info("[corpse compat] resolveCorpse matched corpse={}", corpse);
            return corpse;
        }

        CreateCybernetics.LOGGER.info(
                "[corpse compat] resolveCorpse failed; entity was {}",
                entity == null ? "null" : entity.getClass().getName()
        );
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        copy = stack.copy();

        int corpseSlots = CorpseCompat.CYBERWARE_SLOT_COUNT;
        if (index < corpseSlots) {
            if (!moveItemStackTo(stack, corpseSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    private static class CorpseCyberwareInventory implements Container {
        private final CorpseEntity corpse;
        private final NonNullList<ItemStack> items;

        private CorpseCyberwareInventory(CorpseEntity corpse) {
            this.corpse = corpse;
            this.items = CorpseCompat.getCorpseCyberwareItems(corpse);

            CreateCybernetics.LOGGER.info(
                    "[corpse compat] CorpseCyberwareInventory init; corpse={}, itemCount={}, hasAnyCyberware={}",
                    corpse,
                    this.items.size(),
                    !this.isEmpty()
            );
        }

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack item : items) {
                if (!item.isEmpty()) return false;
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = getItem(slot);
            if (stack.isEmpty()) return ItemStack.EMPTY;

            ItemStack split = stack.split(amount);
            if (!split.isEmpty()) {
                setChanged();
            }
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
            ItemStack stack = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            setChanged();
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= items.size()) return;
            items.set(slot, stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            CorpseCompat.writeCorpseCyberwareItems(corpse, items);
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < items.size(); i++) {
                items.set(i, ItemStack.EMPTY);
            }
            setChanged();
        }
    }
}