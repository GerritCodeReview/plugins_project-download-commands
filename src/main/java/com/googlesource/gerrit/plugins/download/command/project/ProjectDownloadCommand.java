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

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import java.util.Map;

public class ProjectDownloadCommand extends DownloadCommand {
  private final ProjectCache projectCache;
  private final Map<Project.NameKey, String> commands;

  ProjectDownloadCommand(ProjectCache projectCache, Project.NameKey project, String command) {
    this.projectCache = projectCache;
    this.commands = Maps.newHashMap();
    add(project, command);
  }

  public void add(Project.NameKey project, String command) {
    commands.put(project, Strings.nullToEmpty(command));
  }

  public void remove(Project.NameKey project) {
    commands.remove(project);
  }

  public boolean hasCommands() {
    return !commands.isEmpty();
  }

  @Override
  public String getCommand(DownloadScheme scheme, String project, String ref) {
    Project.NameKey projectName = new Project.NameKey(project);
    String command = commands.get(projectName);
    if (command == null) {
      ProjectState projectState = projectCache.get(projectName);
      if (projectState != null) {
        for (ProjectState parent : projectState.parents()) {
          command = commands.get(parent.getProject().getNameKey());
          if (command != null) {
            break;
          }
        }
      }
    }
    if (command != null) {
      command =
          command
              .replaceAll("\\$\\{ref\\}", ref)
              .replaceAll("\\$\\{url\\}", scheme.getUrl(project))
              .replaceAll("\\$\\{project\\}", project);
    }
    return Strings.emptyToNull(command);
  }
}
