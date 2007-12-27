/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.ant;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleJdkUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 19, 2004
 */
public class ModuleChunk {
  private final Module[] myModules;
  private Module myMainModule;
  private ModuleChunk[] myDependentChunks;
  private File myBaseDir = null;

  public ModuleChunk(Module[] modules) {
    myModules = modules;
    myMainModule = myModules[0]; // todo: temporary, let user configure this
  }

  public String getName() {
    return myMainModule.getName();
  }

  public Module[] getModules() {
    return myModules;
  }

  @Nullable
  public String getOutputDirUrl() {
    return CompilerModuleExtension.getInstance(myMainModule).getCompilerOutputUrl();
  }

  @Nullable
  public String getTestsOutputDirUrl() {
    return CompilerModuleExtension.getInstance(myMainModule).getCompilerOutputUrlForTests();
  }

  public boolean isSavePathsRelative() {
    return myMainModule.isSavePathsRelative();
  }

  public boolean isJdkInherited() {
    return ModuleJdkUtil.isJdkInherited(ModuleRootManager.getInstance(myMainModule));
  }

  @Nullable
  public ProjectJdk getJdk() {
    return ModuleJdkUtil.getJdk(ModuleRootManager.getInstance(myMainModule));
  }

  public ModuleChunk[] getDependentChunks() {
    return myDependentChunks;
  }

  public void setDependentChunks(ModuleChunk[] dependentChunks) {
    myDependentChunks = dependentChunks;
  }

  public File getBaseDir() {
    if (myBaseDir != null) {
      return myBaseDir;
    }
    return new File(myMainModule.getModuleFilePath()).getParentFile();
  }

  public void setBaseDir(File baseDir) {
    myBaseDir = baseDir;
  }

  public void setMainModule(Module module) {
    myMainModule = module;
  }

  public Project getProject() {
    return myMainModule.getProject();
  }
}
