package models
case class CallbackQuery(
    id: String,
    data: String,
    message: Option[Message]
)
case class TelegramResponses(ok: Boolean, result: List[Update])
