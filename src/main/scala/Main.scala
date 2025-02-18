import cats.effect.unsafe.implicits.global

object Main extends App {
  TelegramBot.run.unsafeRunSync()
}
