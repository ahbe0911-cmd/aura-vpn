package com.auravpn.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
private val AuraDarkScheme=darkColorScheme(
 primary=AuraColors.Primary, secondary=AuraColors.Secondary,
 background=AuraColors.Background, surface=AuraColors.Surface,
 error=AuraColors.Error, onPrimary=AuraColors.Background,
 onBackground=AuraColors.TextPrimary, onSurface=AuraColors.TextPrimary
)
@Composable fun AuraTheme(content: @Composable () -> Unit) {
 MaterialTheme(colorScheme=AuraDarkScheme, content=content)
}
