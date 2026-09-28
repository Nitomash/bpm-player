# План: сортировка плейлистов и треков внутри плейлистов

## Скоуп фичи

**Внутри скоупа:**
1. Сортировка списка плейлистов (drawer на вкладке Playlists): по дате изменения, по алфавиту, пользовательская (перетаскивание).
2. Сортировка треков внутри плейлиста: **своя** (заданный порядок), по возрастанию BPM, по убыванию BPM, по дате добавления в плейлист, по алфавиту.
3. Режим сортировки **индивидуален для каждого плейлиста** и переживает перезапуск приложения.
4. Пользовательский порядок задаётся перетаскиванием в отдельном режиме экрана плейлиста и в drawer.
5. Порядок воспроизведения следует видимому порядку (сортированному), а не «сырому» `position`.

**За границей (не в скоупе):**
- Сохранение/загрузка сортировки в XSPF и sidecar-файл (XSPF в проекте отсутствует).
- Сортировка папок/треков в Library (`ui/library/TrackSort.kt`) — там та же проблема, но фича отдельная (см. «Побочные находки»).
- Синхронизация сортировки между устройствами.

## Что показала кодовая база

**Готово и переиспользуемо:**
- `playlist_tracks.position` (`PlaylistTrackEntity.kt:35`) — хранение пользовательского порядка треков уже есть. **Но `position` входит в составной PK** `(playlist_id, position)` (`PlaylistTrackEntity.kt:10`) → `UPDATE position` невозможен (конфликт с самим собой), перестановка возможна только «прочитал всё → удалил всё → вставил заново». Готовый паттерн есть: `PlaylistDao.removeTracks` (`PlaylistDao.kt:72–77`).
- Транзакционный паттерн + `getMaxPosition` для append (`PlaylistDao.kt:57–58`).
- Чистые функции сортировки как прецедент: `ui/library/TrackSort.kt` (enum + `sortTracks`, покрыт `TrackSortTest`).
- `TypeConverter` для enum↔Int как прецедент: `data/db/Converters.kt` + `BpmSource` (`data/db/entity/BpmSource.kt`, `fromValue` c fallback).
- `PlaylistSummary` (`data/playlist/PlaylistSummary.kt`) — проекция для drawer.
- Room 2.7.0 → `@Upsert` доступен.

**Отсутствует:**
- Ни одного сохранённого режима сортировки: ни колонки, ни ключа в DataStore. `AppSettings` (`data/settings/AppSettings.kt:5–16`) содержит только диапазон BPM-детектора.
- Reorder-API: в `PlaylistDao` нет ни `reorder`, ни `updatePosition`; в `PlaylistRepository` (`data/playlist/PlaylistRepository.kt:7–19`) — тоже.
- `position` у самих плейлистов нет вообще; список drawer отсортирован жёстко в SQL: `ORDER BY p.created_at_ms DESC, p.id DESC` (`PlaylistDao.kt:23`).
- Даты «добавления в плейлист» нет: в `playlist_tracks` только `(playlist_id, track_id, position)`.
- Drag & drop нет нигде: `ui/common/TrackList.kt:70–98` — голый `LazyColumn`; в `libs.versions.toml` нет ни reorder-библиотеки, ни gesture-хелперов. `TrackRow` (`ui/common/TrackRow.kt:42–46`) умеет только `combinedClickable(onClick, onLongClick)`.
- `updated_at_ms` плейлиста обновляется **только** при переименовании (`PlaylistDao.kt:30–31`), но не при изменении состава треков → «по дате изменения» сейчас не работает.
- `PlaylistTrack` (`domain/playlist/PlaylistGenerator.kt:16`) не несёт позиции; `PlaylistOrder` (`PlaylistGenerator.kt:6–10`) — параметр только на момент генерации, никуда не сохраняется (выживает лишь текстом в имени плейлиста, `GeneratorViewModel.kt:106–120`).
- Тестовые зависимости — **только JUnit 4** (`app/build.gradle.kts:73`); тесты используют `runBlocking` + рукописные фейки (`app/src/test/.../FakePlaylistDao.kt`). Ни Robolectric, ни `kotlinx-coroutines-test`, ни `room-testing` в проекте нет.

## Принятые решения

| Развилка | Решение | Почему |
|---|---|---|
| Где хранить режим сортировки треков | **Колонка `playlists.track_sort_mode`** | Состояние живёт рядом с плейлистом, переживает перезапуск, не требует чистки при удалении плейлиста |
| Пользовательский порядок треков | **Уже есть** — `playlist_tracks.position` | Отдельная колонка не нужна; `CUSTOM`-режим читает `position` |
| Где хранить режим сортировки списка плейлистов | **DataStore** (`AppSettings.playlistListSort`) | Список плейлистов один на приложение, это UI-предпочтение, а не данные плейлиста |
| Пользовательский порядок плейлистов | **Отдельная таблица `playlist_order`** | `playlists` остаётся без служебной колонки; таблица же может хранить глобальный порядок с PK по `playlist_id` (см. ниже) |
| «Дата изменения» трека в плейлисте | **`playlist_tracks.added_at_ms`** (дата добавления) | Даты изменения метаданных трека в проекте нет вообще (в `tracks` только `added_at_ms` сканирования) |
| Где сортировать | **В памяти, чистыми функциями** | Единый путь для обоих списков, юнит-тесты без Robolectric, нет дублей `ORDER BY` в DAO |
| Как делать drag & drop | **Своё, на `detectDragGesturesAfterLongPress`**, без новых зависимостей | В проекте нет сторонних UI-библиотек; AGENTS требует согласовывать новые зависимости |

