/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
@file:JvmName("ProjectUtil")
package com.intellij.openapi.project

import com.intellij.ide.DataManager
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.util.PathUtilRt
import com.intellij.util.io.exists
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Consumer
import javax.swing.JComponent

val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

@JvmOverloads
fun calcRelativeToProjectPath(file: VirtualFile, project: Project?, includeFilePath: Boolean = true, includeUniqueFilePath: Boolean = false, keepModuleAlwaysOnTheLeft: Boolean = false): String {
  if (file is VirtualFilePathWrapper && file.enforcePresentableName()) {
    return if (includeFilePath) file.presentablePath else file.name
  }

  val url = if (includeFilePath) {
    file.presentableUrl
  }
  else if (includeUniqueFilePath) {
    UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file)
  }
  else {
    file.name
  }

  if (project == null) {
    return url
  }
  return displayUrlRelativeToProject(file, url, project, includeFilePath, keepModuleAlwaysOnTheLeft)
}

fun guessProjectForFile(file: VirtualFile?): Project? = ProjectLocator.getInstance().guessProjectForFile(file)

/***
 * guessProjectForFile works incorrectly - even if file is config (idea config file) first opened project will be returned
 */
@JvmOverloads
fun guessProjectForContentFile(file: VirtualFile, fileType: FileType = file.fileType): Project? {
  if (ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType)) {
    return null
  }

  return ProjectManager.getInstance().openProjects.firstOrNull { !it.isDefault && it.isInitialized && !it.isDisposed && ProjectRootManager.getInstance(it).fileIndex.isInContent(file) }
}

fun isProjectOrWorkspaceFile(file: VirtualFile): Boolean {
  // do not use file.getFileType() to avoid autodetection by content loading for arbitrary files
  return ProjectCoreUtil.isProjectOrWorkspaceFile(file, FileTypeManager.getInstance().getFileTypeByFileName(file.name))
}

fun guessCurrentProject(component: JComponent?): Project {
  var project: Project? = null
  if (component != null) {
    project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
  }

  @Suppress("DEPRECATION")
  return project
         ?: ProjectManager.getInstance().openProjects.firstOrNull()
         ?: CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContext)
         ?: ProjectManager.getInstance().defaultProject
}

inline fun <T> Project.modifyModules(crossinline task: ModifiableModuleModel.() -> T): T {
  val model = ModuleManager.getInstance(this).modifiableModel
  val result = model.task()
  runWriteAction {
    model.commit()
  }
  return result
}

fun isProjectDirectoryExistsUsingIo(parent: VirtualFile): Boolean {
  try {
    return Paths.get(FileUtil.toSystemDependentName(parent.path), Project.DIRECTORY_STORE_FOLDER).exists()
  }
  catch (e: InvalidPathException) {
    return false
  }
}

/**
 *  Tries to guess the "main project directory" of the project.
 *
 *  There is no strict definition of what is a project directory, since a project can contain multiple modules located in different places,
 *  and the `.idea` directory can be located elsewhere (making the popular [Project.getBaseDir] method not applicable to get the "project
 *  directory"). This method should be preferred, although it can't provide perfect accuracy either.
 *
 *  @throws IllegalStateException if called on the default project, since there is no sense in "project dir" in that case.
 */
fun Project.guessProjectDir() : VirtualFile {
  if (isDefault) {
    throw IllegalStateException("Not applicable for default project")
  }

  val modules = ModuleManager.getInstance(this).modules
  val module = if (modules.size == 1) modules.first() else modules.find { it.name == this.name }
  module?.rootManager?.contentRoots?.firstOrNull()?.let {
    return it
  }
  return this.baseDir!!
}

private fun Project.getPresentableFileName(): String {
  if (isDefault) {
    return name
  }

  val projectName = FileUtilRt.toSystemIndependentName(PathUtilRt.getFileName(presentableUrl!!))
    .toLowerCase(Locale.US)
    .removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
  return FileUtil.sanitizeFileName(projectName, false)
}

private fun Project.getProjectCacheFileName(forceNameUse: Boolean, hashSeparator: String): String {
  return "${if (forceNameUse) name else getPresentableFileName()}$hashSeparator$locationHash"
}

@JvmOverloads
fun Project.getProjectCachePath(cacheName: String, forceNameUse: Boolean = false): Path {
  return getProjectCachePath(appSystemDir.resolve(cacheName), forceNameUse)
}

/**
 * Use parameters only for migration purposes, once all usages will be migrated, parameters will be removed
 */
@JvmOverloads
fun Project.getProjectCachePath(baseDir: Path, forceNameUse: Boolean = false, hashSeparator: String = "."): Path {
  return baseDir.resolve(getProjectCacheFileName(forceNameUse, hashSeparator))
}

/**
 * Add one-time projectOpened listener.
 */
fun Project.runWhenProjectOpened(handler: Runnable) = runWhenProjectOpened(this) { handler.run() }

/**
 * Add one-time first projectOpened listener.
 */
@JvmOverloads
fun runWhenProjectOpened(project: Project? = null, handler: Consumer<Project>) = runWhenProjectOpened(project) { handler.accept(it) }

/**
 * Add one-time projectOpened listener.
 */
inline fun runWhenProjectOpened(project: Project? = null, crossinline handler: (project: Project) -> Unit) {
  val connection = (project ?: ApplicationManager.getApplication()).messageBus.connect()
  connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
    override fun projectOpened(eventProject: Project) {
      if (project == null || project === eventProject) {
        connection.disconnect()
        handler(eventProject)
      }
    }
  })
}