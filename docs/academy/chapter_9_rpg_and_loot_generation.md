# Глава 9. RPG-система, Генератор Лута & Лингвистический Движок

После низкоуровневой графики и многопоточности пора поговорить о том, что делает игру по-настоящему глубокой и реиграбельной — об RPG-механиках.

В этой главе разберём, как построить продвинутую RPG-систему (Role-Playing Game — ролевая игра) на движке **Zenith** полностью **Data-Driven способом** (весь контент в JSON (JavaScript Object Notation — текстовый формат обмена данными), а в Java-коде — только рельсы), как устроены таблицы выпадения лута с математическими весами и как научить компьютер генерировать уникальные предметы с грамматически согласованными русскими названиями типа «*Ржавая титановая кирка*» или «*Острое кустарное копье*».

---

## 1. Парадигма 100% Data-Driven: Реестры Характеристик

Новички в геймдеве часто совершают одну и ту же ошибку: они хардкодят RPG-статы игрока прямо в коде:
```java
// Типичный хардкод — так делать не стоит
public class Player {
    public float speed;
    public int strength;
    public float stamina;
    public float dexterity; // Сноровка
}
```
Если завтра геймдизайнер захочет добавить новый стат (например, «Удачу» или «Радиационное сопротивление»), программисту придется лезть в самый центр класса игрока, дописывать поля, перекомпилировать код и ломать сейвы.

В **Zenith** характеристики игрока и предметов полностью вынесены в **JSON-конфиги**!
Каждая характеристика — это отдельный файл в папке `registry/stats/`, например `dexterity.json` (Сноровка):

```json
{
  "identifier": "zenith:dexterity",
  "baseValue": 1.0,
  "translationKey": "stat.zenith.dexterity"
}
```

### Как это работает в Java?
При старте игры движок сканирует папку `registry/stats/` с помощью `DataLoader` и регистрирует их в `StatRegistry`.
Для хранения статов у любого живого существа или предмета есть класс-контейнер `StatContainer`:

```java
// com.za.zenith.world.items.stats.StatContainer.java
public class StatContainer {
    // Быстрая мапа: ID характеристики -> Её текущее значение
    private final Map<Identifier, Float> values = new ConcurrentHashMap<>();
    private final Map<Identifier, Float> modifiers = new ConcurrentHashMap<>();

    public float get(Identifier statId) {
        float base = values.getOrDefault(statId, 0.0f);
        float mod = modifiers.getOrDefault(statId, 0.0f);
        return base + mod; // Финальное значение стата
    }

    public void addModifier(Identifier statId, float value) {
        modifiers.put(statId, modifiers.getOrDefault(statId, 0.0f) + value);
    }
}
```
Мы можем добавлять, удалять или переименовывать любые характеристики на лету, просто редактируя JSON-файлы. Java-код работает как универсальные рельсы, которые лишь возят данные в мапах.

---

## 2. Математика Таблиц Лута: Колесо Фортуны с Весами

Когда игрок взламывает старый ящик или побеждает монстра, игра должна выдать награду. Для этого используется система **Loot Tables (Таблицы выпадения лута)**.
Разберём типичный JSON таблицы лута:

```json
{
  "rolls": 2,
  "pools": [
    {
      "weight": 100,
      "item": "zenith:rusty_pipe"
    },
    {
      "weight": 30,
      "item": "zenith:scrap_metal"
    },
    {
      "weight": 5,
      "item": "zenith:titanium_ingot",
      "auto_affix": true
    }
  ]
}
```

### Как работает алгоритм выбора по весам?
Представь колесо фортуны в казино. Но вместо одинаковых секторов у каждого предмета сектор имеет свою ширину, пропорциональную его весу (`weight`):
* Общая сумма всех весов на колесе: $100 + 30 + 5 = 135$.
* Сектор трубы: $100 / 135 \approx 74\%$ шанса.
* Сектор лома: $30 / 135 \approx 22\%$ шанса.
* Сектор редкого титанового слитка: $5 / 135 \approx 4\%$ шанса.

Алгоритм генерации выбирает случайное число от $0.0$ до $1.0$, умножает его на сумму весов ($135$) и бежит по списку предметов, вычитая их веса из полученного числа, пока оно не станет меньше или равно нулю:

```java
// Простейший алгоритм выбора из пула по весам
public ItemStack rollLoot(LootTable table) {
    int totalWeight = table.getPools().stream().mapToInt(Pool::getWeight).sum();
    double randomValue = Math.random() * totalWeight;

    for (Pool pool : table.getPools()) {
        randomValue -= pool.getWeight();
        if (randomValue <= 0.0) {
            ItemStack stack = new ItemStack(ItemRegistry.get(pool.getItem()));
            if (pool.isAutoAffix()) {
                applyRandomAffixes(stack); // Генерируем случайную редкость и аффиксы
            }
            return stack;
        }
    }
    return null;
}
```

---

## 3. Процедурный Лингвистический Движок: Борьба с языковым кринжем

