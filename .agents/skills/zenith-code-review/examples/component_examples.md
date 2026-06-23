# Шаблоны реализации компонентов (Component Implementation Patterns)

Этот справочник содержит эталонные примеры реализации функционала предметов и блоков с использованием компонентного подхода вместо наследования.

---

## 1. Компоненты предметов (ItemComponent)

Все предметы в Zenith являются экземплярами базового класса `Item`. Их функционал расширяется через прикрепление компонентов.

### Шаблон 1.1: Создание класса компонента
Класс должен реализовывать интерфейс `ItemComponent`. Конструкторы и поля должны мапиться на структуру JSON.

```java
package com.za.zenith.world.items.component;

public class ToolComponent implements ItemComponent {
    private final String toolType;
    private final float efficiency;
    private final int harvestLevel;

    public ToolComponent(String toolType, float efficiency, int harvestLevel) {
        this.toolType = toolType;
        this.efficiency = efficiency;
        this.harvestLevel = harvestLevel;
    }

    public String getToolType() { return toolType; }
    public float getEfficiency() { return efficiency; }
    public int getHarvestLevel() { return harvestLevel; }
}
```

### Шаблон 1.2: JSON-описание предмета
Компонент подключается в секции `"components"` в JSON-файле предмета (например, `stone_axe.json`).

```json
{
  "name": "stone_axe",
  "display_name": "Каменный топор",
  "textures": {
    "layer0": "stone_axe.png"
  },
  "tags": ["zenith:axes"],
  "components": {
    "zenith:tool": {
      "toolType": "axe",
      "efficiency": 4.0,
      "harvestLevel": 1
    }
  }
}
```

### Шаблон 1.3: Опрос компонента в Java-коде
**Запрещено**: `if (item instanceof ToolItem)`
**Правильно**:

```java
ItemStack heldStack = player.getInventory().getSelectedItemStack();
if (heldStack != null) {
    ToolComponent tool = heldStack.getItem().getComponent(ToolComponent.class);
    if (tool != null && tool.getToolType().equals("axe")) {
        float speedMultiplier = tool.getEfficiency();
        // Применение скорости добычи...
    }
}
```

---

## 2. Компоненты блоков (BlockComponent)

Блоки объявляются через `BlockDefinition`. Их поведение настраивается с помощью `BlockComponent` и `ModularBlockEntity`.

### Шаблон 2.1: Создание компонента для обтёсывания блока
```java
package com.za.zenith.world.blocks.component;

import com.google.gson.annotations.SerializedName;
import com.za.zenith.world.BlockPos;
import com.za.zenith.world.World;
import com.za.zenith.entities.Player;
import com.za.zenith.world.items.ItemStack;

public class CarvableComponent extends BlockComponent {
    @SerializedName("final_block")
    private String finalBlockId;
    @SerializedName("tool_tag")
    private String toolTag = "zenith:knives";

    @Override
    public boolean onLeftClick(World world, BlockPos pos, Player player, ItemStack heldStack, float hitX, float hitY, float hitZ, boolean isNewClick) {
        // Проверяем дискретность клика (1 удар = 1 сегмент) и попадание по верху блока
        if (!isNewClick || hitY < 0.9f) return false;

        // Проверяем наличие ножа в руках
        if (heldStack != null && heldStack.getItem().hasTag(Identifier.of(toolTag))) {
            var be = world.getBlockEntity(pos);
            if (be instanceof ModularBlockEntity modular) {
                // Изменяем маску вырезания в сущности блока...
                player.swing();
                return true;
            }
        }
        return false;
    }
}
```

### Шаблон 2.2: JSON-описание блока с компонентом
Компоненты перечисляются в виде массива в JSON-файле блока (например, `unfinished_stump.json`).

```json
{
  "name": "unfinished_stump",
  "hardness": 2.0,
  "requiredTool": "axe",
  "textures": {
    "top": "stripped_oak_log_top.png",
    "bottom": "oak_log_top.png",
    "side": "oak_log.png"
  },
  "components": [
    {
      "type": "zenith:carvable",
      "final_block": "zenith:stump"
    }
  ]
}
```
