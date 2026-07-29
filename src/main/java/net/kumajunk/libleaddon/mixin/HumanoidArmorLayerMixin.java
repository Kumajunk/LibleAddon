package net.kumajunk.libleaddon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.kumajunk.libleaddon.state.HideArmorState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidArmorLayer.class)
public class HumanoidArmorLayerMixin {

    @Inject(
            method = "renderArmorPiece",
            at = @At("HEAD"),
            cancellable = true
    )
    private <S extends HumanoidRenderState> void hideArmor(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ItemStack itemStack,
            EquipmentSlot slot,
            int lightCoords,
            S state,
            CallbackInfo ci
    ) {
        if (((HideArmorState) state).libleaddon$isHideArmor()) {
            ci.cancel();
        }
    }
}
