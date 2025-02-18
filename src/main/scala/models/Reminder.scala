package models

import java.time.LocalDateTime

sealed trait ReminderType
case object OneTime extends ReminderType
case object Recurring extends ReminderType

case class Reminder(
    id: Long,
    chatId: Long,
    message: String,
    targetTime: Option[LocalDateTime],
    cronExpr: Option[String],
    reminderType: ReminderType
)
