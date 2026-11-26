package com.nitomash.bpmplayer.data.sharing

import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.nitomash.bpmplayer.domain.sharing.CollectionShareLink

object LinkHandling {

    fun isAllowed(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(DomainVerificationManager::class.java)
            ?.getDomainVerificationUserState(context.packageName)
            ?.isLinkHandlingAllowed == true
    } else {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("${CollectionShareLink.SCHEME}://${CollectionShareLink.HOST}${CollectionShareLink.PATH}"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == context.packageName
    }

    fun settingsIntent(context: Context): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
        } else {
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        }
        return Intent(action, Uri.parse("package:${context.packageName}"))
    }
}
