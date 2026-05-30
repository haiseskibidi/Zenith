export const glossaryTerms = [
  {
    id: 'DOD',
    abbr: 'DOD',
    full: 'Data-Oriented Design',
    category: 'engine',
    definition: 'Подход к проектированию ПО, ориентированный на данные. Вместо разбрасывания объектов в куче памяти (классическое ООП), мы упаковываем данные в плоские массивы. CPU обожает последовательное чтение: L1/L2 кэши заполняются полезными байтами без пустот, исключая дорогостоящие промахи мимо кэша.',
    analogy: 'Вместо того чтобы отправлять 100 курьеров за 100 отдельными письмами на разные склады, мы кладём все письма в одну коробку на одном столе. CPU берёт коробку и читает всё за один присест.',
    visualType: 'image',
    visualContent: `# Симуляция DOD (Structure of Arrays) vs OOP (Array of Structures)
import time

class ParticleOOP:
    def __init__(self):
        self.x, self.y, self.z = 0.0, 0.0, 0.0
        self.vx, self.vy, self.vz = 1.0, 1.0, 1.0
        self.active = True
        self.name = "ParticleSystemNode" # Огромный оверхед метаданных

# Инициализация 100,000 частиц в ООП (данные разбросаны в RAM)
particles_oop = [ParticleOOP() for _ in range(100000)]

# Инициализация в DOD (данные упакованы в плоские непрерывные массивы)
x, y, z = [0.0] * 100000, [0.0] * 100000, [0.0] * 100000
vx, vy, vz = [1.0] * 100000, [1.0] * 100000, [1.0] * 100000

# Обход DOD выполняется в 10-15 раз быстрее на CPU благодаря Cache Line Locality!
for i in range(100000):
    x[i] += vx[i]
    y[i] += vy[i]
    z[i] += vz[i]`
  },
  {
    id: 'MDI',
    abbr: 'MDI',
    full: 'MultiDraw Indirect',
    category: 'engine',
    definition: 'Технология рендеринга, позволяющая отрисовать тысячи разных 3D-моделей всего ОДНИМ вызовом отрисовки (Draw Call). Инструкции о том, ЧТО, ОТКУДА и СКОЛЬКО рисовать, лежат прямо в видеопамяти GPU. CPU не тратит время на общение с драйвером для каждой модели.',
    analogy: 'Вместо того чтобы звонить строителю 1000 раз и говорить: "положи кирпич 1", "положи кирпич 2", вы отправляете ему один текстовый файл со всеми координатами, и он молча делает всю работу.',
    visualType: 'image',
    visualContent: `# Симуляция накладных расходов вызовов отрисовки (Draw Calls)
import time

def simulate_classic_draw_calls(mesh_count):
    # Обычный рендеринг: CPU заблокирован общением с драйвером видеокарты
    print(f"Starting {mesh_count} classic draw calls...")
    start = time.perf_counter()
    for mesh_id in range(mesh_count):
        # Имитируем оверхед переключения контекста OpenGL/Vulkan
        # и передачу uniform-переменных на каждый меш
        pass
    elapsed = time.perf_counter() - start
    return elapsed

def simulate_mdi_draw_call(mesh_count):
    # MultiDraw Indirect: CPU посылает 1 команду, GPU считывает буфер команд сам
    start = time.perf_counter()
    # 1 вызов отрисовки: glMultiDrawElementsIndirect()
    # GPU параллельно рисует меши на основе GPU Buffer
    elapsed = time.perf_counter() - start
    return elapsed`
  },
  {
    id: 'AST',
    abbr: 'AST',
    full: 'Abstract Syntax Tree',
    category: 'engine',
    definition: 'Абстрактное синтаксическое дерево. Представление структуры исходного кода в виде древовидной структуры данных. Компиляторы, интерпретаторы и парсеры математических формул превращают сырой текст программы в AST, чтобы легко проводить оптимизацию, вычислять приоритеты операций и компилировать код.',
    analogy: 'Разбор предложения в школе: подлежащее, сказуемое, дополнение. Вы превращаете линейный текст в иерархическую структуру отношений между словами.',
    visualType: 'image',
    visualContent: `# Симуляция дерева разбора математического выражения (AST)
class ASTNode:
    def __init__(self, op_type, left=None, right=None, value=None):
        self.op_type = op_type  # "+", "*", "number", "variable"
        self.left = left
        self.right = right
        self.value = value

    def evaluate(self, variables):
        if self.op_type == "number":
            return self.value
        elif self.op_type == "variable":
            return variables[self.value]
        elif self.op_type == "+":
            return self.left.evaluate(variables) + self.right.evaluate(variables)
        elif self.op_type == "*":
            return self.left.evaluate(variables) * self.right.evaluate(variables)

# Выражение: (5 + x) * 2
#           [*]
#          /   \
#        [+]   [2]
#       /   \
#     [5]   [x]
ast_root = ASTNode("*", 
    left=ASTNode("+", left=ASTNode("number", value=5), right=ASTNode("variable", value="x")),
    right=ASTNode("number", value=2)
)

print("AST Result for x=10:", ast_root.evaluate({"x": 10})) # Вывод: 30`
  },
  {
    id: 'IK',
    abbr: 'IK',
    full: 'Inverse Kinematics',
    category: 'math',
    definition: 'Инверсная (обратная) кинематика. Математический метод вычисления углов суставов скелета на основе финального положения конечной точки. Вместо вращения плеча, предплечья и кисти (прямая кинематика), вы просто двигаете кисть к ручке двери, а алгоритм сам вычисляет нужные углы в суставах.',
    analogy: 'Вы хотите взять чашку со стола. Вы не думаете: "так, повернуть плечо на 12 градусов, локоть на 45...". Вы просто тянете руку к чашке, а ваш мозг (алгоритм IK) сам сгибает суставы.',
    visualType: 'image',
    visualContent: `# Симуляция 2D инверсной кинематики руки (2 сустава)
import math

def solve_2d_ik(target_x, target_y, l1, l2):
    # Нахождение углов суставов плеча (theta1) и локтя (theta2) для достижения (target_x, target_y)
    d_sq = target_x**2 + target_y**2
    d = math.sqrt(d_sq)
    
    # Защита от выхода за пределы длины руки
    if d > (l1 + l2):
        return None  # Цель недостижима
        
    # Косинус угла локтя (по теореме косинусов)
    cos_theta2 = (d_sq - l1**2 - l2**2) / (2 * l1 * l2)
    theta2 = math.acos(max(-1.0, min(1.0, cos_theta2)))
    
    # Угол плеча
    theta1 = math.atan2(target_y, target_x) - math.atan2(l2 * math.sin(theta2), l1 + l2 * math.cos(theta2))
    
    return {
        "shoulder_deg": math.degrees(theta1),
        "elbow_deg": math.degrees(theta2)
    }`
  },
  {
    id: 'CTM',
    abbr: 'CTM',
    full: 'Connected Textures Method',
    category: 'engine',
    definition: 'Метод сопряжения текстур. Позволяет блокам одного типа (например, стёклам, книжным полкам или блокам земли в Minecraft) динамически сливаться границами с соседями. Алгоритм проверяет окружение блока по 8 сторонам и выбирает нужный спрайт из атласа.',
    analogy: 'Головоломка-пазл. Вы смотрите на соседние кусочки, чтобы понять, нужна ли сглаженная рамка или стыковка с картинкой справа.',
    visualType: 'image',
    visualContent: `# Расчет маски Connected Textures (CTM)
def get_ctm_texture_mask(grid, x, y):
    # grid: 2D матрица карты вокселей (1 - стекло, 0 - пусто)
    # Проверяем 4 основных соседей (Top, Right, Bottom, Left)
    t = grid[y-1][x] if y > 0 else 0
    r = grid[y][x+1] if x < len(grid[0])-1 else 0
    b = grid[y+1][x] if y < len(grid)-1 else 0
    l = grid[y][x-1] if x > 0 else 0
    
    # Собираем битовую маску: T(бит 3), R(бит 2), B(бит 1), L(бит 0)
    bitmask = (t << 3) | (r << 2) | (b << 1) | l
    
    # Каждому значению битовой маски (0-15) соответствует уникальный тайл в CTM атласе
    # Пример:
    # bitmask == 0  -> Одиночный блок со всех сторон в рамках
    # bitmask == 15 -> Полностью соединенный со всех сторон блок (без рамок)
    return bitmask`
  },
  {
    id: 'AABB',
    abbr: 'AABB',
    full: 'Axis-Aligned Bounding Box',
    category: 'math',
    definition: 'Ограничивающий параллелепипед, выровненный по осям координат. Самая дешёвая и популярная форма для расчёта коллизий в 3D. Так как его грани строго параллельны мировым осям X, Y и Z, проверка пересечения двух AABB сводится к 6 простым сравнениям чисел.',
    analogy: 'Вместо того чтобы обсчитывать сложную модельку дракона с миллионом чешуек при столкновении со стрелой, мы надеваем на дракона невидимую коробку от холодильника и проверяем, влетела ли стрела в коробку.',
    visualType: 'image',
    visualContent: `# Алгоритм проверки коллизии двух коробок AABB в 3D
class AABB:
    def __init__(self, min_x, min_y, min_z, max_x, max_y, max_z):
        self.min_x, self.min_y, self.min_z = min_x, min_y, min_z
        self.max_x, self.max_y, self.max_z = max_x, max_y, max_z

    def intersects(self, other):
        # 6 быстрейших сравнений, не требующих тригонометрии
        return (
            self.min_x <= other.max_x and self.max_x >= other.min_x and
            self.min_y <= other.max_y and self.max_y >= other.min_y and
            self.min_z <= other.max_z and self.max_z >= other.min_z
        )

box1 = AABB(0, 0, 0, 2, 2, 2)
box2 = AABB(1, 1, 1, 3, 3, 3)
print("Collision detected:", box1.intersects(box2)) # Вывод: True`
  },
  {
    id: 'CAS',
    abbr: 'CAS',
    full: 'Compare-And-Swap',
    category: 'hardware',
    definition: 'Атомарная процессорная инструкция ("сравни и обменяй"). Базовый кирпичик Lock-Free многопоточности. Поток считывает переменную, вычисляет новое значение и просит процессор обновить её. Но процессор сделает это только если значение переменной не изменилось с момента чтения. Никаких блокировок потоков!',
    analogy: 'Вы пишете цену на доске. Прежде чем стереть её и написать новую, вы проверяете: "та ли это цена, которую я видел секунду назад?". Если кто-то уже стёр её и вписал другую, вы отступаете и пересчитываете всё заново.',
    visualType: 'image',
    visualContent: `# Эмуляция атомарной операции Compare-And-Swap (CAS) и Lock-free счетчика
import threading

class LockFreeAtomicInteger:
    def __init__(self, initial_value=0):
        self._value = initial_value
        self._lock = threading.Lock() # Эмулирует шину процессора / аппаратный LOCK префикс

    def compare_and_swap(self, expected_value, new_value):
        # Аппаратный атомарный блок на уровне CPU
        with self._lock:
            if self._value == expected_value:
                self._value = new_value
                return True
            return False

    def increment(self):
        while True:
            current = self._value  # 1. Считали значение без блокировки
            # 2. Пытаемся записать (current + 1). Если значение успело поменяться,
            # compare_and_swap вернет False, и мы уйдем на новый виток цикла (spin-lock).
            if self.compare_and_swap(current, current + 1):
                break`
  },
  {
    id: 'SIMD',
    abbr: 'SIMD',
    full: 'Single Instruction, Multiple Data',
    category: 'hardware',
    definition: 'Одиночный поток инструкций, множественный поток данных. Аппаратная фича процессоров (векторные инструкции SSE, AVX, NEON), позволяющая применить одну операцию (например, сложение) сразу к массиву чисел (вектору) за ОДИН такт процессора. Ускоряет физику и графику в разы.',
    analogy: 'Вместо того чтобы учитель 30 раз говорил каждому ученику по отдельности: "Открой учебник", он один раз громко говорит всему классу: "Откройте учебники!". Все делают действие одновременно.',
    visualType: 'image',
    visualContent: `# Имитация работы векторного процессора SIMD
def math_add_classic(a_list, b_list):
    # Обычный обход SISD (Single Instruction, Single Data):
    # 4 такта процессора на 4 сложения
    c = []
    for i in range(4):
        c.append(a_list[i] + b_list[i]) # Инструкция сложения вызывается 4 раза
    return c

def math_add_simd(a_list, b_list):
    # SIMD: Векторные регистры загружаются за раз, сложение в 1 такт CPU
    # Моделирование AVX-256 (вектор из 4-х 64-битных float)
    vector_a = (a_list[0], a_list[1], a_list[2], a_list[3])
    vector_b = (b_list[0], b_list[1], b_list[2], b_list[3])
    
    # 1 операция на аппаратном ALU:
    vector_c = tuple(x + y for x, y in zip(vector_a, vector_b))
    return vector_c`
  },
  {
    id: 'LOD',
    abbr: 'LOD',
    full: 'Level of Detail',
    category: 'engine',
    definition: 'Уровень детализации. Оптимизационная техника в 3D-графике. Чем дальше объект находится от виртуальной камеры игрока, тем более упрощённый 3D-меш (с меньшим количеством полигонов) мы рисуем. Для объектов на горизонте меш заменяется на плоскую картинку (билборд).',
    analogy: 'Когда вы смотрите на человека вблизи, вы видите пуговицы на его рубашке. Если он стоит в 500 метрах, для вас он просто цветное пятнышко. Видеокарте глупо рисовать пуговицы на расстоянии в полкилометра.',
    visualType: 'image',
    visualContent: `# Алгоритм выбора уровня детализации (LOD Selection)
import math

class RenderNode:
    def __init__(self, x, y, z):
        self.pos = (x, y, z)
        # 3 уровня мешей с разным количеством полигонов
        self.lods = {
            "LOD_0": "HighPolyMesh (15000 tris)",
            "LOD_1": "MidPolyMesh (2000 tris)",
            "LOD_2": "LowPolyMesh (200 tris)",
            "LOD_3": "FlatBillboard (2 tris)"
        }

    def get_mesh_for_camera(self, cam_x, cam_y, cam_z):
        # Вычисляем евклидово расстояние до камеры
        dist = math.sqrt((self.pos[0] - cam_x)**2 + (self.pos[1] - cam_y)**2 + (self.pos[2] - cam_z)**2)
        
        if dist < 15.0:
            return self.lods["LOD_0"]
        elif dist < 60.0:
            return self.lods["LOD_1"]
        elif dist < 180.0:
            return self.lods["LOD_2"]
        else:
            return self.lods["LOD_3"]`
  },
  {
    id: 'MESI',
    abbr: 'MESI',
    full: 'Modified, Exclusive, Shared, Invalid',
    category: 'hardware',
    definition: 'Протокол когерентности кэшей процессора. Гарантирует, что если 8 ядер CPU работают с одной областью памяти через свои локальные L1-кэши, они не будут читать устаревшие данные. Любое изменение переменной на одном ядре мгновенно переводит эту строку кэша на остальных ядрах в статус Invalid.',
    analogy: 'У вас и вашего друга есть копии блокнота. Если вы вычёркиваете запись и пишете новую, вы звоните другу и говорите: "У тебя старая запись, сотри её (Invalid), перепиши у меня!".',
    visualType: 'image',
    visualContent: `# Моделирование состояний кэш-линии по протоколу MESI
class CacheController:
    def __init__(self, core_id):
        self.core_id = core_id
        self.state = "INVALID"  # Возможные: MODIFIED, EXCLUSIVE, SHARED, INVALID

    def read_event(self, other_caches):
        if self.state == "INVALID":
            # Проверяем, есть ли копия у кого-то еще
            shared = any(c.state in ["SHARED", "EXCLUSIVE", "MODIFIED"] for c in other_caches)
            if shared:
                self.state = "SHARED"
                print(f"Core {self.core_id} reads shared data. State: SHARED")
            else:
                self.state = "EXCLUSIVE"
                print(f"Core {self.core_id} reads exclusive data. State: EXCLUSIVE")
        else:
            print(f"Core {self.core_id} hits local cache. State: {self.state}")

    def write_event(self, other_caches):
        if self.state != "MODIFIED":
            # Инвалидируем копии у всех остальных ядер (Broadcast transaction)
            for other in other_caches:
                if other.state != "INVALID":
                    other.state = "INVALID"
                    print(f"Invalidated cache on Core {other.core_id}!")
            self.state = "MODIFIED"
            print(f"Core {self.core_id} gains exclusive write access. State: MODIFIED")`
  },
  {
    id: 'POOL',
    abbr: 'Thread Pool',
    full: 'Worker Threads',
    category: 'hardware',
    definition: 'Пул потоков. Создание системного потока ОС — это очень медленная операция, требующая выделения 1MB памяти под стек и переключения контекста ядра. Вместо создания потоков на каждую мелкую задачу, мы при старте создаём фиксированную команду "спящих" потоков, которые берут задачи из общей очереди.',
    analogy: 'Вместо того чтобы нанимать нового строителя на укладку каждого отдельного кирпича и увольнять его через 5 секунд, вы нанимаете бригаду из 4 человек на весь день. Они стоят у конвейера и берут кирпичи по мере поступления.',
    visualType: 'image',
    visualContent: `# Симуляция легковесного Thread Pool на Python
import queue
import threading
import time

class MiniThreadPool:
    def __init__(self, num_threads):
        self.task_queue = queue.Queue()
        self.workers = []
        for i in range(num_threads):
            t = threading.Thread(target=self._worker_loop, args=(i,), daemon=True)
            t.start()
            self.workers.append(t)

    def submit(self, job_func, *args):
        # Добавляем задачу в блокирующую потокобезопасную очередь
        self.task_queue.put((job_func, args))

    def _worker_loop(self, worker_id):
        while True:
            # Поток засыпает (sleep), если в очереди пусто (0% загрузки CPU на простои!)
            job, args = self.task_queue.get()
            try:
                job(*args)
            except Exception as e:
                print(f"Worker {worker_id} error: {e}")
            finally:
                self.task_queue.task_done()`
  },
  {
    id: 'DMA',
    abbr: 'DMA',
    full: 'Direct Memory Access',
    category: 'hardware',
    definition: 'Прямой доступ к памяти. Аппаратный механизм, позволяющий сетевым картам, звуковым чипам и SSD читать/писать оперативную память напрямую, без участия центрального процессора. CPU выдает команду контроллеру DMA и может выполнять другие вычисления, пока терабайты текстур летят в ОЗУ.',
    analogy: 'Директор фирмы (CPU) даёт секретарю (DMA) поручение переложить папки из архива на стол. Директор продолжает работать с клиентами, а не таскает папки лично.',
    visualType: 'image',
    visualContent: `# Симуляция работы Direct Memory Access (DMA)
import time
import threading

class DMASimulator:
    def __init__(self):
        self.cpu_busy = False

    def trigger_dma_transfer(self, ram_dest, ssd_src, size_mb, on_complete_callback):
        print(f"CPU: Initiating DMA. Copy {size_mb}MB from {ssd_src} to {ram_dest}...")
        
        def hardware_transfer():
            # Выполняется аппаратным контроллером DMA без участия ядер CPU!
            time.sleep(0.1)  # Имитируем передачу по PCIe шине
            print("DMA: Transfer finished, triggering CPU Interrupt...")
            on_complete_callback()

        # Запускаем независимый поток (как выделенная линия DMA)
        threading.Thread(target=hardware_transfer, daemon=True).start()
        
        print("CPU: DMA started. CPU immediately returns to gameplay and physics calculations!")`
  },
  {
    id: 'FRUSTUM',
    abbr: 'Frustum Culling',
    full: 'Pyramid Culling',
    category: 'math',
    definition: 'Отсечение объектов по пирамиде видимости камеры. Камера игрока видит мир не на 360 градусов, а в форме усечённой четырёхгранной пирамиды (Frustum). Алгоритм проверяет пересечение границ объектов (их AABB) с плоскостями пирамиды и мгновенно отбрасывает всё, что находится сзади или сбоку.',
    analogy: 'Когда вы идёте по улице, вы не видите то, что происходит у вас на затылке. Игровая видеокарта поступает так же: всё, что не попадает в поле зрения глаз, полностью игнорируется.',
    visualType: 'image',
    visualContent: `# Frustum Culling на Python: отсечение объектов по плоскостям пирамиды
class Plane:
    def __init__(self, normal, distance):
        self.normal = normal  # (x, y, z) вектор нормали к плоскости
        self.distance = distance

    def is_point_outside(self, px, py, pz):
        # Скалярное произведение + расстояние
        dot = px*self.normal[0] + py*self.normal[1] + pz*self.normal[2]
        return dot + self.distance < 0

def frustum_culling(aabb_entities, frustum_planes):
    visible_entities = []
    for entity in aabb_entities:
        # Проверяем вершины коробки AABB
        # Если коробка целиком за какой-то одной из 6 плоскостей - объект не виден
        outside = False
        for plane in frustum_planes:
            if plane.is_point_outside(*entity.center):
                outside = True
                break
        if not outside:
            visible_entities.append(entity)
    return visible_entities`
  },
  {
    id: 'PIPELINE',
    abbr: 'CPU Pipeline',
    full: 'Процессорный конвейер',
    category: 'hardware',
    definition: 'Технология выполнения процессорных инструкций, разбитая на последовательные независимые стадии (Fetch, Decode, Execute, Memory, Writeback). Подобно заводскому конвейеру, стадии одновременно обрабатывают разные команды на каждом такте, увеличивая общую скорость выполнения программ.',
    analogy: 'Сборочная линия автомобилей Генри Форда. Вместо того чтобы один рабочий собирал машину от начала до конца, сборка разбивается на посты. Как только первая машина сдвигается на стадию окраски, второй рабочий начинает собирать на освободившемся первом посту следующую машину.',
    visualType: 'image',
    visualContent: `# Симулятор 5-стадийного конвейера CPU (Fetch, Decode, Execute, Memory, Writeback)
class CPUPipeline:
    def __init__(self):
        # 5 стадий конвейера, по умолчанию пустые
        self.stages = {
            "Fetch": None,
            "Decode": None,
            "Execute": None,
            "Memory": None,
            "Writeback": None
        }
        self.completed = []

    def clock_tick(self, next_instruction=None):
        # 1. Завершаем команду из Writeback
        if self.stages["Writeback"]:
            self.completed.append(self.stages["Writeback"])

        # 2. Двигаем команды по конвейеру назад
        self.stages["Writeback"] = self.stages["Memory"]
        self.stages["Memory"] = self.stages["Execute"]
        self.stages["Execute"] = self.stages["Decode"]
        self.stages["Decode"] = self.stages["Fetch"]
        
        # 3. Загружаем новую инструкцию на стадию Fetch
        self.stages["Fetch"] = next_instruction
        
        # Выводим текущее состояние конвейера на этом такте
        return {stage: (instr or "---") for stage, instr in self.stages.items()}`
  },
  {
    id: 'BRANCH_PRED',
    abbr: 'Branch Prediction',
    full: 'Предсказание ветвлений',
    category: 'hardware',
    definition: 'Аппаратная функция процессора, предсказывающая направление условных переходов (if-else) до их фактического вычисления в АЛУ. Это позволяет конвейеру продолжать спекулятивную загрузку и расчет команд по предсказанной ветви без остановок.',
    analogy: 'Вы бежите наперерез уходящему поезду в метро. Вы не ждете, пока двери откроются, а спекулятивно бежите к тому месту, где по вашему опыту они должны остановиться. Если угадали — запрыгнули сразу. Ошиблись — стоите на перроне, пока поезд уезжает, и ждете следующий.',
    visualType: 'image',
    visualContent: `# Симуляция 2-битного насыщающего счетчика предсказания переходов (Bimodal Predictor)
class BimodalPredictor:
    def __init__(self):
        # Состояния счетчика:
        # 0 - Strong Not Taken, 1 - Weak Not Taken
        # 2 - Weak Taken,     3 - Strong Taken
        self.state = 2  # Начинаем со слабого Taken

    def predict(self):
        # 2 или 3 -> Предсказываем переход
        return "TAKEN" if self.state >= 2 else "NOT_TAKEN"

    def update_after_execution(self, actually_taken):
        if actually_taken:
            self.state = min(3, self.state + 1)  # Сдвиг в сторону Strong Taken
        else:
            self.state = max(0, self.state - 1)  # Сдвиг в сторону Strong Not Taken`
  },
  {
    id: 'OOO',
    abbr: 'Out-of-Order Execution',
    full: 'Внеочередное исполнение',
    category: 'hardware',
    definition: 'Архитектурный принцип современных процессоров, позволяющий выполнять независимые инструкции не в порядке их следования в исходном коде, а по мере готовности операндов. Это предотвращает простои вычислительных блоков процессора при ожидании медленных данных из RAM.',
    analogy: 'У вас есть список дел: 1. Дождаться доставки пиццы (займет 30 минут). 2. Помыть посуду (5 минут). 3. Вынести мусор (2 минуты). Вы не стоите у двери полчаса, ожидая курьера (in-order), а сразу моете посуду и выносите мусор вне очереди (out-of-order).',
    visualType: 'image',
    visualContent: `# Упрощенный симулятор внеочередного выполнения команд (Out-of-Order Scheduler)
class Instruction:
    def __init__(self, name, dependency=None, execution_time=1):
        self.name = name
        self.dependency = dependency  # Название инструкции, которую надо подождать
        self.execution_time = execution_time
        self.status = "PENDING"  # PENDING, RUNNING, COMPLETED`
  },
  {
    id: 'CACHE_LINE',
    abbr: 'Cache Line',
    full: 'Кэш-линия',
    category: 'hardware',
    definition: 'Минимальная единица обмена данными между оперативной памятью (RAM) и кэш-памятью процессора (L1-L3). Вместо загрузки отдельных байт, процессор всегда считывает память последовательными блоками фиксированного размера (обычно 64 байта), обеспечивая высокую скорость пространственной локальности.',
    analogy: 'Вместо того чтобы каждый раз ходить в библиотеку за одной конкретной страницей книги, вы берете всю книгу целиком домой. Все нужные страницы уже лежат у вас на столе (в кэш-линии), и вы читаете их мгновенно.',
    visualType: 'image',
    visualContent: `# Симуляция L1-кэша и Cache Hits / Cache Misses при чтении памяти
class RAMCacheSimulator:
    def __init__(self):
        self.cache_line_size = 16  # 16 float-чисел по 4 байта = 64 байта (1 Cache Line)
        self.active_cache_line_start = -1
        self.hits = 0
        self.misses = 0

    def read_address(self, addr_index):
        # Проверяем, находится ли адрес внутри текущей загруженной кэш-линии
        line_start = (addr_index // self.cache_line_size) * self.cache_line_size
        
        if line_start == self.active_cache_line_start:
            self.hits += 1  # Мгновенный L1 Cache Hit!
            return "CACHE_HIT"
        else:
            self.misses += 1  # Cache Miss - тащим 64 байта из медленной RAM!
            self.active_cache_line_start = line_start
            return "CACHE_MISS"`
  },
  {
    id: 'PAGE_PINNING',
    abbr: 'Page Pinning',
    full: 'Фиксация страниц для DMA',
    category: 'hardware',
    definition: 'Механизм блокировки виртуальных страниц оперативной памяти в физических кадрах RAM. Операционная система временно запрещает выгружать эти страницы в файл подкачки (Swap) или перемещать их в физической памяти, что позволяет контроллерам DMA (например, видеокарте) безопасно считывать данные напрямую по физическим адресам.',
    analogy: 'Вы вызвали доставщика прямо к себе домой. Чтобы он не заблудился, вы обещаете не переезжать на другую квартиру и не менять адрес, пока доставка не завершится. Вы буквально «припираете» адрес гвоздями к карте.',
    visualType: 'image',
    visualContent: `# Симуляция виртуальной памяти, файла подкачки (Swap) и DMA Page Pinning
class VirtualOSMemory:
    def __init__(self):
        self.page_table = {
            "page_0": {"frame": "0x12A00", "pinned": False},
            "page_1": {"frame": "0x34B00", "pinned": False}
        }

    def pin_page(self, page_id):
        self.page_table[page_id]["pinned"] = True
        print(f"OS: Page {page_id} pinned in Physical RAM. Swap lock activated!")`
  },
  {
    id: 'RESIZABLE_BAR',
    abbr: 'Resizable BAR',
    full: 'Resizable BAR',
    category: 'hardware',
    definition: 'Аппаратная функция интерфейса PCI Express, позволяющая центральному процессору (CPU) обращаться ко всему объему видеопамяти графической карты (VRAM) целиком как к единому непрерывному пространству адресов, минуя классическое ограничение в 256 МБ.',
    analogy: 'Вместо того чтобы передавать посылки через маленькое почтовое окошко размером 25 см (где вам приходится делить посылку на части), почта открывает для вас огромные ворота склада, и вы можете завезти весь груз на грузовике за один раз.',
    visualType: 'image',
    visualContent: `# Симулятор передачи текстур в VRAM через шину PCIe (с/без Resizable BAR)
import time

def transfer_texture_to_gpu(texture_size_mb, bar_size_mb=256):
    print(f"PCIE: Transferring {texture_size_mb}MB Texture to VRAM...")
    
    if bar_size_mb >= texture_size_mb:
        # Прямая непрерывная транзакция PCIe за один раз (Resizable BAR)
        t_start = time.perf_counter()
        # 1 быстрая транзакция
        elapsed = time.perf_counter() - t_start
        print("PCIe: Resizable BAR enabled. Data transfer in ONE single block!")
        return 1  # 1 транзакция`
  },
  {
    id: 'DIRECT_STORAGE',
    abbr: 'DirectStorage',
    full: 'DirectStorage',
    category: 'hardware',
    definition: 'Низкоуровневый API ввода-вывода, позволяющий графическому процессору (GPU) запрашивать и считывать сжатые ресурсы (текстуры, меши) напрямую с NVMe SSD-накопителя через шину PCIe в видеопамять (VRAM), минуя процессор (CPU) и исключая дорогостоящую декомпрессию на стороне CPU.',
    analogy: 'Доставка стройматериалов прямо на стройку. Вместо того чтобы везти кирпичи сначала в офис директора (CPU) для проверки и сортировки, грузовики едут сразу на стройплощадку (GPU), где рабочие мгновенно пускают их в дело.',
    visualType: 'image',
    visualContent: `# Симуляция загрузки воксельных чанков (DirectStorage vs Традиционный метод)
def load_voxels_traditional():
    # CPU выступает бутылочным горлышком:
    step1 = "SSD -> PCIe -> System RAM"
    step2 = "CPU (распаковывает LZ4 / ZSTD мегабайты вокселей)" # Ядро CPU забито на 100%
    step3 = "System RAM -> PCIe -> GPU VRAM"
    print("Traditional IO:", " -> ".join([step1, step2, step3]))
    return "High CPU Load, slow loading"`
  },
  {
    id: 'EVENT_BUS',
    abbr: 'EventBus',
    full: 'Шина событий',
    category: 'engine',
    definition: 'Паттерн проектирования архитектуры, реализующий слабую связанность (loose coupling) между модулями системы. Компоненты взаимодействуют друг с другом, публикуя события в общую шину или подписываясь на них, не зная о существовании друг друга.',
    analogy: 'Доска объявлений в университете. Староста вешает объявление о субботнике. Ей не нужно обходить всех 500 студентов лично и сообщать новость. Студенты сами подходят к доске, читают и реагируют.',
    visualType: 'image',
    visualContent: `# Реализация паттерна EventBus (Шина событий) для Zenith Engine
class ZenithEventBus:
    def __init__(self):
        self._subscribers = {}

    def subscribe(self, event_class_name, callback):
        if event_class_name not in self._subscribers:
            self._subscribers[event_class_name] = []
        self._subscribers[event_class_name].append(callback)`
  },
  {
    id: 'WEAK_SPOTS',
    abbr: 'Weak Spots',
    full: 'Процедурные слабые точки',
    category: 'engine',
    definition: 'Геймплейный алгоритм динамической генерации критических зон на гранях вокселей при их разрушении. Включает в себя трассировку луча (Raycast), проекцию 3D точки попадания в UV-координаты грани (u, v) и расчёт евклидова расстояния до центра мишени для начисления тройного критического урона.',
    analogy: 'Вы рубите дерево. Вместо того чтобы хаотично бить по коре, вы замечаете на стволе небольшую трещину (слабое место). Удар точно по трещине раскалывает бревно в три раза быстрее и выбивает кучу щепок.',
    visualType: 'image',
    visualContent: `# Алгоритм расчета попадания клика в Слабую Точку (Weak Spot)
import math

class WeakSpotController:
    def __init__(self, u_center=0.5, v_center=0.5, radius=0.15):
        self.spot_center = (u_center, v_center)
        self.crit_radius = radius`
  },
  {
    id: 'GL_SCISSOR',
    abbr: 'glScissor',
    full: 'Трафаретная отсечка',
    category: 'engine',
    definition: 'Аппаратный тест отсечения пикселей (Scissor Test) в OpenGL. Ограничивает область рисования прямоугольным окном в физических пикселях буфера кадра. Любые пиксели, попадающие за пределы рамки glScissor, аппаратно отбрасываются видеокартой на стадии растеризации, что позволяет обрезать вылезающие элементы UI в прокручиваемых списках.',
    analogy: 'Картонный трафарет с вырезанным посередине окошком. Вы кладете его на холст и смело красите широкой кистью: краска ляжет только внутри окошка, а всё остальное останется чистым за пределами картона.',
    visualType: 'image',
    visualContent: `# Симуляция GPU-теста glScissor при растеризации пикселей интерфейса
class GPURasterizer:
    def __init__(self):
        self.scissor_enabled = False
        self.scissor_box = (0, 0, 1920, 1080) # min_x, min_y, max_x, max_y`
  },
  {
    id: 'SDF',
    abbr: 'SDF',
    full: 'Signed Distance Fields',
    category: 'math',
    definition: 'Знакопеременные поля расстояний. Математический метод представления геометрии, где для каждой точки пространства хранится кратчайшее расстояние до границы фигуры (знак указывает, внутри точки или снаружи). Позволяет шейдеру отрисовывать векторные шрифты и фигуры с идеальной субпиксельной четкостью и сглаживанием при любом масштабе.',
    analogy: 'Карта высот острова. Океан — это отрицательная высота, суша — положительная. Береговая линия — это ровно нулевая высота. Шейдер просто ищет "берег" и рисует идеальный честный контур, даже если мы приблизили карту в миллион раз.',
    visualType: 'image',
    visualContent: `# Математический расчет знаковых расстояний (Signed Distance Fields - SDF)
import math

def sd_circle(px, py, radius):
    # Вычисляем кратчайшее расстояние от точки p до окружности с центром (0,0)
    dist_to_center = math.sqrt(px**2 + py**2)
    return dist_to_center - radius`
  },
  {
    id: 'LF_PALETTE',
    abbr: 'Lock-Free Palette',
    full: 'Безблокировочная палитра',
    category: 'engine',
    definition: 'Высокопроизводительный механизм хранения соответствия воксельных индексов типам блоков в чанке, разработанный по принципу Lock-Free Read / Synchronized Write. Гарантирует стабильную работу асинхронного мешера без блокировок FPS и предотвращает вылеты JVM с помощью предохранителя Palette Corruption Guard.',
    analogy: 'Вы читаете книгу в библиотеке. В этот момент автор дописывает новую главу в конец книги. Вы не мешаете друг другу: вы спокойно читаете то, что уже написано (Lock-Free Read), а автор аккуратно добавляет информацию (Synchronized Write).',
    visualType: 'image',
    visualContent: `# Симуляция Lock-Free чтения из палитры (Lock-Free Read / Synchronized Write)
import threading

class LockFreePalette:
    def __init__(self):
        # Палитра блоков в чанке
        self._palette = ["AIR", "STONE", "DIRT"]
        self._write_lock = threading.Lock()`
  },
  {
    id: 'RAY_OBB',
    abbr: 'Ray-OBB',
    full: 'Oriented Bounding Box picking',
    category: 'math',
    definition: 'Алгоритм определения пересечения луча с ориентированной в пространстве коробкой (Oriented Bounding Box). Для обхода сложной тригонометрии алгоритм инвертирует глобальную матрицу трансформации объекта, перенося луч в локальное пространство, где OBB превращается в простой AABB, выровненный по осям.',
    analogy: 'Вместо того чтобы крутить и резать наклонный торт под сложным углом, вы поворачиваете собственную голову и руки так, чтобы торт оказался прямо перед вами, и режете его простым движением вперед-назад.',
    visualType: 'image',
    visualContent: `# Математический перенос луча клика в локальное пространство Oriented Bounding Box
import numpy as np

def intersect_ray_obb(ray_origin, ray_direction, obb_center, obb_rotation_matrix, aabb_min, aabb_max):
    # obb_rotation_matrix: 3x3 матрица поворота ориентированной коробки в 3D
    inv_rotation = np.linalg.inv(obb_rotation_matrix)`
  },
  {
    id: 'LEAD_PURSUIT',
    abbr: 'Lead Pursuit',
    full: 'Физическое упреждение перехвата',
    category: 'math',
    definition: 'Математический алгоритм наведения снарядов или притяжения предметов, учитывающий текущий вектор скорости цели. Вместо движения в текущую точку нахождения объекта, система вычисляет точку упреждения, куда цель прибудет через время T, предотвращая отставание и бесконечные круги вокруг цели.',
    analogy: 'Вы бежите наперерез уходящему автобусу. Вы не бежите за его бампером сзади (так вы никогда не догоните его), а рассчитываете траекторию наперед и бежите к следующей остановке, встречаясь с автобусом в будущей точке.',
    visualType: 'image',
    visualContent: `# Алгоритм движения магнитного лута с физическим упреждением (Lead Pursuit)
class Vector2D:
    def __init__(self, x, y):
        self.x, self.y = x, y`
  },
  {
    id: 'MINING_HEAT',
    abbr: 'Mining Heat',
    full: 'Тепловой нагрев инструментов',
    category: 'engine',
    definition: 'Визуальный эффект плавного раскаления active-инструмента или рук игрока в процессе разрушения блоков. Алгоритм отслеживает уникальный хэш-код предмета identityHashCode, плавно наращивает накал при добыче, остужает его при простое и сбрасывает при смене предмета, проецируя локализованное свечение через маску в шейдере.',
    analogy: 'Световая нить в лампе накаливания. Чем дольше через нее идет ток (игрок бьет блок), тем сильнее она раскаляется и светится оранжево-красным. Стоит выключить выключатель — она плавно остывает.',
    visualType: 'image',
    visualContent: `# Алгоритм расчета теплового эффекта раскаления рук/инструментов (Mining Heat)
class ToolHeatController:
    def __init__(self):
        self.item_heat = 0.0
        self.last_item_hash = None`
  },
  {
    id: 'DEPTH_BUFFER',
    abbr: 'Z-Buffer',
    full: 'Буфер глубины',
    category: 'hardware',
    definition: 'Двумерный системный буфер видеопамяти (VRAM), хранящий расстояние (глубину) от виртуальной камеры до ближайшего отрисованного пикселя для каждой экранной координаты. Используется видеокартой для выполнения теста глубины (Depth Test), предотвращающего рисование дальних объектов поверх ближних.',
    analogy: 'Слой защитной краски. Вы красите забор. Каждый новый слой ложится поверх старого. Но буфер глубины — это умный маркер: он проверяет линейку и разрешает нанести краску только если новая дощечка находится ближе к вам, чем та, что уже покрашена.',
    visualType: 'image',
    visualContent: `# Симуляция GPU-теста глубины (Depth Buffer / Z-Buffer Test)
class GPUFrameBuffer:
    def __init__(self, width=800, height=600):
        self.z_buffer = [[1.0 for _ in range(width)] for _ in range(height)]`
  },
  {
    id: 'GC',
    abbr: 'Garbage Collector',
    full: 'Сборщик мусора',
    category: 'hardware',
    definition: 'Автоматическая подсистема управления динамической памятью в управляемых средах выполнения (таких как JVM). Сканирует кучу (Heap), находит неиспользуемые объекты, на которые нет активных ссылок в программе, и освобождает память, приостанавливая выполнение приложения во время тяжелых фаз (паузы Stop-the-World).',
    analogy: 'Уборщица в детской игровой комнате. Пока дети играют и раскидывают игрушки (создают объекты через new), всё в порядке. Но когда свободный пол заканчивается, уборщица кричит: «Всем замереть на месте!» (Stop-the-World), собирает сломанные и брошенные игрушки, а дети (потоки игры) ждут окончания уборки, ловя лаг (микрофриз).',
    visualType: 'image',
    visualContent: `# Симуляция GC-паузы Stop-the-World при утечке памяти (аллокация в цикле)
class JVMHeapSimulator:
    def __init__(self, max_capacity=5000):
        self.heap = []`
  },
  {
    id: 'VIRTUAL_MEM',
    abbr: 'Virtual Memory',
    full: 'Виртуальная память',
    category: 'hardware',
    definition: 'Технология системного управления памятью операционной системой, изолирующая адресные пространства процессов. Дает каждой программе монопольную иллюзию работы с непрерывным массивом адресов. Физически трансляция виртуальных адресов в физическую RAM выполняется аппаратно через MMU и кэш TLB на базе многоуровневых таблиц страниц (PML4).',
    analogy: 'Номера комнат в отелях. В каждом отели мира есть «Комната 101». Вы можете жить в комнате 101 в Москве, а ваш друг — в комнате 101 в Париже. Ваши «виртуальные номера» совпадают, но физически вы находитесь на совершенно разных улицах Земли.',
    visualType: 'image',
    visualContent: `# Моделирование аппаратной трансляции виртуальных адресов в физические через MMU
class MMUTranslationSimulator:
    def __init__(self):
        self.page_directory_table = {}`
  },
  {
    id: 'FALSE_SHARING',
    abbr: 'False Sharing',
    full: 'Ложное разделение кэш-линий',
    category: 'hardware',
    definition: 'Аппаратная проблема производительности в многопоточных системах, когда несколько ядер процессора одновременно модифицируют независимые переменные, расположенные на одной физической кэш-линии (64 байта). Это заставляет MESI-протокол бесконечно инвалидировать кэши ядер и пересылать кэш-линию по межъядерной шине туда-сюда, парализуя работу CPU.',
    analogy: 'Два писателя сидят за одним столом и пишут каждый в свой блокнот. Но они используют один и тот же ластик, который лежит посередине. Каждый раз, когда одному нужно стереть слово, он выхватывает ластик у другого, мешая писать и бесконечно отвлекая коллегу.',
    visualType: 'image',
    visualContent: `# Симуляция инвалидации кэшей при False Sharing (Ложном разделении кэш-линий)
class CPUCoreCache:
    def __init__(self, core_id):
        self.core_id = core_id`
  },
  {
    id: 'INPUT_HANDLED',
    abbr: 'Input Handled-State',
    full: 'Поглощение кликов интерфейсом',
    category: 'engine',
    definition: 'Архитектурный паттерн диспетчеризации устройств ввода в игровых движках. Клик мыши сначала передается активному экрану интерфейса (UI). Если элемент UI реагирует на событие, он возвращает флаг handled = true, поглощая клик и блокируя его прохождение в низкоуровневые системы взаимодействия с 3D-миром, предотвращая сквозные клики.',
    analogy: 'Двойная шлюзовая дверь. Пока вы не закроете внешнюю дверь (UI-интерфейс), внутренняя дверь (игровой мир) физически заблокирована для любых манипуляций. Клик «поглощается» активным экраном и не летит дальше в глубины игрового пространства.',
    visualType: 'image',
    visualContent: `# Паттерн диспетчеризации устройств ввода (Input Handled-State) на Python
class MouseClickEvent:
    def __init__(self, x, y, button):
        self.handled = False`
  }
];
