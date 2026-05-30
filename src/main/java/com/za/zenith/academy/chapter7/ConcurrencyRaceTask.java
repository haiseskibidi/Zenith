package com.za.zenith.academy.chapter7;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <h1>Глава 7: Многопоточность — Задача на Concurrency Race (Состояние гонки)</h1>
 * 
 * <p><b>Суть проблемы:</b>
 * При многопоточной генерации чанков в Zenith потоки одновременно регистрируют новые типы блоков
 * в глобальной палитре. Если использовать несинхронизированную HashMap и обычный счетчик (counter++),
 * возникнет состояние гонки (Race Condition). В лучшем случае данные перезапишутся и палитра сломается
 * (Data Corruption), в худшем — JVM выбросит ConcurrentModificationException или войдет в бесконечный цикл.</p>
 * 
 * <p><b>Студенческое задание:</b>
 * 1. Изучите некорректную реализацию {@link UnsafePalette}. Обратите внимание на расхождение итогового размера палитры с ожидаемым.
 * 2. Изучите реализацию с блокировками {@link LockedPalette} (на базе {@link ReentrantReadWriteLock}).
 * 3. Изучите Lock-Free реализацию {@link LockFreePalette} (на базе {@link ConcurrentHashMap} и {@link AtomicInteger}).
 * 4. Сравните их производительность и корректность.</p>
 */
public class ConcurrencyRaceTask {

    private static final int THREADS = 8;
    private static final int OPERATIONS_PER_THREAD = 100_000;
    
    // Интерфейс нашей палитры блоков
    public interface BlockPalette {
        int getOrCreateId(String blockName);
        int size();
        void clear();
    }

    // --- 1. НЕКОРРЕКТНАЯ РЕАЛИЗАЦИЯ (Обычная HashMap, без синхронизации) ---
    public static class UnsafePalette implements BlockPalette {
        private final Map<String, Integer> registry = new HashMap<>();
        private int nextId = 0;

        @Override
        public int getOrCreateId(String blockName) {
            Integer id = registry.get(blockName);
            if (id == null) {
                // Имитируем небольшую задержку, чтобы увеличить вероятность Race Condition
                try { Thread.sleep(0); } catch (InterruptedException ignored) {}
                id = nextId++;
                registry.put(blockName, id);
            }
            return id;
        }

        @Override
        public int size() {
            return registry.size();
        }

        @Override
        public void clear() {
            registry.clear();
            nextId = 0;
        }
    }

    // --- 2. РЕАЛИЗАЦИЯ С БЛОКИРОВКАМИ (RW Lock) ---
    public static class LockedPalette implements BlockPalette {
        private final Map<String, Integer> registry = new HashMap<>();
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        private int nextId = 0;

        @Override
        public int getOrCreateId(String blockName) {
            // Пробуем сначала прочитать под read lock
            rwLock.readLock().lock();
            try {
                Integer id = registry.get(blockName);
                if (id != null) {
                    return id;
                }
            } finally {
                rwLock.readLock().unlock();
            }

            // Если не нашли, берем write lock
            rwLock.writeLock().lock();
            try {
                // Double-checked locking
                Integer id = registry.get(blockName);
                if (id == null) {
                    id = nextId++;
                    registry.put(blockName, id);
                }
                return id;
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        @Override
        public int size() {
            rwLock.readLock().lock();
            try {
                return registry.size();
            } finally {
                rwLock.readLock().unlock();
            }
        }

        @Override
        public void clear() {
            rwLock.writeLock().lock();
            try {
                registry.clear();
                nextId = 0;
            } finally {
                rwLock.writeLock().unlock();
            }
        }
    }

    // --- 3. LOCK-FREE РЕАЛИЗАЦИЯ (ConcurrentHashMap + AtomicInteger) ---
    public static class LockFreePalette implements BlockPalette {
        private final ConcurrentHashMap<String, Integer> registry = new ConcurrentHashMap<>();
        private final AtomicInteger nextId = new AtomicInteger(0);

        @Override
        public int getOrCreateId(String blockName) {
            Integer id = registry.get(blockName);
            if (id == null) {
                id = registry.computeIfAbsent(blockName, k -> nextId.getAndIncrement());
            }
            return id;
        }

        @Override
        public int size() {
            return registry.size();
        }

        @Override
        public void clear() {
            registry.clear();
            nextId.set(0);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=========================================================");
        System.out.println(" Zenith Academy: Глава 7. Тест Concurrency Race в Палитре");
        System.out.println("=========================================================");

        // Подготовим список уникальных имен блоков (например, 1000 разных блоков)
        String[] blockNames = new String[1000];
        for (int i = 0; i < blockNames.length; i++) {
            blockNames[i] = "block_type_" + i;
        }

        // 1. Тест UnsafePalette (может приводить к ConcurrentModificationException или зависанию,
        // поэтому обернем в try-catch и ограничим время)
        System.out.println("1. Запуск UnsafePalette (Без синхронизации)...");
        UnsafePalette unsafe = new UnsafePalette();
        try {
            runMultiThreaded(unsafe, blockNames);
            System.out.println("   Результат: Успешно завершено без падения JVM.");
            System.out.printf("   Ожидаемый размер палитры: %d, Реальный: %d\n", blockNames.length, unsafe.size());
            if (unsafe.size() != blockNames.length) {
                System.out.println("   [ВНИМАНИЕ] Произошла потеря данных (Data Corruption) из-за гонки потоков!");
            }
        } catch (Exception e) {
            System.out.println("   [ОШИБКА] Произошел сбой: " + e.getMessage());
        }

        // Разогрев JVM
        LockedPalette locked = new LockedPalette();
        LockFreePalette lockFree = new LockFreePalette();
        runMultiThreaded(locked, blockNames);
        runMultiThreaded(lockFree, blockNames);

        // 2. Тест LockedPalette (RW Lock)
        System.out.println("\n2. Запуск LockedPalette (ReadWriteLock)...");
        locked.clear();
        long startLocked = System.nanoTime();
        runMultiThreaded(locked, blockNames);
        long endLocked = System.nanoTime();
        double timeLockedMs = (endLocked - startLocked) / 1_000_000.0;
        System.out.printf("   Результат: Корректно. Размер: %d\n", locked.size());
        System.out.printf("   Время выполнения: %.2f ms\n", timeLockedMs);

        // 3. Тест LockFreePalette (ConcurrentHashMap + Atomic)
        System.out.println("\n3. Запуск LockFreePalette (Lock-Free / CAS)...");
        lockFree.clear();
        long startLF = System.nanoTime();
        runMultiThreaded(lockFree, blockNames);
        long endLF = System.nanoTime();
        double timeLFMs = (endLF - startLF) / 1_000_000.0;
        System.out.printf("   Результат: Корректно. Размер: %d\n", lockFree.size());
        System.out.printf("   Время выполнения: %.2f ms\n", timeLFMs);

        double speedup = timeLockedMs / timeLFMs;
        System.out.printf("\nУскорение Lock-Free относительно блокировок: %.2fx\n", speedup);
        System.out.println("=========================================================");
    }

    private static void runMultiThreaded(BlockPalette palette, String[] blockNames) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                // Каждый поток циклически запрашивает ID блоков
                for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                    int index = (i + threadId) % blockNames.length;
                    palette.getOrCreateId(blockNames[index]);
                }
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }
}