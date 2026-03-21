package dev.pivisolutions.dictus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import dev.pivisolutions.dictus.core.theme.DictusTheme

/**
 * Main activity that hosts the Compose UI.
 *
 * @AndroidEntryPoint enables Hilt dependency injection in this Activity.
 * For now it displays a simple instruction screen; the full UI will be
 * built in later plans.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DictusTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Enable Dictus Keyboard in Settings > System > Languages & Input > On-screen keyboard",
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
