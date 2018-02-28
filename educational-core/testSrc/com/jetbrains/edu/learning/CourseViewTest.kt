// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.edu.learning

import com.intellij.ide.projectView.ProjectView
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.jetbrains.edu.learning.actions.CheckAction
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checker.TaskChecker
import com.jetbrains.edu.learning.checker.TaskCheckerProvider
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.projectView.CourseViewPane
import junit.framework.TestCase
import org.junit.Assert
import java.io.IOException

class CourseViewTest : EduTestCase() {

  private var myCourse: Course? = null

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myCourse?.language = PlainTextLanguage.INSTANCE.id
    registerPlainTextConfigurator()
    ProjectViewTestUtil.setupImpl(project, true)
  }

  fun testCoursePane() {
    configureByTaskFile(1, 1, "taskFile1.txt")
    val pane = createPane()

    val structure = "-Project\n" +
                    " -CourseNode Edu test course  0/4\n" +
                    "  -LessonNode lesson1\n" +
                    "   +TaskNode task1\n" +
                    "   +TaskNode task2\n" +
                    "   +TaskNode task3\n" +
                    "   +TaskNode task4\n"
    PlatformTestUtil.assertTreeEqual(pane.tree, structure)
  }

  fun testProjectOpened() {
    EduUtils.openFirstTask(myCourse!!, project)
    val projectView = ProjectView.getInstance(project)
    projectView.changeView(CourseViewPane.ID)
    val structure = "-Project\n" +
                    " -CourseNode Edu test course  0/4\n" +
                    "  -LessonNode lesson1\n" +
                    "   -TaskNode task1\n" +
                    "    taskFile1.txt\n" +
                    "   +TaskNode task2\n" +
                    "   +TaskNode task3\n" +
                    "   +TaskNode task4\n"
    val pane = projectView.currentProjectViewPane
    PlatformTestUtil.waitWhileBusy(pane.tree)
    PlatformTestUtil.assertTreeEqual(pane.tree, structure)
  }

  fun testExpandAfterNavigation() {
    configureByTaskFile(1, 1, "taskFile1.txt")
    val projectView = ProjectView.getInstance(project)
    projectView.changeView(CourseViewPane.ID)
    navigateToNextTask()

    val pane = projectView.currentProjectViewPane
    val structure = "-Project\n" +
                    " -CourseNode Edu test course  0/4\n" +
                    "  -LessonNode lesson1\n" +
                    "   +TaskNode task1\n" +
                    "   -TaskNode task2\n" +
                    "    taskFile2.txt\n" +
                    "   +TaskNode task3\n" +
                    "   +TaskNode task4\n"
    PlatformTestUtil.assertTreeEqual(pane.tree, structure)
  }

  fun testCourseProgress() {
    configureByTaskFile(1, 1, "taskFile1.txt")
    val projectView = ProjectView.getInstance(project)
    projectView.changeView(CourseViewPane.ID)
    val pane = projectView.currentProjectViewPane
    UsefulTestCase.assertInstanceOf(pane, CourseViewPane::class.java)
    TestCase.assertNotNull((pane as CourseViewPane).getProgressBar())
  }

  fun testSwitchingPane() {
    val projectView = ProjectView.getInstance(project)
    projectView.changeView(CourseViewPane.ID)
    TestCase.assertEquals(CourseViewPane.ID, projectView.currentViewId)
  }

  fun testCheckTask() {
    configureByTaskFile(1, 1, "taskFile1.txt")
    val projectView = ProjectView.getInstance(project)
    projectView.changeView(CourseViewPane.ID)

    val fileName = "lesson1/task1/taskFile1.txt"
    val taskFile = myFixture.findFileInTempDir(fileName)
    val action = CheckAction()
    launchAction(taskFile, action)

    val pane = projectView.currentProjectViewPane
    val structure = "-Project\n" +
                          " +CourseNode Edu test course  1/4\n"
    PlatformTestUtil.waitWhileBusy(pane.tree)
    PlatformTestUtil.assertTreeEqual(pane.tree, structure)
  }

  private fun launchAction(taskFile: VirtualFile, action: CheckAction) {
    val e = getActionEvent(taskFile, action)
    action.beforeActionPerformedUpdate(e)
    Assert.assertTrue(e.presentation.isEnabled && e.presentation.isVisible)
    action.actionPerformed(e)
  }

  private fun getActionEvent(virtualFile: VirtualFile, action: AnAction): TestActionEvent {
    val context = MapDataContext()
    context.put(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(virtualFile))
    context.put<Project>(CommonDataKeys.PROJECT, project)
    return TestActionEvent(context, action)
  }

  private fun navigateToNextTask() {
    val eduEditor = EduUtils.getSelectedStudyEditor(project)
    val eduState = EduState(eduEditor)
    TestCase.assertTrue(eduState.isValid)
    val targetTask = NavigationUtils.nextTask(eduState.task)
    TestCase.assertNotNull(targetTask)
    NavigationUtils.navigateToTask(project, targetTask!!)
  }

  @Throws(IOException::class)
  override fun createCourse() {
    myFixture.copyDirectoryToProject("lesson1", "lesson1")
    myCourse = Course()
    myCourse!!.language = PlainTextLanguage.INSTANCE.id
    myCourse!!.name = "Edu test course"
    StudyTaskManager.getInstance(myFixture.project).course = myCourse

    val lesson1 = createLesson(1, 4)
    myCourse!!.addLesson(lesson1)
    myCourse!!.initCourse(false)
  }

  private fun createPane(): CourseViewPane {
    val pane = CourseViewPane(project)
    pane.createComponent()
    Disposer.register(project, pane)
    return pane
  }

  private fun registerPlainTextConfigurator() {
    val extension = LanguageExtensionPoint<Annotator>()
    extension.language = PlainTextLanguage.INSTANCE.id
    extension.implementationClass = PlainTextConfigurator::class.java.name
    PlatformTestUtil.registerExtension(
      ExtensionPointName.create(EduConfigurator.EP_NAME), extension, myFixture.testRootDisposable)
  }

  class PlainTextConfigurator : EduConfigurator<Unit> {
    override fun getCourseBuilder() = object : EduCourseBuilder<Unit> {
      override fun createTaskContent(project: Project, task: Task, parentDirectory: VirtualFile, course: Course): VirtualFile? = null
      override fun getLanguageSettings(): EduCourseBuilder.LanguageSettings<Unit> = EduCourseBuilder.LanguageSettings { Unit }
    }

    override fun getTestFileName() = "test.txt"

    override fun excludeFromArchive(name: String) = false

    override fun getTaskCheckerProvider() = TaskCheckerProvider{ task, project -> object: TaskChecker<EduTask>(task, project) {
      override fun check(): CheckResult {
        return CheckResult(CheckStatus.Solved, "")
      }
    }}
  }

  override fun getTestDataPath(): String {
    return super.getTestDataPath() + "/projectView"
  }
}