package com.minecraft.atlamod.client;

import com.minecraft.atlamod.Atlamod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the stone suit over anyone wearing Earth armor.
 *
 * A LAYER rather than a change of equipment, which is the whole reason the ability can
 * say "adds ten armor on top of whatever you have on" and mean it — the bender's real
 * armor is untouched underneath, and simply not visible while the stone is over it.
 *
 * Whether to draw is asked of ClientEarthArmor rather than of the entity's effects,
 * because a client only ever knows its OWN effects: for anyone else, the server has to
 * say so, which is what EarthArmorPacket is for.
 */
public class EarthArmorLayer<T extends AbstractClientPlayer, M extends PlayerModel<T>>
        extends RenderLayer<T, M> {

    /**
     * A standard 64x32 armor sheet. Vanilla's armor renderer expects the outer layer
     * at this path shape, and using the same layout means the humanoid armor model
     * maps onto it without any special casing.
     */
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Atlamod.MODID, "textures/models/armor/stone_layer_1.png");

    private final HumanoidArmorModel<T> armor;

    public EarthArmorLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        // The OUTER armor layer: the fatter of vanilla's two, the one helmets,
        // chestplates and boots are drawn with. Rendering it whole gives a full suit
        // in one pass, where the inner layer exists only so leggings do not clip.
        this.armor = new HumanoidArmorModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {

        if (!ClientEarthArmor.has(entity.getId())) return;
        if (entity.isInvisible()) return;

        // The suit has to move with the body, so the pose is copied straight off the
        // player model this layer is attached to rather than animated a second time.
        this.getParentModel().copyPropertiesTo(this.armor);
        this.armor.setAllVisible(true);
        this.armor.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.armor.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        this.armor.renderToBuffer(
                poseStack,
                buffer.getBuffer(RenderType.armorCutoutNoCull(TEXTURE)),
                packedLight,
                OverlayTexture.NO_OVERLAY);
    }
}
