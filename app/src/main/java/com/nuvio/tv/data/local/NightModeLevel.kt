// [fork] Nuvio C round 31 night_mode: copied verbatim from ysosrs123/NuvioTV-Fork (GPL-3.0) @ 2b51aba.
package com.nuvio.tv.data.local

enum class NightModeLevel(val key: String) {
    OFF("off"),
    LIGHT("light"),
    MEDIUM("medium"),
    STRONG("strong");

    companion object {
        fun fromKey(key: String?): NightModeLevel = entries.firstOrNull { it.key == key } ?: OFF
    }
}
