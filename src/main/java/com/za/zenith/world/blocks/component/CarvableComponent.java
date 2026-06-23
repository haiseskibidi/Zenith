package com.za.zenith.world.blocks.component;

import com.google.gson.annotations.SerializedName;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.entities.Player;
import com.za.zenith.world.items.ItemStack;
import com.za.zenith.world.blocks.entity.ModularBlockEntity;
import com.za.zenith.utils.Identifier;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.world.blocks.CarvingLayoutEngine;

/**
 * Компонент для обтёсывания блока (carving) по сетке 4х4.
 */
public class CarvableComponent extends BlockComponent {
    @SerializedName("final_block")
    private String finalBlockId;
    @SerializedName("tool_tag")
    private String toolTag = "zenith:knives";

    @Override
    public boolean onLeftClick(World world, BlockPos pos, Player player, ItemStack heldStack, float hitX, float hitY, float hitZ, boolean isNewClick) {
        // Обтёсывание работает только при новом клике и ударе по верхней грани
        if (!isNewClick || hitY < 0.9f) return false;

        if (heldStack != null && (heldStack.getItem().hasTag(Identifier.of(toolTag)) || heldStack.getItem().getIdentifier().getPath().contains("knife"))) {
            var be = world.getBlockEntity(pos);
            if (!(be instanceof ModularBlockEntity modular)) return false;

            int index = CarvingLayoutEngine.getZoneIndex(hitX, hitZ);
            int mask = (int) modular.getFloat(ModularBlockEntity.PROP_CARVE_MASK, 0);
            
            // Если эта зона еще не обтёсана
            if ((mask & (1 << index)) == 0) {
                mask |= (1 << index);
                modular.setFloat(ModularBlockEntity.PROP_CARVE_MASK, (float) mask);
                
                player.swing();
                
                // Если все 16 зон обтёсаны - превращаем в финальный блок
                if (mask == 0xFFFF) {
                    Identifier nextBlock = Identifier.of(finalBlockId);
                    var nextDef = BlockRegistry.getBlock(nextBlock);
                    if (nextDef != null) {
                        world.setBlock(pos, new com.za.zenith.world.blocks.Block(nextDef.getId()));
                    }
                }
                return true;
            }
        }
        return false;
    }
}
