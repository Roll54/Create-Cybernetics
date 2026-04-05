package com.perigrine3.createcybernetics.mixin.client;

import com.mojang.authlib.GameProfile;
import com.perigrine3.createcybernetics.compat.corpse.CorpseVisualSnapshotClientCache;
import de.maxhenkel.corpse.corelib.CachedMap;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.maxhenkel.corpse.entities.CorpseRenderer;
import de.maxhenkel.corpse.entities.DummyPlayer;
import de.maxhenkel.corpse.entities.DummySkeleton;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(CorpseRenderer.class)
public abstract class CorpseRendererMixin {

    @Shadow
    @Final
    private CachedMap<CorpseEntity, DummyPlayer> players;

    @Shadow
    @Final
    private CachedMap<CorpseEntity, DummySkeleton> skeletons;

    @Inject(method = "render", at = @At("HEAD"))
    private void createcybernetics$applyCorpseVisualSnapshot(
            CorpseEntity entity,
            float entityYaw,
            float partialTicks,
            com.mojang.blaze3d.vertex.PoseStack matrixStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLightIn,
            CallbackInfo ci
    ) {
        if (entity == null || entity.isSkeleton()) {
            return;
        }

        AbstractClientPlayer visualPlayer = this.players.get(
                entity,
                () -> new DummyPlayer(
                        (ClientLevel) entity.level(),
                        new GameProfile(
                                entity.getCorpseUUID().orElse(new UUID(0L, 0L)),
                                entity.getCorpseName()
                        ),
                        entity.getEquipment(),
                        entity.getCorpseModel()
                )
        );

        CorpseVisualSnapshotClientCache.applyToPlayer(
                visualPlayer,
                entity.getCorpseUUID().orElse(null)
        );
    }
}