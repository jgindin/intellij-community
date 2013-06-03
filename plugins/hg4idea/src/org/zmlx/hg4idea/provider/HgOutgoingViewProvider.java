/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManagerQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesBrowserUseCase;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesTreeBrowser;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgRefreshOutgoingAction;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class HgOutgoingViewProvider implements ChangesViewContentProvider {

  private final Project myProject;
  private final MessageBus myBus;
  private final HgCachingCommitedChangesProvider myCommitedChangesProvider;
  private final ProgressManagerQueue myTaskQueue;
  private CommittedChangesTreeBrowser myBrowser;
  private MessageBusConnection myConnection;

  public HgOutgoingViewProvider(Project project, MessageBus bus, HgCachingCommitedChangesProvider commitedChangesProvider) {
    myProject = project;
    myBus = bus;
    myCommitedChangesProvider = commitedChangesProvider;
    myTaskQueue = new ProgressManagerQueue(project, VcsBundle.message("committed.changes.refresh.progress"));
  }

  @Override
  public JComponent initContent() {

    myTaskQueue.start();

    myBrowser = new CommittedChangesTreeBrowser(myProject, Collections.<CommittedChangeList>emptyList());
    myBrowser.getEmptyText().setText(VcsBundle.message("outgoing.changes.not.loaded.message"));
    ActionGroup group = (ActionGroup) ActionManager.getInstance().getAction("OutgoingChangesToolbar");
    final ActionToolbar toolbar = myBrowser.createGroupFilterToolbar(myProject, group, null, Collections.<AnAction>emptyList());
    myBrowser.setToolBar(toolbar.getComponent());
    myBrowser.setTableContextMenu(group, Collections.<AnAction>emptyList());
    myConnection = myBus.connect();
    myConnection.subscribe(HgRefreshOutgoingAction.REFRESH_OUTGOING_TOPIC, new HgRefreshOutgoingAction.OutgoingChangesListener() {
      @Override
      public void loadChanges() {
        updateModel();
      }
    });
    updateModel();

    JPanel contentPane = new JPanel(new BorderLayout());
    contentPane.add(myBrowser, BorderLayout.CENTER);
    return contentPane;
  }

  @Override
  public void disposeContent() {
    myConnection.disconnect();
    Disposer.dispose(myBrowser);
    myBrowser = null;
  }


  private void updateModel() {

    myTaskQueue.run(new Runnable() {
      @Override
      public void run() {
        List<CommittedChangeList> outgoingChanges = new ArrayList<CommittedChangeList>();

        final CommittedChangesCache cache = CommittedChangesCache.getInstance(myProject);
        HgVcs hgVcs = HgVcs.getInstance(myProject);
        if (null == hgVcs) {
          return;
        }

        Map<VirtualFile, RepositoryLocation> rootsUnderVcs = cache.getCachesHolder().getAllRootsUnderVcs(hgVcs);
        for (RepositoryLocation repositoryLocation : rootsUnderVcs.values()) {
          try {
            outgoingChanges.addAll(myCommitedChangesProvider.getOutgoingChanges(repositoryLocation));
          }
          catch (VcsException e) {
            VcsBalloonProblemNotifier.showOverChangesView(myProject, e.getMessage(), MessageType.ERROR);
          }
          loadChangesToBrowser(outgoingChanges);
        }
      }
    });
  }

  private void loadChangesToBrowser(final List<CommittedChangeList> outgoingChanges) {

    Runnable runnable = new Runnable() {
      public void run() {
        if (myProject.isDisposed()) return;
        if (myBrowser != null) {
          if (outgoingChanges.isEmpty()) {
            myBrowser.getEmptyText().setText(VcsBundle.message("outgoing.changes.empty.message"));
            myBrowser.setItems(Collections.<CommittedChangeList>emptyList(), CommittedChangesBrowserUseCase.COMMITTED);
          }
          else {
            myBrowser.setItems(outgoingChanges, CommittedChangesBrowserUseCase.COMMITTED);
          }
        }
      }
    };

    ApplicationManager.getApplication().invokeLater(runnable);
  }

} // End of HgOutgoingViewProvider class