## Модель данных

### Перечисления

```kotlin
// data/playlist/PlaylistTrackSort.kt — персистится в playlists.track_sort_mode
enum class PlaylistTrackSort(val value: Int) {
    CUSTOM(0),
    BPM_ASC(1),
    BPM_DESC(2),
    DATE_ADDED(3),
    TITLE(4),
    ;

    companion object {
        fun fromValue(value: Int): PlaylistTrackSort =
            entries.firstOrNull { it.value == value } ?: CUSTOM
    }
}

// data/playlist/PlaylistListSort.kt — персистится в DataStore
enum class PlaylistListSort(val value: Int) {
    DATE_MODIFIED(0),
    TITLE(1),
    CUSTOM(2),
    ;

    companion object {
        fun fromValue(value: Int): PlaylistListSort =
            entries.firstOrNull { it.value == value } ?: DATE_MODIFIED
    }
}
```

`CUSTOM = 0` и `DATE_MODIFIED = 0` — чтобы миграция на существующих данных не меняла поведение: плейлисты, отсортированные «как раньше», получают тот же порядок, что и до обновления.

`fromValue` с fallback (как в `BpmSource.fromValue`) — обязателен: значение из БД/DataStore может быть неизвестным после downgrade/правки.

### Сущности

```kotlin
// data/db/entity/PlaylistEntity.kt — добавить поле
@ColumnInfo(name = "track_sort_mode", defaultValue = "0")
val trackSortMode: PlaylistTrackSort = PlaylistTrackSort.CUSTOM,

// data/db/entity/PlaylistTrackEntity.kt — добавить поле
@ColumnInfo(name = "added_at_ms", defaultValue = "0")
val addedAtMs: Long = 0L,
```

```kotlin
// data/db/entity/PlaylistOrderEntity.kt — новая таблица
@Entity(
    tableName = "playlist_order",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistOrderEntity(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    val position: Int,
)
```

**Почему PK только `playlist_id`:** пользовательский порядок плейлистов глобальный и единственный — по одной строке на плейлист. Из этого следует главное преимущество перед `playlist_tracks`: перестановка — это обычный `UPDATE playlist_order SET position = ? WHERE playlist_id = ?`, без конфликтов, без «удалить всё и вставить заново». Позиции могут совпадать (ties) — tie-break по `id`.

Индекс по `position` не нужен: сортировка списка плейлистов выполняется в памяти, а строки десятки.

### Проекции

```kotlin
// data/playlist/PlaylistTrackRow.kt
data class PlaylistTrackRow(
    @Embedded val track: TrackEntity,
    val position: Int,
    @ColumnInfo(name = "added_to_playlist_at_ms")
    val addedToPlaylistAtMs: Long,
)
```

**Важно:** псевдоним `added_to_playlist_at_ms` обязателен. И `tracks`, и `playlist_tracks` имеют колонку `added_at_ms` (`TrackEntity.kt:47`), при `SELECT t.*, pt.added_at_ms` в проекции окажутся два поля с одним именем — Room не сможет собрать POJO.

```kotlin
// data/playlist/PlaylistSummary.kt — расширить
data class PlaylistSummary(
    val id: Long,
    val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "sort_position") val sortPosition: Int,
)
```

### Конвертеры

В `data/db/Converters.kt` добавить пару по образцу `bpmSourceFromValue/bpmSourceToValue`:

```kotlin
@TypeConverter
fun playlistTrackSortFromValue(value: Int?): PlaylistTrackSort? = value?.let(PlaylistTrackSort::fromValue)

@TypeConverter
fun playlistTrackSortToValue(sort: PlaylistTrackSort?): Int? = sort?.value
```

`@TypeConverters(Converters::class)` уже висит на `BpmDatabase` (`BpmDatabase.kt:35`) — регистрация не нужна.

## Миграция 2 → 3

