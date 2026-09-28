# План: работа с плейлистами (хранение, просмотр, добавление треков)

## Скоуп фичи

**Внутри скоупа:**
1. Хранение плейлистов в БД + список в drawer-меню вкладки Playlists (меню слева, у каждой строки play/крестик, кнопка «+» сверху).
2. Экран просмотра плейлиста: переиспользует модуль списка треков, имя вместо папки (клик = переименование), без refresh/import/export, с «Добавить треки»; клик по треку = загрузка плейлиста в плеер + play этого трека (без навигации); подсветка текущего трека, если плейлист играет; long-press → режим выделения (крестик + удаление с подтверждением).
3. Экран «Добавить треки»: плоский список треков, которых нет в плейлисте, все выделены по умолчанию.
4. Кнопка «просмотр плейлиста» на вкладке Player.
5. **Сохранение из генерации в плейлист**: имя составляется из параметров запроса, значок «Сохранить» рядом с «Воспроизвести».

**За границей (не в скоупе):**
- Генерация как форма — не меняется; результат генерации сохраняется только по явной кнопке.
- XSPF, веса/conditions, drag-reorder треков.

## Что показала кодовая база

- Таблицы БД уже есть и мёртвые: `PlaylistEntity` (`data/db/entity/PlaylistEntity.kt:24`), `PlaylistTrackEntity` (`PlaylistTrackEntity.kt:30`) зарегистрированы в `BpmDatabase.kt:30–31`, присутствуют и в `schemas/.../1.json`, и в `2.json` (проверено). DAO, репозитория, использования в коде — ноль.
- «Формат плейлистов» для обмена = эти сущности как персистент + `TrackEntity` для списков + `PlayerTrack` для плеера. Между модулями передаём только `playlistId` (nav-arg), данные — через Flow репозитория.
- «Модуль отображения треков в папке» отдельным модулем не является: это секция внутри монолита `LibraryScreen.kt` (504 строки, папки вложены в один LazyColumn, строки 245–289). Переиспользуемые примитивы: `ui/common/TrackRow.kt` (только `clickable`, без long-press/выделения/подсветки), `LibrarySearchField` + `filterTracksByTitle`.
- Плеер не знает, какой плейлист играет: `PlaybackSession.openQueue(items)` (`PlaybackSession.kt:112`) не сохраняет идентичность очереди; `Mode.Playlist/Preview/Empty` — это всё. Для подсветки и кнопки «просмотр плейлиста» нужно новое состояние.
- Режима выделения треков и переиспользуемого confirm-диалога нет — 3 инлайн `AlertDialog` (шаблон: `LibraryScreen.kt:322–341`).
- Роуты: `library` / `playlists` / `player` / `mass_bpm` (`BpmPlayerApp.kt:70–110`), bottom bar скрывается только на `mass_bpm` (строки 32, 36).

## Нужен ли рефакторинг — да, точечный

| Что | Зачем |
|---|---|
| `TrackRow`: optional `onLongClick` (через `combinedClickable`), состояние выделения (checkbox), подсветка | Обратно совместимо, нужно 3 новым экранам |
| Выделить переиспользуемый `ui/common/TrackList.kt` (header-slot + LazyColumn треков + selection + highlight + empty) | «Модуль отображения треков» для просмотра/добавления; Library на него пока не переносим (структура с вложенными папками отличается — отдельный рефакторинг) |
| `ui/common/ConfirmDialog.kt` по образцу `LibraryScreen:322–341` | 2 новых удаления с подтверждением (AGENTS требует) |
| `PlaybackSession`: `currentPlaylistId: StateFlow<Long?>`, `openQueue(items, playlistId, startIndex)` | Подсветка, кнопка на плеере, play с позиции трека |
| `BpmDatabase`: добавить `playlistDao()` | DAO не существует |
| `di/AppModule.kt`: provide DAO + репозиторий | Единственный Hilt-модуль |
| `BpmPlayerApp.kt`: 2 новых роута, скрытие bottom bar | Как у `mass_bpm` («новая активити» = nav-route, приложение single-activity) |
| Опционально: `PlaylistViewModel` → `GeneratorViewModel` | Чёткая граница «не смешиваясь с генерацией» |

## БД и миграция

**Миграция не нужна, версия остаётся 2.** Таблицы `playlists`/`playlist_tracks` существуют с v1: FK с CASCADE, индексы, unique `(playlist_id, track_id)` — всё уже под нужды фичи. Нужен только DAO-слой.

