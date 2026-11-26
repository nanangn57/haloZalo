const path = require('path')
const { getDefaultConfig } = require('expo/metro-config')

const projectRoot = __dirname

const config = getDefaultConfig(projectRoot)

config.resolver.extraNodeModules = {
  '@core': path.resolve(projectRoot, 'core'),
}

module.exports = config