include_defs('//bucklets/gerrit_plugin.bucklet')

gerrit_plugin(
  name = 'project-download-commands',
  srcs = glob(['src/main/java/**/*.java']),
  resources = glob(['src/main/resources/**/*']),
  manifest_entries = [
    'Gerrit-PluginName: project-download-commands',
    'Gerrit-Module: com.googlesource.gerrit.plugins.download.command.project.Module'
  ]
)

