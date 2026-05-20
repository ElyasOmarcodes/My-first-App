package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.R

import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    LaunchedEffect(key1 = true) {
        delay(2000L)
        onSplashFinished()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.School,
            contentDescription = "Madrasa Logo",
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "جامع Madrasa",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Islamic Education System",
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
