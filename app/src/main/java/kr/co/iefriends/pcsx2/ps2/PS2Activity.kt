package kr.co.iefriends.pcsx2.ps2

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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

class PS2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(this, "需要所有文件访问权限才能加载PS2游戏", Toast.LENGTH_LONG).show()
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

        setContent {
            PS2View(
                biosFolder = biosFolder,
                ps2BaseFolder = ps2BaseFolder,
                gameFile = gameFile,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 调用隐藏状态栏需要放在 setContent 之后，确保 DecorView 已经初始化完成
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
}