`BpmDatabase.version = 2` (`BpmDatabase.kt:32`), определена одна миграция. Добавляем `MIGRATION_2_3` и регистрируем в `di/AppModule.kt:36–39`.

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `track_sort_mode` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `playlist_tracks` ADD COLUMN `added_at_ms` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE `playlist_tracks` SET `added_at_ms` = COALESCE(" +
                "(SELECT p.`created_at_ms` FROM `playlists` p WHERE p.`id` = `playlist_tracks`.`playlist_id`), 0)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_order` (" +
                "`playlist_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`playlist_id`), " +
                "FOREIGN KEY(`playlist_id`) REFERENCES `playlists`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT OR IGNORE INTO `playlist_order` (`playlist_id`, `position`) " +
                "SELECT p.`id`, (SELECT COUNT(*) FROM `playlists` p2 " +
                "WHERE p2.`created_at_ms` > p.`created_at_ms` " +
                "OR (p2.`created_at_ms` = p.`created_at_ms` AND p2.`id` > p.`id`)) " +
                "FROM `playlists` p",
        )
    }
}
```

Подводные камни:

- **Без оконных функций.** `minSdk = 26` (`app/build.gradle.kts:18`) → SQLite 3.18, `ROW_NUMBER()` доступен только с 3.25 (API 30). Поэтому ранг считается коррелированным подзапросом. Бэкфилл воспроизводит текущий порядок drawer (`created_at_ms DESC, id DESC`) → при первом переключении в `CUSTOM` список не «прыгает».
- **`defaultValue` в `@ColumnInfo` обязателен** и должен совпадать с `DEFAULT` в `ALTER TABLE`, иначе Room при открытии БД получит расхождение схемы (`TableInfo.Column.defaultValue`).
- **DDL `playlist_order` должен совпасть** с тем, что сгенерирует Room. Сверить по `app/schemas/…/3.json` после сборки (файл появится сам, `room.schemaLocation` уже настроен, `app/build.gradle.kts:46–48`).
- Таблицы `playlists`/`playlist_tracks` существуют с v1 — их структура не меняется, только добавление колонок.

## Где выполняется сортировка

Сортируем **в памяти чистыми функциями**, SQL всегда отдаёт нейтральный порядок. Причины: один механизм для обоих списков; тесты без Robolectric; смена режима не требует вариантов `ORDER BY` в DAO и не ломает инвалидацию `Flow` (режим приходит из отдельного потока).

```kotlin
// data/playlist/Sorting.kt
object PlaylistTrackSorter {
    fun sort(rows: List<PlaylistTrackRow>, mode: PlaylistTrackSort): List<PlaylistTrackRow>
}

object PlaylistListSorter {
    fun sort(summaries: List<PlaylistSummary>, mode: PlaylistListSort): List<PlaylistSummary>
}
```

Правила:

- `CUSTOM` → по `position` (для плейлистов — по `sortPosition`, tie-break по `id`).
- `BPM_ASC` / `BPM_DESC` → по `bpm`, **null-BPM в конец** в обоих режимах, tie-break по `title` (как в `TrackSort.kt:7–20`), затем по `id`.
- `DATE_ADDED` → по `addedToPlaylistAtMs` по убыванию (новые сверху), tie-break по `position`.
- `TITLE` → для треков `track.title.trim().lowercase()`, для плейлистов `name.trim().lowercase()`; в обоих случаях tie-break по `id` (сравнение лексикографическое, без локали).
- Пустой вход → пустой выход; один элемент → без копий.

Файлы кладутся в `data/playlist/`, а не в `domain/playlist/`: проекция `PlaylistTrackRow` помечена `@Embedded` (androidx.room), и тащить её в «чистый» domain нельзя. Сами функции Android-зависимостей не имеют и тестируются обычным JUnit.

Порог, когда сортировку пора переносить в SQL: плейлисты на тысячи треков. `tracks` уже имеет индексы по `bpm` и `title` (`TrackEntity.kt:14–15`), так что миграция на `ORDER BY`-варианты — локальная задача DAO.

## API слоя данных

### DAO

```kotlin
@Query(
    "SELECT t.*, pt.position AS position, pt.added_at_ms AS added_to_playlist_at_ms " +
        "FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id " +
        "WHERE pt.playlist_id = :playlistId ORDER BY pt.position",
)
fun observeTrackRows(playlistId: Long): Flow<List<PlaylistTrackRow>>

@Query("SELECT track_sort_mode FROM playlists WHERE id = :playlistId")
suspend fun getTrackSortMode(playlistId: Long): PlaylistTrackSort?

@Query("UPDATE playlists SET updated_at_ms = :updatedAtMs WHERE id = :playlistId")
suspend fun touch(playlistId: Long, updatedAtMs: Long)

@Transaction
suspend fun createPlaylist(playlist: PlaylistEntity): Long {
    val id = insert(playlist)
    upsertPlaylistOrder(
        listOf(PlaylistOrderEntity(playlistId = id, position = getMaxPlaylistOrderPosition() + 1)),
    )
    return id
}

@Transaction
suspend fun setTrackOrder(playlistId: Long, trackIds: List<Long>): Boolean {
    val byId = getPlaylistTrackRows(playlistId).associateBy { it.trackId }
    val requested = trackIds.toSet()
    if (requested.size != trackIds.size) return false
    if (byId.keys != requested) return false
    deleteAllTracks(playlistId)
    insertAll(trackIds.mapIndexed { index, trackId -> byId.getValue(trackId).copy(position = index) })
    return true
}

@Query("SELECT COALESCE(MAX(position), -1) FROM playlist_order")
suspend fun getMaxPlaylistOrderPosition(): Int

@Upsert
suspend fun upsertPlaylistOrder(rows: List<PlaylistOrderEntity>)

@Query("DELETE FROM playlist_order WHERE playlist_id NOT IN (:keepIds)")
suspend fun deletePlaylistOrderExcept(keepIds: List<Long>)

