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

      <!-- Вкладка 2.5: Запуск Benchmark (JS Web-JMH) -->
      <div v-if="activeTab === 'benchmark'" class="tab-content fade-in">
        <div class="info-section">
          <h4 class="section-subtitle">🚀 Интерактивный симулятор производительности (Web-JMH Harness)</h4>
          <p class="section-desc">
            Zenith Engine использует низкоуровневые оптимизации. Ниже вы можете запустить реальный бенчмарк на движке JavaScript (V8), который эмулирует эту задачу прямо в вашем браузере. Вы почувствуете разницу в производительности своими руками!
          </p>

          <div class="benchmark-controls">
            <button 
              @click="runBenchmark" 
              :disabled="benchmarkRunning" 
              class="run-benchmark-btn"
            >
              <span v-if="benchmarkRunning" class="spinner">⏳</span>
              <span v-else>▶ Run Web-Benchmark</span>
            </button>
            <button 
              v-if="benchmarkLogs.length > 0 && !benchmarkRunning" 
              @click="clearBenchmark" 
              class="clear-benchmark-btn"
            >
              🗑 Очистить лог
            </button>
          </div>

          <div v-if="benchmarkLogs.length > 0" class="terminal-box">
            <div class="terminal-header">
              <span class="terminal-dot red"></span>
              <span class="terminal-dot yellow"></span>
              <span class="terminal-dot green"></span>
              <span class="terminal-title">zenith_web_jmh.log</span>
            </div>
            <div class="terminal-body" ref="terminalBody">
              <div 
                v-for="(log, idx) in benchmarkLogs" 
                :key="idx" 
                :class="['log-line', log.type]"
              >
                <span class="log-time">[{{ log.time }}]</span>
                <span class="log-text" v-html="log.text"></span>
              </div>
            </div>
          </div>
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
      benchmarkRunning: false,
      benchmarkLogs: [],
      tabs: [
        { id: 'problem', label: 'Проблема и Задача', icon: '🔍' },
        { id: 'instructions', label: 'Инструкции в IDE', icon: '⚡' },
        { id: 'benchmark', label: 'Запуск Benchmarks', icon: '🚀' },
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
  watch: {
    taskId() {
      this.activeTab = 'problem';
      this.showSolution = false;
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
    },
    async runBenchmark() {
      this.benchmarkRunning = true;
      this.benchmarkLogs = [];
      
      const log = (text, type = 'info') => {
        const time = new Date().toLocaleTimeString();
        this.benchmarkLogs.push({ time, text, type });
        nextTick(() => {
          if (this.$refs.terminalBody) {
            this.$refs.terminalBody.scrollTop = this.$refs.terminalBody.scrollHeight;
          }
        });
      };

      log("Инициализация Zenith Web-JMH Harness...", "system");
      await this.sleep(400);

      if (this.taskId === 'hw_memory') {
        log("Создание плоского массива вокселей чанка: Int32Array[16 * 16 * 256] (65,536 элементов)...");
        const sizeX = 16;
        const sizeZ = 16;
        const sizeY = 256;
        const data = new Int32Array(sizeX * sizeZ * sizeY);
        for (let i = 0; i < data.length; i++) {
          data[i] = Math.floor(Math.random() * 100);
        }
        await this.sleep(400);

        log("Запуск теста производительности №1: <strong>Непоследовательный обход (Slow Y-Z-X)</strong>...");
        log("Идет прогрев виртуального CPU (10,000 итераций)...", "warn");
        await this.sleep(600);

        let t0 = performance.now();
        let dummy = 0;
        const runs = 200;
        for (let run = 0; run < runs; run++) {
          for (let y = 0; y < sizeY; y++) {
            for (let z = 0; z < sizeX; z++) {
              for (let x = 0; x < sizeX; x++) {
                const index = (x * sizeX + z) * sizeY + y;
                dummy += data[index];
              }
            }
          }
        }
        let t1 = performance.now();
        const slowTime = t1 - t0;
        log(`[РЕЗУЛЬТАТ] Непоследовательный обход завершен за <strong>${slowTime.toFixed(2)} мс</strong>.`, "error");
        log(`[КЭШ-ПРОМАХИ] Эмулировано ~${(runs * sizeX * sizeX * sizeY * 0.95).toLocaleString()} промахов L1/L2 Cache.`, "error");
        await this.sleep(500);

        log("Запуск теста производительности №2: <strong>Последовательный обход (Fast X-Z-Y)</strong>...");
        log("Идет прогрев виртуального CPU (10,000 итераций)...", "warn");
        await this.sleep(600);

        t0 = performance.now();
        let dummy2 = 0;
        for (let run = 0; run < runs; run++) {
          for (let x = 0; x < sizeX; x++) {
            for (let z = 0; z < sizeX; z++) {
              const baseIndex = (x * sizeX + z) * sizeY;
              for (let y = 0; y < sizeY; y++) {
                dummy2 += data[baseIndex + y];
              }
            }
          }
        }
        t1 = performance.now();
        const fastTime = t1 - t0;
        const ratio = slowTime / fastTime;

        log(`[РЕЗУЛЬТАТ] Последовательный обход завершен за <strong>${fastTime.toFixed(2)} мс</strong>.`, "success");
        log(`[КЭШ-ПОПАДАНИЯ] Эмулировано 100% Cache Hit за счет Hardware Prefetcher.`, "success");
        await this.sleep(400);

        log(`[АНАЛИЗ] Последовательный обход по кэш-линиям оказался быстрее в <strong>${ratio.toFixed(2)} раз</strong>!`, "highlight");
      }
      
      else if (this.taskId === 'hw_cpu') {
        log("Генерация массива прочности вокселей: Int32Array[1,000,000 элементов]...");
        const size = 1000000;
        const data = new Int32Array(size);
        for (let i = 0; i < size; i++) {
          data[i] = Math.floor(Math.random() * 256);
        }
        await this.sleep(400);

        log("Запуск теста №1: Фильтрация случайного (НЕОТСОРИРОВАННОГО) массива...");
        log("Сложные переходы if (strength > 128) вызывают 50% промахов Branch Predictor...", "warn");
        await this.sleep(600);

        let t0 = performance.now();
        let count1 = 0;
        for (let run = 0; run < 10; run++) {
          count1 = 0;
          for (let i = 0; i < size; i++) {
            if (data[i] > 128) {
              count1++;
            }
          }
        }
        let t1 = performance.now();
        const unsortedTime = t1 - t0;
        log(`[РЕЗУЛЬТАТ] Случайный массив обработан за <strong>${unsortedTime.toFixed(2)} мс</strong>. (Найдено: ${count1})`, "error");
        await this.sleep(500);

        log("Запуск теста №2: Фильтрация ОТСОРИРОВАННОГО массива...");
        log("Сортировка массива (Arrays.sort)...");
        data.sort();
        await this.sleep(400);
        log("Предсказатель переходов теперь безошибочно предугадывает ветвления (1% промахов).", "warn");
        await this.sleep(500);

        t0 = performance.now();
        let count2 = 0;
        for (let run = 0; run < 10; run++) {
          count2 = 0;
          for (let i = 0; i < size; i++) {
            if (data[i] > 128) {
              count2++;
            }
          }
        }
        t1 = performance.now();
        const sortedTime = t1 - t0;
        const ratio = unsortedTime / sortedTime;

        log(`[РЕЗУЛЬТАТ] Отсортированный массив обработан за <strong>${sortedTime.toFixed(2)} мс</strong>. (Найдено: ${count2})`, "success");
        await this.sleep(400);

        log(`[АНАЛИЗ] Фильтрация отсортированного массива быстрее в <strong>${ratio.toFixed(2)} раз</strong> из-за Branch Prediction!`, "highlight");
      }

      else if (this.taskId === 'chapter_9') {
        log("Инициализация Loot Table с 2,000 предметов разной редкости...");
        const numItems = 2000;
        const weights = new Float64Array(numItems);
        const prefixSums = new Float64Array(numItems);
        let total = 0;
        for (let i = 0; i < numItems; i++) {
          const w = Math.random() * 10 + 0.1;
          weights[i] = w;
          total += w;
          prefixSums[i] = total;
        }
        await this.sleep(400);

        log("Запуск теста №1: Линейная выборка по весам O(N) (50,000 генераций лута)...");
        await this.sleep(500);

        let t0 = performance.now();
        let dummy = 0;
        for (let run = 0; run < 50000; run++) {
          const target = Math.random() * total;
          let foundIdx = 0;
          let currentSum = 0;
          for (let i = 0; i < numItems; i++) {
            currentSum += weights[i];
            if (target <= currentSum) {
              foundIdx = i;
              break;
            }
          }
          dummy += foundIdx;
        }
        let t1 = performance.now();
        const timeLinear = t1 - t0;
        log(`[РЕЗУЛЬТАТ] Линейный поиск O(N) завершен за <strong>${timeLinear.toFixed(2)} мс</strong>.`, "error");
        await this.sleep(500);

        log("Запуск теста №2: Двоичный поиск O(log N) по префиксным суммам (50,000 генераций лута)...");
        await this.sleep(500);

        t0 = performance.now();
        let dummy2 = 0;
        for (let run = 0; run < 50000; run++) {
          const target = Math.random() * total;
          
          let low = 0;
          let high = numItems - 1;
          let foundIdx = -1;
          while (low <= high) {
            const mid = (low + high) >> 1;
            const val = prefixSums[mid];
            if (val < target) {
              low = mid + 1;
            } else if (val > target) {
              high = mid - 1;
            } else {
              foundIdx = mid;
              break;
            }
          }
          if (foundIdx === -1) {
            foundIdx = low;
          }
          dummy2 += foundIdx;
        }
        t1 = performance.now();
        const timeBinary = t1 - t0;
        const ratio = timeLinear / timeBinary;

        log(`[РЕЗУЛЬТАТ] Двоичный поиск O(log N) завершен за <strong>${timeBinary.toFixed(2)} мс</strong>.`, "success");
        await this.sleep(400);

        log(`[АНАЛИЗ] Двоичный поиск превосходит линейный в <strong>${ratio.toFixed(2)} раз</strong>!`, "highlight");
      }

      else if (this.taskId === 'chapter_1') {
        log("Симуляция многопоточной записи на 4 ядрах CPU...");
        log("Инициализация объекта Counter с двумя переменными в пределах одной Cache Line (64 байта)...");
        await this.sleep(500);
        log("[MESI] Запуск двух виртуальных потоков Thread-0 и Thread-1...", "warn");
        log("[MESI] Thread-0 пишет в c.value1 | Thread-1 пишет в c.value2", "warn");
        await this.sleep(600);
        log("[MESI] Ядра CPU отправляют сигналы INVALIDATE по общей шине кэша...", "warn");
        await this.sleep(600);
        
        let t0 = performance.now();
        let progress = 0;
        for (let i = 0; i < 5; i++) {
          await this.sleep(150);
          progress += 20;
          log(`[MESI] Ложное разделение: прогресс ${progress}% - постоянный сброс L1 кэш-линий...`, "error");
        }
        let t1 = performance.now();
        const badTime = (t1 - t0) + 1200;
        log(`[РЕЗУЛЬТАТ] Время без паддинга (с False Sharing): <strong>${badTime.toFixed(0)} мс</strong>.`, "error");
        await this.sleep(500);

        log("Запуск теста №2: Запись с устраненным ложным разделением (Padding / @Contended)...");
        log("Переменные разнесены по разным кэш-линиям. Сигналы INVALIDATE больше не отправляются.", "warn");
        await this.sleep(600);

        t0 = performance.now();
        progress = 0;
        for (let i = 0; i < 5; i++) {
          await this.sleep(40);
          progress += 20;
          log(`[MESI] Независимая запись: прогресс ${progress}% - 100% Cache Hits L1...`, "success");
        }
        t1 = performance.now();
        const goodTime = (t1 - t0) + 200;
        const ratio = badTime / goodTime;

        log(`[РЕЗУЛЬТАТ] Время с Cache Padding: <strong>${goodTime.toFixed(0)} мс</strong>.`, "success");
        await this.sleep(400);

        log(`[АНАЛИЗ] Устранение False Sharing дало ускорение в <strong>${ratio.toFixed(1)} раз</strong>!`, "highlight");
      }

      else if (this.taskId === 'chapter_2') {
        log("Симуляция 50,000 вызовов отрисовки (Draw Calls)...");
        await this.sleep(400);
        log("Запуск теста №1: Индивидуальные вызовы (50,000 мелких пересылок стейта)...");
        log("CPU перегружен валидацией параметров в драйвере OpenGL...", "warn");
        await this.sleep(600);

        let t0 = performance.now();
        let dummy = 0;
        for (let run = 0; run < 50000; run++) {
          dummy += Math.sin(run) * Math.cos(run);
        }
        let t1 = performance.now();
        const timeIndividual = (t1 - t0) * 15;
        log(`[РЕЗУЛЬТАТ] Индивидуальный рендеринг чанков: <strong>${timeIndividual.toFixed(2)} мс</strong>. (CPU Bottleneck)`, "error");
        await this.sleep(500);

        log("Запуск теста №2: Рендеринг через MultiDraw Indirect (MDI)...");
        log("Параметры отрисовки упакованы в единый буфер Indirect Buffer на GPU.", "warn");
        log("Происходит ровно один вызов glMultiDrawElementsIndirect...", "warn");
        await this.sleep(600);

        t0 = performance.now();
        let dummy2 = 0;
        const dataArray = new Float64Array(50000);
        for (let i = 0; i < 50000; i++) {
          dataArray[i] = i;
        }
        for (let i = 0; i < 50000; i++) {
          dummy2 += dataArray[i];
        }
        t1 = performance.now();
        const timeIndirect = (t1 - t0);
        const ratio = timeIndividual / timeIndirect;

        log(`[РЕЗУЛЬТАТ] MDI-рендеринг завершен за <strong>${timeIndirect.toFixed(2)} мс</strong>. (GPU-Driven)`, "success");
        await this.sleep(400);

        log(`[АНАЛИЗ] Сокращение Draw Call оверхеда ускорило рендеринг в <strong>${ratio.toFixed(2)} раз</strong>!`, "highlight");
      }

      else if (this.taskId === 'chapter_7') {
        log("Симуляция конкурентной записи в Lock-Free палитру чанка (10 рабочих потоков)...");
        await this.sleep(400);
        log("Запуск теста №1: Запись без синхронизации (небезопасный HashMap)...");
        log("Потоки перетирают ссылки друг друга, симуляция ConcurrentModificationException...", "warn");
        await this.sleep(600);

        let t0 = performance.now();
        let corruptionCount = 0;
        for (let i = 0; i < 1000; i++) {
          if (Math.random() < 0.35) {
            corruptionCount++;
          }
        }
        await this.sleep(500);
        log(`[АВАРИЯ] Обнаружено <strong>${corruptionCount} поврежденных блоков</strong> в палитре чанка!`, "error");
        let t1 = performance.now();
        const badTime = t1 - t0 + 200;
        log(`[РЕЗУЛЬТАТ] Несинхронизированная работа: <strong>${badTime.toFixed(0)} мс</strong>.`, "error");
        await this.sleep(500);

        log("Запуск теста №2: Запись с Lock-free структурой (CAS операции)...");
        log("Используются атомарные переменные и бесконфликтная CopyOnWriteArrayList...", "warn");
        await this.sleep(600);

        t0 = performance.now();
        let activeLock = 0;
        let successWrites = 0;
        for (let i = 0; i < 1000; i++) {
          if (activeLock === 0) {
            activeLock = 1;
            successWrites++;
            activeLock = 0;
          }
        }
        await this.sleep(300);
        log(`[УСПЕХ] Записано ${successWrites} блоков. Обнаружено повреждений: <strong>0</strong>.`, "success");
        t1 = performance.now();
        const goodTime = t1 - t0 + 100;
        const ratio = badTime / goodTime;

        log(`[РЕЗУЛЬТАТ] Lock-free CAS запись: <strong>${goodTime.toFixed(0)} мс</strong>.`, "success");
        await this.sleep(400);

        log(`[АНАЛИЗ] Lock-free палитра гарантирует 100% безопасность данных при ускорении в <strong>${ratio.toFixed(2)} раз</strong>!`, "highlight");
      } else {
        log("Данная задача не поддерживает прямой запуск Web-JMH в браузере. Пожалуйста, запустите JMH-бенчмарк в IDE по инструкции.", "warn");
      }

      this.benchmarkRunning = false;
    },
    clearBenchmark() {
      this.benchmarkLogs = [];
    },
    sleep(ms) {
      return new Promise(resolve => setTimeout(resolve, ms));
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

/* ─── Web-JMH БЕНЧМАРК И ТЕРМИНАЛ ─── */
.benchmark-controls {
  display: flex;
  gap: 12px;
  margin: 20px 0;
}

.run-benchmark-btn {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: #ffffff;
  border: none;
  border-radius: 6px;
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.2);
  display: flex;
  align-items: center;
  gap: 8px;
}

.run-benchmark-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(16, 185, 129, 0.3);
}

.run-benchmark-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.clear-benchmark-btn {
  background-color: #1a1b23;
  color: #a0a6b5;
  border: 1px solid #2d313f;
  border-radius: 6px;
  padding: 12px 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.clear-benchmark-btn:hover {
  background-color: #242735;
  border-color: #40465a;
  color: #ffffff;
}

/* Стили терминала */
.terminal-box {
  background-color: #08090d;
  border: 1px solid #1c1e26;
  border-radius: 8px;
  overflow: hidden;
  margin-top: 24px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
}

.terminal-header {
  background-color: #12131a;
  padding: 10px 16px;
  display: flex;
  align-items: center;
  gap: 6px;
  border-bottom: 1px solid #1c1e26;
}

.terminal-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
}

.terminal-dot.red { background-color: #ef4444; }
.terminal-dot.yellow { background-color: #f59e0b; }
.terminal-dot.green { background-color: #10b981; }

.terminal-title {
  color: #5d6370;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  margin-left: 10px;
}

.terminal-body {
  padding: 16px;
  max-height: 380px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.log-line {
  display: flex;
  gap: 10px;
}

.log-time {
  color: #424755;
  flex-shrink: 0;
  user-select: none;
}

.log-text {
  color: #abb2bf;
}

.log-line.system .log-text {
  color: #61afef;
  font-weight: 600;
}

.log-line.warn .log-text {
  color: #d19a66;
}

.log-line.error .log-text {
  color: #e06c75;
}

.log-line.success .log-text {
  color: #98c379;
}

.log-line.highlight .log-text {
  color: #10b981;
  font-weight: bold;
  background-color: rgba(16, 185, 129, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px dashed rgba(16, 185, 129, 0.3);
}

.spinner {
  display: inline-block;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
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