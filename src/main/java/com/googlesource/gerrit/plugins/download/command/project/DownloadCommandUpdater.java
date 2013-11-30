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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.LifecycleListener;
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
import java.util.concurrent.Callable;

@Singleton
public class DownloadCommandUpdater implements GitReferenceUpdatedListener,
    LifecycleListener {
  private static final Logger log = LoggerFactory
      .getLogger(DownloadCommandUpdater.class);

  private final String pluginName;
  private final DynamicMap<DownloadCommand> downloadCommands;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final Multimap<String, RegistrationHandle> registrationHandles;
  private final Executor executor;

  @Inject
  DownloadCommandUpdater(@PluginName String pluginName,
      DynamicMap<DownloadCommand> downloadCommands,
      MetaDataUpdate.Server metaDataUpdateFactory,
      ProjectCache projectCache, WorkQueue queue) {
    this.pluginName = pluginName;
    this.downloadCommands = downloadCommands;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.registrationHandles = ArrayListMultimap.create();
    this.executor = queue.createQueue(1, "download-command-updater");
  }

  @Override
  public void start() {
    for (Project.NameKey p : projectCache.all()) {
      ProjectState projectState = projectCache.get(p);
      if (projectState != null) {
        installCommandAsync(projectState);
      }
    }
  }

  @Override
  public void stop() {
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
    executor.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        PluginConfig cfg = p.getConfig().getPluginConfig(pluginName);
        for (String name : cfg.getNames()) {
          installCommand(p.getProject().getNameKey(), name, cfg.getString(name));
        }
        return null;
      }
    });
  }

  private void installCommand(final Project.NameKey p, String name,
      final String command) {
    registrationHandles.put(key(p, name),
        map().put(pluginName + "_" + p.get(), name.replaceAll("-", " "),
            new Provider<DownloadCommand>() {
              @Override
              public DownloadCommand get() {
                return new DownloadCommand() {
                  @Override
                  public String getCommand(DownloadScheme scheme,
                      String project, String ref) {
                    if (!p.get().equals(project)) {
                      return null;
                    }
                    return command.replaceAll("\\$\\{ref\\}", ref)
                        .replaceAll("\\$\\{url\\}", scheme.getUrl(project))
                        .replaceAll("\\$\\{project\\}", p.get());
                  }
                };
              }
            }));
  }

  private void removeCommand(Project.NameKey p, String name) {
    for (RegistrationHandle h : registrationHandles.get(key(p, name))) {
      h.remove();
    }
  }

  private PrivateInternals_DynamicMapImpl<DownloadCommand> map() {
    return (PrivateInternals_DynamicMapImpl<DownloadCommand>) downloadCommands;
  }

  private static String key(Project.NameKey project, String commandName) {
    return project.get() + "_" + commandName;
  }
}
