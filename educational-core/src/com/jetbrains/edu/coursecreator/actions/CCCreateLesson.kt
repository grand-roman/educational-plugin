package com.jetbrains.edu.coursecreator.actions

import com.intellij.openapi.project.Project
import com.jetbrains.edu.coursecreator.stepik.StepikCourseChangeHandler
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.Section
import com.jetbrains.edu.learning.courseFormat.StudyItem
import icons.EducationalCoreIcons

open class CCCreateLesson : CCCreateLessonBase<Lesson>(EduNames.LESSON, EducationalCoreIcons.Lesson) {

  override fun createAndInitItem(project: Project, course: Course, parentItem: StudyItem?, name: String, index: Int): Lesson {
    return Lesson().apply {
      this.name = name
      this.course = course
      this.index = index
      if (parentItem is Section) {
        this.section = parentItem
      }
    }
  }
}
