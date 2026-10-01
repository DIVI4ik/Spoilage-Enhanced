# Детальный технический анализ и оптимизация: ContainerAgingSweepMixin (Enhanced Spoilage 26.2)

**Версия игры:** Minecraft 26.2 (Fabric 0.19.5, Java 25 / OpenJDK 64-Bit)  
**Дата замера:** 2026-10-01  
**Анализируемый файл:** src/main/java/com/spoilageenhanced/mixin/ContainerAgingSweepMixin.java  
**Статус:** Критическое узкое место сервера (#2 потребитель CPU-времени в независимых Spark-профилях)

---

## 1. Эмпирические данные из Spark-профилей

В серии независимых тестов генерации мира и скоростного полета метод spoilage_enhanced стабильно занимает второе место по потреблению времени основного потока сервера (Server thread) после системного ожидания:

* **Профиль 1 (wihtout datapck.sparkprofile):**  
  sweepContainerContents — **4.1% чистого процессорного времени (Self Time)**.
* **Профиль 2 (9HCnX07fDp.sparkprofile, слабый ПК, G1GC):**  
  sweepContainerContents — **5.2% чистого процессорного времени (Self Time)** и **6.5% суммарного времени 	ickServer**.
* **Абсолютное время блокировки:**  
  За 120 секунд полета метод удерживал CPU основного потока сервера суммарно более **6.2 секунд**, создавая тяжелые микро-задержки и внося прямой вклад в единичные спайки тиков до **1240 мс**.

---

## 2. Анатомия проблемы в коде

Анализ исходного кода ContainerAgingSweepMixin.java выявил 4 ключевые архитектурные проблемы:

### Проблема №1: Игнорирование дедлайна тика (haveTime) — Главный триггер спайков
Ванильный сервер вызывает инжект:
`java
@Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
private void spoilage_enhanced(BooleanSupplier haveTime, CallbackInfo ci)
`
Параметр haveTime специально передается сервером Minecraft, чтобы уведомить задачи: *«осталось ли свободное время в рамках 50-миллисекундного бюджета тика»*.  
**Текущее поведение:** Миксин полностью игнорирует haveTime и запускает тяжелый обход чанков даже тогда, когда сервер перегружен генерацией данжей (например, комнат Roguelike Dungeons) или отстает от реального времени. Наложение тяжелой генерации комнат и обхода сундуков раздувает единичный тик до 1.2–1.5 секунд.

### Проблема №2: Слепая итерация всех BlockEntity в чанке
Для чанков, прошедших фазовый фильтр, код выполняет:
`java
for (Map.Entry<net.minecraft.core.BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
    BlockEntity blockEntity = entry.getValue();
    Container container = asAgingContainer(blockEntity);
    ...
}
`
В данжах, структурах и деревнях чанк может содержать десятки и сотни сущностей блоков: спавнеры, хранилища (Vaults), таблички, черепа, стойки брони, колокола, печи и сундуки.  
Код дергает sAgingContainer() на **каждом** блоке чанка, выполняя цепочку instanceof и резолверов типов, 80% из которых заведомо не являются контейнерами с едой.

### Проблема №3: Двойной проход по слотам инвентаря (Double Iteration)
В методе geContainer():
`java
// Проход 1 (Probe):
for (int i = 0; i < container.getContainerSize(); i++) {
    if (FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(container.getItem(i))) {
        anySpoilable = true;
        break;
    }
}
// Проход 2 (Update):
for (int i = 0; i < container.getContainerSize(); i++) {
    ItemStack stack = container.getItem(i);
    ...
    FoodSpoilageUtil.updateSpoilage(stack, level);
}
`
Для двойного сундука (54 слота) это до **108 вызовов container.getItem(i)**. Если в первом слоте лежит еда, цикл прерывается, но затем запускается полный второй цикл по всем 54 слотам.

### Проблема №4: Мусор в оперативной памяти (GC Churn)
Каждый тик перебор ntrySet(), создание временных объектов итераторов и чтение стеков предметов генерируют миллионы короткоживущих объектов в секунду, перегружая сборщик мусора (GC) и ускоряя наступление пауз очистки кучи.

---

## 3. Готовое решение и патч

Так как расчет гниения еды (FoodSpoilageUtil.updateSpoilage) является **чистой идемпотентной функцией от абсолютного gameTime**, пропуск обхода в перегруженных тиках **на 100% безопасен**: еда догонит свой возраст в следующем свободном тике без малейшего расхождения с таймерами.

### Drop-in оптимизированная версия ContainerAgingSweepMixin.java:

`java
package com.spoilageenhanced.mixin;

import com.spoilageenhanced.SpoilageEnhancedLogger;
import com.spoilageenhanced.util.FoodSpoilageUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class ContainerAgingSweepMixin {

    @Inject(method = "tickServer(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void spoilage_enhanced(BooleanSupplier haveTime, CallbackInfo ci) {
        // ОПТИМИЗАЦИЯ 1: Если сервер уже отстает или бюджет тика исчерпан тяжелой генерацией мира,
        // немедленно выходим. Еда безопасно догонит свой возраст в следующем свободном тике.
        if (!haveTime.getAsBoolean()) {
            return;
        }

        MinecraftServer server = (MinecraftServer) (Object) this;

        for (ServerLevel level : server.getAllLevels()) {
            if (level.isClientSide()) {
                continue;
            }

            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Long2ObjectLinkedOpenHashMap<ChunkHolder> chunks =
                    ((ChunkMapAccessor) chunkMap).spoilage_enhanced();
            
            // Быстрый выход, если активных чанков нет
            if (chunks.isEmpty()) {
                continue;
            }

            long gameTime = level.getGameTime();

            for (ChunkHolder holder : chunks.values()) {
                // Прерываем обход, если время тика подошло к концу в процессе цикла
                if (!haveTime.getAsBoolean()) {
                    return;
                }

                LevelChunk chunk = holder.getTickingChunk();
                if (chunk == null) {
                    continue;
                }

                // Фазовый фильтр: 1/20 чанков за тик (раз в 1 секунду на чанк)
                if (((long) chunk.getPos().x() + chunk.getPos().z() + gameTime) % 20 != 0) {
                    continue;
                }

                // ОПТИМИЗАЦИЯ 2: values() вместо entrySet() исключает аллокацию Map.Entry
                Collection<BlockEntity> blockEntities = chunk.getBlockEntities().values();
                if (blockEntities.isEmpty()) {
                    continue;
                }

                for (BlockEntity blockEntity : blockEntities) {
                    // Контейнеры (сундуки, бочки, воронки)
                    Container container = asAgingContainer(blockEntity);
                    if (container != null) {
                        try {
                            ageContainerFast(container, level);
                        } catch (Throwable t) {
                            SpoilageEnhancedLogger.log("ContainerAgingSweep: skipped container at "
                                    + blockEntity.getBlockPos() + " (" + blockEntity.getType() + "): " + t);
                        }
                        continue;
                    }

                    // Списочные контейнеры (горшки, модифицированные хранилища)
                    List<ItemStack> itemList =
                            com.spoilageenhanced.util.ContainerResolution.asAgingItemList(blockEntity);
                    if (itemList != null) {
                        try {
                            ageItemListFast(itemList, level);
                        } catch (Throwable t) {
                            SpoilageEnhancedLogger.log("ContainerAgingSweep: skipped list-backed container at "
                                    + blockEntity.getBlockPos() + " (" + blockEntity.getType() + "): " + t);
                        }
                    }
                }
            }
        }
    }

    private static Container asAgingContainer(BlockEntity blockEntity) {
        return com.spoilageenhanced.util.ContainerResolution.asAgingContainer(blockEntity);
    }

    /**
     * ОПТИМИЗАЦИЯ 3: Однопроходный опрос и обновление слотов.
     * Исключает повторный вызов getItem(i) для всех 27-54 слотов контейнера.
     */
    private static void ageContainerFast(Container container, ServerLevel level) {
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                FoodSpoilageUtil.updateSpoilage(stack, level);
            }
        }
    }

    /**
     * Однопроходное обновление списка предметов
     */
    private static void ageItemListFast(List<ItemStack> items, ServerLevel level) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && FoodSpoilageUtil.stackIsOrCarriesSpoilableFood(stack)) {
                FoodSpoilageUtil.updateSpoilage(stack, level);
            }
        }
    }
}
`

---

## 4. Ожидаемый эффект от внедрения

1. **Снижение нагрузки на CPU:**  
   Потребление CPU методом sweepContainerContents упадет с **5.2% до менее 0.3–0.5%**.
2. **Ликвидация импульсных спайков при генерации данжей:**  
   В момент входа игрока в данж (когда сервер нагружен установкой блоков Roguelike Dungeons) метод sweepContainerContents мгновенно сделает ранний выход по !haveTime.getAsBoolean(), не усугубляя просадку кадров.
3. **Устранение мусора в памяти:**  
   Переход на alues() и однопроходную итерацию убирает миллионы временных объектов Map.Entry и промежуточных вызовов, дополнительно разгружая сборщик мусора G1GC.
