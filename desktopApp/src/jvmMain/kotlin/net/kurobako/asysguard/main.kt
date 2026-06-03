package net.kurobako.asysguard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

@OptIn(ExperimentalFoundationApi::class)
fun main() =
  application {
    val config = remember { desktopAppConfig() }
    val wake = remember { DbusScreenWake() }
    val fontScale = System.getProperty("asysguard.fontscale")?.toFloatOrNull() ?: 1f
    val state =
      rememberWindowState(
        placement =
          if (System.getProperty("asysguard.fullscreen").toBoolean()) {
            WindowPlacement.Fullscreen
          } else {
            WindowPlacement.Floating
          },
      )

    fun toggleFullscreen() {
      state.placement =
        if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
    }

    Window(
      onCloseRequest = ::exitApplication,
      title = "ASysGuard",
      state = state,
      onKeyEvent = {
        if (it.type == KeyEventType.KeyDown && it.key == Key.F11) {
          toggleFullscreen()
          true
        } else {
          false
        }
      },
    ) {
      val base = LocalDensity.current
      CompositionLocalProvider(
        LocalDensity provides Density(base.density, base.fontScale * fontScale),
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .combinedClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onDoubleClick = { toggleFullscreen() },
              onClick = {},
            ),
        ) {
          DesktopDashboard(config, wake)
        }
      }
    }
  }
