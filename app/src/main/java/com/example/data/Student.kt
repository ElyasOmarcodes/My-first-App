package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val fatherName: String,
    val className: String,
    val contactNumber: String,
    val enrollmentDate: Long = System.currentTimeMillis(),
    val enrollmentId: String
)