Если в английском языке генерация названий предметов тривиальна:
`"Rusty"` (Prefix) + `"Iron"` (Material) + `"Sword"` (Item) = `"Rusty Iron Sword"`
то в русском языке нас подстерегает огромная лингвистическая стена — **согласование родов и окончаний**!

Если склеить слова в лоб, получится жуткий кринж:
*«Ржавый титановый кирка»*, *«Тяжелый кустарное лопата»* или *«Острое стальной заточка»*. 
Игрок сразу поймет, что перевод делала дешевая нейросеть, и закроет игру.

### Как Zenith решает эту проблему?
Мы научили игру понимать грамматический род предметов! 
1. Для каждого предмета в локализации (`ru_ru.json`) прописывается его род с помощью невидимого тега.
2. В классе `ItemStack` при генерации названия из аффиксов и базы запускается **Лингвистический Движок**:

```java
// com.za.zenith.world.items.ItemStack.java
public String getDisplayName() {
    StringBuilder name = new StringBuilder();
    Identifier rarity = getRarity();
    RarityDefinition rarityDef = RarityRegistry.get(rarity);

    // Цвет редкости (Minecraft-style форматирование цвета)
    name.append(rarityDef.getColorCode());

    // 1. Определяем род базового предмета по суффиксу его перевода
    String baseName = I18n.get(item.getTranslationKey());
    Gender gender = detectGender(baseName);

    // 2. Добавляем префикс (аффикс) с правильным окончанием!
    for (Identifier affixId : activeAffixes) {
        AffixDefinition affix = AffixRegistry.get(affixId);
        if (affix.type() == AffixDefinition.Type.PREFIX) {
            String prefix = I18n.get(affix.translationKey());
            name.append(applyGenderToAdjective(prefix, gender)).append(" ");
        }
    }

    // 3. Добавляем название самого предмета
    name.append(baseName);

    // 4. Добавляем суффикс
    for (Identifier affixId : activeAffixes) {
        AffixDefinition affix = AffixRegistry.get(affixId);
        if (affix.type() == AffixDefinition.Type.SUFFIX) {
            name.append(" ").append(I18n.get(affix.translationKey()));
        }
    }

    return name.toString();
}
```

### Алгоритм согласования окончаний прилагательных:
Метод `applyGenderToAdjective` считывает прилагательное в мужском роде (например, «*Ржавый*», «*Тяжелый*») и на лету заменяет его окончание в зависимости от рода предмета:

```java
public static String applyGenderToAdjective(String adjective, Gender gender) {
    if (gender == Gender.MASCULINE) return adjective; // Оставляем "Ржавый"
    
    // Получаем корень слова, отбрасывая окончания "-ый", "-ий"
    String root = adjective.substring(0, adjective.length() - 2);
    
    if (gender == Gender.FEMININE) {
        // "Ржавый" -> "Ржавая", "Тяжелый" -> "Тяжелая"
        return root + (adjective.endsWith("ий") ? "ая" : "ая");
    } else if (gender == Gender.NEUTER) {
        // "Ржавый" -> "Ржавое", "Тяжелый" -> "Тяжелое"
        return root + (adjective.endsWith("ий") ? "ое" : "ое");
    }
    return adjective;
}
```
**Результат:** когда на титановую кирку (женский род) вешается префикс «Ржавый» и суффикс «Мусорщика», движок на лету собирает грамматически корректное название: **«Ржавая титановая кирка Мусорщика»**. Выглядит естественно и воспринимается как написанное человеком.

[WIDGET:LOOT_GENERATOR]

---

## 4. Физическое Вскрытие Лутбоксов: Забываем про 2D-меню

В большинстве игр открытие сундуков — это нажатие кнопки, после которого открывается 2D-окошко инвентаря. В **Zenith** мир материален, поэтому вскрытие контейнеров — физический и тактильный процесс, происходящий прямо в игровом мире.

В JSON-определение блока-сундука (например, `rusty_car.json` — ржавая машина) добавляется компонент `zenith:loot_container`:

```json
{
  "block": "zenith:rusty_car",
  "components": {
    "zenith:loot_container": {
      "loot_table": "zenith:chests/car_trunk",
      "interaction_time": 3.0,
      "required_tool_tag": "zenith:crowbar_tools"
    }
  }
}
```

### Как устроена логика вскрытия?
1. Когда игрок зажимает ПКМ, наведясь на ржавую машину, `InputManager` блокирует движение и запускает таймер вскрытия.
2. Игрок начинает проигрывать циклическую скелетную анимацию взлома (например, рывки фомкой).
3. **Влияние характеристик**: Скорость вскрытия напрямую зависит от стата **Сноровка (Dexterity)** из `StatContainer` игрока:
   $$t_{final} = \frac{t_{base}}{1.0 + \text{Dexterity} \times 0.1}$$
   Чем выше Сноровка игрока, тем быстрее он вскрывает заржавевшие замки.
