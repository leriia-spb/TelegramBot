package models

case class TelegramMessage(ok: Boolean, result: Message)
case class Message(message_id: Long, chat: Chat, text: Option[String])
