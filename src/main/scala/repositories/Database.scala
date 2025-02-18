package repositories

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor

object Database {
  private val transactor: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.sqlite.JDBC",
    "jdbc:sqlite:reminders.db",
    "",
    ""
  )
  def createTables: IO[Unit] = {
    val createTempTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS temp_reminders (
          id INTEGER PRIMARY KEY,
          chat_id INTEGER,
          name TEXT,
          time TEXT
        )
      """.update.run

    val createRecurringTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS recurring_reminders (
          id INTEGER PRIMARY KEY,
          chat_id INTEGER,
          name TEXT,
          formula TEXT
        )
      """.update.run

    createTempTableQuery.transact(transactor) *>
      createRecurringTableQuery.transact(transactor).void
  }
  def addTempReminder(
      id: Long,
      chatId: Long,
      name: String,
      time: String
  ): IO[Int] = {
    sql"""
      INSERT INTO temp_reminders (id, chat_id, name, time)
      VALUES ($id, $chatId, $name, $time)
    """.update.run.transact(transactor)
  }
  def addRecurringReminder(
      id: Long,
      chatId: Long,
      name: String,
      formula: String
  ): IO[Int] = {
    sql"""
      INSERT INTO recurring_reminders (id, chat_id, name, formula)
      VALUES ($id, $chatId, $name, $formula)
    """.update.run.transact(transactor)
  }
  def deleteTempReminder(id: Long, chatId: Long): IO[Int] = {
    sql"""
      DELETE FROM temp_reminders WHERE id = $id AND chat_id = $chatId
    """.update.run.transact(transactor)
  }
  def deleteRecurringReminder(id: Long, chatId: Long): IO[Int] = {
    sql"""
      DELETE FROM recurring_reminders WHERE id = $id AND chat_id = $chatId
    """.update.run.transact(transactor)
  }
  def getAllTempReminders: IO[List[(Long, Long, String, String)]] = {
    sql"""
      SELECT id, chat_id, name, time FROM temp_reminders
    """.query[(Long, Long, String, String)].to[List].transact(transactor)
  }
  def getAllRecurringReminders: IO[List[(Long, Long, String, String)]] = {
    sql"""
      SELECT id, chat_id, name, formula FROM recurring_reminders
    """.query[(Long, Long, String, String)].to[List].transact(transactor)
  }
}
