package com.shoqremote.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.shoqremote.app.ui.RemoteApp
import com.shoqremote.atv.Keys

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent { RemoteApp(vm) }
        if (Build.VERSION.SDK_INT >= 33 && vm.state.value.notificationControls &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.start()
    }

    override fun onStop() {
        vm.stop()
        super.onStop()
    }

    /** The phone's own volume buttons drive the box while the remote is on screen. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val boxKey = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> Keys.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> Keys.VOLUME_DOWN
            else -> null
        }
        if (boxKey != null && vm.state.value.link == Link.CONNECTED) {
            vm.key(boxKey)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
