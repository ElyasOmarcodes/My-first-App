package com.example.data

import kotlinx.coroutines.flow.Flow

class StudentRepository(private val studentDao: StudentDao) {
    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()

    fun getStudentById(id: Int): Flow<Student> {
        return studentDao.getStudentById(id)
    }

    suspend fun insertStudent(student: Student) {
        studentDao.insertStudent(student)
    }

    suspend fun updateStudent(student: Student) {
        studentDao.updateStudent(student)
    }

    suspend fun deleteStudent(student: Student) {
        studentDao.deleteStudent(student)
    }
}
