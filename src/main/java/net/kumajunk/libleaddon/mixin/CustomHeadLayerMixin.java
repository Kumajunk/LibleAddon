package net.kumajunk.libleaddon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kumajunk.libleaddon.state.HideArmorState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {

    @Inject(
            method = "submit*",
            at = @At("HEAD"),
            cancellable = true
    )
    private void libleaddon$hideCustomHead(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            LivingEntityRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        if (state instanceof HideArmorState hideState
                && hideState.libleaddon$isHideArmor()) {
            ci.cancel();
        }
    }
}