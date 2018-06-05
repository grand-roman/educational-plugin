package com.jetbrains.edu.coursecreator.stepik

import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.ext.PushStatus
import com.jetbrains.edu.learning.courseFormat.tasks.Task

object StepikCourseChangeHandler {
  fun changed(placeholder: AnswerPlaceholder) {
    val taskFile = placeholder.taskFile ?: return
    changed(taskFile)
  }

  fun changed(taskFile: TaskFile) {
    changed(taskFile.task)
  }

  fun changed(task: Task) {
    if (!isRemoteCourse(task)) return
    task.pushStatus = PushStatus.ALL
  }

  private fun isRemoteCourse(task: Task): Boolean {
    return task.lesson.course is RemoteCourse
  }

  fun notChanged(task: Task) {
    if (!isRemoteCourse(task)) return
    task.pushStatus = PushStatus.NOTHING
  }

  fun infoChanged(lesson: Lesson) {
    if (lesson.course !is RemoteCourse) return
    lesson.pushStatus = if (lesson.pushStatus == PushStatus.CONTENT) PushStatus.ALL else PushStatus.INFO
  }

  fun contentChanged(lesson: Lesson) {
    if (lesson.course !is RemoteCourse) return
    lesson.pushStatus = if (lesson.pushStatus == PushStatus.INFO) PushStatus.ALL else PushStatus.CONTENT
  }

  fun infoChanged(section: Section) {
    if (section.course == 0) return
    section.pushStatus = if (section.pushStatus == PushStatus.CONTENT) PushStatus.ALL else PushStatus.INFO
  }

  fun contentChanged(section: Section) {
    if (section.course == 0) return
    section.pushStatus = if (section.pushStatus == PushStatus.INFO) PushStatus.ALL else PushStatus.CONTENT
  }

  fun infoChanged(course: Course) {
    if (course !is RemoteCourse) return
    course.pushStatus = if (course.pushStatus == PushStatus.CONTENT) PushStatus.ALL else PushStatus.INFO
  }

  fun contentChanged(course: Course) {
    if (course !is RemoteCourse) return
    course.pushStatus = if (course.pushStatus == PushStatus.INFO) PushStatus.ALL else PushStatus.CONTENT
  }

  fun contentChanged(itemContainer: ItemContainer) {
    if (itemContainer is Course) {
      contentChanged(itemContainer)
    }

    if (itemContainer is Section) {
      contentChanged(itemContainer)
    }
  }


}