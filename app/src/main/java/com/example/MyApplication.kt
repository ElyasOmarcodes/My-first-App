package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.StudentRepository

class MyApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "madrasa_database").build()
    }
    
    val studentRepository by lazy {
        StudentRepository(database.studentDao())
    }
}
