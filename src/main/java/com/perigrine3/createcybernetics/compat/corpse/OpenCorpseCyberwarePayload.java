package com.perigrine3.createcybernetics.compat.corpse;

import com.perigrine3.createcybernetics.CreateCybernetics;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.maxhenkel.corpse.gui.CorpseInventoryContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record OpenCorpseCyberwarePayload(UUID corpseUUID) implements CustomPacketPayload {

    public static final Type<OpenCorpseCyberwarePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCybernetics.MODID, "open_corpse_cyberware"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCorpseCyberwarePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUUID(payload.corpseUUID()),
                    buf -> new OpenCorpseCyberwarePayload(buf.readUUID())
            );

    @Override
    public Type<OpenCorpseCyberwarePayload> type() {
        return TYPE;
    }

    public static void handle(OpenCorpseCyberwarePayload payload, IPayloadContext ctx) {
        CreateCybernetics.LOGGER.info("[corpse compat] payload received, corpseUUID={}", payload.corpseUUID());

        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof CorpseInventoryContainer corpseMenu)) return;

            CorpseEntity corpse = corpseMenu.getCorpse();
            if (corpse == null) return;
            if (payload.corpseUUID() == null) return;
            if (!payload.corpseUUID().equals(corpse.getUUID())) return;

            CorpseCompat.ensureCorpseCyberwareLoaded(corpse);

            CreateCybernetics.LOGGER.info(
                    "[corpse compat] opening corpse cyberware menu; corpseUUID={}, corpseEntityId={}, editable={}, hasCyberware={}",
                    corpse.getUUID(),
                    corpse.getId(),
                    corpseMenu.isEditable(),
                    CorpseCompat.hasCyberware(corpse)
            );

            player.openMenu(
                    new SimpleMenuProvider(
                            (id, inv, p) -> new CorpseCyberwareMenu(
                                    ModCorpseCompatMenus.CORPSE_CYBERWARE.get(),
                                    id,
                                    inv,
                                    corpse,
                                    corpseMenu.isEditable()
                            ),
                            Component.translatable("gui.createcybernetics.corpse_cyberware")
                    ),
                    buf -> {
                        buf.writeInt(corpse.getId());
                        buf.writeBoolean(corpseMenu.isEditable());
                    }
            );
        });
    }
}