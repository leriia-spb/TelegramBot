import cats.effect.{IO, IOApp}
import config.ConfigLoader
import models.AppConfig
import org.slf4j.LoggerFactory
import services.{ReminderService, TelegramService}
object TelegramBot extends IOApp.Simple {
  private val logger = LoggerFactory.getLogger(TelegramBot.getClass)
  private val config: AppConfig = ConfigLoader.loadConfig
  private val bot = TelegramService(config.bot)
  private val reminder = ReminderService(bot)
  override def run: IO[Unit] = {
    reminder.run *>
      IO.delay(logger.info("Bot started")) *>
      bot.pollUpdates(0)
  }
}
