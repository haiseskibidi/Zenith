<template>
  <div class="coding-task-card" v-if="task">
    <!-- Шапка задачи в премиальном стиле Zenith -->
    <div class="task-header">
      <div class="task-title-group">
        <span class="task-badge">Zenith Practice Core</span>
        <h3 class="task-title">{{ task.title }}</h3>
      </div>
      <div :class="['difficulty-badge', `diff-${task.difficulty.toLowerCase()}`]">
        {{ task.difficulty }}
      </div>
    </div>

    <!-- Моноширинный путь к классу в IDE -->
    <div class="ide-path-container">
      <span class="ide-icon">💻</span>
      <span class="ide-label">IDE Path:</span>
      <code class="ide-path">{{ task.idePath }}</code>
    </div>

    <!-- Интерактивные вкладки -->
    <div class="task-tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.id"
        @click="setActiveTab(tab.id)"
        :class="['tab-btn', { 'active-tab': activeTab === tab.id }]"
      >
        <span class="tab-icon">{{ tab.icon }}</span>
        {{ tab.label }}
      </button>
    </div>

    <!-- Содержимое вкладок -->
    <div class="tab-viewport">
      <!-- Вкладка 1: Проблема и Задача -->
      <div v-if="activeTab === 'problem'" class="tab-content fade-in">
        <div class="info-section">
          <h4 class="section-subtitle">🚨 Симптом и Боль</h4>
          <p class="section-desc" v-html="task.problem"></p>
        </div>
      </div>

      <!-- Вкладка 2: Инструкции по запуску -->
      <div v-if="activeTab === 'instructions'" class="tab-content fade-in">
        <div class="info-section">
          <h4 class="section-subtitle">🛠 Алгоритм воспроизведения</h4>
          <ol class="instructions-list">
            <li v-for="(step, idx) in task.launchInstructions" :key="idx">
              <span class="step-num">{{ idx + 1 }}</span>
              <span class="step-text" v-html="step"></span>
            </li>
          </ol>
        </div>
      </div>

      <!-- Вкладка 3: Разбор и решение -->
      <div v-if="activeTab === 'solution'" class="tab-content fade-in">
        <div class="solution-disclaimer" v-if="!showSolution">
          <div class="disclaimer-icon">🔒</div>
          <h4 class="disclaimer-title">Эталонное решение скрыто</h4>
          <p class="disclaimer-desc">Попробуйте сначала решить задачу самостоятельно в вашей IDE, прежде чем открывать разбор.</p>
          <button @click="revealSolution" class="reveal-btn">Раскрыть решение и код</button>
        </div>
        
        <div v-else class="solution-details">
          <div class="info-section">
            <h4 class="section-subtitle">💡 Архитектурный разбор</h4>
            <p class="section-desc" v-html="task.solution.explanation"></p>
          </div>
          
          <div class="code-section">
            <h4 class="section-subtitle">💾 Эталонный код решения</h4>
            <pre class="line-numbers"><code class="language-java">{{ task.solution.code }}</code></pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { nextTick } from 'vue';

