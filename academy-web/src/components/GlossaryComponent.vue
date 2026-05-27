<template>
  <div class="glossary-container">
    <!-- Шапка словаря -->
    <div class="glossary-header">
      <h2 class="glossary-main-title">Словарь абстракций</h2>
      <p class="glossary-desc">
        Демистифицируем сложные низкоуровневые термины, архитектурные паттерны и математические трюки. 
        Никакой душной академической теории — только простые жизненные аналогии, ASCII-схемы и живой код.
      </p>
    </div>

    <!-- Панель управления (Поиск и Фильтры) -->
    <div class="control-panel">
      <!-- Поисковая строка -->
      <div class="search-box">
        <span class="search-icon">🔍</span>
        <input 
          type="text" 
          v-model="searchQuery" 
          class="search-input" 
          placeholder="Поиск по терминам или описанию..."
        />
        <button v-if="searchQuery" @click="searchQuery = ''" class="clear-btn">✕</button>
      </div>

      <!-- Фильтры категорий -->
      <div class="category-filters">
        <button 
          v-for="cat in categories" 
          :key="cat.id"
          @click="activeCategory = cat.id"
          :class="['filter-btn', { 'filter-active': activeCategory === cat.id }]"
        >
          <span class="filter-dot" :style="{ backgroundColor: cat.color }"></span>
          {{ cat.name }}
        </button>
      </div>
    </div>

    <!-- Сетка терминов -->
    <TransitionGroup name="list" tag="div" class="terms-grid" v-if="filteredTerms.length > 0">
      <div 
        v-for="term in filteredTerms" 
        :key="term.id"
        class="term-card"
        :class="{ 'card-expanded': expandedCards[term.id] }"
      >
        <!-- Верхняя часть карточки: Категория и название -->
        <div class="card-top">
          <span 
            class="category-badge" 
            :style="{ 
              color: getCategoryColor(term.category), 
              borderColor: getCategoryColor(term.category, 0.3),
              backgroundColor: getCategoryColor(term.category, 0.08)
            }"
          >
            {{ getCategoryName(term.category) }}
          </span>
          <span class="term-id-pill">#{{ term.id }}</span>
        </div>

        <h3 class="term-title">
          <span class="term-abbr">{{ term.abbr }}</span>
          <span class="term-full" v-if="term.full">({{ term.full }})</span>
        </h3>

        <!-- Определение (Простое и понятное) -->
        <p class="term-definition">{{ term.definition }}</p>

        <!-- Аналогия "На пальцах" -->
        <div class="analogy-box">
          <span class="analogy-title">💡 На пальцах:</span>
          <p class="analogy-text">{{ term.analogy }}</p>
        </div>

        <!-- Интерактивная зона визуализации (Схема или Код) -->
        <div class="visual-section" v-if="expandedCards[term.id]">
          <div class="visual-header">
            <span class="visual-label">
              {{ term.visualType === 'image' ? '📊 Схема Zenith Architecture' : term.visualType === 'code' ? '💻 Java / GLSL Код' : '📊 ASCII Схема' }}
            </span>
            <button v-if="term.visualType !== 'image'" @click="copyVisualText(term.visualContent)" class="copy-btn" title="Копировать в буфер">
              📋
            </button>
          </div>
          <div class="visual-image-container" v-if="term.visualType === 'image'">
            <img :src="`/assets/glossary/${term.id.toLowerCase()}.png`" :alt="term.abbr" class="visual-image" />
          </div>
          <pre class="visual-content" v-else><code>{{ term.visualContent }}</code></pre>
        </div>

        <!-- Кнопка разворачивания схемы/кода -->
        <div class="card-actions">
          <button 
            @click="toggleCard(term.id)" 
            class="toggle-details-btn"
          >
            {{ expandedCards[term.id] ? '▲ Скрыть схему' : term.visualType === 'image' ? '▼ Показать наглядную схему' : '▼ Показать ASCII-схему / Код' }}
          </button>
        </div>
      </div>
    </TransitionGroup>

    <!-- Заглушка, если ничего не найдено -->
    <div v-else class="empty-state">
      <div class="empty-icon">🛸</div>
      <h3 class="empty-title">Ничего не найдено</h3>
      <p class="empty-desc">
        По запросу «<span class="query-highlight">{{ searchQuery }}</span>» в категории 
        «{{ getCategoryName(activeCategory) }}» совпадений не обнаружено. Попробуйте сбросить фильтры.
      </p>
      <button @click="resetFilters" class="reset-btn">Сбросить фильтры</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GlossaryComponent',
  data() {
    return {
      searchQuery: '',
      activeCategory: 'all',
      categories: [
        { id: 'all', name: 'Все темы', color: '#3b82f6' },
        { id: 'hardware', name: 'Hardware', color: '#f59e0b' },
        { id: 'engine', name: 'Engine', color: '#06b6d4' },
        { id: 'math', name: 'Math', color: '#a855f7' }
      ],
      expandedCards: {
        DOD: true,
        MDI: true,
        AST: false,
        IK: false,
        CTM: false,
        AABB: false,
        CAS: false,
        SIMD: false,
        LOD: false,
        MESI: false,
        POOL: false,
        DMA: false,
        FRUSTUM: false
      },
      terms: [
        {
          id: 'DOD',
          abbr: 'DOD',
          full: 'Data-Oriented Design',
          category: 'engine',
          definition: 'Подход к проектированию ПО, ориентированный на данные. Вместо разбрасывания объектов в куче памяти (классическое ООП), мы упаковываем данные в плоские массивы. CPU обожает последовательное чтение: L1/L2 кэши заполняются полезными байтами без пустот, исключая дорогостоящие промахи мимо кэша.',
          analogy: 'Вместо того чтобы отправлять 100 курьеров за 100 отдельными письмами на разные склады, мы кладём все письма в одну коробку на одном столе. CPU берёт коробку и читает всё за один присест.',
          visualType: 'image',
          visualContent: `OOP (Ужасно для кэша CPU):
[Объект1 (x,y,z, цвет, имя)] ──(ссылка в куче)──> [Объект2 (x,y,z, цвет, имя)]
(Данные разбросаны по RAM хаотично, CPU ловит Cache Miss)

DOD (Идеально для кэша CPU):
Массив X: [ x1, x2, x3, x4, x5 ] ─── Читается за 1 такт!
Массив Y: [ y1, y2, y3, y4, y5 ]
Массив Z: [ z1, z2, z3, z4, z5 ] ─── Все данные лежат в памяти подряд.`
        },
        {
          id: 'MDI',
          abbr: 'MDI',
          full: 'MultiDraw Indirect',
          category: 'engine',
          definition: 'Технология рендеринга, позволяющая отрисовать тысячи разных 3D-моделей всего ОДНИМ вызовом отрисовки (Draw Call). Инструкции о том, ЧТО, ОТКУДА и СКОЛЬКО рисовать, лежат прямо в видеопамяти GPU. CPU не тратит время на общение с драйвером для каждой модели.',
          analogy: 'Вместо того чтобы звонить строителю 1000 раз и говорить: "положи кирпич 1", "положи кирпич 2", вы отправляете ему один текстовый файл со всеми координатами, и он молча делает всю работу.',
          visualType: 'image',
          visualContent: `Обычный рендеринг:
 CPU ─── Draw(Меш 1) ───> GPU (Загрузка шины)
 CPU ─── Draw(Меш 2) ───> GPU (Задержки драйвера)
 CPU ─── Draw(Меш 3) ───> GPU (CPU перегружен)

MultiDraw Indirect (MDI):
 CPU ─── Запиши команды отрисовки в буфер GPU RAM ───> GPU RAM
 CPU ─── Вызови glMultiDrawElementsIndirect() ────────> GPU
 GPU читает инструкции из своего буфера напрямую и рисует миллионы мешей!`
        },
        {
          id: 'AST',
          abbr: 'AST',
          full: 'Abstract Syntax Tree',
          category: 'engine',
          definition: 'Абстрактное синтаксическое дерево. Представление структуры исходного кода в виде древовидной структуры данных. Компиляторы, интерпретаторы и парсеры математических формул превращают сырой текст программы в AST, чтобы легко проводить оптимизацию, вычислять приоритеты операций и компилировать код.',
          analogy: 'Разбор предложения в школе: подлежащее, сказуемое, дополнение. Вы превращаете линейный текст в иерархическую структуру отношений между словами.',
          visualType: 'image',
          visualContent: `Выражение: "a = 5 + x"

           [ = ] (Оператор присваивания)
          /     \\
    [ a ]         [ + ] (Оператор сложения)
                 /     \\
           [ 5 ]         [ x ]
           
(Дерево позволяет парсеру вычислить сначала "5 + x", а затем записать результат в "a")`
        },
        {
          id: 'IK',
          abbr: 'IK',
          full: 'Inverse Kinematics',
          category: 'math',
          definition: 'Инверсная (обратная) кинематика. Математический метод вычисления углов суставов скелета на основе финального положения конечной точки. Вместо вращения плеча, предплечья и кисти (прямая кинематика), вы просто двигаете кисть к ручке двери, а алгоритм сам вычисляет нужные углы в суставах.',
          analogy: 'Вы хотите взять чашку со стола. Вы не думаете: "так, повернуть плечо на 12 градусов, локоть на 45...". Вы просто тянете руку к чашке, а ваш мозг (алгоритм IK) сам сгибает суставы.',
          visualType: 'image',
          visualContent: `Forward Kinematics (Вращаем углы):
[Плечо] (угол θ1) ──> [Локоть] (угол θ2) ──> [Кисть] (финал)

Inverse Kinematics (Математический тригонометрический расчёт):
[Плечо]
   \\
    \\  <- Вычисляется алгоритмом (FABRIK / CCD)
     \\
   [Локоть]
       \\
        \\
         ▼ [Целевой маркер (Чашка на столе)]`
        },
        {
          id: 'CTM',
          abbr: 'CTM',
          full: 'Connected Textures Method',
          category: 'engine',
          definition: 'Метод сопряжения текстур. Позволяет блокам одного типа (например, стёклам, книжным полкам или блокам земли в Minecraft) динамически сливаться границами с соседями. Алгоритм проверяет окружение блока по 8 сторонам и выбирает нужный спрайт из атласа.',
          analogy: 'Головоломка-пазл. Вы смотрите на соседние кусочки, чтобы понять, нужна ли сглаженная рамка или стыковка с картинкой справа.',
          visualType: 'image',
          visualContent: `Без CTM (Скучная сетка):     С CTM (Красивые бесшовные окна):
 ┌───┬───┬───┐               ┌───────────┐
 │ G │ G │ G │               │ G       G │  (Границы рисуются
 ├───┼───┼───┤               │           │   только на внешнем
 │ G │ G │ G │               │ G       G │   контуре группы)
 └───┴───┴───┘               └───────────┘`
        },
        {
          id: 'AABB',
          abbr: 'AABB',
          full: 'Axis-Aligned Bounding Box',
          category: 'math',
          definition: 'Ограничивающий параллелепипед, выровненный по осям координат. Самая дешёвая и популярная форма для расчёта коллизий в 3D. Так как его грани строго параллельны мировым осям X, Y и Z, проверка пересечения двух AABB сводится к 6 простым сравнениям чисел.',
          analogy: 'Вместо того чтобы обсчитывать сложную модельку дракона с миллионом чешуек при столкновении со стрелой, мы надеваем на дракона невидимую коробку от холодильника и проверяем, влетела ли стрела в коробку.',
          visualType: 'image',
          visualContent: `// Быстрая проверка пересечения двух AABB в 3D
public boolean intersects(AABB other) {
    return (this.minX <= other.maxX && this.maxX >= other.minX) &&
           (this.minY <= other.maxY && this.maxY >= other.minY) &&
           (this.minZ <= other.maxZ && this.maxZ >= other.minZ);
} // Всего 6 быстрых проверок - CPU в восторге!`
        },
        {
          id: 'CAS',
          abbr: 'CAS',
          full: 'Compare-And-Swap',
          category: 'hardware',
          definition: 'Атомарная процессорная инструкция ("сравни и обменяй"). Базовый кирпичик Lock-Free многопоточности. Поток считывает переменную, вычисляет новое значение и просит процессор обновить её. Но процессор сделает это только если значение переменной не изменилось с момента чтения. Никаких блокировок потоков!',
          analogy: 'Вы пишете цену на доске. Прежде чем стереть её и написать новую, вы проверяете: "та ли это цена, которую я видел секунду назад?". Если кто-то уже стёр её и вписал другую, вы отступаете и пересчитываете всё заново.',
          visualType: 'image',
          visualContent: `// Пример реализации Lock-free счетчика в Java на основе CAS
public class AtomicCounter {
    private final AtomicInteger val = new AtomicInteger(0);

    public void increment() {
        int current;
        do {
            current = val.get(); // 1. Прочитали текущее значение
        } while (!val.compareAndSet(current, current + 1)); 
        // 2. Атомарно сравниваем с current и пишем current + 1. 
        // Если кто-то успел вклиниться, compareAndSet вернет false, и мы идем на новый круг.
    }
}`
        },
        {
          id: 'SIMD',
          abbr: 'SIMD',
          full: 'Single Instruction, Multiple Data',
          category: 'hardware',
          definition: 'Одиночный поток инструкций, множественный поток данных. Аппаратная фича процессоров (векторные инструкции SSE, AVX, NEON), позволяющая применить одну операцию (например, сложение) сразу к массиву чисел (вектору) за ОДИН такт процессора. Ускоряет физику и графику в разы.',
          analogy: 'Вместо того чтобы учитель 30 раз говорил каждому ученику по отдельности: "Открой учебник", он один раз громко говорит всему классу: "Откройте учебники!". Все делают действие одновременно.',
          visualType: 'image',
          visualContent: `Обычный CPU (SISD):            SIMD CPU (Векторный регистр AVX):
  A1 + B1 = C1 (1 такт)          [ A1, A2, A3, A4 ] (Вектор A)
  A2 + B2 = C2 (2 такт)                  +          (1 такт CPU!)
  A3 + B3 = C3 (3 такт)          [ B1, B2, B3, B4 ] (Вектор B)
  A4 + B4 = C4 (4 такт)                  =
                                 [ C1, C2, C3, C4 ] (Результат)`
        },
        {
          id: 'LOD',
          abbr: 'LOD',
          full: 'Level of Detail',
          category: 'engine',
          definition: 'Уровень детализации. Оптимизационная техника в 3D-графике. Чем дальше объект находится от виртуальной камеры игрока, тем более упрощённый 3D-меш (с меньшим количеством полигонов) мы рисуем. Для объектов на горизонте меш заменяется на плоскую картинку (билборд).',
          analogy: 'Когда вы смотрите на человека вблизи, вы видите пуговицы на его рубашке. Если он стоит в 500 метрах, для вас он просто цветное пятнышко. Видеокарте глупо рисовать пуговицы на расстоянии в полкилометра.',
          visualType: 'image',
          visualContent: `Игрок ──👁️ (Камера)

  Расстояние < 10м  ──> LOD 0: 3D Модель дерева (15 000 полигонов, листья шелестят)
  Расстояние 10-50м ──> LOD 1: Средний меш (2 000 полигонов, без мелких веток)
  Расстояние > 50м  ──> LOD 2: Грубый меш (200 полигонов)
  Расстояние > 200м ──> LOD 3: Flat Billboard (2 полигона, обычная плоская текстура)`
        },
        {
          id: 'MESI',
          abbr: 'MESI',
          full: 'Modified, Exclusive, Shared, Invalid',
          category: 'hardware',
          definition: 'Протокол когерентности кэшей процессора. Гарантирует, что если 8 ядер CPU работают с одной областью памяти через свои локальные L1-кэши, они не будут читать устаревшие данные. Любое изменение переменной на одном ядре мгновенно переводит эту строку кэша на остальных ядрах в статус Invalid.',
          analogy: 'У вас и вашего друга есть копии блокнота. Если вы вычёркиваете запись и пишете новую, вы звоните другу и говорите: "У тебя старая запись, сотри её (Invalid), перепиши у меня!".',
          visualType: 'image',
          visualContent: `[ Ядро CPU 1 ] ── L1 Cache [ X = 42 ] (Modified) ──┐
                                                           │ (MESI ШИНА синхронизации)
[ Ядро CPU 2 ] ── L1 Cache [ X = -- ] (Invalid) ◄────────┘
(Как только Ядро 1 поменяло X, кэш Ядра 2 инвалидировался. 
При попытке прочитать X, Ядро 2 будет обязано запросить свежие данные)`
        },
        {
          id: 'POOL',
          abbr: 'Thread Pool',
          full: 'Worker Threads',
          category: 'hardware',
          definition: 'Пул потоков. Создание системного потока ОС — это очень медленная операция, требующая выделения 1MB памяти под стек и переключения контекста ядра. Вместо создания потоков на каждую мелкую задачу, мы при старте создаём фиксированную команду "спящих" потоков, которые берут задачи из общей очереди.',
          analogy: 'Вместо того чтобы нанимать нового строителя на укладку каждого отдельного кирпича и увольнять его через 5 секунд, вы нанимаете бригаду из 4 человек на весь день. Они стоят у конвейера и берут кирпичи по мере поступления.',
          visualType: 'image',
          visualContent: `// Суть пула потоков на пальцах:
public class SimpleThreadPool {
    private final Queue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
    
    public SimpleThreadPool(int threadCount) {
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                while (true) {
                    Runnable task = taskQueue.poll();
                    if (task != null) {
                        task.run(); // Забираем задачу и выполняем
                    }
                }
            }).start();
        }
    }
}`
        },
        {
          id: 'DMA',
          abbr: 'DMA',
          full: 'Direct Memory Access',
          category: 'hardware',
          definition: 'Прямой доступ к памяти. Аппаратный механизм, позволяющий сетевым картам, звуковым чипам и SSD читать/писать оперативную память напрямую, без участия центрального процессора. CPU выдает команду контроллеру DMA и может выполнять другие вычисления, пока терабайты текстур летят в ОЗУ.',
          analogy: 'Директор фирмы (CPU) даёт секретарю (DMA) поручение переложить папки из архива на стол. Директор продолжает работать с клиентами, а не таскает папки лично.',
          visualType: 'image',
          visualContent: `Без DMA (CPU забит пересылкой данных):
 [ SSD Диск ] ──(Байт за байтом)──> [ CPU ] ──(Байт за байтом)──> [ RAM ОЗУ ]
 (CPU тратит 100% времени на бессмысленное копирование)

С DMA (CPU свободен):
 [ CPU ] ─── "Скопируй 500MB из SSD по адресу RAM 0x7F" ───> [ Контроллер DMA ]
 [ CPU ] занимается физикой и рендерингом, пока...
 [ SSD Диск ] ─────────────(Пересылает данные напрямую)─────────────> [ RAM ОЗУ ]`
        },
        {
          id: 'FRUSTUM',
          abbr: 'Frustum Culling',
          full: 'Pyramid Culling',
          category: 'math',
          definition: 'Отсечение объектов по пирамиде видимости камеры. Камера игрока видит мир не на 360 градусов, а в форме усечённой четырёхгранной пирамиды (Frustum). Алгоритм проверяет пересечение границ объектов (их AABB) с плоскостями пирамиды и мгновенно отбрасывает всё, что находится сзади или сбоку.',
          analogy: 'Когда вы идёте по улице, вы не видите то, что происходит у вас на затылке. Игровая видеокарта поступает так же: всё, что не попадает в поле зрения глаз, полностью игнорируется.',
          visualType: 'image',
          visualContent: `                 Плоскость Frustum (Левая)
                       /
                      /      * [Объект 1: Овечка] (РИСУЕМ)
     👁️ (Камера) ────*
    [Игрок]           \\      * [Объект 2: Дерево] (РИСУЕМ)
                       \\
                 Плоскость Frustum (Правая)
  -------------------------------------------------------------
    [Объект 3: Огромный Замок] (Сзади игрока - НЕ РИСУЕМ, ОТСЕКАЕМ)`
        }
      ]
    };
  },
  computed: {
    filteredTerms() {
      const query = this.searchQuery.toLowerCase().trim();
      return this.terms.filter(term => {
        // Фильтр по категории
        if (this.activeCategory !== 'all' && term.category !== this.activeCategory) {
          return false;
        }
        // Поиск по ключевому слову
        if (query) {
          const inAbbr = term.abbr.toLowerCase().includes(query);
          const inFull = term.full ? term.full.toLowerCase().includes(query) : false;
          const inDef = term.definition.toLowerCase().includes(query);
          const inAnalogy = term.analogy.toLowerCase().includes(query);
          return inAbbr || inFull || inDef || inAnalogy;
        }
        return true;
      });
    }
  },
  methods: {
    getCategoryColor(catId, opacity = 1) {
      const cat = this.categories.find(c => c.id === catId);
      const color = cat ? cat.color : '#3b82f6';
      if (opacity === 1) return color;
      
      // Преобразуем hex в rgba для прозрачности
      const r = parseInt(color.slice(1, 3), 16);
      const g = parseInt(color.slice(3, 5), 16);
      const b = parseInt(color.slice(5, 7), 16);
      return `rgba(${r}, ${g}, ${b}, ${opacity})`;
    },
    getCategoryName(catId) {
      const cat = this.categories.find(c => c.id === catId);
      return cat ? cat.name : catId;
    },
    toggleCard(id) {
      this.expandedCards[id] = !this.expandedCards[id];
    },
    resetFilters() {
      this.searchQuery = '';
      this.activeCategory = 'all';
    },
    async copyVisualText(text) {
      try {
        await navigator.clipboard.writeText(text);
        alert('Визуализация скопирована в буфер обмена!');
      } catch (err) {
        console.error('Не удалось скопировать текст:', err);
      }
    }
  }
};
</script>

