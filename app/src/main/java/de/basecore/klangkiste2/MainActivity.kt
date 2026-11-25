package de.basecore.klangkiste2

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.basecore.klangkiste2.ui.KlangKisteApp
import de.basecore.klangkiste2.ui.KlangKisteTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback, SensorEventListener {
    private val viewModel: KlangKisteViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 41)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(viewModel.isChildMode, viewModel.settings) { child, prefs -> child && prefs.ecoMode }.collectLatest { active ->
                    if (active && accelerometer != null) {
                        sensorManager.registerListener(this@MainActivity, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
                        try { awaitCancellation() } finally { sensorManager.unregisterListener(this@MainActivity) }
                    } else sensorManager.unregisterListener(this@MainActivity)
                }
            }
        }
        setContent { KlangKisteTheme { KlangKisteApp(viewModel) { runCatching { startLockTask() }.onFailure { viewModel.showMessage("App-Pinning konnte nicht gestartet werden.") } } } }
    }
    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, null)
    }
    override fun onPause() { nfcAdapter?.disableReaderMode(this); sensorManager.unregisterListener(this); super.onPause() }
    override fun onTagDiscovered(tag: Tag) {
        val uid = tag.id.joinToString("") { "%02X".format(it) }
        runOnUiThread { viewModel.onNfcTag(uid) }
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) viewModel.setFaceDown(event.values[2] < -8.0f)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
