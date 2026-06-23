# Руководство по оптимизации кучи (Zero-Allocation Heap Guidelines)

Этот справочник описывает практические паттерны избегания неявных аллокаций памяти (Garbage Collection Pressure) в воксельном движке Zenith.

---

## 1. Избегание аллокаций JOML-объектов

Любое использование оператора `new` для векторов или матриц в циклах `update()` и `render()` запрещено.

### Неправильно (Аллокация на каждом тике):
```java
public void update(float deltaTime) {
    Vector3f velocity = new Vector3f(1.0f, 0.0f, 0.0f); // Аллокация!
    position.add(velocity.mul(deltaTime));
}
```

### Правильно (Использование destination-вектора из аргументов или пула):
```java
// Вариант A: Передача вектора-приемника в аргументы
public Vector3f getInterpolatedPosition(float alpha, Vector3f dest) {
    return dest.set(prevPosition).lerp(position, alpha);
}

// Вариант B: Получение временного вектора из потокобезопасного пула рендера
public void render(RenderState state) {
    Vector3f temp = RenderContext.getVector(); // Возвращает переиспользуемый объект из пула
    temp.set(0.0f);
    // ... операции ...
}
```

---

## 2. Кэширование объектов в синглтонах и менеджерах

Долгоживущие менеджеры (например, `AtmosphereManager`) не должны создавать новые вектора при расчете цвета или направления света.

### Неправильно:
```java
public void update(World world, float deltaTime) {
    Vector3f skyColor = new Vector3f(0.5f, 0.7f, 1.0f); // Аллокация каждый тик!
    shader.setVector3f("skyColor", skyColor);
}
```

### Правильно:
```java
public class AtmosphereManager {
    // Вектор аллоцируется один раз при инициализации класса
    private final Vector3f skyColor = new Vector3f(); 

    public void update(World world, float deltaTime) {
        // Обновляем значения существующего вектора без создания нового
        skyColor.set(0.5f, 0.7f, 1.0f); 
        shader.setVector3f("skyColor", skyColor);
    }
}
```

---

## 3. Примитивный доступ вместо объектного

Многие встроенные классы при обращении к внутренним объектам создают новые инстансы. Например, методы `AABB` часто возвращают новые `Vector3f` для `min`/`max` или копируют состояние.

### Неправильно (Создает 2 новых Vector3f на кадр):
```java
if (state.getFrustum().testAab(entity.getBoundingBox().getMin(), entity.getBoundingBox().getMax())) {
    // ...
}
```

### Правильно (Использование примитивных геттеров без создания объектов):
```java
AABB localBox = entity.getLocalBoundingBox();
Vector3f p = entity.getInterpolatedPosition(alpha, RenderContext.getVector());

if (state.getFrustum().testAab(
    localBox.minX() + p.x, localBox.minY() + p.y, localBox.minZ() + p.z,
    localBox.maxX() + p.x, localBox.maxY() + p.y, localBox.maxZ() + p.z
)) {
    // ...
}
```

---

## 4. Кэширование массивов примитивов

Методы текстурных атласов или парсеров не должны выделять новые массивы для возврата данных, если эти данные фиксированы.

### Неправильно (Создает float[12] при каждом обращении):
```java
public float[] uvFor(String key) {
    Integer layer = keyToLayer.get(key);
    float l = (layer != null) ? (float) layer : 0.0f;
    return new float[]{ 0, 0, l,  1, 0, l,  1, 1, l,  0, 1, l }; // Аллокация!
}
```

### Правильно (Кэширование массивов по индексам слоёв):
```java
private final float[][] uvCache = new float[1024][];

public float[] uvFor(String key) {
    Integer layer = keyToLayer.get(key);
    int l = (layer != null) ? layer : 0;
    if (l >= 0 && l < uvCache.length) {
        if (uvCache[l] == null) {
            uvCache[l] = new float[]{ 0, 0, (float)l,  1, 0, (float)l,  1, 1, (float)l,  0, 1, (float)l };
        }
        return uvCache[l];
    }
    // fallback...
}
```

---

## 5. Обход списков без создания итераторов

Использование сокращенного синтаксиса `for-each` для коллекций `List` неявно создает объект `Iterator` на куче. В горячих циклах это вызывает скрытую лавинообразную аллокацию.

### Неправильно:
```java
for (ItemEntity item : world.getItemsInChunk(chunkPos)) {
    // Неявно компилируется в: Iterator<ItemEntity> it = list.iterator(); ...
    tryMerge(item);
}
```

### Правильно (Классический цикл по индексу):
```java
List<ItemEntity> items = world.getItemsInChunk(chunkPos);
for (int i = 0; i < items.size(); i++) {
    ItemEntity item = items.get(i);
    tryMerge(item);
}
```

---

## 6. Переиспользуемые Thread-Safe буферы в генераторах

В ChunkMeshGenerator генерация тысяч полигонов чанка приводит к миллионам аллокаций, если вершины для каждого лица кубического бокса выделяются заново.

### Неправильно (Создает float[][] на каждый воксель):
```java
float[][] facePositions = new float[][]{
    {min.x, min.y, max.z,  max.x, min.y, max.z,  max.x, max.y, max.z,  min.x, max.y, max.z},
    // ... еще 5 граней
};
```

### Правильно (Использование переиспользуемого буфера в ThreadLocal контексте MeshData):
```java
public static class MeshData {
    private final float[] tempFaceVertices = new float[12]; // Один массив на поток/MeshData

    public float[] getFaceVertices(int face, AABB box) {
        // Заполняем tempFaceVertices примитивами из box в зависимости от face...
        return tempFaceVertices; // Возвращаем ссылку на переиспользуемый массив
    }
}
```
