package com.perigrine3.createcybernetics.mixin.client;

import com.perigrine3.createcybernetics.compat.corpse.CorpseCompat;
import com.perigrine3.createcybernetics.compat.corpse.OpenCorpseCyberwarePayload;
import de.maxhenkel.corpse.entities.CorpseEntity;
import de.maxhenkel.corpse.gui.CorpseInventoryScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CorpseInventoryScreen.class)
public abstract class CorpseInventoryScreenMixin {

    @Shadow
    private CorpseEntity corpse;

    @Unique
    private static final Component CREATECYBERNETICS$CYBERWARE_TEXT =
            Component.translatable("gui.createcybernetics.corpse_cyberware_button");

    @Inject(method = "updateButtons", at = @At("TAIL"))
    private void createcybernetics$addCyberwareButton(CallbackInfo ci) {
        if (!CorpseCompat.isLoaded()) return;
        if (corpse == null) return;

        CorpseInventoryScreen screen = (CorpseInventoryScreen) (Object) this;

        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();

        ((ScreenInvoker) screen).cc$invokeAddRenderableWidget(
                Button.builder(
                                CREATECYBERNETICS$CYBERWARE_TEXT,
                                b -> PacketDistributor.sendToServer(
                                        new OpenCorpseCyberwarePayload(corpse.getUUID())
                                )
                        )
                        .bounds(left + 38, top + 142, 100, 20)
                        .build()
        );
    }
}