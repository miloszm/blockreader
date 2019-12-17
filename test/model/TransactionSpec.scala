package model

import org.scalatest.{Matchers, WordSpec}

class TransactionSpec extends WordSpec with Matchers {

  "fee only transaction" should {
    val transaction = JsonTransaction(Seq(JsonInput(Some(JsonOutput(Some(30000))))), Seq(JsonOutput(Some(26000))), 0L, 0, 0, "abc", 220, 0)
    val feeOnlyTransaction = transaction.toFeeOnlyTransaction(600000l,0,0)
    "calculate fees" in {
      feeOnlyTransaction.fees shouldBe 4000
    }
    "calculate fee per byte" in {
      feeOnlyTransaction.feePerByte shouldBe 18
    }
  }

}
