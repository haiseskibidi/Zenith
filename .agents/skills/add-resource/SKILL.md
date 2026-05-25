---
name: add-resource
description: Навык для добавления новых игровых ресурсов (блоков, предметов, структур). Гарантирует правильное создание JSON и безопасное обновление индексов.
---

# Add Resource Workflow

Этот навык используется при добавлении в игру новых блоков или предметов. Он обеспечивает Data-Driven подход и целостность индексов.

## Когда использовать
- При создании нового блока.
- При создании нового предмета.
- При добавлении нового префаба (структуры).

## Процедура добавления

### 1. Создание JSON-файла
- Определи тип ресурса (блок или предмет).
- Создай соответствующий JSON-файл в `src/main/resources/zenith/blocks/` или `src/main/resources/zenith/items/`.
- Используй свободный ID (проверь другие JSON в папке).
- Убедись, что `identifier` соответствует формату `zenith:name`.

### 2. Обновление индекса (.index)
- **ЗАПРЕЩЕНО** использовать `write_file` или `replace` для прямой модификации `.index` файлов.
- Для обновления индекса **ОБЯЗАТЕЛЬНО** используй скрипт `.gemini/skills/add_resource.js`.
- Выполняй команду: `node .gemini/skills/add_resource.js <path_to_index> <resource_filename>`.
- Если нужно добавить несколько ресурсов сразу в PowerShell, используй `;` вместо `&&`:
  - Пример: `node .gemini/skills/add_resource.js path/to/.index res1.json ; node .gemini/skills/add_resource.js path/to/.index res2.json`.

### 3. Регистрация в коде (Java)
- Найди класс-холдер: `com.za.zenith.world.blocks.Blocks` или `com.za.zenith.world.items.Items`.
- Объяви `public static` поле с типом `BlockDefinition` или `Item` соответственно.
- Имя поля должно быть в верхнем регистре (SNAKE_CASE) и совпадать с названием из JSON.
- Поле заполнится автоматически при запуске через рефлексию.

### 4. Локализация
- Добавь перевод для `translationKey` в файлы:
    - `src/main/resources/zenith/lang/ru_ru.json`
    - `src/main/resources/zenith/lang/en_us.json`

### 5. Верификация
- Скомпилируй проект: `./gradlew build`.
- Попроси пользователя затестить новый ресурс в игре.
- После подтверждения ("ОК", "Работает") активируй навык `update-memory`.

## Принципы
- **Append-only Index:** Индексы только дополняются. Удаление или перезапись запрещены.
- **No Hardcode:** Все параметры (текстуры, статы, ID) должны быть в JSON.
- **Validation:** Всегда проверяй уникальность ID перед сохранением.
