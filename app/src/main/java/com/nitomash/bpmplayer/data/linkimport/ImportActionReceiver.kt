package com.nitomash.bpmplayer.data.linkimport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkImportUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ImportActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var importUseCase: PublicLinkImportUseCase

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ImportActions.CANCEL) return
        val jobId = intent.getStringExtra(ImportActions.EXTRA_JOB_ID)?.let(::ImportJobId) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                runCatching { importUseCase.cancel(jobId) }
            } finally {
                pending.finish()
            }
        }
    }
}
