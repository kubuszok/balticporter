import os


def define_env(env):
    """Baltic Porter publishes one snapshot per commit, versioned `<commit hash>-SNAPSHOT`."""

    version = ''
    try:
        pipe = os.popen('git rev-parse HEAD')
        commit = pipe.read().strip()
        pipe.close()
        if commit:
            version = commit + '-SNAPSHOT'
    except Exception:
        version = ''
    if not version:
        try:
            version = env.conf['extra']['local']['commit'] + '-SNAPSHOT'
        except KeyError:
            version = 'commit-SNAPSHOT'

    @env.macro
    def balticporter_version():
        return version
