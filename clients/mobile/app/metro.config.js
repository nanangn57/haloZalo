const path = require('path')
const { getDefaultConfig } = require('expo/metro-config')

const projectRoot = __dirname
// Local: symlink app/core → ../../core. Docker Compose mounts ../core at /workspace/core.
const workspaceRoot = path.resolve(projectRoot, '../..')
const coreRoot = path.resolve(projectRoot, 'core')

const config = getDefaultConfig(projectRoot)

config.watchFolders = [workspaceRoot]
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(workspaceRoot, 'node_modules'),
]
config.resolver.extraNodeModules = {
  '@core': coreRoot,
}

module.exports = config
