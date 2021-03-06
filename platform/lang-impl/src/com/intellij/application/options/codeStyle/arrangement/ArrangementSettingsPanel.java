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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.codeStyle.arrangement.additional.ForceArrangementPanel;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProviderImpl;
import com.intellij.application.options.codeStyle.arrangement.group.ArrangementGroupingRulesPanel;
import com.intellij.application.options.codeStyle.arrangement.match.ArrangementMatchingRulesPanel;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.std.*;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Denis Zhdanov
 * @since 10/30/12 5:17 PM
 */
public abstract class ArrangementSettingsPanel extends CodeStyleAbstractPanel {

  @NotNull private final JPanel myContent = new JPanel(new GridBagLayout());

  @NotNull private final Language                         myLanguage;
  @NotNull private final ArrangementStandardSettingsAware mySettingsAware;
  @NotNull private final ArrangementGroupingRulesPanel    myGroupingRulesPanel;
  @NotNull private final ArrangementMatchingRulesPanel    myMatchingRulesPanel;
  @Nullable private final ForceArrangementPanel myForceArrangementPanel;

  public ArrangementSettingsPanel(@NotNull CodeStyleSettings settings, @NotNull Language language) {
    super(settings);
    myLanguage = language;
    Rearranger<?> rearranger = Rearranger.EXTENSION.forLanguage(language);

    assert rearranger instanceof ArrangementStandardSettingsAware;
    mySettingsAware = (ArrangementStandardSettingsAware)rearranger;

    final ArrangementColorsProvider colorsProvider;
    if (rearranger instanceof ArrangementColorsAware) {
      colorsProvider = new ArrangementColorsProviderImpl((ArrangementColorsAware)rearranger);
    }
    else {
      colorsProvider = new ArrangementColorsProviderImpl(null);
    }

    ArrangementStandardSettingsManager settingsManager = new ArrangementStandardSettingsManager(mySettingsAware, colorsProvider);

    myGroupingRulesPanel = new ArrangementGroupingRulesPanel(settingsManager, colorsProvider);
    myMatchingRulesPanel = new ArrangementMatchingRulesPanel(settingsManager, colorsProvider);

    myContent.add(myGroupingRulesPanel, new GridBag().coverLine().fillCellHorizontally().weightx(1));
    myContent.add(myMatchingRulesPanel, new GridBag().fillCell().weightx(1).weighty(1).coverLine());



    if (settings.getCommonSettings(myLanguage).isForceArrangeMenuAvailable()) {
      myForceArrangementPanel = new ForceArrangementPanel();
      myForceArrangementPanel.setSelectedMode(settings.getCommonSettings(language).FORCE_REARRANGE_MODE);
      myContent.add(myForceArrangementPanel.getPanel(), new GridBag().anchor(GridBagConstraints.WEST).coverLine().fillCellHorizontally());
    }
    else {
      myForceArrangementPanel = null;
    }

    final List<CompositeArrangementSettingsToken> groupingTokens = settingsManager.getSupportedGroupingTokens();
    myGroupingRulesPanel.setVisible(groupingTokens != null && !groupingTokens.isEmpty());

    registerShortcut(ArrangementConstants.MATCHING_RULE_ADD, CommonShortcuts.getNew(), myMatchingRulesPanel);
    registerShortcut(ArrangementConstants.MATCHING_RULE_REMOVE, CommonShortcuts.getDelete(), myMatchingRulesPanel);
    registerShortcut(ArrangementConstants.MATCHING_RULE_MOVE_UP, CommonShortcuts.MOVE_UP, myMatchingRulesPanel);
    registerShortcut(ArrangementConstants.MATCHING_RULE_MOVE_DOWN, CommonShortcuts.MOVE_DOWN, myMatchingRulesPanel);
    final CustomShortcutSet edit = new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
    registerShortcut(ArrangementConstants.MATCHING_RULE_EDIT, edit, myMatchingRulesPanel);

    registerShortcut(ArrangementConstants.GROUPING_RULE_MOVE_UP, CommonShortcuts.MOVE_UP, myGroupingRulesPanel);
    registerShortcut(ArrangementConstants.GROUPING_RULE_MOVE_DOWN, CommonShortcuts.MOVE_DOWN, myGroupingRulesPanel);
  }

  private void registerShortcut(@NotNull String actionId, @NotNull ShortcutSet shortcut, @NotNull JComponent component) {
    final AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action != null) {
      action.registerCustomShortcutSet(shortcut, component, this);
    }
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myContent;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    return null;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private StdArrangementSettings getSettings(@NotNull CodeStyleSettings settings) {
    StdArrangementSettings result = (StdArrangementSettings)settings.getCommonSettings(myLanguage).getArrangementSettings();
    if (result == null) {
      result = mySettingsAware.getDefaultSettings();
    }
    return result;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    CommonCodeStyleSettings commonSettings = settings.getCommonSettings(myLanguage);
    commonSettings.setArrangementSettings(new StdRulePriorityAwareSettings(myGroupingRulesPanel.getRules(), myMatchingRulesPanel.getRules()));
    if (myForceArrangementPanel != null) {
      commonSettings.FORCE_REARRANGE_MODE = myForceArrangementPanel.getRearrangeMode();
    }
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    StdArrangementSettings s = new StdRulePriorityAwareSettings(myGroupingRulesPanel.getRules(), myMatchingRulesPanel.getRules());
    return !Comparing.equal(getSettings(settings), s)
           || myForceArrangementPanel != null && settings.getCommonSettings(myLanguage).FORCE_REARRANGE_MODE != myForceArrangementPanel.getRearrangeMode();
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    StdArrangementSettings s = getSettings(settings);
    if (s == null) {
      myGroupingRulesPanel.setRules(null);
      myMatchingRulesPanel.setRules(null);
    }
    else {
      List<ArrangementGroupingRule> groupings = s.getGroupings();
      myGroupingRulesPanel.setRules(ContainerUtilRt.newArrayList(groupings));
      myMatchingRulesPanel.setRules(copy(s.getRules()));
      if (myForceArrangementPanel != null) {
        myForceArrangementPanel.setSelectedMode(settings.getCommonSettings(myLanguage).FORCE_REARRANGE_MODE);
      }
    }
  }

  @NotNull
  private static List<StdArrangementMatchRule> copy(@NotNull List<StdArrangementMatchRule> rules) {
    List<StdArrangementMatchRule> result = new ArrayList<StdArrangementMatchRule>();
    for (StdArrangementMatchRule rule : rules) {
      result.add(rule.clone());
    }
    return result;
  }

  @Override
  protected String getTabTitle() {
    return ApplicationBundle.message("arrangement.title.settings.tab");
  }
}
