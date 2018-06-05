package com.jetbrains.edu.coursecreator.stepik

import com.intellij.openapi.project.Project
import com.jetbrains.edu.learning.courseFormat.Lesson
import com.jetbrains.edu.learning.courseFormat.PushStatus
import com.jetbrains.edu.learning.courseFormat.RemoteCourse
import com.jetbrains.edu.learning.courseFormat.Section
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.stepik.StepikConnector
import java.util.*
import kotlin.collections.ArrayList

class StepikCourseUploader(val project: Project, val course: RemoteCourse) {
  private var courseInfoToUpdate = false

  var sectionsToPush: ArrayList<Section> = ArrayList()
  var sectionsToDelete: ArrayList<Int> = ArrayList()
  var sectionsInfoToUpdate: ArrayList<Int> = ArrayList()

  val lessonsToPush: ArrayList<Lesson> = ArrayList()
  private var lessonsToDelete: ArrayList<Int> = ArrayList()
  private var lessonsInfoToUpdate: ArrayList<Lesson> = ArrayList()

  val tasksToPush: ArrayList<Task> = ArrayList()
  private var tasksToDelete: ArrayList<Int> = ArrayList()
  private var tasksToUpdate: ArrayList<Int> = ArrayList()

  public fun updateCourse() {
    val lastUpdateDate = course.lastUpdateDate()

    processCourseChanges(lastUpdateDate)
    processSectionChanges(lastUpdateDate)
    processLessonChanges(lastUpdateDate)
    processTaskChanges()
    //push changes
    // reset all changed file statuses
  }

  private fun processCourseChanges(lastUpdateDate: Date) {
    when (course.pushStatus) {
      PushStatus.INFO -> {
        courseInfoToUpdate = true
      }

      PushStatus.CONTENT -> {
        processCourseContentChanged(lastUpdateDate)
      }

      PushStatus.ALL -> {
        courseInfoToUpdate = true
        processCourseContentChanged(lastUpdateDate)
      }
    }
  }

  private fun processSectionChanges(lastUpdateDate: Date) {
    val pushCandidates = course.sections.filter { it.pushStatus != PushStatus.NOTHING }
    val sectionsFromStepik = StepikConnector.getSections(pushCandidates.map { it.id }.filter { it != 0 }.map { it.toString() }.toTypedArray())

    val deleteCandidates = ArrayList<Int>()
    for ((section, sectionFromServer) in pushCandidates.zip(sectionsFromStepik)) {
      when (section.pushStatus) {
        PushStatus.INFO -> {
          sectionsInfoToUpdate.add(section.id)
        }

        PushStatus.CONTENT -> {
          processSectionContentChanged(section, deleteCandidates, sectionFromServer)
        }

        PushStatus.ALL -> {
          sectionsInfoToUpdate.add(section.id)
          processSectionContentChanged(section, deleteCandidates, sectionFromServer)
        }
      }
    }

    lessonsToDelete.addAll(StepikConnector.getUnits(deleteCandidates.map { it.toString() }.toTypedArray()).filter { it.update_date <= lastUpdateDate }.map { it.id })
  }

  private fun processSectionContentChanged(section: Section,
                                           deleteCandidates: ArrayList<Int>,
                                           sectionFromServer: Section) {
    lessonsToPush.addAll(section.lessons.filter { it.id == 0 })

    val localSectionUnits = section.lessons.map { it.unitId }
    val allLocalUnits = course.allLessons().map { it.unitId }
    deleteCandidates.addAll(sectionFromServer.units.filter {
      val isMoved = it in allLocalUnits
      it !in localSectionUnits && !isMoved
    })
  }

  private fun processLessonChanges(lastUpdateDate: Date) {
    val pushCandidates = course.allLessons().filter { it.pushStatus != PushStatus.NOTHING }
    val lessonsFromStepik = StepikConnector.getLessonsFromUnits(course, pushCandidates.map { it.unitId.toString() }.toTypedArray(), false)

    val deleteCandidates = ArrayList<Int>()
    val allSteps = course.allLessons().flatMap { it.taskList }.map { it.stepId }
    for ((lesson, lessonFromServer) in pushCandidates.zip(lessonsFromStepik)) {
      when (lesson.pushStatus) {
        PushStatus.INFO -> {
          lessonsInfoToUpdate.add(lesson)
        }

        PushStatus.CONTENT -> {
          processLessonContentChanged(lesson, lessonFromServer, allSteps, deleteCandidates, lastUpdateDate)
        }

        PushStatus.ALL -> {
          lessonsInfoToUpdate.add(lesson)
          processLessonContentChanged(lesson, lessonFromServer, allSteps, deleteCandidates, lastUpdateDate)
        }
      }
    }

    lessonsToDelete.addAll(StepikConnector.getUnits(deleteCandidates.map { it.toString() }.toTypedArray()).filter { it.update_date <= lastUpdateDate }.map { it.id })
  }