export default {
  name: 'CodingTaskComponent',
  props: {
    taskId: {
      type: String,
      required: true
    }
  },
  data() {
    return {
      activeTab: 'problem',
      showSolution: false,
      tabs: [
        { id: 'problem', label: 'Проблема и Задача', icon: '🔍' },
        { id: 'instructions', label: 'Инструкции по запуску', icon: '⚡' },
        { id: 'solution', label: 'Разбор и решение', icon: '💡' }
      ],
      tasks: {
        chapter_1: {
          title: "False Sharing в асинхронном мешинге",
          difficulty: "HARD",
          idePath: "src/main/java/com/za/zenith/world/chunk/ChunkMeshGenerator.java",
          problem: "При интенсивной генерации чанков несколько рабочих потоков в пуле <code>MeshThreadPool</code> постоянно обновляют свои локальные переменные прогресса или счетчики статистики, расположенные в одном общем объекте. Так как эти переменные находятся на расстоянии менее 64 байт друг от друга, они попадают в одну кэш-линию CPU. Это заставляет протокол когерентности <strong>MESI</strong> постоянно инвалидировать L1/L2 кэш соседних ядер, переводя ядра в режим ожидания (<strong>Cache Line Ping-Pong</strong>) и снижая FPS с 144 до 45 при движении игрока.",
          launchInstructions: [
            "Запустить Gradle-таск бенчмарка: <code class='inline-code'>./gradlew jmh -PjmhInclude=FalseSharingBenchmark</code>",
            "В игре включить Dev Mode (<code class='inline-code'>F3</code> -> <code class='inline-code'>Dev HUD</code>) и быстро лететь вперед в режиме генерации ландшафта.",
            "Наблюдать за ростом Cache Misses в профайлере VisualVM/async-profiler и микро-фризами (Frame Spikes)."
          ],
          solution: {
            explanation: "Для устранения ложного разделения необходимо разнести конкурирующие переменные по разным кэш-линиям. В Java 8+ для этого предназначена аннотация <code>@jdk.internal.vm.annotation.Contended</code>. По умолчанию доступ к ней ограничен, поэтому JVM нужно запускать с флагом <code>-XX:-RestrictContended</code>. Альтернативное портативное решение — ручной Padding (добавление неиспользуемых полей типа <code>long</code> перед и после горячей переменной).",
            code: `// Вариант 1: Использование аннотации @Contended (рекомендуется)
import jdk.internal.vm.annotation.Contended;

public class ChunkMeshProgress {
    @Contended("group1")
    public volatile long meshCount = 0;
    
    @Contended("group2")
    public volatile long bytesGenerated = 0;
}

// Вариант 2: Ручной Padding для старых систем / без флагов JVM
public class ChunkMeshProgressPadding {
    public long p1, p2, p3, p4, p5, p6, p7; // 56 байт padding
    public volatile long meshCount = 0;      // 8 байт
    public long p8, p9, p10, p11, p12, p13, p14; // еще 56 байт padding
}`
          }
        },
        chapter_2: {
          title: "Draw Call Overhead на чанках",
          difficulty: "HARD",
          idePath: "src/main/java/com/za/zenith/graphics/renderer/ChunkRenderSystem.java",
          problem: "При рендеринге воксельного мира каждый видимый чанк отрисовывается индивидуальным вызовом <code>glDrawElements</code>. При дальности прорисовки в 16 чанков на экране может находиться более 1000 секций. Это создает оверхед в 1000+ вызовов отрисовки (<strong>Draw Calls</strong>) за кадр. CPU тратит до 70% времени кадра в драйвере OpenGL на валидацию стейта и переключение контекста, в то время как видеокарта простаивает в ожидании команд (<strong>GPU starvation</strong>).",
          launchInstructions: [
            "Запустить игру и нажать <code class='inline-code'>F9</code> для открытия <code class='inline-code'>DevInspector</code>.",
            "В секции 'Graphics' обратить внимание на метрику 'Draw Calls' (показывает 1200+).",
            "Заметить падение FPS в лесистых биомах с густой растительностью."
          ],
          solution: {
            explanation: "Применение современного конвейера <strong>Indirect Rendering</strong> на базе MultiDraw. Все команды отрисовки упаковываются в один массив структур типа <code>DrawElementsIndirectCommand</code> и отправляются в буфер <code>GL_DRAW_INDIRECT_BUFFER</code> на GPU. После этого вызывается один системный метод <code>glMultiDrawElementsIndirect</code>, который выполняет рендеринг сотен чанков за один вызов.",
            code: `// Структура команды (Indirect Command) в Java
public class DrawCommand {
    public int count;         // Число индексов
    public int instanceCount; // Число инстансов (обычно 1)
    public int firstIndex;    // Смещение первого индекса в IBO
    public int baseVertex;    // Смещение первой вершины в VBO
    public int baseInstance;  // Смещение инстанса (gl_InstanceID)
}

// Рендеринг в ChunkRenderSystem
public void drawIndirect(int commandVBO, int drawCount) {
    glBindBuffer(GL_DRAW_INDIRECT_BUFFER, commandVBO);
    glMultiDrawElementsIndirect(
        GL_TRIANGLES, 
        GL_UNSIGNED_INT, 
        0, 
        drawCount, 
        0
    );
    glBindBuffer(GL_DRAW_INDIRECT_BUFFER, 0);
}`
          }
        },
        chapter_7: {
          title: "Race Condition в EventBus",
          difficulty: "MEDIUM",
          idePath: "src/main/java/com/za/zenith/event/EventBus.java",
          problem: "Система событий игры обрабатывает критически важные взаимодействия (нанесение урона, подбор лута, сетевые пакеты). В многопоточной среде, когда фоновые потоки генерации мира или ИИ генерируют события параллельно с главным потоком рендеринга, обычный <code>HashMap</code> внутри <code>EventBus</code> ломается с <code>ConcurrentModificationException</code>, либо события теряются из-за рассинхронизации ссылок при добавлении/удалении слушателей.",
          launchInstructions: [
            "Запустить игру с флагом отладки потоков.",
            "Спавнить более 50 сущностей <code class='inline-code'>ScoutEntity</code> и заставить их активно двигаться и издавать шум.",
            "Ловить случайные краши JVM или зависания в шине событий."
          ],
          solution: {
            explanation: "Использование потокобезопасных lock-free коллекций и атомарных операций CAS. Подписчики группируются по типу события с использованием <code>CopyOnWriteArrayList</code> для безопасного обхода списка во время вызова событий без блокировок, а регистрация новых слушателей защищается CAS-обертками.",
            code: `package com.za.zenith.event;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    // Потокобезопасная мапа списков подписчиков
    private final ConcurrentHashMap<Class<? extends Event>, CopyOnWriteArrayList<EventListener>> listeners = 
        new ConcurrentHashMap<>();

    public void register(Class<? extends Event> eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }

    public void post(Event event) {
        CopyOnWriteArrayList<EventListener> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return;
        
        // Безопасный обход без блокировок и ConcurrentModificationException
        for (EventListener listener : list) {
            listener.onEvent(event);
            if (event.isConsumed()) {
                break;
            }
        }
    }
}`
          }
        },
        chapter_9: {
          title: "Оптимизация взвешенного рандома лута",
          difficulty: "MEDIUM",
          idePath: "src/main/java/com/za/zenith/world/items/loot/LootTable.java",
          problem: "Игровой цикл Zenith часто генерирует лут при разрушении блоков и убийстве мобов. Простая реализация взвешенного рандома обходит список предметов и суммирует веса в рантайме. Это алгоритм сложности <code>O(N)</code>, совершающий аллокации итератора и массивов при каждом открытии сундука. При таблице лута из 10,000 предметов это вызывает микро-лаги и просадки по GC.",
          launchInstructions: [
            "Создать сундук с большой таблицей предметов (например, 1000+ видов мусора и ресурсов).",
            "Прописать спавн 1000 сундуков за раз.",
            "Наблюдать скачки времени кадра в <code class='inline-code'>DevHUD</code> и паузы сборщика мусора."
          ],
          solution: {
            explanation: "Оптимизировать выборку до <code>O(log N)</code> с помощью предварительного расчета префиксных сумм весов (Prefix Sums) и двоичного поиска (<code>Arrays.binarySearch</code>). Для критически нагруженных путей (Zero-Allocation) можно применить Alias Method (алгоритм Вакера), дающий выборку за константное время <code>O(1)</code>.",
            code: `package com.za.zenith.world.items.loot;

import java.util.*;

public class LootTable {
    private final List<LootEntry> entries = new ArrayList<>();
    private double[] prefixWeights;
    private double totalWeight;

    public void rebuild() {
        prefixWeights = new double[entries.size()];
        double sum = 0;
        for (int i = 0; i < entries.size(); i++) {
            sum += entries.get(i).weight;
            prefixWeights[i] = sum;
        }
        totalWeight = sum;
    }

    public LootEntry getRandomEntry(Random random) {
        if (totalWeight <= 0) return null;
        double target = random.nextDouble() * totalWeight;
        
        // Двоичный поиск за O(log N) вместо линейного O(N)
        int idx = Arrays.binarySearch(prefixWeights, target);
        if (idx < 0) {
            idx = -idx - 1;
        }
        
        // Предотвращаем выход за границы
        idx = Math.min(idx, entries.size() - 1);
        return entries.get(idx);
    }
}`
          }
        },
        hw_cpu: {
          title: "Конвейер CPU и Branch Prediction",
          difficulty: "EXPERT",
          idePath: "src/main/java/com/za/zenith/utils/VoxelFilter.java",
          problem: "Метод фильтрации вокселей обходит массив из миллионов блоков и отбирает только те, у которых прозрачность или прочность выше определенного порога. Если массив не отсортирован, условие <code>if (voxel.strength > threshold)</code> ведет себя хаотично. Предсказатель переходов процессора (<strong>Branch Predictor</strong>) постоянно ошибается (до 50% промахов), что приводит к сбросу всего конвейера CPU (<strong>Pipeline Flush</strong>) и замедлению работы алгоритма в 4-5 раз.",
          launchInstructions: [
            "Запустить бенчмарк: <code class='inline-code'>./gradlew jmh -PjmhInclude=BranchPredictionBenchmark</code>",
            "Сравнить результаты обработки для случайного и предварительно отсортированного массивов блоков."
          ],
          solution: {
            explanation: "Вариант A — предварительная сортировка данных, чтобы ветвления шли сериями (все false, затем все true), снижая промахи предсказателя до 1%. Вариант B — перевод логики на безветвенный (branchless) код с помощью битовых масок.",
            code: `// Вариант A: Предварительная сортировка (промахи сводятся к 0)
public void processSorted(Voxel[] voxels, float threshold) {
    Arrays.sort(voxels, Comparator.comparingDouble(v -> v.strength));
    for (Voxel voxel : voxels) {
        if (voxel.strength > threshold) {
            doAction(voxel);
        }
    }
}

// Вариант B: Безветвенный (Branchless) расчет
public int countVoxelsBranchless(int[] strengths, int threshold) {
    int count = 0;
    for (int strength : strengths) {
        // Условие (strength > threshold) в побитовом виде
        // В Java сдвиг >>> 31 возвращает знак (1 для отрицательных, 0 для положительных)
        int diff = threshold - strength;
        int bit = (diff >>> 31) & 1; // 1 если strength > threshold, 0 иначе
        count += bit;
    }
    return count;
}`
          }
        },
        hw_memory: {
          title: "Cache Locality при обходе Чанка",
          difficulty: "EXPERT",
          idePath: "src/main/java/com/za/zenith/world/chunk/Chunk.java",
          problem: "Чанк в Zenith представляет собой трехмерную сетку блоков 16x256x16. В памяти этот массив линеаризован. Если обходить чанк в неправильном порядке индексов (например, Y во внешнем цикле, затем Z, затем X), то при каждом переходе процессор обращается к адресам памяти, отстоящим далеко друг от друга. Это вызывает 100% промахов кэша (<strong>Cache Misses</strong>) L1/L2 и заставляет процессор простаивать, ожидая медленную оперативную память DRAM.",
          launchInstructions: [
            "Запустить тест производительности генерации меша чанка.",
            "Запустить профайлер с отслеживанием Hardware PMU (кэш-промахи последнего уровня L3)."
          ],
          solution: {
            explanation: "Обходить блоки чанка строго в порядке их линеаризации в памяти. Если формула индекса в плоском массиве: <code>index = (x * 16 + z) * 256 + y</code>, то внутренний цикл должен обновлять <code>y</code>, средний — <code>z</code>, а внешний — <code>x</code>. Это обеспечивает последовательное чтение памяти шаг за шагом, позволяя аппаратному префетчеру (<strong>Hardware Prefetcher</strong>) заранее подгружать кэш-линии (64 байта) в L1 кэш.",
            code: `public class Chunk {
    private final short[] blockData = new short[16 * 16 * 256];

    // Ужасно медленно (промахи кэша на каждом шаге):
    public void slowScan() {
        for (int y = 0; y < 256; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int index = (x * 16 + z) * 256 + y; // Прыжки по адресам памяти!
                    short block = blockData[index];
                    processBlock(block);
                }
            }
        }
    }

    // Идеально быстро (последовательный обход по кэш-линиям):
    public void fastScan() {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int baseIndex = (x * 16 + z) * 256;
                for (int y = 0; y < 256; y++) {
                    short block = blockData[baseIndex + y]; // Последовательное чтение!
                    processBlock(block);
                }
            }
        }
    }
}`
          }
        }
      }
    };
  },
  computed: {
    task() {
      return this.tasks[this.taskId] || null;
    }
  },
  methods: {
    setActiveTab(tabId) {
      this.activeTab = tabId;
      if (tabId === 'solution') {
        this.highlightCode();
      }
    },
    revealSolution() {
      this.showSolution = true;
      this.highlightCode();
    },
    highlightCode() {
      nextTick(() => {
        if (window.Prism) {
          window.Prism.highlightAllUnder(this.$el);
        }
      });
    }
  }
};
</script>