Нюансы реализации:
- Позиции могут иметь дырки (FK CASCADE при удалении трека/папки) → append через `COALESCE(MAX(position),-1)+1`, не `COUNT`.
- PK `(playlist_id, position)` — при удалении выделенного в `@Transaction` лучше удалить все строки плейлиста и вставить заново с 0..n, чем шифтить (иначе конфликты PK).
- Удаление играющего плейлиста → `session.clearPlaylistIdIf(id)` (очередь продолжит играть, кнопка просмотра скрывается).

## План по этапам

**Этап 0 — Рефакторинг-подготовка** (без видимых изменений):
`TrackRow` (long-press/selection/highlight) → `ui/common/ConfirmDialog.kt` → `ui/common/TrackList.kt` → `PlaybackSession` (+`currentPlaylistId`, +`startIndex` в `openQueue`, `clearPlaylistIdIf`) → перенос `LibrarySearchField`/`filterTracksByTitle` в `ui/common`.

**Этап 1 — Data layer** (без миграции):
`data/db/dao/PlaylistDao.kt` (observeAll со счётчиком, insert, updateName, delete; playlist_tracks: observeTracks JOIN … ORDER BY position, append, replace-after-delete в @Transaction, query «треки не из плейлиста») → `playlistDao()` в `BpmDatabase` → `data/playlist/PlaylistRepository(Impl)` + модель `PlaylistSummary(id, name, trackCount)` → provide в `AppModule` → Robolectric-тесты DAO (порядок, recompact, not-in, cascade).

**Этап 2 — Сохранение из генерации** (иконка рядом с play):
`PlaylistsScreen.kt:209–221`: рядом с `TextButton` «Воспроизвести» добавить иконку «Сохранить», видна только при `result.isNotEmpty()`. Имя из запроса — чистая функция (юнит-тест):
- обе границы → «90–120 BPM, 30 треков»
- только min → «от 90 BPM…», только max → «до 120 BPM…»
- без границ → «Плейлист, 30 треков» (формат уточним при реализации, строки в `strings.xml`)

`PlaylistViewModel` (или переименованный `GeneratorViewModel`) получает `PlaylistRepository`: `createPlaylist(name, trackIds)` → одна транзакция insert плейлиста + вставка треков с позициями 0..n из `result` в порядке генерации. Возврат в UI без навигации (опционально snackbar «Сохранено»). Новый плейлист сразу виден в drawer (Этап 3) — реактивный Flow.

**Этап 3 — Drawer со списком плейлистов на вкладке Playlists:**
`PlaylistsScreen` оборачивается в `ModalNavigationDrawer`, topBar получает иконку Menu; `ModalDrawerSheet`: «+» (создаёт «Новый плейлист» без треков) + список строк (имя → роут просмотра; play → `openQueue(playlistId)` + навигация PLAYER; крестик → ConfirmDialog → delete). Новый `PlaylistListViewModel`. Форма генерации внутри не меняется.

**Этап 4 — Экран просмотра** (`playlist/{playlistId}`, bottom bar скрыт, back-стрелка):
`PlaylistDetailViewModel(playlistId)`: имя/треки из repo, playback из session, selection-state. TopBar: normal → back + «Добавить треки»; selection → крестик + удаление (ConfirmDialog, plural). Header = имя (клик → RenameDialog). Клик по треку → `openQueue(всё, playlistId, startIndex)` без навигации; подсветка `trackId == currentTrackId && currentPlaylistId == id && !isPreview`; long-press → вход в выделение. Empty-state.

**Этап 5 — Экран «Добавить треки»** (`playlist/{playlistId}/add`):
`AddTracksViewModel`: кандидаты из DAO (не в плейлисте), все выделены при первой эмиссии, поиск, кнопка «Добавить (N)» → `repo.addTracks` → popBackStack.

**Этап 6 — Плеер:**
`PlayerViewModel` пробрасывает `currentPlaylistId`; в `CenterAlignedTopAppBar` (`PlayerScreen.kt:96–98`) добавить `actions` с иконкой → `playlist/{id}`; скрыта, если плейлиста нет.

**Этап 7 — Строки и проверка:**
`strings.xml` (RU: drawer, «Новый плейлист», «Добавить треки», переименование, 2 confirm-а с plural, empty-states, имя из генерации) → `./gradlew test` → `./gradlew lintDebug` → `./gradlew assembleDebug`, APK — пользователю для установки.

## Допущения

- «Новая активити» = новый nav-route (приложение single-activity), полноэкранный со скрытым bottom bar — как `mass_bpm`.
- Имя нового плейлиста по «+» — «Новый плейлист», переименование — в просмотре.
- Поиск в экране «Добавить треки» включаю (плоский список всех треков без поиска непригоден; код уже есть).