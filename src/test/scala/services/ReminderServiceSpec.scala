package services

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import models._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.LocalDateTime
import scala.reflect.runtime.universe._

class ReminderServiceSpec extends AnyFlatSpec with Matchers {
  private val reminder1: Reminder = Reminder(
    id = 1,
    chatId = 12345,
    message = "Don't forget the meeting!",
    targetTime = Some(LocalDateTime.of(2024, 12, 22, 14, 30, 0, 0)),
    cronExpr = None,
    reminderType = OneTime
  )
  private val reminder2: Reminder = Reminder(
    id = 2,
    chatId = 12345,
    message = "Daily check-in!",
    targetTime = None,
    cronExpr = Some("0 9 * * *"),
    reminderType = Recurring
  )
  private val reminder3: Reminder = Reminder(
    id = 3,
    chatId = 12345,
    message = "Weekly check-in!",
    targetTime = None,
    cronExpr = Some("0 8 * * 7"),
    reminderType = Recurring
  )

  private val telegramService = TelegramService(BotConfig("someToken"))
  private val reminderService = ReminderService(telegramService)
  private def addReminderViaReflection(reminder: Reminder): Unit = {
    val mirror = runtimeMirror(getClass.getClassLoader)
    val instanceMirror = mirror.reflect(reminderService)

    val fieldSymbol =
      typeOf[ReminderService].decl(TermName("remindersQueue")).asTerm

    if (!fieldSymbol.isPrivate) {
      throw new IllegalAccessException("Field remindersQueue must be private.")
    }

    val fieldMirror = instanceMirror.reflectField(fieldSymbol)

    val remindersQueue = fieldMirror.get.asInstanceOf[Ref[IO, List[Reminder]]]
    remindersQueue.update(_ :+ reminder).unsafeRunSync()
  }

  "nthReminder" should "return the correct reminder for a valid number" in {
    addReminderViaReflection(reminder1)
    addReminderViaReflection(reminder2)
    addReminderViaReflection(reminder3)

    reminderService.nthReminder(12345L, "2") shouldEqual Some(reminder2)
  }

  it should "return None if the number is out of bounds" in {
    reminderService.nthReminder(12345L, "4") shouldEqual None
  }

  it should "return None if the number is not a valid integer" in {
    reminderService.nthReminder(12345L, "invalid") shouldEqual None
  }

  it should "return None if there are no reminders for the specified chatId" in {
    reminderService.nthReminder(99999L, "1") shouldEqual None
  }

  it should "return None if the number is less than or equal to zero" in {
    reminderService.nthReminder(12345L, "0") shouldEqual None
    reminderService.nthReminder(12345L, "-1") shouldEqual None
  }

  "getReminders" should "return 3 reminders" in {
    reminderService
      .getReminders(12345L)
      .unsafeRunSync() shouldEqual "1. Don't forget the meeting!\n2. Daily check-in!\n3. Weekly check-in!"
  }

  it should "return empty string for the specified chatId" in {
    reminderService.getReminders(99999L).unsafeRunSync() shouldEqual ""
  }

  "removeReminder" should "after remove getReminders return 2 reminders" in {
    reminderService.removeReminder(reminder2).unsafeRunSync()
    reminderService
      .getReminders(12345L)
      .unsafeRunSync() shouldEqual "1. Don't forget the meeting!\n2. Weekly check-in!"
  }
}
