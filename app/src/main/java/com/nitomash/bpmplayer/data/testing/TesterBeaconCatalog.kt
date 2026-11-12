package com.nitomash.bpmplayer.data.testing

data class TesterBeaconInfo(
    val beacon: TesterBeacon,
    val title: String,
    val category: String,
)

object TesterBeaconCatalog {
    val items: List<TesterBeaconInfo> = listOf(
        TesterBeaconInfo(TesterBeacon.BETA_INTRO_ACCEPTED, "Принять условия бета-тестирования", "Общее"),
        TesterBeaconInfo(TesterBeacon.NAV_LIBRARY_OPENED, "Открыть аудиотеку", "Общее"),
        TesterBeaconInfo(TesterBeacon.NAV_PLAYLISTS_OPENED, "Открыть плейлисты", "Общее"),
        TesterBeaconInfo(TesterBeacon.NAV_GENERATOR_OPENED, "Открыть генератор", "Общее"),
        TesterBeaconInfo(TesterBeacon.NAV_PLAYER_OPENED, "Открыть плеер", "Общее"),
        TesterBeaconInfo(TesterBeacon.SCREEN_SETTINGS_OPENED, "Открыть настройки", "Общее"),
        TesterBeaconInfo(TesterBeacon.SCREEN_ABOUT_OPENED, "Открыть «О программе»", "Общее"),
        TesterBeaconInfo(TesterBeacon.SCREEN_TESTING_OPENED, "Открыть экран тестирования", "Общее"),

        TesterBeaconInfo(TesterBeacon.FOLDER_ADDED, "Добавить папку", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.LIBRARY_REFRESH_ALL, "Обновить библиотеку", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.FOLDER_RESCAN, "Обновить папку", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.FOLDER_REMOVED, "Удалить папку", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.FOLDER_EXPAND_COLLAPSE, "Свернуть или развернуть папку", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.LIBRARY_SEARCH_USED, "Использовать поиск в аудиотеке", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.LIBRARY_SORT_CHANGED, "Изменить сортировку треков", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.TRACK_OPEN_EDITOR, "Открыть редактор трека", "Аудиотека"),
        TesterBeaconInfo(TesterBeacon.TRACK_ADD_TO_PLAYLIST_FROM_LIBRARY, "Добавить трек в плейлист из аудиотеки", "Аудиотека"),

        TesterBeaconInfo(TesterBeacon.BPM_MANUAL_SAVED, "Сохранить BPM вручную", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_TAP_USED, "Определить BPM тапом", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_TAP_SAVED, "Сохранить BPM после тапа", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_AUTO_DETECT_STARTED, "Запустить автоопределение BPM", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_AUTO_DETECT_SUCCEEDED, "Успешно автоопределить BPM", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_AUTO_DETECT_SAVED, "Сохранить автоопределённый BPM", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_EDITOR_SKIP_FORWARD, "Перемотать в редакторе BPM", "BPM"),
        TesterBeaconInfo(TesterBeacon.BPM_EDITOR_PREVIEW_PLAY, "Прослушать трек в редакторе BPM", "BPM"),

        TesterBeaconInfo(TesterBeacon.MASS_BPM_SETUP_OPENED, "Открыть настройку массового BPM", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.MASS_BPM_SCREEN_OPENED, "Открыть массовую простановку BPM", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.MASS_BPM_BATCH_DETECT_STARTED, "Запустить пакетное определение BPM", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.MASS_BPM_ITEM_EDITED, "Отредактировать трек в массовом режиме", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.MASS_BPM_SAVED, "Сохранить результаты массовой простановки", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.MASS_BPM_DISCARDED, "Отменить изменения массовой простановки", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.LIBRARY_BPM_PROMPT_ACCEPTED, "Перейти к определению BPM из подсказки", "Массовый BPM"),
        TesterBeaconInfo(TesterBeacon.LIBRARY_BPM_PROMPT_DISMISSED, "Отложить подсказку определения BPM", "Массовый BPM"),

        TesterBeaconInfo(TesterBeacon.PLAYLIST_CREATED, "Создать плейлист", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_OPENED, "Открыть плейлист", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_RENAMED, "Переименовать плейлист", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_DELETED, "Удалить плейлист", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_DELETED_SELECTED, "Удалить выбранные плейлисты", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_LIST_SORT_CHANGED, "Изменить сортировку списка плейлистов", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_LIST_REORDER_ENTERED, "Войти в режим порядка плейлистов", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_LIST_REORDER_COMMITTED, "Сохранить порядок плейлистов", "Плейлисты"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_SELECTION_MODE_USED, "Использовать выделение плейлистов", "Плейлисты"),

        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACK_SORT_CHANGED, "Изменить сортировку треков в плейлисте", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACKS_REORDER_ENTERED, "Войти в режим порядка треков", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACKS_REORDER_COMMITTED, "Сохранить порядок треков", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACK_REMOVED, "Удалить трек из плейлиста", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACKS_REMOVED_SELECTED, "Удалить выбранные треки из плейлиста", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACK_SELECTION_USED, "Использовать выделение треков в плейлисте", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_ADD_TRACKS_OPENED, "Открыть добавление треков в плейлист", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_TRACKS_ADDED, "Добавить треки в плейлист", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_ADD_TRACKS_SEARCH_USED, "Искать треки при добавлении", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_ADD_TRACKS_PREVIEW, "Прослушать трек при добавлении", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_PLAY_STARTED, "Запустить воспроизведение плейлиста", "Треки в плейлисте"),
        TesterBeaconInfo(TesterBeacon.PLAYLIST_PLAY_FROM_TRACK, "Запустить плейлист с выбранного трека", "Треки в плейлисте"),

