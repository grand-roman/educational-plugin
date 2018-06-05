package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.stepik.StepikCourseChangeHandler;
import com.jetbrains.edu.learning.EduUtils;
import com.jetbrains.edu.learning.NewPlaceholderPainter;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class CCHideFromStudent extends CCTaskFileActionBase {
  private static final String ACTION_NAME = "Hide from Student";

  public CCHideFromStudent() {
    super(ACTION_NAME);
  }

  @Override
  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    TaskFile taskFile = EduUtils.getTaskFile(project, file);
    if (taskFile == null) {
      return;
    }
    EduUtils.runUndoableAction(project, ACTION_NAME, new HideTaskFile(project, file, task, taskFile));
  }

  private static class HideTaskFile extends BasicUndoableAction {

    private final Project myProject;
    private final VirtualFile myFile;
    private final Task myTask;
    private final TaskFile myTaskFile;

    public HideTaskFile(Project project, VirtualFile file, Task task, TaskFile taskFile) {
      super(file);
      myProject = project;
      myFile = file;
      myTask = task;
      myTaskFile = taskFile;
    }

    @Override
    public void undo() {
      myTask.getTaskFiles().put(EduUtils.pathRelativeToTask(myProject, myFile), myTaskFile);
      if (!myTaskFile.getAnswerPlaceholders().isEmpty() && FileEditorManager.getInstance(myProject).isFileOpen(myFile)) {
        for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getEditors(myFile)) {
          if (fileEditor instanceof TextEditor) {
            Editor editor = ((TextEditor)fileEditor).getEditor();
            EduUtils.drawAllAnswerPlaceholders(editor, myTaskFile);
          }
        }
      }
      ProjectView.getInstance(myProject).refresh();
      StepikCourseChangeHandler.INSTANCE.notChanged(myTask);
    }

    @Override
    public void redo() {
      hideFromStudent(myFile, myProject, myTask.getTaskFiles(), myTaskFile);
      ProjectView.getInstance(myProject).refresh();

      StepikCourseChangeHandler.INSTANCE.changed(myTask);
    }

    @Override
    public boolean isGlobal() {
      return true;
    }
  }

  public static void hideFromStudent(VirtualFile file, Project project, Map<String, TaskFile> taskFiles, @NotNull final TaskFile taskFile) {
    final List<AnswerPlaceholder> placeholders = taskFile.getAnswerPlaceholders();
    if (!placeholders.isEmpty() && FileEditorManager.getInstance(project).isFileOpen(file)) {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getEditors(file)) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          for (AnswerPlaceholder placeholder : placeholders) {
            NewPlaceholderPainter.INSTANCE.removePainter(editor, placeholder);
          }
        }
      }
    }
    String taskRelativePath = EduUtils.pathRelativeToTask(project, file);
    taskFiles.remove(taskRelativePath);
  }

  @Override
  protected boolean isAvailable(Project project, VirtualFile file) {
    return EduUtils.getTaskFile(project, file) != null;
  }
}
