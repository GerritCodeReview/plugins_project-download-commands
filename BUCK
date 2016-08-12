include_defs('//bucklets/gerrit_plugin.bucklet')

VERSION = '1.0-SNAPSHOT'

gerrit_plugin(
  name = 'project-download-commands',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: project-download-commands',
    'Gerrit-Module: com.googlesource.gerrit.plugins.download.command.project.Module',
    'Gerrit-ApiVersion: 2.13-SNAPSHOT',
  ]
)
# this is required for bucklets/tools/eclipse/project.py to work
java_library(
  name = 'classpath',
  deps = [':project-download-commands__plugin'],
)