        TesterBeaconInfo(TesterBeacon.GENERATOR_BPM_RANGE_SET, "Задать диапазон BPM в генераторе", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_COUNT_SET, "Задать количество треков в генераторе", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_ORDER_CHANGED, "Изменить порядок в генераторе", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_ADVANCED_OPENED, "Открыть доп. настройки генератора", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_SCOPE_FOLDERS_USED, "Генерировать из выбранных папок", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_SCOPE_PLAYLISTS_USED, "Генерировать из выбранных плейлистов", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_GENERATED, "Сгенерировать плейлист", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_RESULT_OPEN_PLAYER, "Открыть результат генерации в плеере", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_RESULT_PLAY_FROM_TRACK, "Запустить результат с выбранного трека", "Генератор"),
        TesterBeaconInfo(TesterBeacon.GENERATOR_SAVED_TO_PLAYLIST, "Сохранить сгенерированный плейлист", "Генератор"),

        TesterBeaconInfo(TesterBeacon.SIDECAR_IMPORT, "Импортировать данные папки", "Импорт и экспорт"),
        TesterBeaconInfo(TesterBeacon.SIDECAR_EXPORT, "Экспортировать данные папки", "Импорт и экспорт"),
        TesterBeaconInfo(TesterBeacon.XSPF_IMPORT, "Импортировать плейлист (XSPF)", "Импорт и экспорт"),
        TesterBeaconInfo(TesterBeacon.XSPF_EXPORT, "Экспортировать плейлист (XSPF)", "Импорт и экспорт"),
        TesterBeaconInfo(TesterBeacon.BUNDLE_EXPORT, "Экспортировать плейлист с файлами", "Импорт и экспорт"),
        TesterBeaconInfo(TesterBeacon.BUNDLE_EXPORT_CANCELLED, "Отменить экспорт с файлами", "Импорт и экспорт"),

        TesterBeaconInfo(TesterBeacon.PLAYER_PLAY_PAUSE_TOGGLED, "Воспроизвести или поставить на паузу", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_NEXT, "Перейти к следующему треку", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_PREVIOUS, "Перейти к предыдущему треку", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_SEEK_USED, "Перемотать трек", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_SHUFFLE_TOGGLED, "Переключить случайный порядок", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_REPEAT_CYCLED, "Переключить режим повтора", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_OPEN_PLAYLIST_FROM_PLAYER, "Открыть плейлист из плеера", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_EDIT_BPM, "Изменить BPM из плеера", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_ADD_CURRENT_TO_PLAYLIST, "Добавить текущий трек в плейлист", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_INTERVAL_ENABLED, "Включить интервальное воспроизведение", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_INTERVAL_LIMIT_CHANGED, "Изменить длительность фрагмента", "Плеер"),
        TesterBeaconInfo(TesterBeacon.PLAYER_INTERVAL_GAP_CHANGED, "Изменить паузу между треками", "Плеер"),

        TesterBeaconInfo(TesterBeacon.SETTINGS_DETECTOR_RANGE_SAVED, "Сохранить диапазон автоопределения BPM", "Настройки"),
        TesterBeaconInfo(TesterBeacon.SETTINGS_DETECTOR_RANGE_RESET, "Сбросить диапазон автоопределения BPM", "Настройки"),
    )

    private val byId = items.associateBy { it.beacon.id }

    fun info(beacon: TesterBeacon): TesterBeaconInfo? = byId[beacon.id]
}
