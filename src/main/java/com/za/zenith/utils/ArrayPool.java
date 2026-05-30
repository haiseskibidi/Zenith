package com.za.zenith.utils;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ArrayPool {
    private static final ConcurrentLinkedQueue<int[]> blockDataPool = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<byte[]> lightDataPool = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<double[]> densityGridPool = new ConcurrentLinkedQueue<>();
    
    private static final int BLOCK_DATA_SIZE = 16 * 512 * 16;
    private static final int LIGHT_DATA_SIZE = 16 * 512 * 16;
    private static final int DENSITY_GRID_SIZE = 5 * 5 * 129;

    private static final int MAX_POOL_SIZE = 32;

    public static int[] rentBlockDataArray() {
        int[] arr = blockDataPool.poll();
        return (arr != null) ? arr : new int[BLOCK_DATA_SIZE];
    }

    public static void returnBlockDataArray(int[] arr) {
        if (arr != null && arr.length == BLOCK_DATA_SIZE && blockDataPool.size() < MAX_POOL_SIZE) {
            blockDataPool.offer(arr);
        }
    }

    public static byte[] rentLightDataArray() {
        byte[] arr = lightDataPool.poll();
        return (arr != null) ? arr : new byte[LIGHT_DATA_SIZE];
    }

    public static void returnLightDataArray(byte[] arr) {
        if (arr != null && arr.length == LIGHT_DATA_SIZE && lightDataPool.size() < MAX_POOL_SIZE) {
            lightDataPool.offer(arr);
        }
    }

    public static double[] rentDensityGrid() {
        double[] arr = densityGridPool.poll();
        return (arr != null) ? arr : new double[DENSITY_GRID_SIZE];
    }

    public static void returnDensityGrid(double[] arr) {
        if (arr != null && arr.length == DENSITY_GRID_SIZE && densityGridPool.size() < MAX_POOL_SIZE) {
            densityGridPool.offer(arr);
        }
    }
}
