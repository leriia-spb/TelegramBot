package models

sealed trait UserState {
  def message: String
}
case object Idle extends UserState {
  override def message: String = ""
}
case class WaitingForReminderTime(message: String) extends UserState
case class WaitingForDayAndTime(message: String) extends UserState
case class WaitingForHours(message: String) extends UserState
case class WaitingForWeekTime(message: String, day: Int) extends UserState
case object MakingNewReminder extends UserState {
  override def message: String = ""
}
case object DeletingOldReminder extends UserState {
  override def message: String = ""
}
case class UserContext(state: UserState, data: Option[String])
