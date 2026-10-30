package com.auravpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auravpn.ui.theme.AuraColors
import com.auravpn.ui.theme.AuraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraTheme { AuraFoundationScreen() } }
    }
}

@Composable
private fun AuraFoundationScreen() {
    Surface(color = AuraColors.Background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(AuraColors.Background, AuraColors.BackgroundEnd)))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AURA", color = AuraColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("VPN", color = AuraColors.Primary, fontSize = 14.sp, letterSpacing = 6.sp)
            Text(
                "زیرساخت اولیه آماده است",
                color = AuraColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 28.dp)
            )
            Text(
                "هسته اتصال هنوز پیاده‌سازی نشده است",
                color = AuraColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
