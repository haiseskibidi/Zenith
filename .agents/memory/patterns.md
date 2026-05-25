# Architectural Patterns & Blueprints: Zenith

Этот документ содержит **эталонные реализации (Blueprints)** для основных подсистем движка Zenith. При реализации нового функционала **СТРОГО ЗАПРЕЩЕНО** отклоняться от этих паттернов. ИИ должен копировать структуру из этого файла и адаптировать только бизнес-логику.

---

## 1. BlockEntity и Логика Тиков (ITickable)
**Правило:** Любой блок, требующий обновления каждый кадр или хранящий сложные данные (энергию, инвентарь), должен использовать `BlockEntity`.

### Blueprint: Создание базового BlockEntity
```java
package com.za.zenith.world.blocks.entity;

import com.za.zenith.world.BlockPos;

public class MyCustomBlockEntity extends BlockEntity implements ITickable {
    
    public MyCustomBlockEntity(BlockPos pos) {
        super(pos);
    }

    @Override
    public void update(float deltaTime) {
        // КРИТИЧЕСКИ ВАЖНО: Всегда проверяйте world на null перед логикой
        if (world == null) return;

        // Ваша логика здесь...
    }
}
```

---

## 2. Поиск соседей (Neighbor Searching)
**Правило:** **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО** хардкодить массивы смещений типа `int[][] offsets = {{0,1,0}, {1,0,0}}`. Всегда используйте enum `Direction`.

### Blueprint: Обход соседних блоков (на примере передачи энергии)
```java
import com.za.zenith.utils.Direction;
import com.za.zenith.world.BlockPos;

// ... внутри метода update(float deltaTime) ...

for (Direction dir : Direction.values()) {
    BlockPos neighborPos = dir.offset(this.pos);
    BlockEntity neighborBE = world.getBlockEntity(neighborPos);
    
    if (neighborBE instanceof IEnergyStorage storage) {
        // Взаимодействие с соседом
        if (storage.canReceive()) {
            storage.receiveEnergy(10.0f, false);
        }
    }
}
```

---

## 3. Компоненты Предметов (Item Components)
**Правило:** В Zenith **нет** наследования предметов (нет классов `ToolItem`, `FoodItem`). Все предметы — это экземпляры базового класса `Item`. Логика строится на композиции через `ItemComponent`.

### Blueprint: Проверка наличия компонента
**КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО:** `if (item instanceof ToolItem)`
**ПРАВИЛЬНО:**
```java
import com.za.zenith.world.items.component.ToolComponent;

// ... где-то в логике взаимодействия ...
ToolComponent toolComp = itemStack.getItem().getComponent(ToolComponent.class);

if (toolComp != null) {
    float efficiency = toolComp.getEfficiency();
    // Логика инструмента
}
```

### Blueprint: Создание нового компонента
```java
package com.za.zenith.world.items.component;

// 1. Создать класс компонента
public class MyNewComponent implements ItemComponent {
    public final float myValue;
    
    // Поля должны совпадать с ключами в JSON
    public MyNewComponent(float myValue) {
        this.myValue = myValue;
    }
}

// 2. JSON предмета будет выглядеть так:
// "components": {
//   "zenith:my_new": { "myValue": 10.5 }
// }
```

---

## 3.5. Компоненты Блоков (Block Components) и ModularBlockEntity
**Правило:** Запрещено наследовать классы блоков (например `ChestBlockDefinition`). Вся уникальная логика (инвентарь, крафт, рубка) должна реализовываться через `BlockComponent` и `ModularBlockEntity`.

### Blueprint: Data-Driven Блок с компонентами
Вместо создания `MyCustomMachineBlockEntity` используется универсальный `ModularBlockEntity`, который парсит JSON.
```json
{
  "identifier": "zenith:my_machine",
  "components": {
    "container": { "slots": 9, "isCustomLayout": false },
    "crafting_surface": { "gridSize": 3 }
  }
}
```

### Blueprint: Взаимодействие с компонентом блока
```java
BlockDefinition def = BlockRegistry.getBlock(blockType);
ContainerComponent container = def.getComponent(ContainerComponent.class);

if (container != null) {
    ModularBlockEntity mbe = (ModularBlockEntity) world.getBlockEntity(pos);
    // Работаем с инвентарем через mbe
    ItemStack stack = mbe.getStackInSlot(0);
}
```

---

## 4. Система Регистрации (Data-Driven Registries)
**Правило:** Никаких хардкодных числовых ID (`int id = 5`). Движок назначает их динамически. Везде используется `Identifier`.

