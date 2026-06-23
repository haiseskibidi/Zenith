package com.za.zenith.world.blocks;

import com.za.zenith.entities.FallingBlockEntity;
import com.za.zenith.engine.core.GameLoop;
import com.za.zenith.engine.graphics.Renderer;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import org.joml.Vector3f;

/**
 * System handling falling physics triggers for gravity-sensitive blocks (e.g., sand, gravel).
 * ponytail: isolated to keep BlockDefinition clean and focused.
 */
public class BlockGravitySystem {

    public static void onBlockAdded(World world, BlockPos pos, BlockDefinition def) {
        if (def.hasGravity()) {
            checkFall(world, pos, def);
        }
    }

    public static void checkFall(World world, BlockPos pos, BlockDefinition def) {
        if (world.isGenerating()) return;

        BlockPos belowPos = pos.down();
        Block belowBlock = world.getBlock(belowPos);

        // Fall if block below is air, replaceable, or fluid
        if (belowBlock.isAir() || belowBlock.isReplaceable() || BlockRegistry.getBlock(belowBlock.getType()).isFluid()) {
            Block currentBlock = world.getBlock(pos);
            if (currentBlock.getType() == def.getId()) {
                int typeToFall = currentBlock.getType();
                byte metaToFall = currentBlock.getMetadata();

                // Replace block with air and trigger physics updates for neighbors
                world.setBlock(pos, new Block(Blocks.AIR.getId()));

                // Hide block immediately on GPU to prevent visual Z-fighting/ghosting before chunk mesh updates
                if (GameLoop.getInstance() != null) {
                    Renderer renderer = GameLoop.getInstance().getRenderer();
                    if (renderer != null) {
                        renderer.addTemporaryHiddenBlock(pos);
                    }
                }

                BlockDefinition.FallingSettings settings = def.getFallingSettings();
                float startGravity = settings != null ? settings.getGravity() : -28.0f;
                float startTerminal = settings != null ? settings.getTerminalVelocity() : -50.0f;

                FallingBlockEntity entity = new FallingBlockEntity(
                    new Vector3f(pos.x() + 0.5f, pos.y(), pos.z() + 0.5f),
                    typeToFall,
                    metaToFall,
                    startGravity,
                    startTerminal
                );
                world.spawnEntity(entity);
            }
        }
    }
}