  private fun processLessonContentChanged(lesson: Lesson,
                                          lessonFromServer: Lesson,
                                          allSteps: List<Int>,
                                          deleteCandidates: ArrayList<Int>,
                                          lastUpdateDate: Date) {
    tasksToPush.addAll(lesson.taskList.filter { it.stepId == 0 })

    val localSteps = lesson.taskList.map { it.stepId }
    for (step in lessonFromServer.steps) {
      val isMoved = step in allSteps
      if (step !in localSteps && !isMoved) {
        deleteCandidates.add(step)
      }
    }

    val stringIds = deleteCandidates.map { it.toString() }.toTypedArray()
    val stepSources = StepikConnector.getStepSources(stringIds)
    val tasksFromStep = StepikConnector.getTasks(course, stringIds, stepSources)
    tasksToDelete.addAll(tasksFromStep.filter { it.updateDate <= lastUpdateDate }.map { it.stepId })

  }

  private fun processTaskChanges() {
    val allTasks = course.allLessons().flatMap { it.taskList }
    tasksToUpdate.addAll(allTasks.filter { it.pushStatus != PushStatus.NOTHING }.map { it.stepId })
  }

  private fun processCourseContentChanged(lastUpdateDate: Date) {
    val courseInfo = CCStepikConnector.getCourseInfo(course.id.toString())!!
    val allLessons = course.allLessons().map { it.id }
    val hasTopLevelLessons = !course.lessons.isEmpty()
    if (hasTopLevelLessons) {
      lessonsToPush.addAll(course.lessons.filter { it.id == 0 })

      val section = StepikConnector.getSection(courseInfo.sectionIds[0])
      val topLevelLessonsIds = course.lessons.map { it.id }
      for (lesson in section.lessons) {
        if (lesson.id !in topLevelLessonsIds) {
          val isMoved = lesson.id in allLessons
          if (!isMoved && lesson.updateDate <= lastUpdateDate) {
            lessonsToDelete.add(lesson.id)
          }
        }
      }
    }
    else {
      sectionsToPush.addAll(course.sections.filter { it.id == 0 })

      //remove additional materials sections
      val remoteSectionIds = courseInfo.sectionIds.subList(0, courseInfo.sectionIds.size - 2)
      val sections = StepikConnector.getSections(remoteSectionIds.map { it.toString() }.toTypedArray())
      val localSectionIds = course.sections.map { it.id }
      for (section in sections) {
        if (section.id !in localSectionIds && section.updateDate <= lastUpdateDate) {
          sectionsToDelete.add(section.id)
        }
      }
    }
  }
}


private fun RemoteCourse.lastUpdateDate(): Date {
  var lastUpdateDate = updateDate
  allLessons().filter { it.id > 0 }.forEach {
    if (lastUpdateDate < it.updateDate) {
      lastUpdateDate = it.updateDate
    }

    it.taskList.filter { it.stepId > 0 }.forEach {
      if (lastUpdateDate < it.updateDate) {
        lastUpdateDate = it.updateDate
      }
    }
  }

  sections.filter { it.id > 0 }.forEach {
    if (lastUpdateDate < it.updateDate) {
      lastUpdateDate = it.updateDate
    }
  }

  return lastUpdateDate
}

private fun RemoteCourse.allLessons() = lessons.plus(sections.flatMap { it.lessons })
fun contentEquals(section: Section, sectionFromServer: Section): Boolean = TODO()
fun infoEquals(section: Section, sectionFromServer: Section): Boolean = TODO()

fun contentEquals(lesson: Lesson, lessonFromServer: Lesson): Boolean = TODO()
fun infoEquals(lesson: Lesson, lessonFromServer: Lesson): Boolean = TODO()

fun equals(task: Task, taskFromServer: Section): Boolean = TODO()