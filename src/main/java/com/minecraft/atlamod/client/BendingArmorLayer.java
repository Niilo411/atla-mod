package com.minecraft.atlamod.client;

import com.minecraft.atlamod.BendingArmorSuit;
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

/**
 * Draws whichever bending armor suit a player is wearing over the top of them.
 *
 * A LAYER rather than a change of equipment, which is the whole reason the abilities
 * can say "adds armor on top of whatever you have on" and mean it — the bender's real
 * armor is untouched underneath, and simply not visible while the suit is over it.
 *
 * What to draw is asked of ClientBendingArmor rather than of the entity's effects,
 * because a client only ever knows its OWN effects: for anyone else, the server has to
 * say so, which is what BendingArmorPacket is for.
 *
 * ONE layer serves every suit. The alternative — a layer per suit — would draw two
 * sheets onto the same model whenever a bender had both up, which z-fights; asking for
 * the top suit once settles that here instead of hoping it never happens.
 */
public class BendingArmorLayer<T extends AbstractClientPlayer, M extends PlayerModel<T>>
        extends RenderLayer<T, M> {

    private final HumanoidArmorModel<T> armor;

    public BendingArmorLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
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

        BendingArmorSuit suit = ClientBendingArmor.top(entity.getId());
        if (suit == null) return;
        if (entity.isInvisible()) return;

        // The suit has to move with the body, so the pose is copied straight off the
        // player model this layer is attached to rather than animated a second time.
        this.getParentModel().copyPropertiesTo(this.armor);
        this.armor.setAllVisible(true);
        this.armor.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
        this.armor.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        this.armor.renderToBuffer(
                poseStack,
                buffer.getBuffer(RenderType.armorCutoutNoCull(suit.texture())),
                packedLight,
                OverlayTexture.NO_OVERLAY);
    }
}