### Blueprint: Получение блока или предмета из кода
**ПРАВИЛЬНО:** Использовать авто-генерируемые поля-холдеры.
```java
// Эти поля в Blocks.java и Items.java заполняются автоматически через рефлексию!
// Имя переменной должно точно совпадать с путем Identifier (без namespace).
Block grass = Blocks.grass_block; 
Item axe = Items.stone_axe;
```

**ПРАВИЛЬНО:** Если нужно получить по строке в рантайме:
```java
Identifier id = Identifier.of("zenith:grass_block");
int numericalId = BlockRegistry.getId(id); // Для рендеринга или чанков
Block block = BlockRegistry.get(id);       // Для логики
```

---

## 5. Работа с Метаданными (Metadata Masking)
**Правило:** Метаданные блока (1 байт) хранят несколько флагов. Нельзя просто сравнивать метаданные целиком (`if (meta == 1)`), нужно использовать **битовые маски**.

### Blueprint: Маскирование направления и флагов
```java
// 0x07 (0000 0111) - Маска для извлечения направления (0-5)
int direction = metadata & 0x07; 

// 0x80 (1000 0000) - Маска флага BIT_NATURAL (используется для деревьев)
boolean isNatural = (metadata & 0x80) != 0;

// Если нужно добавить флаг:
int newMetadata = direction | 0x80;
```

---

## 6. UI и Инвентари (Data-Driven GUI)
**Правило:** GUI верстается исключительно в JSON (папка `gui/`). В Java создается только экран-контроллер.

### Blueprint: Создание экрана на базе JSON
```java
public class MyCustomScreen extends InventoryScreen {
    
    public MyCustomScreen(Player player) {
        // Указываем Identifier JSON файла из папки gui/
        super(Identifier.of("zenith:my_custom_gui"), player);
    }

    @Override
    protected void onInit() {
        super.onInit(); // Автоматически загрузит JSON и создаст Layout
        // Дополнительная настройка, если нужна
    }
}
```

---

## 7. Система Частиц (Visual Particles)
**Правило:** В Zenith используется классическая воксельная система частиц. Частицы — это «призраки», они **не имеют физических коллизий** для обеспечения максимальной производительности и стабильности.

### Blueprint: Создание новой частицы
```java
package com.za.zenith.world.particles;

import org.joml.Vector3f;

public class MyCustomParticle extends Particle {
    public MyCustomParticle(Vector3f pos, Vector3f vel, float life) {
        super(pos, vel, life);
        this.scale = 0.5f; // Настройка размера
    }

    @Override
    public void update(float deltaTime) {
        // Базовая физика (движение + затухание) уже в super.update()
        super.update(deltaTime);
        
        // Кастомная логика (например, изменение цвета или вращение)
        this.roll += rollVelocity * deltaTime;
    }
}
```

### Blueprint: Спавн частицы
```java
ParticleManager.getInstance().addParticle(new MyCustomParticle(pos, velocity, 2.0f));
```

---

## 8. Сущности и Коллизии (Entity & Interpolation)
**Правило:** Все сущности должны поддерживать интерполяцию для плавности 144Hz+ при 20Hz физике.

### Blueprint: Создание новой сущности
```java
package com.za.zenith.entities;

import org.joml.Vector3f;

public class MyNewEntity extends Entity {
    public MyNewEntity(Vector3f pos) {
        super(pos, 0.5f, 0.5f); // Позиция, ширина, высота
    }

    @Override
    public void update(float deltaTime) {
        // 1. Всегда сохраняем старую позицию для интерполяции в НАЧАЛЕ тика
        this.prevPosition.set(this.position);
        this.prevRotation.set(this.rotation);

        // 2. Логика движения с коллизиями
        // move(velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);
    }
}
```

## 9. Настройки и Абстрактный Ввод (Dynamic Settings Pattern)
**Правило:** Запрещено хардкодить клавиши GLFW в логике игры. Запрещено кэшировать настройки FOV или мыши при старте игры.

### Blueprint: Проверка нажатия клавиши
```java
// НЕПРАВИЛЬНО: if (window.isKeyPressed(GLFW_KEY_W))
// ПРАВИЛЬНО:
if (inputManager.isActionPressed("move_forward")) {
    moveVector.y = 1;
}
```

### Blueprint: Динамическое применение настроек
```java
// Внутри метода, который вызывается каждый кадр (например, update или render)
float baseSens = 0.002f; // Базовый множитель
float currentSens = SettingsManager.getInstance().getMouseSensitivity() * baseSens;
float deltaYaw = rotVec.y * currentSens;
```
## 12. Модульная загрузка и Hot Reload (Asset Management Pattern)
**Правило:** Загрузка данных должна быть делегирована специализированным загрузчикам. Объекты данных должны поддерживать горячую перезагрузку без перезапуска игры.