<style scoped>
/* ─── ПРЕМИАЛЬНЫЙ ДИЗАЙН ZENITH ACADEMY TASK CORE ─── */

.coding-task-card {
  background-color: #12131a;
  border: 1px solid #232733;
  border-radius: 12px;
  padding: 24px;
  margin: 32px 0;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25);
  position: relative;
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.coding-task-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 3px;
  background: linear-gradient(90deg, #06b6d4 0%, #3b82f6 50%, #8b5cf6 100%);
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.task-badge {
  display: inline-block;
  background: linear-gradient(135deg, rgba(6, 182, 212, 0.1) 0%, rgba(59, 130, 246, 0.1) 100%);
  border: 1px solid rgba(6, 182, 212, 0.2);
  color: #06b6d4;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
}

.task-title {
  color: #ffffff;
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  line-height: 1.4;
}

.difficulty-badge {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

.diff-easy {
  background-color: rgba(16, 185, 129, 0.1);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.2);
}

.diff-medium {
  background-color: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.2);
}

.diff-hard {
  background-color: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.diff-expert {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.15) 0%, rgba(236, 72, 153, 0.15) 100%);
  color: #c084fc;
  border: 1px solid rgba(139, 92, 246, 0.3);
  box-shadow: 0 0 10px rgba(139, 92, 246, 0.1);
}

