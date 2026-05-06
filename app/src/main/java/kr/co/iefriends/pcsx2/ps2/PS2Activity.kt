package kr.co.iefriends.pcsx2.ps2

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kr.co.iefriends.pcsx2.NativeApp
import kr.co.iefriends.pcsx2.R
import kr.co.iefriends.pcsx2.input.RemoteGamepadInputPacket
import kr.co.iefriends.pcsx2.input.RemoteInputReceiver
import android.util.Log
import java.lang.ref.WeakReference

class PS2Activity : ComponentActivity() {
    companion object {
        private var remoteInputReceiverRef = WeakReference<RemoteInputReceiver>(null)

        @JvmStatic
        fun dispatchRemoteInputJson(rawJson: String): Boolean {
            return remoteInputReceiverRef.get()?.receiveJson(rawJson) == true
        }

        @JvmStatic
        fun dispatchRemoteInputPacket(packet: RemoteGamepadInputPacket): Boolean {
            return remoteInputReceiverRef.get()?.receivePacket(packet) == true
        }

        @JvmStatic
        fun getActiveRemoteInputReceiver(): RemoteInputReceiver? {
            return remoteInputReceiverRef.get()
        }
    }

    private var gamepadManager: PS2GamepadManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, getString(R.string.ps2_storage_permission_required), Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
            return
        }

        val biosFolder = intent.getStringExtra("biosFolder") ?: ""
        val ps2BaseFolder = intent.getStringExtra("ps2BaseFolder") ?: ""
        val gameFile = intent.getStringExtra("gameFile") ?: ""
        val cheatsPath = intent.getStringExtra("cheatsPath") ?: ""
        val manager = PS2GamepadManager(this).also { it.start() }
        gamepadManager = manager
        remoteInputReceiverRef = WeakReference(manager.getRemoteInputReceiver())
        
        Log.d("cheats PS2Activity", "Received intent extras - biosFolder: $biosFolder, ps2BaseFolder: $ps2BaseFolder, gameFile: $gameFile, cheatsPath: $cheatsPath")
        val bundle = intent.extras
        if (bundle != null) {
            for (key in bundle.keySet()) {
                Log.d("cheats PS2Activity", "Intent Extra Key: $key, Value: ${bundle.get(key)}")
            }
        } else {
            Log.d("cheats PS2Activity", "Intent extras bundle is null")
        }

        setContent {
            PS2View(
                biosFolder = biosFolder,
                ps2BaseFolder = ps2BaseFolder,
                gameFile = gameFile,
                cheatsPath = cheatsPath,
                gamepadManager = manager,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Hide the system bars after compose content is attached so DecorView is ready.
        hideStatusBar()
    }

    private fun hideStatusBar() {
        val w = window ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = w.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            w.attributes = lp
        }

        WindowCompat.setDecorFitsSystemWindows(w, false)
        val controller = WindowCompat.getInsetsController(w, w.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideStatusBar()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (gamepadManager?.handleKeyEvent(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (gamepadManager?.handleMotionEvent(event) == true) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onStart() {
        super.onStart()
        gamepadManager?.setRemoteInputEnabled(true)
    }

    override fun onStop() {
        gamepadManager?.setRemoteInputEnabled(false)
        gamepadManager?.releaseAllInputs()
        super.onStop()
    }

    override fun onDestroy() {
        val currentReceiver = gamepadManager?.getRemoteInputReceiver()
        if (remoteInputReceiverRef.get() === currentReceiver) {
            remoteInputReceiverRef = WeakReference(null)
        }
        gamepadManager?.stop()
        gamepadManager = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        NativeApp.onNativeSurfaceDestroyed()
        NativeApp.shutdownAndWait()
        super.onBackPressed()
    }
}
