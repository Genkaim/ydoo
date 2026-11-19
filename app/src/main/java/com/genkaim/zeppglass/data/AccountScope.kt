package com.genkaim.zeppglass.data

import android.content.Context
import java.io.File

/**
 * Local files belong to an account, not to the install.
 *
 * One phone can be signed in as more than one person over its life, and the
 * health snapshot, the diet log and the chat archive are all readings of one
 * person's body. Naming each file after the account that produced it means
 * signing out is just "stop showing this account": nothing is destroyed, and
 * the next account starts from an empty set instead of inheriting someone
 * else's numbers.
 */
object AccountScope {

    /** Prefix used while nobody is signed in. */
    const val GUEST = "guest"

    fun id(settings: AppSettings): String =
        settings.userId?.takeIf { it.isNotBlank() } ?: GUEST

    /** Account ids come from the server; keep them usable as file names. */
    fun safeId(id: String): String = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /**
     * `filesDir/<base>-<account>.json` (optionally inside `folder`).
     *
     * A build that kept one shared file is adopted on first use: at upgrade
     * time the only data present belongs to whoever is signed in now, so
     * handing it to that account is the honest reading — and it keeps the
     * history of an existing install instead of starting empty.
     */
    fun file(context: Context, base: String, settings: AppSettings, folder: String? = null): File {
        val root = context.applicationContext
        val parent = if (folder.isNullOrBlank()) root.filesDir else File(root.filesDir, folder)
        parent.mkdirs()
        val target = File(parent, "$base-${safeId(id(settings))}.json")
        val legacy = File(parent, "$base.json")
        if (legacy.isFile && !target.exists()) {
            runCatching {
                legacy.copyTo(target, overwrite = false)
                legacy.delete()
            }
        }
        return target
    }
}
