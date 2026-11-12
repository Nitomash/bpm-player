package com.nitomash.bpmplayer.data.testing

import com.nitomash.bpmplayer.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TesterProgress @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    fun observeCompletedIds(): Flow<Set<String>> = settingsRepository.observe().map { it.testerBeacons }

    fun observeIntroAccepted(): Flow<Boolean> = settingsRepository.observe().map { it.betaIntroAccepted }

    suspend fun mark(beacon: TesterBeacon) {
        settingsRepository.update { settings ->
            if (beacon.id in settings.testerBeacons) {
                settings
            } else {
                settings.copy(testerBeacons = settings.testerBeacons + beacon.id)
            }
        }
    }

    suspend fun acceptIntro() {
        settingsRepository.update { settings ->
            if (settings.betaIntroAccepted) {
                settings
            } else {
                settings.copy(
                    betaIntroAccepted = true,
                    testerBeacons = settings.testerBeacons + TesterBeacon.BETA_INTRO_ACCEPTED.id,
                )
            }
        }
    }
}
