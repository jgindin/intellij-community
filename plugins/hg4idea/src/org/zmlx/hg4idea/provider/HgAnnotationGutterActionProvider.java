/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.zmlx.hg4idea.provider;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineNumberListener;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;

/**
 * Adds an action from the annotation to show the other affected files for a given revision.
 */
public class HgAnnotationGutterActionProvider implements AnnotationGutterActionProvider {

  @NotNull
  @Override
  public AnAction createAction(FileAnnotation annotation) {
    return new ShowAffectedFilesAction( annotation );
  }


  private static class ShowAffectedFilesAction extends AnAction implements LineNumberListener {

    private final FileAnnotation myAnnotation;
    private int myLineNumber;

    public ShowAffectedFilesAction(FileAnnotation annotation) {
      super(HgVcsMessages.message("hg4idea.annotation.show-affected-files"),
            HgVcsMessages.message("hg4idea.annotation.show-affected-files"),
            AllIcons.Vcs.AllRevisions);
      myAnnotation = annotation;
    }

    @Override
    public void consume(Integer integer) {
      myLineNumber = integer;
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);

      ActionData actionData = calcActionData(e);
      e.getPresentation().setEnabled(actionData.isValid());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {

      ActionData actionData = calcActionData(e);
      if (!actionData.isValid()) {
        return;
      }

      ShowAllAffectedGenericAction.showSubmittedFiles(actionData.getProject(), actionData.myRevisionNumber,
                                                      actionData.myVirtualFile, HgVcs.getKey());
    }

    private ActionData calcActionData(AnActionEvent e) {

      final Project project = e.getData(PlatformDataKeys.PROJECT);
      final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      final VcsRevisionNumber revisionNumber = (myLineNumber == -1 ? null : myAnnotation.getLineRevisionNumber(myLineNumber));

      return new ActionData(project, virtualFile, revisionNumber);
    }


    private static class ActionData {
      private final Project myProject;
      private final VirtualFile myVirtualFile;
      private final VcsRevisionNumber myRevisionNumber;

      private ActionData(Project project, VirtualFile virtualFile, VcsRevisionNumber revisionNumber) {

        myProject = project;
        myVirtualFile = virtualFile;
        myRevisionNumber = revisionNumber;
      }

      public Project getProject() {
        return myProject;
      }

      public VirtualFile getVirtualFile() {
        return myVirtualFile;
      }

      public VcsRevisionNumber getRevisionNumber() {
        return myRevisionNumber;
      }

      public boolean isValid() {
        return null != myProject && null != myVirtualFile && null != myRevisionNumber;
      }
    }
  }

}  // End of HgAnnotationGutterActionProvider class