<style scoped>
.glossary-container {
  display: flex;
  flex-direction: column;
  gap: 32px;
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Шапка */
.glossary-header {
  margin-bottom: 8px;
}

.glossary-main-title {
  color: #ffffff;
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 10px 0;
  letter-spacing: -0.5px;
  background: linear-gradient(135deg, #ffffff 30%, #3b82f6 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.glossary-desc {
  color: #798299;
  font-size: 15px;
  line-height: 1.6;
  margin: 0;
  max-width: 800px;
}

/* Панель управления */
.control-panel {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  background: rgba(17, 18, 22, 0.4);
  border: 1px solid #22252e;
  padding: 16px 20px;
  border-radius: 12px;
  backdrop-filter: blur(12px);
}

/* Строка поиска */
.search-box {
  position: relative;
  flex: 1;
  min-width: 280px;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 14px;
  color: #4b5269;
  font-size: 14px;
}

.search-input {
  width: 100%;
  background-color: #0b0c10;
  border: 1px solid #22252e;
  color: #ffffff;
  border-radius: 8px;
  padding: 11px 40px 11px 40px;
  font-size: 14px;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.search-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 16px rgba(59, 130, 246, 0.25);
  background-color: #0f1015;
}

.clear-btn {
  position: absolute;
  right: 12px;
  background: transparent;
  border: none;
  color: #4b5269;
  cursor: pointer;
  padding: 4px;
  font-size: 12px;
  transition: color 0.15s ease;
}

.clear-btn:hover {
  color: #ffffff;
}

/* Кнопки категорий */
.category-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-btn {
  background-color: #0d0e12;
  border: 1px solid #22252e;
  border-radius: 6px;
  color: #798299;
  padding: 8px 14px;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 0.2s ease;
}

.filter-btn:hover {
  border-color: #3b82f6;
  color: #ffffff;
  background-color: #12131a;
}

.filter-active {
  background-color: rgba(59, 130, 246, 0.08) !important;
  border-color: #3b82f6 !important;
  color: #ffffff !important;
  box-shadow: 0 0 10px rgba(59, 130, 246, 0.1);
}

.filter-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

/* Сетка терминов */
.terms-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.term-card {
  background: rgba(22, 23, 29, 0.45);
  border: 1px solid rgba(59, 130, 246, 0.08);
  border-radius: 12px;
  padding: 24px;
  backdrop-filter: blur(16px);
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: relative;
  overflow: hidden;
}

.term-card:hover {
  border-color: rgba(59, 130, 246, 0.2);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.category-badge {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.75px;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid;
}

.term-id-pill {
  color: #3c4254;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
}

/* Заголовки */
.term-title {
  margin: 0;
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.term-abbr {
  color: #ffffff;
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.25px;
}

.term-full {
  color: #555d70;
  font-size: 14px;
  font-weight: 500;
}

/* Описание и логика "на пальцах" */
.term-definition {
  color: #c9ccd6;
  font-size: 14.5px;
  line-height: 1.6;
  margin: 0;
}

.analogy-box {
  background: rgba(11, 12, 16, 0.5);
  border-radius: 8px;
  border: 1px solid rgba(16, 185, 129, 0.15);
  padding: 12px 16px;
}

.analogy-title {
  color: #10b981;
  font-size: 13px;
  font-weight: 600;
  display: block;
  margin-bottom: 4px;
}

.analogy-text {
  color: #94a3b8;
  font-size: 13.5px;
  line-height: 1.5;
  margin: 0;
  font-style: italic;
}

/* Секция визуализации (код или ASCII) */
.visual-section {
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  border: 1px solid #22252e;
  overflow: hidden;
  background-color: #07080b;
  margin-top: 6px;
}

.visual-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: #111216;
  padding: 8px 16px;
  border-bottom: 1px solid #1c1e26;
}

.visual-label {
  color: #798299;
  font-size: 11.5px;
  font-family: 'JetBrains Mono', monospace;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.copy-btn {
  background: transparent;
  border: none;
  cursor: pointer;
  color: #798299;
  font-size: 13px;
  padding: 2px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.copy-btn:hover {
  transform: scale(1.15);
  color: #ffffff;
}

.visual-image-container {
  width: 100%;
  background-color: #07080b;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 12px;
  border-top: 1px solid #1c1e26;
}

.visual-image {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  display: block;
}

.visual-content {
  margin: 0;
  padding: 16px;
  overflow-x: auto;
}

.visual-content code {
  color: #06b6d4;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre;
  display: block;
  text-align: left;
}

.card-actions {
  display: flex;
  justify-content: flex-start;
  margin-top: 4px;
}

.toggle-details-btn {
  background: transparent;
  border: none;
  color: #3b82f6;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  transition: color 0.15s ease;
}

.toggle-details-btn:hover {
  color: #60a5fa;
  text-decoration: underline;
}

/* Пустой результат */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 60px 20px;
  background: rgba(17, 18, 22, 0.4);
  border: 1px dashed #22252e;
  border-radius: 12px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.empty-title {
  color: #ffffff;
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.empty-desc {
  color: #798299;
  font-size: 14.5px;
  line-height: 1.5;
  margin: 0 0 20px 0;
  max-width: 480px;
}

.query-highlight {
  color: #3b82f6;
  font-weight: 600;
}

.reset-btn {
  background-color: #3b82f6;
  border: none;
  color: #ffffff;
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.reset-btn:hover {
  background-color: #2563eb;
}

/* Анимация переходов списка */
.list-enter-active,
.list-leave-active {
  transition: all 0.3s ease;
}
.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>
