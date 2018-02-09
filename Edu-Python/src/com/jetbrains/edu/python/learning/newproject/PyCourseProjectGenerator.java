package com.jetbrains.edu.python.learning.newproject;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.EduNames;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseGeneration.GeneratorUtils;
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator;
import com.jetbrains.edu.python.learning.PyConfigurator;
import com.jetbrains.edu.python.learning.PyCourseBuilder;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class PyCourseProjectGenerator extends CourseProjectGenerator<PyNewProjectSettings> {
  private static final Logger LOG = Logger.getInstance(PyCourseProjectGenerator.class);

  public PyCourseProjectGenerator(@NotNull PyCourseBuilder builder, @NotNull Course course) {
    super(builder, course);
  }

  @Override
  protected void createAdditionalFiles(@NotNull Project project, @NotNull VirtualFile baseDir) throws IOException {
    final String testHelper = EduNames.TEST_HELPER;
    if (baseDir.findChild(testHelper) != null) return;
    final FileTemplate template = FileTemplateManager.getInstance(project).getInternalTemplate("test_helper");
    GeneratorUtils.createChildFile(baseDir, testHelper, template.getText());
  }

  @Override
  protected void afterProjectGenerated(@NotNull Project project, @NotNull PyNewProjectSettings settings) {
    super.afterProjectGenerated(project, settings);
    Sdk sdk = settings.getSdk();

    if (sdk != null && sdk.getSdkType() == PyFakeSdkType.INSTANCE) {
      createAndAddVirtualEnv(project, settings);
      sdk = settings.getSdk();
    }
    if (sdk instanceof PyDetectedSdk) {
      sdk = addDetectedSdk(sdk, project);
    }
    SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk);
  }

  private Sdk addDetectedSdk(@NotNull Sdk sdk, @NotNull Project project) {
    final String name = sdk.getName();
    VirtualFile sdkHome = WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(name));
    sdk = SdkConfigurationUtil.createAndAddSDK(sdkHome.getPath(), PythonSdkType.getInstance());
    if (sdk != null) {
      PythonSdkUpdater.updateOrShowError(sdk, null, project, null);
      addSdk(project, sdk);
    }

    return sdk;
  }

  public void createAndAddVirtualEnv(@NotNull Project project, @NotNull PyNewProjectSettings settings) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final String baseSdk = getBaseSdk(course);

    if (baseSdk != null) {
      final PyPackageManager packageManager = PyPackageManager.getInstance(new PyDetectedSdk(baseSdk));
      try {
        final String path = packageManager.createVirtualEnv(project.getBasePath() + "/.idea/VirtualEnvironment", false);
        AbstractCreateVirtualEnvDialog.setupVirtualEnvSdk(path, true, (createdSdk, associateWithProject) -> {
          settings.setSdk(createdSdk);
          addSdk(project, createdSdk);
          if (associateWithProject) {
            SdkAdditionalData additionalData = createdSdk.getSdkAdditionalData();
            if (additionalData == null) {
              additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(createdSdk.getHomePath()));
              ((ProjectJdkImpl)createdSdk).setSdkAdditionalData(additionalData);
            }
            ((PythonSdkAdditionalData)additionalData).associateWithNewProject();
          }
        });
      }
      catch (ExecutionException e) {
        LOG.warn("Failed to create virtual env " + e.getMessage());
      }
    }
  }

  @Nullable
  static String getBaseSdk(@NotNull final Course course) {
    LanguageLevel baseLevel = LanguageLevel.PYTHON30;
    final String version = course.getLanguageVersion();
    if (PyConfigurator.PYTHON_2.equals(version)) {
      baseLevel = LanguageLevel.PYTHON27;
    }
    else if (PyConfigurator.PYTHON_3.equals(version)) {
      baseLevel = LanguageLevel.PYTHON31;
    }
    else if (version != null) {
      baseLevel = LanguageLevel.fromPythonVersion(version);
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getApplicableFlavors(false).get(0);
    String baseSdk = null;
    final Collection<String> baseSdks = flavor.suggestHomePaths();
    for (String sdk : baseSdks) {
      final String versionString = flavor.getVersionString(sdk);
      final String prefix = flavor.getName() + " ";
      if (versionString != null && versionString.startsWith(prefix)) {
        final LanguageLevel level = LanguageLevel.fromPythonVersion(versionString.substring(prefix.length()));
        if (level.isAtLeast(baseLevel)) {
          baseSdk = sdk;
          break;
        }
      }
    }
    if (baseSdk != null) return baseSdk;
    return baseSdks.isEmpty() ? null : baseSdks.iterator().next();
  }

  protected void addSdk(@NotNull Project project, @NotNull Sdk sdk) {
    SdkConfigurationUtil.addSdk(sdk);
  }

  @NotNull
  protected List<Sdk> getAllSdks(@NotNull Project project) {
    return ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance());
  }
}