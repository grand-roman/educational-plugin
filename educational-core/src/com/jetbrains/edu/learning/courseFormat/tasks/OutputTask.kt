package com.jetbrains.edu.learning.courseFormat.tasks

import com.jetbrains.edu.learning.checker.OutputTaskChecker

/**
 * Task type that allows to test output without any test files.
 * Correct output is specified in output.txt file which is invisible for student.
 * @see OutputTaskChecker
 */
class OutputTask : Task() {
  companion object {
    const val OUTPUT_TASK_TYPE = "output"
  }

  override fun getTaskType() = OUTPUT_TASK_TYPE
}
