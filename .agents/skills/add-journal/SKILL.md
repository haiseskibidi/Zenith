---
name: add-journal
description: Навык для добавления записи в Дневник (Survivor's Tablet)
---

# Навык: Добавление записи в Дневник (Survivor's Tablet)

Этот навык автоматизирует процесс создания новых обучающих статей и их правильную регистрацию в системе.

## Алгоритм действий

### 1. Создание контента
- Создать JSON файл в `src/main/resources/zenith/journal/entries/<id>.json`.
- Формат JSON должен строго следовать схеме: `id`, `title` (ключ локализации), `icon` (Identifier), `elements` (массив блоков контента).
- Типы элементов: `text`, `header`, `tip`, `image`, `recipe`, `item_row`, `spacer`.

### 2. Регистрация (Индексация)
- **Файловый индекс**: Добавить строку `<id>.json` в конец файла `src/main/resources/zenith/journal/entries/.index`.
- **Категория**: Найти файл категории в `src/main/resources/zenith/journal/categories/` и добавить `"zenith:<id>"` в массив `entries`.

### 3. Локализация
- Добавить ключ `"journal.entry.<id>.title"` в `src/main/resources/zenith/lang/ru_ru.json`.
- Добавить все текстовые ключи, используемые в элементах `text`, `header` и `tip`.

### 4. Валидация
- Убедиться, что все Identifier (предметы, иконки, рецепты), указанные в статье, существуют в игре.
- Проверить, что файл категории существует и в нем нет дубликатов.

## Пример JSON статьи
```json
{
  "id": "zenith:example",
  "title": "journal.entry.example.title",
  "icon": "zenith:stick",
  "elements": [
    { "type": "text", "value": "journal.entry.example.desc" },
    { "type": "recipe", "value": "zenith:stick" }
  ]
}
```
