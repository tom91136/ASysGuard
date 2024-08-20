package net.kurobako.asysguard

import android.app.Application
import net.time4j.android.ApplicationStarter

class Application : Application() {
  override fun onCreate() {
    super.onCreate()
    ApplicationStarter.initialize(this, true)
  }
}
