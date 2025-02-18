package models

case class Update(
    update_id: Long,
    message: Option[Message],
    callback_query: Option[CallbackQuery]
)
