package com.microbeaver.guardian.widget

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import com.microbeaver.guardian.LocaleManager

/**
 * Quick Settings Tile: toggle language (System / English / Arabic).
 */
class LangTileService : TileService() {

    override fun onClick() {
        val tag = when (LocaleManager.current(this)) {
            LocaleManager.EN -> LocaleManager.AR
            LocaleManager.AR -> LocaleManager.SYSTEM
            else -> LocaleManager.EN
        }
        LocaleManager.apply(tag)
        Toast.makeText(this, "Language: $tag", Toast.LENGTH_SHORT).show()
        updateTile()
    }

    override fun onStartListening() {
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.label = "Language"
        tile.contentDescription = when (LocaleManager.current(this)) {
            LocaleManager.EN -> "English"
            LocaleManager.AR -> "العربية"
            else -> "System"
        }
        tile.state = android.service.quicksettings.Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
