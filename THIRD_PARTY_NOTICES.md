# Сторонние компоненты

В приложении используются сторонние библиотеки. Ниже перечислены компоненты, лицензии которых требуют отдельного упоминания при распространении.

## TarsosDSP

- **Компонент:** `be.tarsos.dsp:core:2.5`
- **Назначение:** определение BPM
- **Лицензия:** GNU General Public License v3.0 (GPL-3.0)
- **Исходный код:** https://github.com/JorenSix/TarsosDSP
- **Релизы / артефакты:** https://0110.be/releases/TarsosDSP/ и https://mvn.0110.be/releases/be/tarsos/dsp/core/2.5/

Текст лицензии GPL-3.0 приведён в файле [`LICENSE`](LICENSE).

## Прочие зависимости

Большинство остальных зависимостей (AndroidX, Jetpack Compose, Room, DataStore, Media3, Hilt, Kotlin и др.) распространяются на условиях Apache License 2.0. Библиотека `sh.calvin.reorderable:reorderable` также распространяется на условиях Apache License 2.0.

Актуальный список зависимостей и версий см. в [`gradle/libs.versions.toml`](gradle/libs.versions.toml) и [`app/build.gradle.kts`](app/build.gradle.kts). При распространении приложения необходимо соблюдать условия лицензий всех включённых компонентов.
