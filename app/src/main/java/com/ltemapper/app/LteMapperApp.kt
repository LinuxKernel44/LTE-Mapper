package com.ltemapper.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.ltemapper.app.data.repository.SettingsRepository
import com.ltemapper.app.mapping.OsmMapConfig
import com.ltemapper.app.service.MappingForegroundService
import com.ltemapper.app.service.MappingSessionState
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LteMapperApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sessionState: MappingSessionState

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var backgroundMappingEnabled = false

    override fun onCreate() {
        super.onCreate()
        OsmMapConfig.initialize(this)

        appScope.launch {
            settingsRepository.settingsFlow.collect { backgroundMappingEnabled = it.backgroundMappingEnabled }
        }

        // The foreground-service notification alone lets mapping survive the screen turning off,
        // but "Background mapping" (Settings) is what the user actually consents to for mapping
        // continuing once they've left the app entirely — honor that by stopping cleanly here
        // when they haven't opted in, rather than letting it run unbounded in the background.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (!backgroundMappingEnabled && sessionState.state.value.isMapping) {
                        MappingForegroundService.stop(this@LteMapperApp)
                    }
                }
            },
        )
    }

    /** Lets WorkManager construct Hilt-injected workers (see OfflineTileDownloadWorker). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