@Transaction
suspend fun setPlaylistOrder(playlistIds: List<Long>) {
    val distinct = playlistIds.distinct()
    if (distinct.isEmpty()) return
    deletePlaylistOrderExcept(distinct)
    upsertPlaylistOrder(distinct.mapIndexed { index, id -> PlaylistOrderEntity(id, index) })
}
```

Существующие `observeTracks`/`getTracks` (`PlaylistDao.kt:39–49`) удалены, `PlaylistTrackEntity` больше не протекает в UI. `getTracksNotInPlaylist` осталась как была.

Два решения, которые важны:

- **`setTrackOrder` сверяет множества, а не размеры.** Проверка «столько же элементов, сколько строк» пропускала бы дубликаты: `setTrackOrder(id, [10, 10])` проходил бы как «валидный» набор из двух id, а на живой БД `UNIQUE(playlist_id, track_id)` отверг бы вторую вставку. Поэтому сравнивается именно `byId.keys == requested` плюс отдельная проверка на дубликаты; результат — `Boolean`, и репозиторий трогает `updated_at_ms` только при успешной записи.
- **`setPlaylistOrder` авторитетна:** строки плейлистов, не попавших в переданный список, удаляются. Иначе после перетаскивания остались бы старые позиции, которые могут совпасть с новыми. Плейлист без строки_ORDER отсортируется в хвост через `COALESCE(..., 2147483647)`. Пустой список — no-op (иначе `NOT IN ()` — синтаксическая ошибка SQLite).

`observeSummaries` переписывается: убрать `ORDER BY` из SQL (нейтральный `ORDER BY p.id`), добавить `p.updated_at_ms`, `COALESCE(po.position, 2147483647) AS sort_position` и `LEFT JOIN playlist_order`:

```sql
SELECT p.id AS id, p.name AS name, p.updated_at_ms AS updated_at_ms,
       COALESCE(po.position, 2147483647) AS sort_position,
       COUNT(pt.playlist_id) AS track_count
