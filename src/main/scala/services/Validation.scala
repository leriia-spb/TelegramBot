package services

import java.time.{LocalDateTime, LocalTime}
import java.time.format.DateTimeFormatter

object Validation {
  def isValidDateTime(input: String): Boolean = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val dateTimeOpt =
      scala.util.Try(LocalDateTime.parse(input, formatter)).toOption
    dateTimeOpt.isDefined
  }
  def isValidDailyTime(input: String): Boolean = {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateTimeOpt =
      scala.util.Try(LocalTime.parse(input, formatter)).toOption
    val p = dateTimeOpt.isDefined
    p
  }
  def isValidMonthlyTime(input: String): Boolean = {
    val parts = input.split(" ")
    parts.length == 2 &&
    parts(0).toIntOption.exists(n => n >= 1 && n <= 31) &&
    isValidDailyTime(parts(1))
  }
}
