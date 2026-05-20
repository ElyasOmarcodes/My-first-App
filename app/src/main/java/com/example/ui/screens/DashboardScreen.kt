package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.navigation.Routes

data class DashboardItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val iconTint: Color,
    val iconBg: Color
)

val dashboardItems = listOf(
    DashboardItem("شاګردان\nStudents", Icons.Filled.Group, Routes.STUDENTS, Color(0xFF386B1D), Color(0xFFE8F3E1)),
    DashboardItem("اساتذه\nTeachers", Icons.Filled.Person, Routes.TEACHERS, Color(0xFF42493F), Color(0xFFE2E3DE)),
    DashboardItem("حاضري\nAttendance", Icons.Filled.Assignment, Routes.ATTENDANCE, Color(0xFF006874), Color(0xFFE1F0F3)),
    DashboardItem("صنفونه\nClasses", Icons.Filled.Class, Routes.CLASSES, Color(0xFFBA1A1A), Color(0xFFF3E1E1)),
    DashboardItem("مالي چارې\nFinance", Icons.Filled.AccountBalance, Routes.FINANCE, Color(0xFF6750A4), Color(0xFFE8E1F3)),
    DashboardItem("تنظيمات\nSettings", Icons.Filled.Settings, Routes.SETTINGS, Color(0xFF386B1D), Color(0xFFE8F3E1)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = "جامع سيستم", 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "Madrasa Management System", 
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                            .clickable { /* Profile */ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick Stats Horizontal Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    StatCard(
                        title = "شاګردان",
                        value = "۱۲۴۰",
                        icon = Icons.Filled.Group,
                        containerColor = Color(0xFFD7E8CD),
                        contentColor = Color(0xFF386B1D)
                    )
                }
                item {
                    StatCard(
                        title = "استادان",
                        value = "۴۸",
                        icon = Icons.Filled.School,
                        containerColor = Color(0xFFE2E3DE),
                        contentColor = Color(0xFF42493F)
                    )
                }
                item {
                    StatCard(
                        title = "حاضري",
                        value = "۹۴٪",
                        icon = Icons.Filled.HowToReg,
                        containerColor = Color(0xFFF3E0BD),
                        contentColor = Color(0xFF705D00)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "مديريت (Management)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(dashboardItems) { item ->
                    DashboardCard(item = item, onClick = { navController.navigate(item.route) })
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: ImageVector, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .width(112.dp)
            .background(color = containerColor, shape = RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun DashboardCard(item: DashboardItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(item.iconBg, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp),
                    tint = item.iconTint
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
