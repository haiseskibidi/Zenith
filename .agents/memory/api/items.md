# Zenith API: Items, Registries & RPG System

Этот документ содержит полные технические спецификации систем идентификации, регистрации и RPG-составляющей (предметы, статы, лут).

---

## 1. Core API & Registries

### Идентификаторы и Динамические ID (Identifiers & Dynamic IDs)
Любой контент в игре (блоки, предметы, анимации, рецепты, UI) идентифицируется через класс `Identifier`.
Движок Zenith использует систему **динамических числовых ID**. Это значит:
- В JSON-файлах поле `"id"` является **необязательным** (и крайне не рекомендуется для нового контента).
- Числовые ID назначаются автоматически при загрузке на основе порядка файлов в `.index` и их `Identifier`.
- Это исключает коллизии (конфликты номеров) и упрощает добавление новых предметов.

Никогда не используй магические числа или сырые строки там, где ожидается ID.
```java
// Правильно
Identifier myId = Identifier.of("zenith:grass_block");
int numericalId = BlockRegistry.getId(myId); // Получение динамического ID в рантайме
```

### Авто-регистрация (Classes-holders)
Для быстрого доступа к контенту в Java не нужно вручную искать его в реестре. Классы `Blocks.java`, `Items.java`, `PrefabManager.java` заполняются автоматически через рефлексию (имена полей мапятся на JSON).
```java
// Blocks.java
public static Block grass_block; // Автоматически заполнится блоком "zenith:grass_block"
```

---

## 2. Предметы (`items/` и Компоненты)
Предметы **НЕ наследуются** (никаких `ToolItem` или `FoodItem`). Все предметы — это базовый класс `Item`, а их поведение определяется компонентами.

**Базовый пример (`stone_axe.json`):**
```json
{
  "identifier": "zenith:stone_axe",
  "texture": "zenith/textures/item/stone_axe.png",
  "visualScale": 1.2, // Увеличивает размер 3D-модели в руке
  "weight": 1.5,      // Влияет на инерцию и пружинную физику
  "components": {
    "zenith:tool": {
      "type": "axe",
      "efficiency": 1.5,
      "durability": 120
    },
    "zenith:thermal": { // Позволяет предмету раскаляться
      "initialTemperature": 20.0,
      "burnThreshold": 60.0
    }
  }
}
```

**Другие важные компоненты:**
- **Рюкзаки и экипировка:**
```json
"components": {
  "zenith:bag": { "slots": 18 }, // Делает предмет контейнером (ItemInventory)
  "zenith:equipment": { "slot": "accessory", "strict": true } // Разрешает надевать в спец. слоты GUI
}
```
- **Еда (`type: food`):** В базовых полях указывается `"nutrition": 2.0` и `"saturation": 1.0`.
- **Топливо:** `"zenith:fuel": { "fuelAmount": 100.0 }`.

---

## 3. RPG System & Items
Система предметов построена на компонентах (ItemComponents) и динамических реестрах характеристик.

### Реестры (Registries)
- `StatRegistry`: Регистрация всех игровых параметров.
- `RarityRegistry`: Определение уровней редкости (цвета, количество слотов аффиксов).
- `AffixRegistry`: База данных префиксов и суффиксов.

### Item JSON Structure
Пример расширенного предмета:
```json
{
  "identifier": "zenith:scrap_axe",
  "rarity": "zenith:uncommon",
  "gender": "masculine",
  "tags": ["zenith:tools", "zenith:melee"],
  "components": {
    "lootbox": {
      "loot_table": "zenith:equipment",
      "opening_time": 1.0,
      "rarity_weights": { "zenith:common": 40, "zenith:uncommon": 60 }
    }
  }
}
```

### Принципы работы
1. **Гендерная локализация**: Используйте суффиксы `.m`, `.f`, `.n` в `ru_ru.json` для автоматического склонения аффиксов.
2. **Data-Driven Tooltips**: Тултипы собираются автоматически на основе имеющихся в `ItemStack` компонентов и статов. Максимальная ширина — 280px.
3. **Loot Tables**: Находятся в `resources/zenith/registry/loot_tables/`. Поддерживают вложенные пулы и веса.
4. **Stats Calculation**: Итоговый стат предмета = `Base (из JSON)` + `Modifiers (от аффиксов)`.
5. Formatting: Для текста в коде и JSON используются Minecraft-style коды через символ `$`.

---

## 4. Item Search Engine
Универсальный движок фильтрации в `ItemSearchEngine.java`.
- Поддерживает поиск по локализованным именам, ID и путям.
- Интегрирован с `UISearchBar` в Developer Panel.
- Позволяет искать предметы на двух языках одновременно.
