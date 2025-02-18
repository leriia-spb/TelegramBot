package services
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import cron4s.Cron
import cron4s.lib.javatime._
import fs2.Stream
import models._
import org.slf4j.LoggerFactory
import repositories._

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime, ZonedDateTime}
import scala.concurrent.duration._

case class ReminderService(telegramBot: TelegramService) {
  private val remindersQueue: Ref[IO, List[Reminder]] = Ref.unsafe(List.empty)
  private val database = Database
  private val logger = LoggerFactory.getLogger(ReminderService.getClass)
  // ==========================
  // Работа с напоминаниями
  // ==========================
  def RecurringReminder(
      id: Long,
      chatId: Long,
      message: String,
      cronExpr: String
  ): IO[Unit] = {
    val reminder = Reminder(
      id = id,
      targetTime = None,
      chatId = chatId,
      message = message,
      cronExpr = Some(cronExpr),
      reminderType = Recurring
    )
    database.addRecurringReminder(id, chatId, message, cronExpr) *>
      remindersQueue.update(_ :+ reminder) *>
      telegramBot.sendMessage(chatId, s"Напоминание '$message' установлено")
  }
  def OnceReminder(
      id: Long,
      chatId: Long,
      message: String,
      targetTime: LocalDateTime
  ): IO[Unit] = {
    val now = LocalDateTime.now()
    val delayDuration = Duration.between(now, targetTime)
    if (delayDuration.isNegative || delayDuration.isZero) {
      telegramBot.sendMessage(chatId, "Указанное время уже прошло!")
    } else {
      val reminder = Reminder(
        id = id,
        targetTime = Some(targetTime),
        chatId = chatId,
        message = message,
        cronExpr = None,
        reminderType = OneTime
      )
      database.addTempReminder(id, chatId, message, targetTime.toString) *>
        remindersQueue.update(_ :+ reminder) *>
        telegramBot.sendMessage(
          chatId,
          s"Напоминание  $message установлено на $targetTime"
        )
    }
  }
  private def processReminders: IO[Unit] = {
    Stream
      .repeatEval(remindersQueue.get)
      .flatMap { reminders =>
        val now = LocalDateTime.now()

        val (oneTimeReminders, recurringReminders) =
          reminders.partition(_.reminderType == OneTime)

        val dueOneTimeReminders =
          oneTimeReminders.filter(_.targetTime.exists(_.isBefore(now)))

        val oneTimeStream =
          Stream.emits(dueOneTimeReminders).evalMap { reminder =>
            telegramBot
              .sendMessage(
                reminder.chatId,
                s"Это ваше напоминание: ${reminder.message}"
              )
              .flatMap(_ => remindersQueue.update(_.filterNot(_ == reminder)))
          }

        val recurringStream =
          Stream.emits(recurringReminders).evalMap { reminder =>
            reminder.cronExpr match {
              case Some(expr) =>
                Cron.parse(expr) match {
                  case Right(cron) =>
                    val nowZdt = ZonedDateTime.now()

                    cron.next(nowZdt) match {
                      case Some(nextExecution) =>
                        if (
                          nextExecution
                            .isBefore(ZonedDateTime.now().plusSeconds(1))
                        ) {
                          IO.delay(
                            logger.info(
                              s"telegramBot.sending reminder: ${reminder.message}"
                            )
                          ) *>
                            telegramBot
                              .sendMessage(
                                reminder.chatId,
                                s"Напоминание: ${reminder.message}"
                              )
                              .flatMap(_ =>
                                remindersQueue.update(queue =>
                                  queue.map(r =>
                                    if (r == reminder)
                                      reminder.copy(targetTime =
                                        Some(nextExecution.toLocalDateTime)
                                      )
                                    else r
                                  )
                                )
                              )
                        } else {
                          IO.unit
                        }
                      case None =>
                        IO.unit
                    }
                  case Left(_) =>
                    IO.delay(
                      logger.warn(s"Failed to parse cron expression: $expr")
                    ) *>
                      IO.unit
                }
              case None =>
                IO.unit
            }
          }
        oneTimeStream ++ recurringStream
      }
      .metered(1.second)
      .compile
      .drain
  }
  def removeReminder(elementToRemove: Reminder): IO[Unit] = {
    val id = elementToRemove.id
    val chatId = elementToRemove.chatId
    for {
      _ <- elementToRemove.reminderType match {
        case OneTime   => database.deleteTempReminder(id, chatId)
        case Recurring => database.deleteRecurringReminder(id, chatId)
      }
      _ <- remindersQueue.update { reminders =>
        reminders.filterNot(_ == elementToRemove)
      }
      _ <- IO(logger.info(s"Deleted $id reminder from chat $chatId"))
    } yield ()
  }

  private def removeReminder(
      id: Long,
      chatId: Long,
      reminderType: ReminderType
  ): IO[Unit] = {
    reminderType match {
      case OneTime   => database.deleteTempReminder(id, chatId)
      case Recurring => database.deleteRecurringReminder(id, chatId)
    }
    remindersQueue.update { reminders =>
      reminders.filterNot(reminder =>
        reminder.id == id && reminder.chatId == chatId
      )
    }
  }
  // ==========================
  // Подготовка БД к началу работы бота
  // ==========================
  def run: IO[Unit] = {
    IO.delay(
      logger.info("Creating tables if they don't exist...")
    ) *>
      database.createTables *>
      IO.delay(
        logger.info("Collecting data from tables...")
      ) *>
      collectData *>
      processReminders.foreverM.start *> IO.delay(
        logger.info("Reminders started)")
      )
  }
  private def collectData: IO[Unit] = {
    for {
      tempReminders <- database.getAllTempReminders
      recurringReminders <- database.getAllRecurringReminders
      tempMapped = tempReminders.flatMap { case (id, chatId, name, time) =>
        val parsedTime =
          LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME)
        if (parsedTime.isAfter(LocalDateTime.now)) {
          Some(
            Reminder(
              id = id,
              chatId = chatId,
              message = name,
              targetTime = Some(parsedTime),
              cronExpr = None,
              reminderType = OneTime
            )
          )
        } else {
          removeReminder(id, chatId, OneTime)
          None
        }
      }

      recurringMapped = recurringReminders.map {
        case (id, chatId, name, formula) =>
          Reminder(
            id = id,
            chatId = chatId,
            message = name,
            targetTime = None,
            cronExpr = Some(formula),
            reminderType = Recurring
          )
      }
      allReminders = tempMapped ++ recurringMapped
      _ <- remindersQueue.set(allReminders)
      _ <- IO.delay(
        logger.info(
          s"Added ${allReminders.size} reminders: ${tempMapped.size} oneTime, ${recurringMapped.size} repeating"
        )
      )
    } yield ()
  }
  // ==========================
  // Взятие напоминания
  // ==========================
  def getReminders(chatId: Long): IO[String] = {
    remindersQueue.get.flatMap { reminders =>
      val filteredReminders = reminders.filter(_.chatId == chatId)
      val numberedReminders = filteredReminders.zipWithIndex.map {
        case (reminder, index) =>
          s"${index + 1}. ${reminder.message}"
      }
      IO.pure(numberedReminders.mkString("\n"))
    }
  }

  def nthReminder(chatId: Long, number: String): Option[Reminder] = {
    val reminderNumber = number.toIntOption
    reminderNumber match {
      case Some(n) =>
        val remindersForChat =
          remindersQueue.get.unsafeRunSync().filter(_.chatId == chatId)
        if (n > 0 && n <= remindersForChat.length) {
          Some(remindersForChat(n - 1))
        } else {
          None
        }
      case None => None
    }
  }
}
