package services
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidationSpec extends AnyFlatSpec with Matchers {
  private val validation = Validation
  "The method isValidDateTime" should "return true for a valid date-time string" in {
    val data1 = validation.isValidDateTime("2024-12-25 15:30")
    val data2 = validation.isValidDateTime("2005-01-25 00:00")
    val data3 = validation.isValidDateTime("2024-12-25 21:30")
    data1 shouldEqual true
    data2 shouldEqual true
    data3 shouldEqual true
  }
  it should "return false for an invalid date-time string" in {
    val data1 = validation.isValidDateTime("25-12-2024 15:30")
    val data2 = validation.isValidDateTime("2024-13-20 15:30")
    val data3 = validation.isValidDateTime("2-12-24 15:30")
    val data4 = validation.isValidDateTime("25-12-2024 15.30")
    val data5 = validation.isValidDateTime("2024-2-31 15:30")
    data1 shouldEqual false
    data2 shouldEqual false
    data3 shouldEqual false
    data4 shouldEqual false
    data5 shouldEqual false
  }
  "The method isValidDailyTime" should "return true for a valid daily time" in {
    val result = validation.isValidDailyTime("23:12")
    result shouldEqual true
  }
  it should "return false for an invalid daily time" in {
    val time1 = validation.isValidDailyTime("15:60")
    val time2 = validation.isValidDailyTime("25:00")
    val time3 = validation.isValidDailyTime("5:00")
    val time4 = validation.isValidDailyTime("15:0")
    val time5 = validation.isValidDailyTime("15.60")
    time1 shouldEqual false
    time2 shouldEqual false
    time3 shouldEqual false
    time4 shouldEqual false
    time5 shouldEqual false
  }
  "The method isValidMonthlyTime" should "return true for a valid monthly time" in {
    val result = validation.isValidMonthlyTime("25 15:30")
    result shouldEqual true
  }
  it should "return false for an invalid monthly time" in {
    val result = validation.isValidMonthlyTime("32 15:30")
    result shouldEqual false
  }
}
