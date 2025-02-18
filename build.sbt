name := "TelegramBot"

version := "0.1"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  // ==========================
  // HTTP-запросы и JSON
  // ==========================
  "com.softwaremill.sttp.client3" %% "core" % "3.9.7",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.7",
  "io.circe" %% "circe-generic" % "0.14.9",
  "io.circe" %% "circe-parser" % "0.14.9",
  // ==========================
  // Cats и Cats Effect
  // ==========================
  "org.typelevel" %% "cats-core" % "2.12.0",
  "org.typelevel" %% "cats-effect" % "3.5.2",
  // ==========================
  // Библиотеки для работы с базой данных
  // ==========================
  "org.tpolecat" %% "doobie-core"       % "1.0.0-RC2",
  "org.xerial" % "sqlite-jdbc" % "3.46.0.0",
  // ==========================
  // Работа с эффектами и расписаниями
  // ==========================
  "co.fs2" %% "fs2-core" % "3.10.2",
  "com.github.alonsodomin.cron4s" %% "cron4s-core" % "0.7.0",
  // ==========================
  // Логирование
  // ==========================
  "org.slf4j" % "slf4j-api" % "2.0.12",
  "ch.qos.logback" % "logback-classic" % "1.5.6",
  // ==========================
  // Конфиг
  // ==========================
  "com.github.pureconfig" %% "pureconfig" % "0.17.7",
  // ==========================
  // Тестирование
  // ==========================
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
  "org.scalatestplus" %% "scalacheck-1-15" % "3.2.11.0" % Test,
  "org.mockito" % "mockito-core" % "5.11.0" % Test,
  "org.typelevel" %% "cats-effect-testkit" % "3.5.0" % Test,
  "org.scalameta" %% "munit" % "1.0.0" % Test,
)