4. **Инструментальный замок**: Если в поле `required_tool_tag` указан тег фомки, а игрок пытается открыть багажник голыми руками — шкала прогресса не сдвинется с места, а на HUD (Head-Up Display — интерфейс поверх экрана) вылезет тактильное предупреждение: *«Требуется Фомка!»*.
5. По завершении таймера блок не просто исчезает — крышка багажника физически отлетает со звуком скрежета металла, а из контейнера вылетают 3D-предметы с процедурными аффиксами, притягиваясь к игроку.

---

## 5. Stamina Integration: Гравитация не прощает усталости

### Живая аналогия
Попробуйте подтянуться на турнике и повисеть. Первые пять секунд — всё нормально. Через пятнадцать — предплечья наливаются свинцом. Через тридцать — пальцы разжимаются сами собой.

Эту механику мы перенесли в физику паркура Zenith. Повиснув на уступе блока или карабкаясь вверх, персонаж не может висеть там вечно.

Логика полностью **Data-Driven**: из JSON-конфигураций действий игра считывает стоимость удержания на уступе за секунду. Шкала стамины начинает таять. Как только она достигает нуля — пальцы персонажа разжимаются, и гравитация отправляет его вниз.

### Код «на пальцах»
Посмотрим, как это выглядит изнутри Java-кода системы выносливости:

```java
// Java-система интеграции стамины в паркур
public class PlayerStaminaSystem {
    private float stamina = 100.0f; // Текущий запас сил игрока (0.0 - 100.0)
    private final float maxStamina = 100.0f;

    /**
     * Вызывается каждый физический тик игрока
     */
    public void updateStamina(Player player, ActionManager actionManager, float deltaTime) {
        PlayerState state = player.getMovementState();
        
        // 1. Проверяем, находится ли игрок в состоянии виса или карабканья по уступу
        if (state == PlayerState.HANGING || state == PlayerState.CLIMBING) {
            // Извлекаем стоимость физического действия из JSON файлов конфигурации
            ActionDefinition action = actionManager.getAction("parkour_hold");
            float staminaCost = action.getStaminaCostPerSecond(); 
            
            // Тратим стамину каждую секунду
            stamina = Math.max(0.0f, stamina - staminaCost * deltaTime);
            
            // 2. Срыв: если силы закончились — падаем
            if (stamina <= 0.0f) {
                player.fallFromLedge(); 
            }
        } else {
            // Если мы твердо стоим на земле, стамина плавно восстанавливается
            stamina = Math.min(maxStamina, stamina + deltaTime * 20.0f);
        }
    }
}
```

---

## 6. World Damage & Scars: Система повреждений блоков

Система сохраняет информацию о повреждениях блоков, используя вектор `Vector4f` (x, y, z — точка удара, w — интенсивность шрама). Для корректного рендеринга без Z-fighting (мерцания пересекающихся текстур) используется `glPolygonOffset`.

```java
public class WorldDamageSystem {
    private final Map<BlockPos, BlockDamageInstance> damageMap = new ConcurrentHashMap<>();
    
    public static class BlockDamageInstance {
        public final Vector4f scarData; 
        public long lastHitTime;
        
        public BlockDamageInstance(Vector4f data) {
            this.scarData = data;
            this.lastHitTime = System.currentTimeMillis();
        }
    }

    public void updateDamageInstances(float deltaTime) {
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<BlockPos, BlockDamageInstance> entry : damageMap.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockDamageInstance instance = entry.getValue();
            
            // Запускаем регенерацию шрама только спустя 5 секунд покоя
            if (currentTime - instance.lastHitTime > 5000) {
                float currentIntensity = instance.scarData.w;
                
                float newIntensity = currentIntensity - deltaTime * 0.1f; 
                
                if (newIntensity <= 0.0f) {
                    damageMap.remove(pos);
                } else {
                    instance.scarData.w = newIntensity;
                }
            }
        }
    }

    public void renderScars(GLRenderer renderer) {
        // Включаем смещение полигонов (Z-offset)
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(-1.0f, -1.0f);
        
        for (Map.Entry<BlockPos, BlockDamageInstance> entry : damageMap.entrySet()) {
            renderer.drawDamageDecal(entry.getKey(), entry.getValue().scarData);
        }
        
        // Обязательно выключаем смещение, чтобы не сломать рендер остального мира
        glDisable(GL_POLYGON_OFFSET_FILL);
    }
}
```

---

## 🛠️ Практическое задание: Эффективный генератор RPG-лута

При больших объемах генерации лута наивный выбор с весами за O(N) начинает заметно тормозить процессор. Давай реализуем и сравним на бенчмарке наивный алгоритм и быстрый бинарный поиск по префиксным суммам за O(log N).

[TASK:chapter_9]

---

Всё это вместе создаёт ощущение выживания в осязаемом мире. В следующей главе разберём, как предметы притягиваются к игроку магнитным полем, и рассмотрим физику spring-систем рук.
