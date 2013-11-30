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
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.reviewdb.client.Project;

import java.util.Map;

public class ProjectDownloadCommand extends DownloadCommand {
  private final Map<Project.NameKey, String> commands;

  ProjectDownloadCommand(Project.NameKey project, String command) {
    this.commands = Maps.newHashMap();
    add(project, command);
  }

  public void add(Project.NameKey project, String command) {
    commands.put(project, command);
  }

  public void remove(Project.NameKey project) {
    commands.remove(project);
  }

  public boolean hasCommands() {
    return !commands.isEmpty();
  }

  @Override
  public String getCommand(DownloadScheme scheme, String project, String ref) {
    String command = commands.get(new Project.NameKey(project));
    if (command != null) {
      command = command.replaceAll("\\$\\{ref\\}", ref)
          .replaceAll("\\$\\{url\\}", scheme.getUrl(project))
          .replaceAll("\\$\\{project\\}", project);
    }
    return command;
  }
}