### Blueprint: Реализация LiveReloadable
```java
public class MyData implements LiveReloadable {
    private transient String sourcePath;
    @Override public String getSourcePath() { return sourcePath; }
    @Override public void setSourcePath(String path) { this.sourcePath = path; }
}
```

### Blueprint: Синхронизация при Hot Reload (Reflection)
```java
// При обновлении базового объекта (например, Item)
if (oldObject != null && oldObject != newObject) {
    // Находим все ссылки на старый объект и обновляем их
    for (var stack : inventory.getAllStacks()) {
        if (stack.getItem() == oldObject) {
            Field field = ItemStack.class.getDeclaredField("item");
            field.setAccessible(true);
            field.set(stack, newObject);
        }
    }
}
```

## 13. Разделение прозрачности (Rendering Passes Pattern)
**Правило:** Не путать геометрическую прозрачность (куллинг) с визуальной (блендинг).

- **Transparent (FLAG_TRANSPARENT)**: "Блок не заполняет весь куб". Используется в `ChunkMeshGenerator` для отрисовки граней соседей. Рисуется в **Opaque Pass** с записью в Depth Buffer.
- **Translucent (FLAG_TRANSLUCENT)**: "Блок как стекло/вода". Рисуется в **Translucent Pass** (в самом конце) без записи в Depth Buffer для правильного смешивания (alpha blending).

### Blueprint: JSON конфигурация
```json
// Плита (неполный блок, но не стекло)
{ "transparent": true, "translucent": false } 

// Стекло (и неполный, и прозрачный)
{ "transparent": true, "translucent": true }
```

---

## 14. Safety Contracts & Regression Prevention (v2.5 UPDATED)
...
Чтобы избежать регрессий при изменении смежных систем (например, WorldGen и Mechanics), необходимо следовать контрактам:

### 10.1. Контракт "Натуральности" (Naturalness Contract)
- **Правило**: Любой блок, генерируемый миром (деревья, растительность, руды), ОБЯЗАН иметь флаг `BIT_NATURAL`.
- **Реализация**: Избегать прямого `new Block(id)`. Использовать фабрики или методы-помощники.
- **Зависимость**: Система `Treecapitator` и `Decay` зависят от этого флага.

### 10.2. Валидация данных (Data Integrity)
- **Именование**: Все поля в JSON должны следовать `snake_case`. В Java-классах использовать `@SerializedName` для явной привязки.
- **Fail-fast**: При загрузке ресурсов через `DataLoader` выбрасывать предупреждения в лог, если обнаружены неизвестные поля или битые ссылки (`next_stage` на несуществующий блок).

### 10.3. Визуальная диагностика (Debug Visibility)
- **Инспекция**: Любой критический флаг (`NATURAL`, `TINTED`, `STRENGTH`) должен быть виден в дебаг-панели (F3/F9).
- **Принцип**: Если механика не работает, причина должна быть видна в свойствах объекта, а не только в коде.

---

## 11. Модульные системы рендеринга (Modular Rendering Systems)
**Правило:** Логика отрисовки различных типов объектов (чанков, сущностей, оверлеев) должна быть разделена на независимые системы, управляемые через `RenderPipeline`.

### Blueprint: Специализированная система рендеринга
```java
public class MyNewRenderSystem {
    private final MultiDrawBatch batch;

    public MyNewRenderSystem(MeshPool pool) {
        this.batch = new MultiDrawBatch(pool);
    }

    public void render(SceneState state, RenderContext ctx) {
        // 1. Подготовка данных (Culling, Sorting)
        // 2. Настройка шейдера через ctx
        // 3. Наполнение батча (batch.put)
        // 4. Отрисовка (batch.render)
    }
}
```

---

## 12. Zero-Allocation Rendering
**Правило:** В методах отрисовки, вызываемых 60+ раз в секунду, **ЗАПРЕЩЕНО** использовать оператор `new`. Используйте пулы объектов из `RenderContext`.

### Blueprint: Использование пулов в рендеринге
```java
public void renderEntity(Entity entity, RenderContext ctx, float alpha) {
    // Получаем временную матрицу из пула
    Matrix4f modelMatrix = ctx.getMatrix(); 

    // Выполняем расчеты
    modelMatrix.identity()
               .translate(entity.getInterpolatedPosition(alpha))
               .rotate(entity.getInterpolatedRotation(alpha));

    // Передаем в шейдер
    shader.setMatrix4f("model", modelMatrix);

    // Пул автоматически сбрасывается в начале следующего кадра в RenderContext
}
```