/* IDE Path Container */
.ide-path-container {
  background-color: #0b0c10;
  border: 1px solid #1e2230;
  border-radius: 6px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
}

.ide-icon {
  font-size: 14px;
}

.ide-label {
  color: #626875;
  font-size: 13px;
  font-weight: 500;
}

.ide-path {
  font-family: 'JetBrains Mono', monospace;
  color: #06b6d4;
  font-size: 13px;
  word-break: break-all;
}

/* Вкладки */
.task-tabs {
  display: flex;
  gap: 8px;
  border-bottom: 1px solid #1e2230;
  margin-bottom: 20px;
  padding-bottom: 1px;
}

.tab-btn {
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: #7a8296;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s ease;
  border-radius: 6px 6px 0 0;
}

.tab-btn:hover {
  color: #ffffff;
  background-color: rgba(255, 255, 255, 0.02);
}

.active-tab {
  color: #3b82f6 !important;
  border-bottom-color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.04);
}

.tab-icon {
  font-size: 14px;
}

/* Viewport содержимого */
.tab-viewport {
  min-height: 120px;
}

.tab-content {
  line-height: 1.6;
}

.section-subtitle {
  color: #ffffff;
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px 0;
}

.section-desc {
  color: #a0a6b5;
  font-size: 14.5px;
  margin: 0;
}

