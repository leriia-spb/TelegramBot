package services

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.parser._
import models._
import org.slf4j.LoggerFactory
import sttp.client3._

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.collection.concurrent.TrieMap

case class TelegramService(config: BotConfig) {
  private val baseUrl = s"https://api.telegram.org/bot${config.token}"
  private val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  private val userStates: TrieMap[Long, UserContext] = TrieMap.empty
  private val messageStates: TrieMap[Long, String] = TrieMap.empty
  private val logger = LoggerFactory.getLogger(TelegramService.getClass)
  private val validation = Validation
  private val reminderService = ReminderService(this)
  def pollUpdates(offset: Long): IO[Unit] = {
    for {
      response <- getUpdates(offset)
      newOffset <- response match {
        case Right(updates) =>
          val newOffsetIO = updates.foldLeft(IO(offset)) { (accIO, update) =>
            accIO.flatMap { _ =>
              processUpdate(update).as(
                update.update_id + 1
              )
            }
          }
          newOffsetIO
        case Left(error) =>
          IO.delay(logger.error(s"Failed to fetch updates: $error")).as(offset)
      }
      _ <- pollUpdates(newOffset)
    } yield ()
  }
  private def getUpdates(offset: Long): IO[Either[String, List[Update]]] = IO {
    val request =
      basicRequest.get(uri"$baseUrl/getUpdates?offset=$offset&timeout=10")
    val response = request.send(backend)
    response.body.flatMap { json =>
      decode[TelegramResponses](json).map(_.result).left.map(_.getMessage)
    }
  }
  // ==========================
  // Логика
  // ==========================
  private def processUpdate(update: Update): IO[Unit] = {
    update match {
      case Update(_, Some(message), None) =>
        message.text match {
          case Some("/start") =>
            sendStartInteraction(message.chat.id)
          case Some("/reminder") =>
            for {
              _ <- sendMessage(
                message.chat.id,
                "Напиши название или краткое описание напоминания "
              )
              _ = userStates.update(
                message.chat.id,
                UserContext(MakingNewReminder, None)
              )
            } yield ()
          case Some("/delete") =>
            for {
              _ <- sendRemindersWithNumbers(message.chat.id)
              _ = userStates.update(
                message.chat.id,
                UserContext(DeletingOldReminder, None)
              )
            } yield ()
          case Some(text) =>
            handleUserInput(message.message_id, message.chat.id, text)
          case None => IO.unit
        }

      case Update(_, None, Some(callbackQuery)) =>
        handleCallbackQuery(callbackQuery)

      case _ => IO.unit
    }
  }
  private def handleUserInput(
      messageId: Long,
      chatId: Long,
      text: String
  ): IO[Unit] = {
    val currentContext = userStates.getOrElse(chatId, UserContext(Idle, None))
    currentContext.state match {
      case Idle =>
        sendMessage(
          chatId,
          "Пожалуйста, используйте /reminder чтобы добавить напоминание"
        )
      case MakingNewReminder =>
        for {
          newMessageId <- sendReminderOptions(chatId)
          _ = messageStates.update(
            newMessageId,
            text
          )
          _ = userStates.update(chatId, UserContext(Idle, None))
        } yield ()
      case DeletingOldReminder =>
        reminderService.nthReminder(chatId, text) match {
          case Some(reminder) =>
            for {
              _ <- reminderService.removeReminder(reminder)
              _ <- sendMessage(
                chatId,
                "Напоминание успешно удалено!"
              )
              _ = userStates.update(chatId, UserContext(Idle, None))
            } yield ()
          case None =>
            sendMessage(
              chatId,
              "Неверный номер. Попробуйте ещё раз."
            )
        }

      case WaitingForReminderTime(message)
          if validation.isValidDateTime(text) =>
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val targetTime = LocalDateTime.parse(text, formatter)
        for {
          _ <- reminderService.OnceReminder(
            messageId,
            chatId,
            message,
            targetTime
          )
          _ = userStates.update(chatId, UserContext(Idle, None))
        } yield ()

      case WaitingForReminderTime(_) =>
        sendMessage(
          chatId,
          "Неверный формат даты. Ожидается yyyy-MM-dd HH:mm. Попробуйте ещё раз."
        )

      case WaitingForHours(message) if validation.isValidDailyTime(text) =>
        val timeParts = text.split(":")
        val hour = timeParts(0).toInt
        val minute = timeParts(1).toInt
        for {
          _ <- reminderService.RecurringReminder(
            messageId,
            chatId,
            message,
            s"0 $minute $hour ? * *"
          )
          _ = userStates.update(chatId, UserContext(Idle, None))
        } yield ()

      case WaitingForHours(_) =>
        sendMessage(
          chatId,
          "Неверный формат времени. Ожидается HH:mm. Попробуйте ещё раз."
        )

      case WaitingForDayAndTime(message)
          if validation.isValidMonthlyTime(text) =>
        val parts = text.split(" ")
        val day = parts(0).toInt
        val timeParts = parts(1).split(":")
        val hour = timeParts(0).toInt
        val minute = timeParts(1).toInt
        for {
          _ <- reminderService.RecurringReminder(
            messageId,
            chatId,
            message,
            s"0 $minute $hour $day * ?"
          )
          _ = userStates.update(chatId, UserContext(Idle, None))
        } yield ()
      case WaitingForDayAndTime(_) =>
        sendMessage(
          chatId,
          "Неверный формат времени. Ожидается D HH:mm. Попробуйте ещё раз."
        )
      case WaitingForWeekTime(message, day)
          if validation.isValidDailyTime(text) =>
        val timeParts = text.split(":")
        val hour = timeParts(0).toInt
        val minute = timeParts(1).toInt
        for {
          _ <- reminderService.RecurringReminder(
            messageId,
            chatId,
            message,
            s"0 $minute $hour ? * ${day - 1}"
          )
          _ = userStates.update(chatId, UserContext(Idle, None))
        } yield ()
      case WaitingForWeekTime(_, _) =>
        sendMessage(
          chatId,
          "Неверный формат времени. Ожидается HH:mm. Попробуйте ещё раз."
        )
    }
  }
  private def handleCallbackQuery(callbackQuery: CallbackQuery): IO[Unit] = {
    val callbackData = callbackQuery.data
    val chatId = callbackQuery.message.map(_.chat.id).getOrElse(0L)
    val messageId = callbackQuery.message.map(_.message_id).getOrElse(0L)
    val message = messageStates.getOrElse(messageId, "без комментариев")

    callbackData match {
      case "once" =>
        for {
          _ <- IO.delay(logger.info(s"User $chatId selected 'Once' option."))
          _ <- deleteMessage(chatId, messageId)
          _ <- sendMessage(
            chatId,
            "Вы выбрали единоразовое напоминание. Введите дату и время в формате YYYY-MM-DD HH:MM"
          )
          _ = userStates.update(
            chatId,
            UserContext(WaitingForReminderTime(message), None)
          )
        } yield ()

      case "repeat" =>
        for {
          _ <- IO.delay(logger.info(s"User $chatId selected 'Repeat' option."))
          _ <- deleteMessage(chatId, messageId)
          newMessageId <- sendRepeatOptions(chatId)
          _ = messageStates.update(
            newMessageId,
            message
          )
          _ = userStates.update(
            chatId,
            UserContext(Idle, None)
          )
        } yield ()

      case "daily" =>
        for {
          _ <- IO.delay(logger.info(s"User $chatId selected 'daily' option."))
          _ <- deleteMessage(chatId, messageId)
          _ <- sendMessage(
            chatId,
            "Вы выбрали ежедневное напоминание. Введите время напоминания в формате HH:MM"
          )
          _ = userStates.update(
            chatId,
            UserContext(WaitingForHours(message), None)
          )
        } yield ()

      case "weekly" =>
        for {
          _ <- IO.delay(logger.info(s"User $chatId selected 'weekly' option."))
          _ <- deleteMessage(chatId, messageId)
          newMessageId <- sendDayOfWeek(chatId)
          _ = messageStates.update(
            newMessageId,
            message
          )
        } yield ()

      case "monthly" =>
        for {
          _ <- IO.delay(logger.info(s"User $chatId 'monthly' option."))
          _ <- deleteMessage(chatId, messageId)
          _ <- sendMessage(
            chatId,
            "Вы выбрали ежемесячное напоминание. Введите номер дня и время в формате Day HH:MM"
          )
          _ = userStates.update(
            chatId,
            UserContext(WaitingForDayAndTime(message), None)
          )
        } yield ()
      case day if day.toIntOption.isDefined =>
        for {
          _ <- IO.delay(logger.info(s"User $chatId selected $day day"))
          _ <- deleteMessage(chatId, messageId)
          _ <- sendMessage(
            chatId,
            s"Вы выбрали $day день недели. Введите время в формате HH:MM"
          )
          _ = userStates.update(
            chatId,
            UserContext(WaitingForWeekTime(message, day.toInt), None)
          )
        } yield ()

      case _ =>
        for {
          _ <- IO.delay(logger.warn(s"Unknown callback data: $callbackData"))
          _ <- sendMessage(chatId, "Неизвестная команда. Увы(")
        } yield ()
    }
  }

  // ==========================
  // Всякие отправлялки
  // ==========================
  private def sendRemindersWithNumbers(chatId: Long): IO[Unit] = {
    for {
      message <- reminderService.getReminders(chatId)
      _ <-
        if (message.isEmpty) {
          sendMessage(chatId, "У вас нет напоминаний.Удалять нечего(")
        } else {
          sendMessage(chatId, s"$message") *>
            sendMessage(
              chatId,
              "Напиши номер напоминания из списка для удаления"
            )
        }
    } yield ()
  }
  private def sendKeyboard(
      chatId: Long,
      keyboard: String,
      text: String
  ): IO[Long] = {
    val request = basicRequest
      .post(uri"$baseUrl/sendMessage")
      .body(
        Map(
          "chat_id" -> chatId.toString,
          "text" -> text,
          "reply_markup" -> keyboard
        )
      )

    IO {
      val response = request.send(backend)
      response.body.flatMap { json =>
        decode[TelegramMessage](json) match {
          case Right(telegramMessage) if telegramMessage.ok =>
            Right(telegramMessage.result.message_id)
          case Right(_) =>
            Left("Telegram API responded with an error.")
          case Left(decodingError) =>
            Left(s"Failed to decode Telegram response: $decodingError")
        }
      } match {
        case Right(messageId) => messageId
        case Left(errorMessage) =>
          throw new Exception(errorMessage)
      }
    }.handleErrorWith { error =>
      IO.delay(
        logger.error(s"Error sending keyboard: ${error.getMessage}")
      ) *> IO
        .raiseError(error)
    }
  }
  def sendMessage(chatId: Long, text: String): IO[Unit] = {
    val request = basicRequest
      .post(uri"$baseUrl/sendMessage")
      .body(Map("chat_id" -> chatId.toString, "text" -> text))

    IO.blocking(
      request.send(backend)
    ).flatMap { response =>
      response.body match {
        case Right(_) =>
          IO(logger.info(s"Message sent to chat $chatId"))
        case Left(error) =>
          IO(logger.error(s"Failed to send message: $error"))
      }
    }.handleErrorWith { ex =>
      IO(
        logger.error(
          s"Unexpected error while sending message: ${ex.getMessage}",
          ex
        )
      )
    }
  }

  private def sendStartInteraction(chatId: Long): IO[Unit] = for {
    _ <- IO.delay(logger.info("Start command received."))
    _ <- sendMessage(
      chatId,
      "Привет! Чтобы поставить напомнинание, используй /reminder"
    )
    _ <- sendStartKeyboard(chatId)
    _ = userStates.update(chatId, UserContext(Idle, None))
  } yield ()
  private def sendStartKeyboard(chatId: Long): IO[Long] = for {
    _ <- IO.delay(
      logger.info(
        s"Send start keyboard to chat $chatId"
      )
    )
    number <- sendKeyboard(
      chatId,
      """{
      "keyboard": [
        [{"text": "/start"}],
        [{"text": "/reminder"}],
        [{"text": "/delete"}]
      ],
      "one_time_keyboard": true,
      "resize_keyboard": true
    }""",
      "Что хочешь делать?"
    )
  } yield number
  private def sendReminderOptions(chatId: Long): IO[Long] = for {
    _ <- IO.delay(
      logger.info(
        s"Send choice remind option to chat $chatId"
      )
    )
    number <- sendKeyboard(
      chatId,
      s"""
         |{
         |  "inline_keyboard": [
         |    [{"text": "Единоразово", "callback_data": "once"}],
         |    [{"text": "С повторениями", "callback_data": "repeat"}]
         |  ]
         |}
         |""".stripMargin,
      "Выбери тип напоминания:"
    )
  } yield number
  private def sendRepeatOptions(chatId: Long): IO[Long] = for {
    _ <- IO.delay(
      logger.info(
        s"Send choice repeat option to chat $chatId"
      )
    )
    number <- sendKeyboard(
      chatId,
      s"""
         |{
         |  "inline_keyboard": [
         |    [{"text": "Ежедневно", "callback_data": "daily"}],
         |    [{"text": "Еженедельно", "callback_data": "weekly"}],
         |    [{"text": "Ежемесячно", "callback_data": "monthly"}]]
         |}
         |""".stripMargin,
      "Укажите, как часто хотите получать напоминания:"
    )
  } yield number
  private def sendDayOfWeek(chatId: Long): IO[Long] = for {
    _ <- IO.delay(
      logger.info(
        s"Send choice of the day to chat $chatId"
      )
    )
    number <- sendKeyboard(
      chatId,
      s"""
         |{
         |  "inline_keyboard": [
         |    [{"text": "Понедельник", "callback_data": "1"}],
         |    [{"text": "Вторник", "callback_data": "2"}],
         |    [{"text": "Среда", "callback_data": "3"}],
         |    [{"text": "Четверг", "callback_data": "4"}],
         |    [{"text": "Пятница", "callback_data": "5"}],
         |    [{"text": "Суббота", "callback_data": "6"}],
         |    [{"text": "Воскресенье", "callback_data": "7"}]]
         |}
         |""".stripMargin,
      "Выберите день недели:"
    )
  } yield number
  // ==========================
  // Удаление
  // ==========================
  private def deleteMessage(chatId: Long, messageId: Long): IO[Unit] = IO {
    val request = basicRequest
      .post(uri"$baseUrl/deleteMessage")
      .body(
        Map(
          "chat_id" -> chatId.toString,
          "message_id" -> messageId.toString
        )
      )

    val response = request.send(backend)
    response.body match {
      case Right(_) =>
        IO.delay(
          logger.info(
            s"Message $messageId deleted successfully from chat $chatId"
          )
        )
      case Left(error) =>
        IO.delay(logger.error(s"Failed to delete message: $error"))
    }
  }
}
