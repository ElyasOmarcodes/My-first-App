package com.example.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.student.StudentsListScreen
import com.example.ui.screens.student.AddStudentScreen
import com.example.ui.screens.UnderConstructionScreen

object Routes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val STUDENTS = "students"
    const val ADD_STUDENT = "add_student"
    const val TEACHERS = "teachers"
    const val ATTENDANCE = "attendance"
    const val FINANCE = "finance"
    const val CLASSES = "classes"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onSplashFinished = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        
        composable(Routes.DASHBOARD) {
            DashboardScreen(navController = navController)
        }
        
        composable(Routes.STUDENTS) {
            StudentsListScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToAddStudent = { navController.navigate(Routes.ADD_STUDENT) }
            )
        }
        
        composable(Routes.ADD_STUDENT) {
            AddStudentScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        
        composable(Routes.TEACHERS) {
            UnderConstructionScreen(title = "اساتذه (Teachers)", onNavigateBack = { navController.navigateUp() })
        }
        composable(Routes.ATTENDANCE) {
            UnderConstructionScreen(title = "حاضري (Attendance)", onNavigateBack = { navController.navigateUp() })
        }
        composable(Routes.FINANCE) {
            UnderConstructionScreen(title = "مالي چارې (Finance)", onNavigateBack = { navController.navigateUp() })
        }
        composable(Routes.CLASSES) {
            UnderConstructionScreen(title = "صنفونه (Classes)", onNavigateBack = { navController.navigateUp() })
        }
        composable(Routes.SETTINGS) {
            UnderConstructionScreen(title = "تنظيمات (Settings)", onNavigateBack = { navController.navigateUp() })
        }
    }
}