.section-desc :deep(code), .section-desc :deep(strong) {
  font-family: 'JetBrains Mono', monospace;
  color: #e06c75;
}

.section-desc :deep(strong) {
  color: #ffffff;
  font-weight: 600;
}

/* Инструкции по запуску */
.instructions-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.instructions-list li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.step-num {
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1e2230;
  color: #3b82f6;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  margin-top: 2px;
  border: 1px solid rgba(59, 130, 246, 0.2);
}

.step-text {
  color: #c9ccd6;
  font-size: 14px;
}

.step-text :deep(.inline-code) {
  background-color: #0b0c10;
  border: 1px solid #1e2230;
  color: #e06c75;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 4px;
}

/* Разбор и решение - Замок */
.solution-disclaimer {
  text-align: center;
  padding: 24px;
  background-color: #0b0c10;
  border: 1px dashed #232733;
  border-radius: 8px;
}

.disclaimer-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.disclaimer-title {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.disclaimer-desc {
  color: #626875;
  font-size: 13.5px;
  max-width: 440px;
  margin: 0 auto 20px auto;
  line-height: 1.5;
}

.reveal-btn {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #ffffff;
  border: none;
  border-radius: 6px;
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
}

.reveal-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.35);
}

.solution-details {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.code-section {
  display: flex;
  flex-direction: column;
}

.code-section :deep(pre) {
  margin: 8px 0 0 0 !important;
  background-color: #050508 !important;
  border: 1px solid #1a1d26 !important;
}

/* Анимации перехода */
.fade-in {
  animation: fadeIn 0.25s ease-out forwards;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>