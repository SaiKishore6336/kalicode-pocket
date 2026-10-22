package com.saikishore.kalicodepocket

import android.app.Application
import com.saikishore.kalicodepocket.data.Settings
import com.saikishore.kalicodepocket.editor.EditorLanguages

class PocketApp : Application() {
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        EditorLanguages.install(this)
        EditorLanguages.applyTheme(settings.editorTheme)
    }
}
