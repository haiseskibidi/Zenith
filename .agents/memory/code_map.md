# Code Map: Zenith

## Asset Map
### src/main/resources/zenith/registry
Назначение: Глобальные конфигурации и реестры данных.
- `physics.json`: Физические параметры мира.
- `easings.json`: (NEW) Реестр функций интерполяции (builtin, expressions, bezier).
- `world.json`: Настройки времени, скорости цикла дня/ночи и цветов освещения (Sun/Moon/Ambient).
- `celestial.json`: Визуальные параметры небесных тел (текстуры, масштаб, процедурные пиксельные сетки).
- `wood_types.json`: Список пород деревьев.

### src/main/resources/zenith/textures
Назначение: Полный набор оригинальных ассетов zenith.
- **block/**: Текстуры блоков (16x16).
- **item/**: Иконки предметов.
- **gui/**: Элементы интерфейса.
    - `crosshair.png`: Прицел.
- **entity/**: Текстуры сущностей.

### src/main/resources/zenith/gui
Назначение: Конфигурации игровых интерфейсов в формате JSON.
- `player_inventory.json`: Главное окно игрока.
- `hotbar.json`: Конфигурация HUD-хотбара.
- `chest.json`: Интерфейс сундуков.
- `blueprints/`: Процедурные чертежи для HUD и Дневника (SDF фигуры и матрицы).

### src/main/java/com/za/zenith/engine/graphics/ui/blueprints (NEW)
- **GraphicBlueprint.java**: Модель данных (слои, SDF фигуры, анимации).
- **BlueprintRegistry.java**: Загрузка и кэширование чертежей.
- **BlueprintRenderer.java**: Оркестратор рендеринга (SDF + Матрицы).

### com.za.zenith.entities.parkour.animation
- **EasingRegistry.java**: (NEW) Ядро системы интерполяций. Запекает формулы из JSON в LUT для O(1) производительности.
- **Keyframe.java**: (UPDATED) Модель ключевого кадра. Конвертирована в класс для поддержки рефлексии и гибкой сериализации.
- **AnimationProfile.java**: (UPDATED) Контейнер анимации. Реализует `LiveReloadable` для авто-сортировки треков при редактировании.
- **AnimationTrack.java**: (UPDATED) Отдельный канал анимации. Использует `EasingRegistry` для расчета значений.
- **ViewmodelController.java**: Парсер треков. Отвечает за применение AnimationProfile. Внедрена система `Snapshot Buffer` для хранения прошлых поз и плавной `slerp`-интерполяции (Cross-fade) при смене анимаций или предметов.

### com.za.zenith.engine.graphics.model
- **ModelNode.java**: Узел скелета. Теперь имеет два состояния вращения: `animRotation` (Euler, v1) и `animRotationQuat` (Quaternion, v2).
- **HeldItemRenderer.java**: Отрисовывает предметы в руках. Очищен от хардкода.
- **ViewmodelRenderer.java**: Центрирует блоки математически точно в сокете (socket_palm), опираясь на параметры `ViewmodelComponent`.
- **GripRegistry.java** / **GripDefinition.java**: Система Data-Driven пресетов для костей пальцев (например, `flat_sheet` для удержания блоков снизу).

## Voxel Lighting & Celestial Systems (v1.0 NEW)
### com.za.zenith.world.lighting
- **LightEngine.java**: Ядро системы освещения. Реализует BFS-распространение блочного света (от источников) и первичную заливку солнечного света (`generateInitialSunlight`) сверху вниз. Алгоритм оптимизирован для стабильности (без агрессивных проверок `isOpaqueAbove`).
- **WorldSettings.java**: Data-Driven контейнер для настроек времени и цветов освещения (загружается из `world.json`).

### com.za.zenith.engine.graphics (Zenith v2.0 Modular Architecture)
- **RenderPipeline.java**: Главный оркестратор графического конвейера. Управляет жизненным циклом кадра, переключением буферов и делегирует отрисовку специализированным системам. Вычисляет циклы лунных фаз, `isNight` состояние, направляет серебряные лучи Moonlight Shafts сквозь листву и передает недавно разрушенные блоки (`recentlyBrokenHoles`) в массив `uHiddenPositions` для моментального GPU-сокрытия во избежание мерцания граней.
- **RenderContext.java**: Синхронизация глобального UBO (`GlobalData`), включая передачу флага `isNight`. Управляет Zero-Alloc пулами объектов (матрицы, векторы JOML) и централизованным сбросом состояний шейдеров.
- **MeshRegistry.java**: Единый кэш мешей для блоков, предметов и сущностей. Устраняет дублирование генерации геометрии и управляет инвалидацией кэша.
- **ChunkRenderSystem.java**: Система отрисовки мира. (UPDATED) Реализует спаренный MDI-рендеринг, двухэтапный иерархический фрустум-куллинг, 100% Zero-Allocation и умное коалесцирующее планирование ремеша чанки (`dirtyChunksCoalesceMap` с задержкой 120 мс) для исключения просадок FPS при быстром копании.
- **EntityRenderSystem.java**: Отрисовка динамических сущностей (игроки, мобы) и вьюмоделей рук/предметов.
- **OverlayRenderSystem.java**: Оркестратор рендеринга оверлеев. Управляет последовательным вызовом независимых проходов `RenderPass` с Baseline-сбросом стейтов в `ShaderStateManager`.
- **ShaderStateManager.java**: (NEW) Централизованный менеджер состояний шейдеров. Предотвращает утечки юниформ OpenGL, сбрасывая их в дефолтные значения перед каждым проходом.
- **passes/ (NEW)**: Пакет с изолированными проходами рендеринга.
  - `RenderPass.java`: Базовый интерфейс для проходов рендеринга.
  - `BlockEntityRenderPass.java`: Отрисовка in-world крафта с точечным освещением.
  - `PersistentScarsRenderPass.java`: Рендеринг статических зарубок и сколов на блоках.
  - `HighlightRenderPass.java`: 3D-обводка блока с идеальной синхронизацией колыхания ветра.
  - `PreviewRenderPass.java`: Рендеринг силуэта устанавливаемого блока.
  - `BreakingRenderPass.java`: Рендеринг wobble-деформации и слабых точек ломаемого блока.
- **Renderer.java**: Высокоуровневая обертка для обратной совместимости. Делегирует вызовы в `RenderPipeline`.
- **Mesh.java**: Обертка над GPU-ресурсами. Поддерживает **Vertex Compression** (28 байт), классический формат и **Mesh Pooling**. Инкапсулирует `baseVertex`, `firstIndex` и `poolVersion`.
- **MeshPool.java**: Глобальный аллокатор GPU-памяти. (UPDATED) Реализует схему бесшовного Double Buffering (2x 512МБ VBO и 2x 256МБ EBO) для полного исключения морганий мира при переполнениях.
- **MultiDrawBatch.java**: Оркестратор групповой отрисовки. (UPDATED) Привязывается к конкретному графическому буферу по его индексу и строго фильтрует меши по четности их версии пула для MDI.
- **BlockHighlightRenderer.java**: Специализированный рендерер для обводки выбранного блока. Генерирует процедурную сетку на основе `VoxelShape`. Реализует стабильную отрисовку без покачивания и мерцания за счет принудительного управления униформами `uSwayOverride` и `uChunkSpawnTime`. Поддерживает контекстное освещение от соседних блоков.
- **SkyRenderer.java**: Рендерер небесных тел. Отрисовывает Солнце и Луну как билборды.
- **SkySettings.java**: Конфигурация параметров неба.

### com.za.zenith.world.chunks
- **Chunk.java (Zenith v1.1 UPDATED)**: Контейнер данных (16x512x16). Поддерживает **консолидированные меши**, синхронизированную палитру и `LOGICAL_OFFSET_Y = 128` для трансляции в логические координаты.
- **ChunkSection.java**: Подраздел чанка (16x16x16) для пространственной фильтрации.
- **ChunkMeshGenerator.java (Zenith v1.0)**: Реализует **Greedy Mesh Merging** (объединение всех секций в один VBO).

### com.za.zenith.world.lighting
- **LightEngine.java (v1.0)**: Движок освещения. Реализует **Async Stage 4** (асинхронный расчет света в отдельном потоке). 
- **WorldSettings.java**: Контейнер настроек освещения и времени.

### com.za.zenith.world
### com.za.zenith.world.World (UPDATED Zenith v2.6)
Назначение: Управление состоянием мира и сущностями.
Функции: 
- **Async Pipeline**: Управление асинхронным конвейером (`chunkGenExecutor`, `lightExecutor`). 
- **Spatial Tracking & Safe Cleanup**: Внедрена `itemSpatialMap` для $O(1)$ поиска предметов в чанках. Очистка пространственной карты при удалении или подборе предметов опирается на точное зарегистрированное поле `item.getLastChunkPos()`, исключая появление графических фантомов.
- **Zero-Allocation**: Оптимизирован цикл обновления (`update`) — устранены все аллокации векторов и объектов. 
- **L1 Chunk Cache**: Ускоренный доступ к данным чанков.

### com.za.zenith.entities.ItemEntity (UPDATED Zenith v1.0)
Назначение: Выпавшие игровые предметы.
Функции: 
- **Spatial Merging**: Слияние предметов через `world.getItemsInChunk()` ($O(1)$).
- **Physics Sleeping**: Оптимизация CPU для неподвижных предметов.
- **Despawn Timer**: Автоматическое удаление через 5 минут.
- **Zero-Allocation**: Использование пулов векторов для расчетов физики и магнита.
- **Chunk Tracking**: Автоматическое обновление позиции в пространственной карте мира при пересечении границ чанков.

### com.za.zenith.world.lighting.LightEngine (UPDATED v2.1)
Назначение: Высокопроизводительный движок 3D-освещения.
Функции:
- **Synchronous Throttled Fast-Path**: Мгновенно рассчитывает свет на главном потоке для локальных изменений (лимит 5 блоков за 20 мс) при ломании блоков игроком (в т.ч. Молотом Админа), полностью убирая визуальную задержку света. Автоматически переключается на асинхронный расчет при превышении лимита (например, взрыв TNT).
- **Primitive BFS Architecture**: Использует упакованные `long` и циклическую очередь (`long[]`) для предотвращения аллокаций.
- **3D Sunlight Flood Fill**: Реализует двухэтапный расчет солнечного света (вертикальный скан + горизонтальное растекание).
- **Light Stability**: Устранена агрессивная логика удаления света, что решило проблему фантомных теней.
- **Generation Safety**: Взаимодействует с флагом `world.isGenerating()` для пропуска расчетов во время массовых изменений ландшафта.
- **Boundary Safety**: Проверки на наличие чанков предотвращают зависания в незагруженных областях.

### com.za.zenith.engine.graphics.SkyRenderer (UPDATED)
Назначение: Система инвентаря игрока.
Функции: Добавлены методы `isFull()` и `getActiveComponent<T>()` для эффективного взаимодействия с другими системами.
Назначение: Базовый класс предмета.
Функции: Управление компонентами и индивидуальным `interaction_cooldown`.

### com.za.zenith.world.items (UPDATED)
- **ItemSearchEngine.java**: Универсальный движок фильтрации. Поддерживает поиск по локализованным именам, ID и путям.
- **Item.java**: Базовый класс предмета. Теперь содержит `Gender`, `Tags` и `defaultRarity`.
- **ItemStack.java**: Состояние предмета в инвентаре. Хранит `rarity`, `activeAffixes` и `StatContainer`. Поддерживает генерацию гендерно-зависимых имен и авто-перенос в тултипах.
- **ItemRegistry.java**: Центральный реестр предметов.
- **DataLoader.java**: Загрузка JSON. Обновлен для парсинга RPG-полей предметов (rarity, gender, tags) и Loot Tables.

### com.za.zenith.world.items.stats (NEW)
- **StatRegistry.java**: Реестр определений всех игровых характеристик.
- **RarityRegistry.java**: Реестр уровней редкости (Common...Legendary).
- **AffixRegistry.java**: Реестр префиксов/суффиксов.
- **StatContainer.java**: Хранилище и калькулятор модификаторов для Player и ItemStack. Поддерживает динамический пересчет при добавлении аффиксов.
- **RarityDefinition.java**: Модель данных редкости, включая `colorCode` для UI.
- **AffixDefinition.java**: Модель данных аффикса с условиями применимости (`applicableTo`).

### com.za.zenith.world.items.loot (NEW)
- **LootTable.java**: Определение пулов и весов выпадения предметов.
- **LootGenerator.java**: Ядро рандомизации. Реализует ролл редкости, аффиксов и предметов из пулов с учетом весов контейнера.

### com.za.zenith.engine.graphics.ui
- **NotificationManager.java**: (NEW) Центральный менеджер уведомлений. Управляет очередью подбора предметов и высокоприоритетными алертами. Реализует логику слияния (merging) одинаковых предметов и интеграцию с `hud.json`.
- **NotificationTriggers.java**: (NEW) Реестр триггеров. Содержит логику проверки прочности (`checkDurability`) и переполнения инвентаря (`onInventoryFull`).
- **UISearchBar.java**: Переиспользуемый компонент поисковой строки.

    - `FontRenderer.java`: Отрисовка текста. Реализована поддержка цветовых кодов и стилей через символ `$`.
    - `InventoryScreen.java`: Базовый класс экранов инвентаря.
    - `GUIConfig.java`: Модель конфигурации GUI. Добавлена поддержка `HUDElementConfig` для гибкой настройки HUD.
- `com.za.zenith.engine.graphics.ui.renderers`
    - `HUDRenderer.java (v2.0 UPDATED)`: Отрисовка HUD. Реализована полная поддержка `hud.json`, динамическое масштабирование имен предметов и коррекция Y-координаты по формуле `pos.y - LOGICAL_OFFSET_Y` для дебаг-панели.
    - `InventoryScreenRenderer.java`: Отрисовка инвентарей. Исправлено цветовое кодирование отрицательных статов в тултипах.

## World Generation (v4.0 UPDATED)
### com.za.zenith.world.generation.density
- **DensityFunction.java**: (NEW) Базовый интерфейс узла графа плотности.
- **DensityFunctionParser.java**: (NEW) Парсер JSON-конфигов в дерево объектов AST.
- **DensityFunctionRegistry.java**: (NEW) Хранилище загруженных JSON-элементов функций.
- **NoiseRouter.java**: (NEW) Оркестратор. Владеет корнем дерева `final_density` и предоставляет метод `getDensity(ctx)`.
- **functions/**: (NEW) Набор математических узлов (`Add`, `Mul`, `Spline`, `Terrace`, `Noise`, `Abs`, `Square` и т.д.).

### com.za.zenith.world.generation
- **BiomeDefinition.java**: (UPDATED) Модель данных биома. Уточнены веса параметров климата.
- **BiomeGenerator.java**: (UPDATED) Ядро климата. Переведено на 5-октавный фрактальный шум. Реализует честный 5D-поиск биома.
- **SimplexNoise.java**: (UPDATED) Добавлена поддержка лакунарности в `octaveNoise`.

### com.za.zenith.world.generation.pipeline.steps
- **TerrainStep.java**: (UPDATED) Генератор рельефа. Полностью переведен на использование `NoiseRouter` и Density Functions. Реализует 3D-интерполяцию плотности по сетке 4x4x4.

### com.za.zenith.world.generation.zones
- **ZoneManager.java**: (UPDATED) Управление макро-зонами. Частота смены увеличена в 10 раз, добавлен детерминизм через сортировку.
- **ZoneRegistry.java**: Реестр температурных зон.

### com.za.zenith.utils
- **SplineInterpolator.java**: Утилита для линейной интерполяции по набору произвольных точек. Используется в `SplineFunction`.

## Zenith Academy (v2.7 NEW)
### com.za.zenith.academy.chapter1
- **FalseSharingTask.java**: (NEW) Практическая задача на оптимизацию False Sharing. Два параллельных потока пишут в соседние `volatile long` поля объекта, вызывая MESI пинг-понг кэш-линий. Студент должен устранить проблему с помощью выравнивания кэш-линий (padding).

### com.za.zenith.academy.chapter2
- **DrawCallOverheadTask.java**: (NEW) Практическая задача на оверхед Draw Calls. Моделирует накладные расходы JNI-переходов на CPU при отправке множества мелких вызовов отрисовки против группировки (батчинга) в один вызов.

### com.za.zenith.academy.chapter7
- **ConcurrencyRaceTask.java**: (NEW) Практическая задача на конкурентный Data Race. Демонстрирует повреждение данных в общей палитре чанка без синхронизации и сравнивает производительность Lock-based (RW Lock) и Lock-Free (CAS на ConcurrentHashMap + AtomicInteger) подходов.

### com.za.zenith.academy.chapter9
- **LootWeightTask.java**: (NEW) Практическая задача на выборку лута по весам. Сравнивает производительность наивного линейного алгоритма за $O(N)$ и быстрого бинарного поиска по кумулятивным суммам за $O(\log N)$ на миллионах итераций.

### com.za.zenith.academy.hw1
- **BranchPredictionTask.java**: (NEW) Практическая задача на предсказание переходов. Сравнивает скорость выполнения условного ветвления на отсортированном (100% точное предсказание) и неотсортированном (постоянные Branch Mispredictions) массивах.

### com.za.zenith.academy.hw2
- **CacheLocalityTask.java**: (NEW) Практическая задача на Cache Misses и Cache Locality. Сравнивает Row-Major и Column-Major обход большой 2D-матрицы в Java, иллюстрируя работу 64-байтовых кэш-линий.

## Entity System (v5.6 NEW)
### com.za.zenith.entities.Entity (UPDATED)
Назначение: Базовый класс для всех сущностей (игрок, мобы, предметы).
Функции: Добавлена Zero-Alloc мапа компонентов (`components`), методы `getComponent()`, `addComponent()` и `hasComponent()`. Реализует интерполяцию (`prevPosition`, `prevRotation`), систему выталкивания из блоков (`Unstuck`) и общую логику перемещения `move()` с коллизиями.

### com.za.zenith.entities.components (NEW — ECS Lite)
Назначение: Компоненты для гибридной архитектуры сущностей.
- `EntityComponent.java`: Маркерный интерфейс жизненного цикла тиков для всех компонентов.
- `HealthComponent.java`: Компонент здоровья, расчета урона с броней, регенерации и стат-контейнера.
- `InventoryComponent.java`: Унифицированная обертка инвентаря сущностей.
- `AIComponent.java`: Модульный ИИ, управляющий состояниями (Wander, Search, Chase, Idle), восприятием шума и зрения.

### com.za.zenith.entities.ItemEntity (UPDATED)
Назначение: Сущность предмета, выброшенного в мир.
Функции: Реализует `Ground Lock` (отключение гравитации на земле), стабилизацию вращения (выравнивание плашмя при приземлении) и кастомную гравитацию на основе веса.

### com.za.zenith.entities.DecorationEntity (UPDATED)
Назначение: Универсальная декоративная сущность.
Функции: Физика и коллизии полностью отключены для предотвращения нежелательных перемещений при изменении ландшафта (например, в ямах для обжига). Поддерживает вращение и кастомный масштаб из JSON.

### com.za.zenith.entities.ResourceEntity (NEW)

### com.za.zenith.engine.graphics.ui.FontRenderer (UPDATED)
Назначение: Продвинутая отрисовка текста с поддержкой эффектов.
Функции: Поддерживает цветовые коды `$0-f` и динамические анимации: `$z` (Rainbow), `$g` (Glow), `$v` (Wave), `$q` (Shake). Реализует метод `wrapText` для корректного переноса длинных строк.

### com.za.zenith.world.items.Item (UPDATED)
Назначение: Базовый класс предмета.
Функции: Расширен полем `descriptionKey` для Markdown-описаний. Исправлена логика `getMiningSpeed`: инструменты с флагом `isEffectiveAgainstAll` теперь применяют полную эффективность даже к блокам без типа (стекло, бетон).

### com.za.zenith.world.items.ItemStack (UPDATED)
Назначение: Состояние предмета.
Функции: Добавлена защита от NPE (null-check) в конструкторе. Реализована поддержка бесконечной прочности (флаг `-1`).

### src/main/resources/shaders/include/post_stack.glsl (NEW)
Назначение: Унифицированный модуль AAA-постпроцессинга.
Функции: Применяет Stylized Crease AO, Atmospheric Fog, Filmic Contrast, Balanced Vibrance и Cinematic Vignette. Вызывается из `fxaa_fragment.glsl` и `passthrough_fragment.glsl` до сглаживания.

### com.za.zenith.engine.graphics.model.ViewmodelPhysics (UPDATED)
Назначение: Физический симулятор рук.
Функции: Внедрен зажим `deltaTime` (max 0.05s) и проверки `Float.isFinite()`, предотвращающие исчезновение рук при лагах. Иерархия переведена на Semi-implicit Euler интеграцию для стабильности.

### com.za.zenith.engine.graphics.Shader (UPDATED)
Назначение: Обертка над шейдерной программой.
Функции: Добавлен метод `setVector4f()` для передачи координат и интенсивности шрамов.

### com.za.zenith.world.chunks.ChunkCache (NEW)
Назначение: Локальный кэш чанков вокруг Broadphase-области движения сущности.
Функции: Позволяет считывать блоки через плоский массив `Chunk[]` за O(1) время, полностью устраняя Lock Contention и ConcurrentHashMap-поиски на главном потоке.

### com.za.zenith.world.physics (NEW)
- **AABB.java**: Модель ограничивающего параллелепипеда (AABB). (UPDATED) Добавлен статический метод `intersects` для Zero-Alloc тестов пересечений с примитивными смещениями `float`.
- **VoxelShape.java**: Форма воксельных блоков. (UPDATED) Оптимизирован метод `isFullCube()` для работы напрямую с примитивными границами AABB вместо создания векторов.

### com.za.zenith.world.World (UPDATED)
Назначение: Управление состоянием мира, сущностями и чанками.
Функции: Хранит `blockDamageMap` с объектами `BlockDamageInstance` (урон + история шрамов в `Vector4f`). Реализует логику задержки регенерации (5 сек) и плавного затухания шрамов (изменение компоненты `w`).

### com.za.zenith.world.weather (NEW)
- **WeatherManager.java**: Центральный менеджер погодных условий. Управляет переключением состояний (`CLEAR`, `RAIN`, `STORM`), рассчитывает плавную интенсивность осадков и управляет шансами смены погоды на основе DDD-конфига в `world.json`.

### com.za.zenith.engine.graphics.vfx (NEW)
- **RainRenderer.java**: Высокопроизводительный рендерер дождя. Реализует инстансинг 3000+ капель за один вызов отрисовки. Использует "Mathemagical Sync" для синхронизации позиций капель с брызгами на поверхности блоков на основе глобального времени.
- **MiningVFXManager.java**: Менеджер визуальных эффектов добычи. Рассчитывает уровень раскаления (`heatLevel`) инструментов и рук. Обеспечивает плавное остывание и универсальный сброс прогресса добычи при смене/выбрасывании предмета.

### com.za.zenith.engine.graphics (Zenith v2.0 Modular Architecture)
- **vertex.glsl**: Вершинный шейдер мира. (UPDATED) Добавлено схлопывание вершин (`gl_Position = vec4(0.0)`) скрываемых блоков (активных ломаемых блоков) на GPU, что полностью перенесло логику скрытия с фрагментной стадии и сберегло Early-Z. Медленные деления `%` и `/` заменены на быстрые побитовые сдвиги `&` и `>>`.
- **fragment.glsl**: Фрагментный шейдер мира. (UPDATED) Полностью вырезан тяжелый discard-цикл проверки скрытых блоков, возвращая Early-Z на максимальную мощность.
- **include/block_features.glsl**: Процедурные фичи вокселей. (UPDATED) Connected glass освобожден от динамического сэмплирования центрального пикселя, теперь возвращается `vec4(0.0)` напрямую.
- **include/lighting.glsl**: Освещение. (UPDATED) Проверка длины нормали переведена на быстрый `dot` вместо `length` для исключения square root из вычислений.
- **viewmodel_vertex/fragment.glsl**: Изолированные шейдеры для рук и предметов. Поддерживают процедурное раскаление (`uMiningHeat`), маскировку по весам костей и поддержку биомного тинта для блоков в руках.
- **crosshair_vertex/fragment.glsl**: Шейдеры динамического прицела. Поддерживают data-driven анимации разлета (`spreadScale`) и отдачи (`recoilScale`).

### com.za.zenith.engine.graphics.ui.crosshair (UPDATED)
- **CrosshairDefinition**: Добавлены поля `recoilScale` и `spreadScale` для настройки анимаций в JSON.
- **CrosshairManager**: Управляет состояниями. Приоритет `MINING` восстановлен для корректного отображения на интерактивных блоках (пни).
- **CrosshairRenderer**: Теперь строго использует специализированный шейдер для отрисовки матриц из JSON. Реализует анимацию разлета элементов.

### com.za.zenith.engine.graphics.model (UPDATED)
- **ViewmodelRenderer**: Теперь принимает `heat` и распределяет его между руками и инструментами. Доступен геттер для `heldItemRenderer`.
- **HeldItemRenderer**: Реализует точечную передачу уровня жара инструменту. Метод `getOrGenerateMesh` сделан публичным для системы выбора.

### com.za.zenith.world.blocks (UPDATED)
- **CarTireBlockDefinition.java**: Базовый декоративный блок покрышки. Поддерживает трансформацию в `TireWithBoard` при Shift+ПКМ досками.
- **TireWithBoardBlockDefinition.java**: Промежуточная стадия сборки стола. Трансформируется в `ScavengerTable` при Shift+ПКМ листом металла.
- **ScavengerTableBlockDefinition.java**: Блок Стола Мусорщика. Делегирует взаимодействие `ScavengerTableBlockEntity`.
- **StumpBlockDefinition.java**: Определение блока для пня (Stump).

### com.za.zenith.world.blocks.entity (UPDATED)
- **ScavengerTableBlockEntity.java**: Сущность Стола Мусорщика. Реализует `ICraftingSurface`. Хранит 9 слотов инвентаря и прогресс крафта.
- **StumpBlockEntity.java**: Сущность блока для пня (Stump).

## Animation & Locomotion System (v4.5 UPDATED)
### com.za.zenith.entities.Player (UPDATED)
Назначение: Главная сущность игрока.
Функции: Управление инвентарем, статами (голод, стамина), паркуром и анимациями. Поддерживает методы `swing()` (удар) и `interact()` (быстрый сбор/подбор). Поддерживает методы `swing()` (удар) и `interact()` (быстрый сбор/подбор).

### com.za.zenith.engine.input (UPDATED v2.0)
Назначение: Архитектура ввода на основе Domain Controllers.
- **InputManager.java**: Event Dispatcher. Хранит глобальный контекст, централизованно отслеживает фазы нажатий на основе плоских Zero-Alloc буферов и делегирует события специализированным хэндлерам.
- **InputAction.java**: (NEW) Перечисление всех абстрактных логических действий игрока, связывающее настройки SettingsManager с геймплейным кодом.
- **handlers/SystemInputHandler.java**: (NEW) Обработка системных горячих клавиш (F, F3, F9, Z, Q).
- **handlers/MovementInputHandler.java**: (NEW) Расчет вектора движения, прыжков, физики камеры (pitch/yaw) и взаимодействия с паркуром.
- **handlers/InventoryInputHandler.java**: (NEW) Логика UI инвентаря: курсор, Drag & Drop, слияние стаков (Shift+клик), NappingGUI.
- **handlers/InteractionInputHandler.java**: (NEW) Взаимодействие с миром: ЛКМ (добыча, атака), ПКМ (установка блоков, еда, лутбоксы).
- **handlers/HotbarInputHandler.java**: (NEW) Выбор слотов хотбара 1-9 и копирование предметов разработчика.
- **controllers/CombatController.java**: (NEW) Контроллер сражений. Подписывается на `PlayerAttackEntityEvent` и обрабатывает урон по врагам.
- **controllers/InteractionController.java**: (NEW) Контроллер взаимодействия. Подписывается на `PlayerPickupEvent` и `BlockLeftClickEvent` для обработки подбора лута и кастомного поведения блоков.

### com.za.zenith.engine.event (NEW v2.0)
Назначение: Потокобезопасная Zero-Alloc реактивная шина игровых событий.
- **Event.java**: Базовый маркерный интерфейс для событий.
- **EventBus.java**: Высокопроизводительная шина событий, реализующая шаблон Publish-Subscribe с потокобезопасным хранением слушателей.
- **events/PlayerAttackEntityEvent.java**: Событие атаки сущности игроком.
- **events/PlayerPickupEvent.java**: Событие подбора предмета/ресурса игроком. Поддерживает динамическое поглощение (consumption).
- **events/BlockLeftClickEvent.java**: Событие левого клика по блоку. Поддерживает динамическое поглощение.

### com.za.zenith.engine.input.MiningController (NEW)
Назначение: Контроллер процесса добычи блоков.
Функции: Управляет таймерами (cooldown, breakingDelay), генерирует Weak Spots, рассчитывает прогресс разрушения. Автоматически выбирает тип анимации (`swing` vs `interact`) на основе прочности блока. Передает данные для отрисовки прокси-блока в `Renderer`.

### com.za.zenith.engine.core.GameLoop (UPDATED)
Назначение: Главный цикл. Добавлена поддержка переключения в режим Студии (F8) и изоляция обновлений.

## Physical Viewmodel System (v5.0 NEW)
### com.za.zenith.engine.graphics.Mesh (UPDATED)
Назначение: Низкоуровневая обертка над VBO/VAO.
Функции: Отрисовка, очистка ресурсов. Добавлены методы `getMin()` и `getMax()` для вычисления Bounding Box меша в реальном времени.

### com.za.zenith.engine.graphics.model.ViewmodelRenderer (UPDATED)
Назначение: Рендерер рук игрока.
Функции: Рендерит иерархия костей. Делегирует отрисовку удерживаемых предметов классу `HeldItemRenderer`.

### com.za.zenith.engine.graphics.model.HeldItemRenderer (NEW)
Назначение: Специализированный рендерер для предметов и блоков в руках.
Функции: Управляет трансформациями (смещение, поворот, масштаб) относительно костей кисти. Реализует динамическое прижатие блоков к ладони на основе их геометрии.

### com.za.zenith.world.items.ItemMeshGenerator (UPDATED)
Назначение: Генератор 3D мешей из 2D текстур.
Функции: Использует PCA (Principal Component Analysis) для автоматического определения ориентации предмета и точки хвата. Генерирует вертикально выровненные меши.

### com.za.zenith.engine.graphics.model.ViewmodelPhysics
Назначение: Физический симулятор для рук и предметов.
Функции: Решает дифференциальные уравнения 2-го порядка (пружина-масса-демпфер) для расчета инерции и веса.

### com.za.zenith.engine.graphics.model.ViewmodelController
Назначение: Менеджер скелетных анимаций.
Функции: Применяет AnimationProfile к костям скелета (поддержка парсинга треков `nodeName:track`), поддерживает наслоение (blending) нескольких анимаций и динамическое масштабирование времени (`baseMiningCooldown`).

### com.za.zenith.engine.graphics.model.ViewmodelMeshGenerator
Назначение: Генератор воксельных мешей для костей.
Функции: Создает оптимизированную геометрию на основе кубов, описанных в JSON.

### com.za.zenith.engine.graphics.model.ik (NEW)
- **FABRIKSolver.java**: Математическое ядро IK. Реализует алгоритм FABRIK с поддержкой Pole Targets для контроля сгибов.
- **IKChain.java**: Контейнер для цепочки костей, хранит целевые позиции и ограничения.
- **constraints/IKConstraint.java**: Базовый интерфейс для ограничений суставов.
- **constraints/HingeConstraint.java**: Ограничение вращения по одной оси (для локтей и коленей).

### com.za.zenith.engine.graphics.ui.editor.animation (UPDATED)
- **EditorIKManager.java**: Управляет несколькими IK-цепями одновременно. Поддерживает `autoSetup` (руки/ноги) и запекание поз в ключи.
- **AnimationEditorRenderer.java**: Визуализирует цели IK (желтые) и Pole Targets (синие).
- **TransformController.java**: Теперь поддерживает прямой ввод в IK-таргеты при перемещении эффекторов (кистей/стоп).

### com.za.zenith.engine.graphics.model.ModelRegistry
Назначение: Реестр скелетных моделей.
Функции: Загружает и хранит ViewmodelDefinition из ресурсов.

## Data Management (Asset Manager v2.5 UPDATED)
### com.za.zenith.engine.resources.loaders
- **ActionDataLoader.java**: Загрузка определений действий игрока.
- **BlockDataLoader.java**: Загрузка параметров блоков и компонентов.
- **ItemDataLoader.java**: Загрузка предметов и ItemComponents.
- **RecipeDataLoader.java**: Унифицированный загрузчик всех типов рецептов (Stump, Napping, etc.).
- **JournalCategoryLoader/EntryLoader.java**: Загрузка структуры Дневника Выжившего.
- **EntityDataLoader.java**: Загрузка определений сущностей.
- **GUIDataLoader.java**: Загрузка конфигураций интерфейсов.

### com.za.zenith.world.chunks
- **ChunkMeshGenerator.java**: (v3.0 UPDATED) Генератор геометрии. Внедрена поддержка `FLAG_TRANSLUCENT` для разделения пасов. Устранены критические NPE при обращении к соседям.
- **Chunk.java**: (v1.1 UPDATED) Добавлена защита от повреждения палитры (`Palette Corruption Guard`) с автоматическим восстановлением индексов.

### com.za.zenith.engine.input
- **MiningController.java**: (v2.1 UPDATED) Контроллер добычи. Добавлена защита от обработки "воздуха" (автоматический сброс при срубании дерева), предотвращающая вылеты JIT-компилятора.

## Inventory System (NEW)
### com.za.zenith.world.inventory.ItemInventory
Назначение: Реализация `IInventory` для предметов-контейнеров (рюкзаки, мешочки).
Функции: Позволяет предмету (`ItemStack`) хранить внутри себя другие предметы, поддерживает динамический размер из `BagComponent`, запрещает вложенность рюкзаков.

### com.za.zenith.utils.math
- **EasingFunctions.java**: (NEW) Статическая библиотека стандартных математических функций сглаживания.

### com.za.zenith.engine.graphics.ui
- **DevInspectorScreen.java**: (v3.2 UPDATED) Профессиональный редактор ресурсов (F9). Поддерживает Deep Traversal вложенных структур, персистентность состояния, авто-выбор интерполяций и автоматическое сохранение в JSON.
- **EditorHistoryManager.java**: (NEW) Глобальный менеджер истории (Undo/Redo) для Инспектора.
- **ScrollPanel.java**: Универсальный компонент прокрутки.

## Particle & Shard System (v1.0 NEW)
### com.za.zenith.world.particles
- **Particle.java**: Базовый абстрактный класс для всех визуальных эффектов. Хранит позицию, скорость, 2D-вращение (roll), прозрачность и логику затухания. Не имеет физических коллизий для максимальной стабильности.
- **ShardParticle.java**: Реализация классического воксельного осколка. Использует технику Snippet UV (вырезка 4x4 пикселя из текстуры материала) и билбординг. Поддерживает тинтовку биома.
- **ParticleManager.java**: Глобальный синглтон управления частицами. 
    - `spawnImpact`: Создает контекстные частицы при ударе по Weak Spot. Количество осколков динамически масштабируется от `miningDamage`.
    - `spawnShatter`: Эффект полного разрушения блока.
    - Реализует умный резолв текстур для технических стадий (felling logs) и гибридную логику для дерна (`grass_block`).

### com.za.zenith.engine.graphics
- **ColorProvider.java**: Single Source of Truth для биомных цветов. Содержит статические методы доступа к цветам травы и листвы.
- **ParticleRenderer.java**: Высокопроизводительный инстанс-рендерер.
 Отрисовывает квадратные билборды, ориентированные на камеру. Передает параметры времени, текстурных слоев и UV-смещений в шейдеры.

## Asset Map
