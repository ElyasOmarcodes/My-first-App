package com.example.ui.screens.student

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MyApplication
import com.example.data.Student

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as MyApplication
    val viewModel: StudentViewModel = viewModel(
        factory = StudentViewModelFactory(application.studentRepository)
    )

    var name by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var enrollmentId by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("نوی شاګرد (New Student)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("نوم (Full Name)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = fatherName,
                onValueChange = { fatherName = it },
                label = { Text("د پلار نوم (Father's Name)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = className,
                onValueChange = { className = it },
                label = { Text("صنف (Class/Course)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = enrollmentId,
                onValueChange = { enrollmentId = it },
                label = { Text("آی ډي نمبر (ID Number)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("د اړيکې شمېره (Contact Number)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isNotBlank() && className.isNotBlank()) {
                        viewModel.addStudent(
                            Student(
                                name = name.trim(),
                                fatherName = fatherName.trim(),
                                className = className.trim(),
                                contactNumber = contact.trim(),
                                enrollmentId = enrollmentId.trim()
                            )
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = name.isNotBlank() && className.isNotBlank()
            ) {
                Text("ثبت کړئ (Save Student)")
            }
        }
    }
}
