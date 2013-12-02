// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googlesource.gerrit.plugins.download.command.project;

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.MyPluginLifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

@Singleton
public class DownloadCommandUpdater extends MyPluginLifecycleListener implements
    GitReferenceUpdatedListener {
  private static final Logger log = LoggerFactory
      .getLogger(DownloadCommandUpdater.class);

  private final String pluginName;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final Map<String, ProjectDownloadCommand> projectDownloadCommands;
  private final Map<String, RegistrationHandle> registrationHandles;
  private final Executor executor;

  @Inject
  DownloadCommandUpdater(@PluginName String pluginName,
      DynamicMap<DownloadCommand> downloadCommands,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache, WorkQueue queue) {
    super(pluginName);
    this.pluginName = pluginName;
    this.downloadCommands = downloadCommands;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.projectDownloadCommands = Maps.newHashMap();
    this.registrationHandles = Maps.newHashMap();
    this.executor = queue.createQueue(1, "download-command-updater");
  }

  @Override
  public void onLoad() {
    for (Project.NameKey p : projectCache.all()) {
      ProjectState projectState = projectCache.get(p);
      if (projectState != null) {
        installCommandAsync(projectState);
      }
    }
  }

  @Override
  public void onUnload() {
    for (RegistrationHandle rh : registrationHandles.values()) {
      rh.remove();
    }
    registrationHandles.clear();
    projectDownloadCommands.clear();
  }

  @Override
  public void onGitReferenceUpdated(Event event) {
    if (event.getRefName().equals(GitRepositoryManager.REF_CONFIG)) {
      Project.NameKey p = new Project.NameKey(event.getProjectName());
      try {
        ProjectConfig oldCfg =
            ProjectConfig.read(metaDataUpdateFactory.create(p),
                ObjectId.fromString(event.getOldObjectId()));
        PluginConfig oldPluginCfg = oldCfg.getPluginConfig(pluginName);
        for (String name : oldPluginCfg.getNames()) {
          removeCommand(p, name);
        }

        ProjectConfig newCfg =
            ProjectConfig.read(metaDataUpdateFactory.create(p),
                ObjectId.fromString(event.getNewObjectId()));
        PluginConfig newPluginCfg = newCfg.getPluginConfig(pluginName);
        for (String name : newPluginCfg.getNames()) {
          installCommand(p, name, newPluginCfg.getString(name));
        }
      } catch (IOException | ConfigInvalidException e) {
        log.error("Failed to update download commands for project "
            + p.get() + " on update of " + GitRepositoryManager.REF_CONFIG, e);
      }
    }
  }

  private void installCommandAsync(final ProjectState p) {
    executor.submit(new Runnable() {
      @Override
      public void run() {
        PluginConfig cfg = p.getConfig().getPluginConfig(pluginName);
        for (String name : cfg.getNames()) {
          installCommand(p.getProject().getNameKey(), name, cfg.getString(name));
        }
      }
    });
  }

  private void installCommand(final Project.NameKey p, String name,
      final String command) {
    ProjectDownloadCommand dc = projectDownloadCommands.get(name);
    if (dc != null) {
      dc.add(p, command);
    } else {
      dc = new ProjectDownloadCommand(p, command);
      projectDownloadCommands.put(name, dc);
      registrationHandles.put(name,
          map().put(pluginName, name.replaceAll("-", " "), provider(dc)));
    }
  }

  private Provider<DownloadCommand> provider(final DownloadCommand dc) {
    return new Provider<DownloadCommand>() {
      @Override
      public DownloadCommand get() {
        return dc;
      }
    };
  }

  private void removeCommand(Project.NameKey p, String name) {
    ProjectDownloadCommand dc = projectDownloadCommands.get(name);
    dc.remove(p);
    if (!dc.hasCommands()) {
      registrationHandles.remove(name).remove();
    }
  }

  private PrivateInternals_DynamicMapImpl<DownloadCommand> map() {
    return (PrivateInternals_DynamicMapImpl<DownloadCommand>) downloadCommands;
  }
}
