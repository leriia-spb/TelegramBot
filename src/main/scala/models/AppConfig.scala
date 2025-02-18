package models

case class AppConfig(
    bot: BotConfig
)
case class BotConfig(token: String)
