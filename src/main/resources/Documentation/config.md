Configuration
=============

The configuration of the @PLUGIN@ plugin is done on project level in
the `project.config` file of the project.

```
  [plugin "project-download-commands"]
    Build = git fetch ${url} ${ref} && git checkout FETCH_HEAD && buck build ${project}
    Update = git fetch ${url} ${ref} && git checkout FETCH_HEAD && git submodule update
```

plugin.project-download-commands.\<commandName\>
:	The download command.

	Can contain the following placeholders:

	* `${url}`: project URL
	* `${ref}`: change ref
	* `${project}`: project name

	"`-`" in the `commandName` is replaced by space.
