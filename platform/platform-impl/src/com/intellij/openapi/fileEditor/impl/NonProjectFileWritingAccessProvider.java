/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.vfs.*;
import com.intellij.util.NotNullFunction;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NonProjectFileWritingAccessProvider extends WritingAccessProvider {
  public enum AccessStatus {REQUESTED, ALLOWED}

  private static final Key<Boolean> ENABLE_IN_TESTS = Key.create("NON_PROJECT_FILE_ACCESS_ENABLE_IN_TESTS");
  private static final Key<Boolean> ALL_ACCESS_ALLOWED = Key.create("NON_PROJECT_FILE_ALL_ACCESS_STATUS");
  private static final NotNullLazyKey<Map<VirtualFile, AccessStatus>, Project> ACCESS_STATUS
    = NotNullLazyKey.create("NON_PROJECT_FILE_ACCESS_STATUS", new NotNullFunction<Project, Map<VirtualFile, AccessStatus>>() {
    @NotNull
    @Override
    public Map<VirtualFile, AccessStatus> fun(Project project) {
      return new HashMap<VirtualFile, AccessStatus>();
    }
  });

  @NotNull private final Project myProject;

  public NonProjectFileWritingAccessProvider(@NotNull final Project project) {
    myProject = project;
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        getRegisteredFiles(project).remove(event.getFile());
      }
    }, project);

    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        Map<VirtualFile, AccessStatus> files = getRegisteredFiles(project);
        
        // reset access status and notifications for files that became project files  
        for (VirtualFile each : new ArrayList<VirtualFile>(files.keySet())) {
          if (isProjectFile(each)) {
            files.remove(each);
          }
        }
      }
    });
  }

  @Override
  public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
    return true;
  }

  @NotNull
  @Override
  public Collection<VirtualFile> requestWriting(VirtualFile... files) {
    if (allAccessAllowed(myProject)) return Collections.emptyList();

    List<VirtualFile> deniedFiles = new SmartList<VirtualFile>();

    Map<VirtualFile, AccessStatus> statuses = getRegisteredFiles(myProject);
    for (VirtualFile each : files) {
      if (statuses.get(each) == AccessStatus.ALLOWED) continue;

      if (!(each.getFileSystem() instanceof LocalFileSystem)) continue; // do not block e.g., HttpFileSystem, LightFileSystem etc.  
      if (isProjectFile(each)) {
        statuses.remove(each);
        continue;
      }

      statuses.put(each, AccessStatus.REQUESTED);
      deniedFiles.add(each);
    }

    if (deniedFiles.isEmpty()) return Collections.emptyList();

    UnlockOption unlockOption = askToUnlock(deniedFiles);

    if (unlockOption == null) return deniedFiles;

    switch (unlockOption) {
      case UNLOCK:
        for (VirtualFile eachAllowed : deniedFiles) {
          statuses.put(eachAllowed, AccessStatus.ALLOWED);
        }
        break;
      case UNLOCK_ALL:
        myProject.putUserData(ALL_ACCESS_ALLOWED, Boolean.TRUE);
        break;
    }

    return Collections.emptyList();
  }

  @Nullable
  private UnlockOption askToUnlock(@NotNull List<VirtualFile> files) {
    NonProjectFileWritingAccessDialog dialog = new NonProjectFileWritingAccessDialog(myProject, files);
    if (!dialog.showAndGet()) return null;
    return dialog.getUnlockOption();
  }

  private boolean isProjectFile(@NotNull VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectFileIndex.SERVICE.getInstance(myProject);
    if (fileIndex.isInContent(file)) return true;
    if (fileIndex.isIgnored(file)) return true;

    if (myProject instanceof ProjectEx) {
      IProjectStore store = ((ProjectEx)myProject).getStateStore();

      if (store.getStorageScheme() == StorageScheme.DIRECTORY_BASED) {
        VirtualFile baseDir = myProject.getBaseDir();
        VirtualFile dotIdea = baseDir == null ? null : baseDir.findChild(Project.DIRECTORY_STORE_FOLDER);
        if (dotIdea != null && VfsUtilCore.isAncestor(dotIdea, file, false)) return true;
      }

      if (file.equals(store.getWorkspaceFile()) || file.equals(store.getProjectFile())) return true;
      for (Module each : ModuleManager.getInstance(myProject).getModules()) {
        if (file.equals(each.getModuleFile())) return true;
      }
    }

    return false;
  }

  private static boolean allAccessAllowed(@NotNull Project project) {
    // disable checks in tests, if not asked
    if (ApplicationManager.getApplication().isUnitTestMode() && project.getUserData(ENABLE_IN_TESTS) != Boolean.TRUE) {
      return true;
    }
    
    return project.getUserData(ALL_ACCESS_ALLOWED) == Boolean.TRUE;
  }

  @NotNull
  private static Map<VirtualFile, AccessStatus> getRegisteredFiles(@NotNull Project project) {
    return ACCESS_STATUS.getValue(project);
  }

  public enum UnlockOption {UNLOCK, UNLOCK_ALL}
}
