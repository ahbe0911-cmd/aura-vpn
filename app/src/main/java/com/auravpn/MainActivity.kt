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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraApp() }
    }
}

@Composable
private fun AuraApp() {
    val navy = Color(0xFF0B1020)
    Surface(color = navy, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(navy, Color(0xFF151D3B))))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("AURA", color = Color(0xFFF4F7FF), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("VPN", color = Color(0xFF42C8F5), fontSize = 15.sp, letterSpacing = 5.sp)
            Text(
                "Foundation build · VPN engine not connected",
                color = Color(0xFF9AA8C7),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}
