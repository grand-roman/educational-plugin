package com.jetbrains.edu.coursecreator.actions.stepik;

import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.RemoteCourse;
import com.jetbrains.edu.learning.courseFormat.Section;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.edu.coursecreator.stepik.CCStepikConnector.*;

public class CCPushCourse extends DumbAwareAction {
  public CCPushCourse() {
    super("&Upload Course to Stepik", "Upload Course to Stepik", null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    presentation.setEnabledAndVisible(project != null && CCUtils.isCourseCreator(project));
    if (project != null) {
      final Course course = StudyTaskManager.getInstance(project).getCourse();
      if (course instanceof RemoteCourse) {
        presentation.setText("Update Course on Stepik");
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (view == null || project == null) {
      return;
    }
    final Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    if (course instanceof RemoteCourse) {
      ProgressManager.getInstance().run(new Task.Modal(project, "Updating Course", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          updateCourseInfo(project, (RemoteCourse) course);
          updateCourseContent(indicator, course, project);
          showNotification(project, "Course is updated", seeOnStepikAction("/course/" + ((RemoteCourse)course).getId())
          );
        }
      });
    }
    else {
      if (!course.getSections().isEmpty() && !course.getLessons().isEmpty()) {
        int result = Messages.showYesNoDialog(project, "Since you have sections, we have to wrap top-level lessons into section before upload",
                                              "Wrap Lessons Into Sections", "Wrap and Post", "Cancel", null);
        if (result == Messages.YES) {
          wrapUnpushedLessonsIntoSections(project, course);
        }
        else {
          return;
        }
      }
      postCourseWithProgress(project, course);
    }
    EduUsagesCollector.courseUploaded();
  }

  private static void updateCourseContent(@NotNull ProgressIndicator indicator, Course course, Project project) {
    for (Section section : course.getSections()) {
      if (section.getId() > 0) {
        updateSection(project, section);
      }
      else {
        postSection(project, section, indicator);
        updateAdditionalSection(project);
      }
    }

    for (Lesson lesson : course.getLessons()) {
      if (lesson.getId() > 0) {
        updateLessonInfo(project, lesson, false);
        updateLesson(project, lesson, false);
      }
      else {
        postLesson(project, lesson);
      }
    }
  }
}