FROM playlists p
LEFT JOIN playlist_order po ON po.playlist_id = p.id
LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
GROUP BY p.id
ORDER BY p.id
```

`GROUP BY p.id` с «голыми» колонками `po.position` здесь безопасны: `playlist_order` содержит не более одной строки на плейлист, поэтому значение одинаково во всех строках группы. `COALESCE(..., 2147483647)` — плейлист без строки в `playlist_order` уходит в конец пользовательского порядка (страховка на случай, если строка куда-то не попала).

### Репозиторий

```kotlin
fun observeTrackRows(playlistId: Long): Flow<List<PlaylistTrackRow>>
suspend fun getTracksInDisplayOrder(playlistId: Long): List<TrackEntity>
suspend fun setTrackOrder(playlistId: Long, trackIds: List<Long>)
suspend fun setTrackSortMode(playlistId: Long, sort: PlaylistTrackSort)
suspend fun setPlaylistOrder(playlistIds: List<Long>)
```

`getTracksInDisplayOrder` = `PlaylistTrackSorter.sort(dao.getTrackRows(id), dao.getTrackSortMode(id) ?: CUSTOM).map { it.track }` — единственная точка, где «видимый порядок» превращается в очередь. Режим читается точечным запросом, а не через `observeById(...).first()`, чтобы не тянуть Flow ради одного значения.

`addTracks` (`PlaylistRepositoryImpl.kt:47–56`) проставляет `addedAtMs = now` всем добавляемым и `touch()` плейлист. `removeTracks` — `touch()`. `create` идёт через `dao.createPlaylist`, который в одной транзакции создаёт плейлист **и** строку `playlist_order` с `position = getMaxPlaylistOrderPosition() + 1`, чтобы новый плейлист сразу стоял в конце пользовательского порядка.

## Когда обновляется `updated_at_ms`

Именно это делает «по дате изменения» рабочим. Обновляем при: переименовании (уже есть, `PlaylistDao.kt:30`), `addTracks`, `removeTracks`, `setTrackOrder`. **Не** обновляем при смене режима сортировки — это изменение представления, а не содержимого; иначе переключение сортировки «поднимало» плейлист в списке.

## Очередь воспроизведения

`PlaylistDetailViewModel.playFrom(index)` (`PlaylistDetailViewModel.kt:54–60`) сейчас берёт `playlistRepository.getTracks()` — «сырой» порядок `position`. Меняем на `getTracksInDisplayOrder`, чтобы клик по треку в отсортированном по BPM-убыванию списке играл в том же порядке. То же в `PlaylistListViewModel.play()` (`PlaylistListViewModel.kt:42–49`).

Очередь, уже загруженная в плеер, при смене сортировки **не перезапрашивается** — перезагрузка очереди посреди прослушивания была бы неожиданной. Обновится при следующем `playFrom`/`play`.

## UI

### Режимы экрана плейлиста

Явный режим нужен потому, что жест перетаскивания требует long-press (см. «Почему long-press»), а long-press на строке уже занят входом в режим выделения. В режиме перетаскивания жест живёт на отдельной ручке, и долгое нажатие по строке в этом режиме не делает ничего, так что конфликта не остаётся:

```kotlin
enum class PlaylistDetailMode { NORMAL, SELECT, REORDER }
```

`selectionMode: Boolean` в экране заменён на `mode`, но для UI оставлен производный `StateFlow<Boolean>` (`mode == SELECT`), чтобы `TrackList` и TopBar не переписывались целиком. Правила:

- TopBar в `NORMAL`: меню сортировки (галочка на активном режиме) + иконка перетаскивания (вход в `REORDER`) + «Добавить треки».
- `REORDER` доступен только при `trackSortMode == CUSTOM` и больше одного трека. **Кнопка не переключает режим автоматически, а заблокирована** (`enabled = canReorder`), с `contentDescription = «Свой порядок недоступен при выбранной сортировке»`. Это отличается от первоначального плана (авто-переключение в `CUSTOM` + snackbar): режим сортировки — это то, что пользователь выбирал осознанно, и молча переписывать его при нажатии кнопки перетаскивания — сюрприз. Сначала выбираешь «Свой порядок», потом жмёшь ручку.
- В `REORDER`: TopBar показывает подсказку «Перетащите элементы…», кнопку ✕ (сбросить) и ✓ (записать). Клик по строке ничего не делает — воспроизведение из неотсортированного черновика было бы неоднозначно.
- Запись в БД ровно одна, на нажатии ✓. ✕ и смена сортировки на не-`CUSTOM` — сброс черновика. Системная кнопка «назад» из `REORDER` тоже теряет черновик (ViewModel уничтожается вместе с экраном) — сознательно, переоткрытие экрана начинает с сохранённого в БД порядка.

### Drag: библиотека, не своя реализация

`ui/common/ReorderableList.kt` — тонкая обёртка над `sh.calvin.reorderable` (3.1.0, Apache-2.0, согласовано пользователем 26.09.2026):

```kotlin
val listState = rememberLazyListState()
val reorderState = rememberReorderableLazyListState(listState) { from, to ->
    onMove(from.index, to.index)
}
LazyColumn(state = listState) {
    items(items, key = key) { item ->
        ReorderableItem(reorderState, key = key(item)) {
            itemContent(item, Modifier, Modifier.draggableHandle())
        }
    }
}
```

Библиотека даёт: перетаскивание **только за ручку без задержки** (`draggableHandle` на дочернем элементе), автоскролл у краёв списка, анимацию переставляемого элемента (`animateItem`), тактильный отклик и `MutableInteractionSource`.

**Почему своя реализация была отвергнута.** Четыре неудачные сборки подряд, и причины оказались неочевидными (замерено на Compose 1.7.6):

1. `Modifier.pointerInput` на элементе списка поднимается **лениво, при первом касании**, а не при композиции: замерено старт coroutine через 725 мс после сборки списка и через 8 мс после DOWN. `awaitEachGesture` перед своим блоком ждёт, пока все пальцы отпустятся, поэтому **первое касание всегда пропускалось**. Обработчик пришлось бы переносить на контейнер списка.
2. Предок обрабатывается раньше потомка: `scrollable` у `LazyColumn` отменяется, только если уже съедено **первое перемещение** (съеденный `down` не в счёт — поэтому `clickable` и не мешает прокрутке). Собственный детектор с собственным slop внутри `PointerEventPass.Initial` заработал бы, но это ровно тот участок, где легко ошибиться в незаметную сторону.
3. `LazyListItemInfo.size` — это `Int` (высота элемента), а не `IntSize`; ошибка не ловится компилятором при обращении через `info.size`.
4. Импорт `Modifier.size`/`Modifier.height` перебивает одноимённые свойства `IntSize` — нужен алиас.

Правило на будущее: арбитраж жеста с прокруткой в пределах скроллящегося списка не пишется руками. Если библиотеки перестанет хватать — сначала читать её исходники в распакованном AAR (`javap` по кэшу Gradle), а не экспериментировать на устройстве.

**Что осталось наше.** `ListReorder.moved(from, to)` — чистая перестановка списка, её вызывает `onMove` из `PlaylistListViewModel.onReorderMove` / `PlaylistDetailViewModel.onReorderMove`, 6 тестов. Черновик порядка живёт во ViewModel (`reorderIds: List<Long>?`), потому что у библиотеки своё внутреннее состояние перетаскивания. Отмена = черновик `null`; плюс список обёрнут в `key(reorderSession)`, иначе после отмены остался бы «призрачный» порядок.

Оговорки: `key` элементов стабильный (`it.id` в обоих новых списках); вложенных скроллящихся `LazyColumn` на этих экранах нет, в `LibraryScreen` есть — там drag не применяем.

Альтернатива — сторонняя `sh.calvin.reorderable`; отклонена, новая зависимость (согласование по AGENTS) ради ~100 строк.

### Drawer списка плейлистов

По правкам пользователя (после первой сборки UI) в drawer'е **нет кнопки входа в перетаскивание**, но **кнопка выбора сортировки есть** — вернулась по запросу, без неё список нельзя было вернуть из `CUSTOM` в «по дате изменения» / «по названию». Механизм:

- **Долгое нажатие на плейлист** входит в режим перетаскивания (`combinedClickable(onLongClick = enterReorder)`).
- Кнопка сортировки (две полоски) — меню из трёх режимов с галочкой на активном: по дате изменения / по названию / свой порядок.
- В режиме перетаскивания у каждой строки **перед** названием появляется ручка (три полоски), перетаскивание — **только за неё**; тап по строке в этом режиме ничего не делает, кнопок play/delete нет. В шапке вместо кнопки сортировки показывается ✓ — менять сортировку, не закончив перетаскивание, нельзя.
- ✓ в шапке drawer'а сохраняет: сначала `setPlaylistOrder`, затем режим списка переключается в `CUSTOM`, и только потом сбрасывается черновик. Порядок именно такой — если сначала переключить режим, список на мгновение пересортировался бы по старому `playlist_order` и прыгнул; сейчас к моменту сброса черновика БД уже содержит ровно его порядок, поэтому переход незаметен.
- Войти в режим можно из **любого** режима сортировки, и если это не `CUSTOM`, сохранение делает сортировку пользовательской («бесшовно»).
- Жест перетаскивания — **только с ручки** в начале строки, по long-press (см. «Почему long-press»). Всё остальное в строке: обычная прокрутка, а в обычном режиме — тап открывает плейлист и долгое нажатие входит в перетаскивание.
- Смена режима сортировки сбрасывает несохранённый черновик. Явной кнопки ✕ нет — отмена = закрыть drawer (`LaunchedEffect(drawerState.currentValue)`).

### Порядок добавления треков

`AddTracksViewModel` хранит выделение в `Set<Long>` (`AddTracksViewModel.kt:55–61`), порядок обхода экрана теряется, и в `addTracks` уходит произвольный порядок `Set`. Для `DATE_ADDED`/`CUSTOM` это неверно.

Лечится без смены типа состояния: в `save()` выделенные id пропускаются через `_candidates` в порядке экрана (`selectedInCandidateOrder`). Состояние остаётся `Set` — экран и `TrackList` меняются только в этапе 4, а порядок добавления становится детерминированным и не зависящим от порядка нажатий.

## Строки

`strings.xml` (значения — русские, имена — английские), фактический набор: `playlist_sort_menu`, `playlist_sort_custom`, `playlist_sort_bpm_asc`, `playlist_sort_bpm_desc`, `playlist_sort_date_added`, `playlist_sort_title`, `playlist_list_sort_modified`, `playlist_reorder_start`, `playlist_reorder_done`, `playlist_reorder_hint`, `playlist_reorder_disabled`, `playlist_drag_handle`. Снапкара «переключили на свой порядок» не понадобилась.

## Тесты

Существующий стиль — JUnit 4 + `runBlocking` + рукописные фейки (`FakePlaylistDao`). Новых зависимостей для основной части тестов не нужно; `FakePlaylistDao` расширяется (новые методы, `addedAtMs` в `rowsOf`).

- `data/playlist/PlaylistTrackSorterTest` — каждый режим (в т.ч. `TITLE`); null-BPM в конец; tie-break по названию; `CUSTOM` с дырками в `position`; пустой и одноэлементный вход.
- `data/playlist/PlaylistListSorterTest` — дата (новые сверху), алфавит (регистронезависимо, tie-break по `id`), `CUSTOM` с `sortPosition = Int.MAX_VALUE` в хвосте.
- `data/playlist/PlaylistRepositoryImplTest` (дополнить) — `setTrackOrder` сохраняет порядок и **`addedAtMs` не теряется**; чужой/неполный/дублирующийся набор отклоняется без записи; другой плейлист не затронут; `updated_at_ms` меняется; `addTracks` проставляет `addedAtMs`; `create` добавляет строку `playlist_order`; `setPlaylistOrder` переставляет и не задевает удалённые плейлисты.
- `data/settings/SettingsMapperTest` (дополнить) — новое поле `playlistListSort`, дефолт, битое значение → дефолт.

Автотеста миграции 2→3 нет: он требует новых зависимостей (`androidx.room:room-testing`, `org.robolectric:robolectric`, `androidx.test:core`), а они не согласованы. Миграция проверяется вручную — процедура ниже. Плюс после сборки нужно глазами сверить `app/schemas/…/3.json` с текстом `MIGRATION_2_3`: колонки, `NOT NULL`, `DEFAULT`, FK `playlist_order`.

## Ручная проверка миграции 2 → 3

Миграция выполняется один раз — при первом открытии БД новой версией. Проверять её нужно на реальном устройстве поверх текущей установки, потому что данные v2 накапливались вручную.

**0. Подготовить данные на текущей версии (2):**
- 2–3 плейлиста с разными именами (включая кириллицу и разный регистр — проверка лексикографии) и **разными датами создания**;
- в каждом плейлисте 3+ трека, в разном порядке `position`;
- часть плейлистов с NULL-BPM треками (сортировка по BPM должна уводить их в конец);
- один трек без тегов/обложки не нужен, достаточно разного BPM;
- **записать текущий порядок плейлистов в drawer и порядок треков в каждом плейлисте** (скриншоты) — это эталон для проверки, что ничего не «прыгнуло».

**1. Установить новую версию поверх старой (данные должны сохраниться).**
Удалять старую версию перед установкой **нельзя** — вместе с данными. Обычная установка APK поверх (Android Studio Run или `adb install -r`).

**2. Открыть приложение — smoke-проверка:**
- плейлисты на месте, имена и треки целые, счётчик треков совпадает;
- порядок плейлистов в drawer **тот же**, что на эталонных скриншотах (бэкфилл `playlist_order` воспроизводит `created_at_ms DESC, id DESC`);
- порядок треков в плейлисте **тот же** (миграция не трогает `position`);
- в плейлисте появилось меню сортировки со всеми пятью вариантами;
- переключение на «по алфавиту» / «по BPM» / «по дате добавления» не ломает ничего, `NULL` BPM в конце;
- переключение на «своя» для плейлистов даёт **тот же** порядок, что и в drawer до миграции;
- переименование плейлиста поднимает его вверх при сортировке «по дате изменения»;
- добавление трека в плейлист ставит его первым в «по дате добавления» и последним в «своя»;
- удаление трека из плейлиста не ломает позиции оставшихся;
- force-stop и перезапуск приложения: режимы сортировки и расставленный порядок сохранились.

**3. Проверить схему БД (read-only, ADB).**
Room использует WAL, поэтому забирать нужно три файла, иначе часть данных может не попасть в выгрузку:

```bash
adb exec-out run-as com.nitomash.bpmplayer cat databases/bpm_player.db    > v3.db
adb exec-out run-as com.nitomash.bpmplayer cat databases/bpm_player.db-wal > v3.db-wal
adb exec-out run-as com.nitomash.bpmplayer cat databases/bpm_player.db-shm > v3.db-shm
```

`run-as` работает только для debuggable-сборки — это как раз наш случай. Файлы `v3.db*` открыть в DB Browser for SQLite и выполнить:

```sql
PRAGMA user_version;                 -- ожидается 3
PRAGMA table_info(playlists);        -- track_sort_mode INTEGER NOT NULL DEFAULT 0
PRAGMA table_info(playlist_tracks);  -- added_at_ms INTEGER NOT NULL DEFAULT 0
SELECT sql FROM sqlite_master WHERE name = 'playlist_order';
SELECT * FROM playlist_order ORDER BY position;                    -- плотные ранги 0..n-1, без пропусков
SELECT playlist_id, position, added_at_ms FROM playlist_tracks ORDER BY playlist_id, position;
SELECT * FROM playlists;             -- updated_at_ms не должен обнулиться
```

Проверки: `user_version = 3`; в `playlist_order` ровно одна строка на плейлист; позиции — плотная последовательность `0..n-1` в том же порядке, что и эталонный список плейлистов; `added_at_ms` у всех существующих строк `playlist_tracks` равны `created_at_ms` соответствующего плейлиста (бэкфилл), `track_sort_mode` у всех плейлистов `0`.

**4. Откат.**
Возврат на старую версию после миграции приведёт к падению при открытии БД: Room сверяет `identity_hash` из `room_master_table` и откажется открывать базу с более новой схемой. Поэтому перед установкой новой версии стоит сохранить копию `v2.db*` (шаг 3, но до установки) — откат только через uninstall с потерей данных либо через подмену файлов БД.


## Статус

Сборка зелёная: `./gradlew test lintDebug assembleDebug` — 205 тестов, 0 падений, lint без ошибок (только предсуществующие предупреждения о версиях зависимостей и неиспользуемых ресурсах).

- **Этап 0 — сделано**: перечисления, `PlaylistTrackRow`, расширенный `PlaylistSummary`, `Sorting.kt`, тесты сортёров.
- **Этап 1 — сделано и проверено на устройстве**: миграция 2→3 применена, `user_version = 3`, `integrity_check = ok`, `playlist_order` заполнена рангами `0..n-1` в прежнем порядке drawer, `added_at_ms` заполнен у 73/73 строк, последовательности треков не изменились.
- **Этап 2 — сделано**: `observeTrackRows`/`getTrackRows`, `getTrackSortMode`, `touch`, `setTrackSortMode`, `setTrackOrder`, `createPlaylist`, авторитетный `setPlaylistOrder`, репозиторий, `getTracksInDisplayOrder`, порядок добавления; 21 тест репозитория.
- **Этап 3 — сделано**: `AppSettings.playlistListSort`, ключ `playlist_list_sort` в `SettingsMapper` (по явному `value`, не по `ordinal`), 5 новых тестов маппера.
- **Этап 4 — сделано**: `PlaylistDetailMode`, меню сортировки в TopBar, `REORDER` с перетаскиванием за ручку, запись порядка по ✓, черновик порядка во ViewModel, 6 тестов `ListReorder.moved`.
- **Этап 5 — сделано, переделан по правкам пользователя**: вход в перетаскивание по долгому нажатию (кнопки ручки нет), перетаскивание за ручку в начале строки, сохранение по ✓ переводит список в `CUSTOM` без рывка; кнопка выбора сортировки в drawer'е сохранена (сначала убрана по просьбе, затем возвращена — без неё нельзя выйти из `CUSTOM`). Экран треков уравнен с drawer'ом: перетаскивание доступно из любого режима, сохранение само переводит в `CUSTOM`; разница — long-press на строке отдан выделению, вход через кнопку в шапке.
- **Drag — переделан на `sh.calvin.reorderable` 3.1.0** после четырёх неудачных самописных сборок, по решению пользователя. Стек Compose не менялся (библиотека собрана под 1.7.0, у нас 1.7.6).
- **Этап 6 — APK пересобран** (`app/build/outputs/apk/debug/app-debug.apk`, 16:26), ждёт установки пользователем.

Осталось проверить на устройстве (тестами это не ловится): перетаскивание за ручку без задержки, автоскролл у краёв, сохранение `CUSTOM`-порядка плейлистов и треков после перезапуска, отсутствие рывка списка при сохранении из не-`CUSTOM` режима, возврат «по дате изменения» через кнопку сортировки.

**Покрытие тестами:** 192 теста. 6 тестов `ListReorder.moved` (перестановка списка), 21 тест репозитория. Арифметику перетаскивания и раздачу жеста тестами не покрываем — это внутри библиотеки. Правило «сохранение переводит в `CUSTOM`» живёт во ViewModel, а каркаса для тестов ViewModel в проекте нет (`kotlinx-coroutines-test` не подключён) — держится на порядке вызовов в `commitReorder`.

**Этап 0 — Чистые функции** (без БД и UI):
`PlaylistTrackSort` / `PlaylistListSort` → `PlaylistTrackRow`, расширение `PlaylistSummary` → `Sorting.kt` → тесты сортёров.

**Этап 1 — Схема и миграция**:
`PlaylistOrderEntity`, поля в `PlaylistEntity`/`PlaylistTrackEntity`, конвертеры, `MIGRATION_2_3`, `version = 3`, регистрация в `AppModule`, экспорт `3.json`. Затем сборка APK и ручная проверка по процедуре выше (до этапа 4 UI ещё нет — на этом этапе проверяется только схема и сохранность данных).

**Этап 2 — DAO и репозиторий**:
`observeTrackRows`, `setTrackOrder`, `touch`, `upsertPlaylistOrder`/`setPlaylistOrder`, новый `observeSummaries`, `PlaylistRepositoryImpl` (addedAt, touch, `playlist_order` при create), `getTracksInDisplayOrder`, правка порядка выделения в `AddTracksViewModel` → тесты `FakePlaylistDao` + `PlaylistRepositoryImplTest`.

**Этап 3 — Настройка режима списка плейлистов**:
`AppSettings.playlistListSort` + ключ в `SettingsMapper` + `SettingsMapperTest`.

**Этап 4 — Экран плейлиста**:
`PlaylistDetailMode`, меню сортировки в TopBar, `REORDER` с авто-переключением в `CUSTOM`, `ReorderState` + `moved()`, запись порядка на выходе, очередь по `getTracksInDisplayOrder`.

**Этап 5 — Drawer**:
меню сортировки плейлистов, режим `CUSTOM` с перетаскиванием, `PlaylistListViewModel` на `PlaylistListSorter` + режим из `SettingsRepository`.

**Этап 6 — Проверка**:
строки в `strings.xml` → `./gradlew test` → `./gradlew lintDebug` → `./gradlew assembleDebug`, APK — пользователю для установки.

## Допущения и открытые вопросы

1. «Сортировка индивидуальна для каждого списка» трактуется так: **у каждого плейлиста свой режим сортировки треков** (в `playlists.track_sort_mode`); список плейлистов один на приложение, поэтому его режим — одна настройка в DataStore. Если нужен свой режим сортировки у каждой папки в Library — это отдельная фича (тот же механизм, но ключ по `folder_id`).
2. «По алфавиту» для треков внутри плейлиста **согласовано и включено** в `PlaylistTrackSort.TITLE`.
3. Даты изменения метаданных трека не существует; по решению пользователя «по дате изменения» внутри плейлиста = дата добавления (`playlist_tracks.added_at_ms`). Бэкфилл существующих строк — по `playlists.created_at_ms` (точной даты добавления в истории не сохранялось).
4. Для плейлистов «по дате изменения» = `playlists.updated_at_ms`; до этапа 2 это значение обновлялось только при переименовании, поэтому у старых плейлистов дата может не совпадать с фактическим последним изменением состава. Исправить задним числом нельзя.
5. Переключение режима сортировки не перезагружает уже играющую очередь (см. «Очередь воспроизведения»).
6. `CUSTOM` для плейлистов не имеет кнопки «сбросить в обычный порядок» — переключение на «по дате» не стирает сохранённый порядок, он просто не используется. Считаем это правильным: возврат в `CUSTOM` восстанавливает расставленное вручную.

## Побочные находки (вне скоупа)

- Сортировка папок в Library (`TrackSort`) хранится в `MutableStateFlow` (`LibraryViewModel.kt:50–51`) и теряется при пересоздании ViewModel. Ровно та же проблема; лечится тем же приёмом (персистентное поле) — отдельной задачей.
- Таблицы `conditions`/`condition_tags` существуют с v1 и полностью мёртвые: нет DAO, репозитория, доменной модели, UI. Колонка `conditions.order_by` (TEXT) — потенциальное место для режима сортировки сохраняемого условия, когда условия заработают.
- Экспорт/импорт XSPF в проекте отсутствует полностью (0 совпадений по `xspf`/`XmlSerializer`). Когда появится: порядок треков писать в порядке `getTracksInDisplayOrder`, режим сортировки — в атрибуты плейлиста; `TrackEntity.relativePath` уже folder-относительный, join с `FolderEntity.rootUri` — это и есть относительный путь.
