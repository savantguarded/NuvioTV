package com.nuvio.tv.core.nuvioc.health

// [fork] Nuvio C round 67 `ADDON_HEALTH`: the status sources you added, per profile, on this TV. Only the
// normalised addresses and their on / off state are kept (and synced with the profile's Nuvio C settings).
// Default for a new install: none. The formats found for each address are remembered on this TV only.

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object NuvioCHealthSources {
    const val PREFS = "nuvio_c_health"
    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(profile: Int) = "p$profile:sources"
    private fun fmtKey(address: String) = "fmt:" + address.hashCode().toString(16)

    fun load(context: Context, profile: Int): List<HealthSourceSetting> =
        NuvioCHealthRules.decodeSources(prefs(context).getString(key(profile), null))

    fun save(context: Context, profile: Int, list: List<HealthSourceSetting>) {
        prefs(context).edit { putString(key(profile), NuvioCHealthRules.encodeSources(list.distinctBy { it.address }.take(NuvioCHealthRules.MAX_SOURCES))) }
    }

    /** The synced form (same string as stored). */
    fun encoded(context: Context, profile: Int): String = NuvioCHealthRules.encodeSources(load(context, profile))

    /** From the cloud or the phone page: only valid addresses survive (each is checked again). */
    fun applyEncoded(context: Context, profile: Int, raw: String) = save(context, profile, NuvioCHealthRules.decodeSources(raw))

    /** From the phone page: a plain list of addresses (all on); invalid ones are dropped, existing on / off kept. */
    fun applyFromPhone(context: Context, profile: Int, addresses: List<String>) {
        val old = load(context, profile).associateBy { it.address }
        val list = addresses.mapNotNull { (NuvioCHealthRules.checkSourceAddress(it) as? NuvioCHealthRules.Checked.Ok)?.address }
            .distinct().take(NuvioCHealthRules.MAX_SOURCES)
            .map { HealthSourceSetting(it, old[it]?.enabled ?: true) }
        save(context, profile, list)
    }

    fun format(context: Context, address: String): SourceFormat? =
        prefs(context).getString(fmtKey(address), null)?.let { runCatching { SourceFormat.valueOf(it) }.getOrNull() }

    fun rememberFormat(context: Context, address: String, format: SourceFormat?) {
        prefs(context).edit { if (format == null) remove(fmtKey(address)) else putString(fmtKey(address), format.name) }
    }
}
