package com.za.zenith.world.fluid;

import com.za.zenith.world.World;
import com.za.zenith.world.blocks.Block;
import com.za.zenith.world.blocks.Blocks;
import com.za.zenith.world.blocks.BlockDefinition;
import com.za.zenith.world.blocks.BlockRegistry;
import com.za.zenith.utils.Direction;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отдельный симулятор физики жидкостей (воды, лавы, нефти).
 * Обеспечивает монолитное растекание и предотвращает колебания уровней в ямах.
 */
public class FluidSimulator {
    private final World world;
    
    private final Set<Long> pendingFluidTicks = ConcurrentHashMap.newKeySet();
    private final Set<Long> nextFluidTicks = ConcurrentHashMap.newKeySet();
    private float fluidTickTimer = 0.0f;
    
    // Preallocated arrays for fluid drop BFS pathfinding to prevent allocations
    private final int[] fluidBfsX = new int[128];
    private final int[] fluidBfsZ = new int[128];
    private final int[] fluidBfsDist = new int[128];
    private final int[] fluidBfsFirstStep = new int[128];
    private final boolean[] fluidVisited = new boolean[121];

    public FluidSimulator(World world) {
        this.world = world;
    }

    public void scheduleTick(int x, int y, int z) {
        pendingFluidTicks.add(World.packBlockPos(x, y, z));
    }

    public void tick(float deltaTime) {
        fluidTickTimer += deltaTime;
        if (fluidTickTimer >= 0.1f) {
            fluidTickTimer = 0.0f;
            tickFluids();
        }
    }

    private void tickFluids() {
        if (pendingFluidTicks.isEmpty()) return;

        nextFluidTicks.clear();
        nextFluidTicks.addAll(pendingFluidTicks);
        pendingFluidTicks.clear();

        for (long packed : nextFluidTicks) {
            tickFluid(packed);
        }
    }

    private void tickFluid(long packed) {
        int x = World.unpackBlockX(packed);
        int y = World.unpackBlockY(packed);
        int z = World.unpackBlockZ(packed);

        Block block = world.getBlock(x, y, z);
        int fluidId = block.getType();
        BlockDefinition def = BlockRegistry.getBlock(fluidId);

        if (!def.isFluid()) {
            return; // Больше не жидкость
        }

        int currentLevel = block.getMetadata() & 0xFF;

        // Источники (level 0) всегда стабильны и не высыхают.
        if (currentLevel == 0) {
            flowFluid(x, y, z, fluidId, currentLevel);
            return;
        }

        // 1. Проверяем бесконечный источник воды (только для воды, если уровень от 1 до 7)
        if (fluidId == Blocks.WATER.getId() && currentLevel > 0 && currentLevel != 8) {
            int adjacentSources = 0;
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue;
                int nx = x + dir.getDx();
                int ny = y + dir.getDy();
                int nz = z + dir.getDz();
                Block neighbor = world.getBlock(nx, ny, nz);
                if (neighbor.getType() == fluidId && (neighbor.getMetadata() & 0xFF) == 0) {
                    adjacentSources++;
                }
            }
            Block blockBelow = world.getBlock(x, y - 1, z);
            if (adjacentSources >= 2 && !blockBelow.isAir() && !blockBelow.isReplaceable()) {
                world.setBlock(x, y, z, new Block(fluidId, (byte) 0));
                flowFluid(x, y, z, fluidId, 0);
                return;
            }
        }

        // 2. Рассчитываем целевой уровень на основе источников вокруг (дистанционное поле)
        int targetLevel = -1; // -1 означает высыхание (воздух)

        // Проверяем блок сверху (падающий столб)
        if (y < 255) {
            Block blockAbove = world.getBlock(x, y + 1, z);
            if (blockAbove.getType() == fluidId) {
                targetLevel = 8;
            }
        }

