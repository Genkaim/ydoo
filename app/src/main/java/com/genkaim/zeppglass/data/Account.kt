package com.genkaim.zeppglass.data

import android.content.Context

/**
 * Switches every local store from one account to another.
 *
 * Data is not deleted when someone signs out: each account keeps its own
 * health snapshot, diet log, chat archive and daily summary, and signing in
 * again reads them back. What has to change at that moment is only what is in
 * memory — otherwise the next person sees the previous person's numbers.
 */
object Account {

    fun switch(context: Context, settings: AppSettings, store: HealthStore) {
        val app = context.applicationContext
        settings.reloadForUser()
        AiChatSession.switchUser(app)
        DietStore.switchUser(app)
        store.switchUser()
    }
}
