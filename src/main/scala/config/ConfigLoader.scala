package config
import pureconfig._
import pureconfig.generic.auto._
import models.AppConfig

object ConfigLoader {
  def loadConfig: AppConfig = {
    ConfigSource.default.loadOrThrow[AppConfig]
  }
}