        // Если сверху не течет, рассчитываем уровень на основе горизонтальных соседей
        if (targetLevel == -1) {
            int minNeighborLevel = 99;
            for (Direction dir : Direction.values()) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue;
                int nx = x + dir.getDx();
                int ny = y + dir.getDy();
                int nz = z + dir.getDz();
                Block neighbor = world.getBlock(nx, ny, nz);
                if (neighbor.getType() == fluidId) {
                    int neighborLevel = neighbor.getMetadata() & 0xFF;
                    if (neighborLevel == 8) {
                        // Падающий столб питает горизонталь как 0, только если под ним не воздух и не та же жидкость!
                        Block neighborBelow = world.getBlock(nx, ny - 1, nz);
                        int belowType = neighborBelow.getType();
                        if (belowType != Blocks.AIR.getId() && belowType != fluidId) {
                            minNeighborLevel = Math.min(minNeighborLevel, 0);
                        }
                    } else {
                        minNeighborLevel = Math.min(minNeighborLevel, neighborLevel);
                    }
                }
            }

            if (minNeighborLevel <= 6) {
                targetLevel = minNeighborLevel + 1;
            }
        }

        // Применяем изменения уровня и планируем тики
        if (targetLevel == -1) {
            if (currentLevel == 8) {
                Block above = world.getBlock(x, y + 1, z);
                com.za.zenith.utils.Logger.info("Falling column dry at (%d,%d,%d). Above type: %d, meta: %d, expected fluidId: %d", x, y, z, above.getType(), above.getMetadata(), fluidId);
            } else {
                com.za.zenith.utils.Logger.info("Fluid dry at (%d,%d,%d) currentLevel: %d", x, y, z, currentLevel);
            }
            world.setBlock(x, y, z, new Block(Blocks.AIR.getId()));
            
            // Оповещаем соседей
            for (Direction dir : Direction.values()) {
                int nx = x + dir.getDx();
                int ny = y + dir.getDy();
                int nz = z + dir.getDz();
                Block neighbor = world.getBlock(nx, ny, nz);
                if (BlockRegistry.getBlock(neighbor.getType()).isFluid()) {
                    scheduleTick(nx, ny, nz);
                }
            }
        } else {
            if (targetLevel != currentLevel) {
                com.za.zenith.utils.Logger.info("Fluid level change at (%d,%d,%d) from %d to %d", x, y, z, currentLevel, targetLevel);
                world.setBlock(x, y, z, new Block(fluidId, (byte) targetLevel));
                scheduleTick(x, y, z);
                
                // Оповещаем соседей
                for (Direction dir : Direction.values()) {
                    int nx = x + dir.getDx();
                    int ny = y + dir.getDy();
                    int nz = z + dir.getDz();
                    Block neighbor = world.getBlock(nx, ny, nz);
                    if (BlockRegistry.getBlock(neighbor.getType()).isFluid()) {
                        scheduleTick(nx, ny, nz);
                    }
                }
            }
            
            flowFluid(x, y, z, fluidId, targetLevel);
        }
    }

    private void flowFluid(int x, int y, int z, int fluidId, int currentLevel) {
        // 3. Течение вниз
        boolean flowedDown = false;
        if (y > 0) {
            Block blockBelow = world.getBlock(x, y - 1, z);
            if (blockBelow.isReplaceable() && blockBelow.getType() != fluidId) {
                world.setBlock(x, y - 1, z, new Block(fluidId, (byte) 8));
                scheduleTick(x, y - 1, z);
                flowedDown = true;
            } else if (blockBelow.getType() == fluidId) {
                int belowLevel = blockBelow.getMetadata() & 0xFF;
                if (belowLevel != 8 && belowLevel != 0) {
                    world.setBlock(x, y - 1, z, new Block(fluidId, (byte) 8));
                    scheduleTick(x, y - 1, z);
                }
                flowedDown = true;
            }
        }

        // 4. Если потечь вниз не удалось (или снизу твердо/уже вода, или это источник), то течем в стороны
        if (!flowedDown || currentLevel == 0) {
            int targetLevel = (currentLevel == 8) ? 1 : (currentLevel + 1);
            if (targetLevel <= 7) {
                boolean[] flowDirs = new boolean[4]; // NORTH, SOUTH, EAST, WEST
                int foundDrops = getFluidFlowDirections(x, y, z, fluidId, flowDirs);
                
                Direction[] dirs = {
                    Direction.NORTH,
                    Direction.SOUTH,
                    Direction.EAST,
                    Direction.WEST
                };

                for (int i = 0; i < 4; i++) {
                    if (foundDrops > 0 && !flowDirs[i]) continue;

                    Direction dir = dirs[i];
                    int nx = x + dir.getDx();
                    int nz = z + dir.getDz();
                    Block neighbor = world.getBlock(nx, y, nz);

                    if (neighbor.isReplaceable()) {
                        if (neighbor.getType() != fluidId) {
                            world.setBlock(nx, y, nz, new Block(fluidId, (byte) targetLevel));
                            scheduleTick(nx, y, nz);
                        } else {
                            int neighborLevel = neighbor.getMetadata() & 0xFF;
                            if (neighborLevel != 8 && neighborLevel > targetLevel) {
                                world.setBlock(nx, y, nz, new Block(fluidId, (byte) targetLevel));
                                scheduleTick(nx, y, nz);
                            }
                        }
                    }
                }
            }
        }
    }

    private int getFluidFlowDirections(int startX, int startY, int startZ, int fluidId, boolean[] flowDirs) {
        java.util.Arrays.fill(fluidVisited, false);
        fluidVisited[5 * 11 + 5] = true;

        int head = 0;
        int tail = 0;

        Direction[] dirs = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
        };

        int directDrops = 0;
        for (int i = 0; i < 4; i++) {
            Direction dir = dirs[i];
            int nx = startX + dir.getDx();
            int nz = startZ + dir.getDz();
            
            Block neighbor = world.getBlock(nx, startY, nz);
            if (neighbor.isReplaceable()) {
                Block neighborBelow = world.getBlock(nx, startY - 1, nz);
                if (neighborBelow.isReplaceable()) {
                    flowDirs[i] = true;
                    directDrops++;
                }

                fluidBfsX[tail] = nx;
                fluidBfsZ[tail] = nz;
                fluidBfsDist[tail] = 1;
                fluidBfsFirstStep[tail] = i;
                tail++;
                
                int rx = nx - startX + 5;
                int rz = nz - startZ + 5;
                fluidVisited[rx * 11 + rz] = true;
            }
        }

        // Если прямо под боком есть обрывы, течем только в них (по всем направлениям обрывов на расст. 1)
        if (directDrops > 0) {
            return directDrops;
        }

        int minDropDist = 999;
        int foundCount = 0;

        while (head < tail) {
            int cx = fluidBfsX[head];
            int cz = fluidBfsZ[head];
            int dist = fluidBfsDist[head];
            int firstStepIdx = fluidBfsFirstStep[head];
            head++;

            if (dist >= 5) continue; 
            if (dist > minDropDist) break; 

            for (Direction dir : dirs) {
                int nx = cx + dir.getDx();
                int nz = cz + dir.getDz();

                int rx = nx - startX + 5;
                int rz = nz - startZ + 5;
                if (rx < 0 || rx >= 11 || rz < 0 || rz >= 11) continue;

                if (!fluidVisited[rx * 11 + rz]) {
                    fluidVisited[rx * 11 + rz] = true;

                    Block neighbor = world.getBlock(nx, startY, nz);
                    if (neighbor.isReplaceable()) {
                        Block neighborBelow = world.getBlock(nx, startY - 1, nz);
                        if (neighborBelow.isReplaceable()) {
                            minDropDist = dist + 1;
                            flowDirs[firstStepIdx] = true;
                            foundCount++;
                        } else {
                            fluidBfsX[tail] = nx;
                            fluidBfsZ[tail] = nz;
                            fluidBfsDist[tail] = dist + 1;
                            fluidBfsFirstStep[tail] = firstStepIdx;
                            tail++;
                        }
                    }
                }
            }
        }

        return foundCount;
    }
}
