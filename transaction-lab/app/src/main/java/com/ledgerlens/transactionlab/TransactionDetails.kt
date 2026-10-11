package com.ledgerlens.transactionlab

data class TransactionDetails(
 val institution:String?=null,val method:String?=null,val counterparty:String?=null,val role:String?=null,
 val cardholder:String?=null,val cardLast4:String?=null,val accountLast4:String?=null,val note:String?=null
) {
 fun title(p:Parsed):String=when {
  p.kind=="CARD_PAYMENT" -> "Card payment to "+(counterparty?:institution?:"issuer")
  method=="ZELLE" -> "Zelle "+(if(p.direction=="IN")"received" else "sent")+
   (if(counterparty!=null) " "+(if(p.direction=="IN")"from " else "to ")+counterparty else "")
  p.kind=="PURCHASE" -> p.merchant?:counterparty?:"Card purchase"
  p.kind=="TRANSFER" -> "Transfer "+(if(p.direction=="IN")"received from " else "to ")+(counterparty?:"unknown")
  p.kind=="DEPOSIT" -> "Deposit received"
  else -> p.merchant?:institution?:"Review transaction"
 }
}
object DetailsExtractor {
 fun institutionFor(b:String):String?=when {
  b.contains("Capital One",true)->"Capital One"
  b.contains("Chase",true)||b.contains("Zelle(R)",true)->"Chase"
  b.contains("HDFC Bank",true)->"HDFC Bank"
  b.contains("SBI",true)->"SBI"
  else->null
 }
 private fun grab(r:String,b:String):String?=
   Regex(r,RegexOption.IGNORE_CASE).find(b)?.groupValues?.getOrNull(1)?.trim()
 private fun last4(b:String):String?=grab("""Credit Card\s*(?:…|\.\.\.)?\s*\(?(\d{4})\)?""",b)
 private fun namedHolder(b:String):String?=
   grab("""charged to\s+(.{2,55}?)['’]s\s+\w+(?:\s+\w+){0,4}\s+Credit Card""",b)
 fun extract(body:String,p:Parsed):TransactionDetails {
  val bank=institutionFor(body)
  val zin=grab("""Zelle\s*\(R\)\s*:\s*(.+?)\s+sent\s+you\s+\$""",body)
  val zout=grab("""Zelle\s*\(R\)\s*:\s*You\s+sent\s+(.+?)\s+\$""",body)
  val method=when{
   p.rule.startsWith("ZELLE_")->"ZELLE"
   p.kind=="CARD_PAYMENT"->"CARD_REPAYMENT"
   p.rule.startsWith("CAPITAL_ONE_")&&p.kind=="PURCHASE"->"CREDIT_CARD"
   p.kind=="PURCHASE"->"DEBIT_CARD"
   p.rule=="CHASE_WIRE_ALERT"->"WIRE"
   p.rule=="CHASE_ATM_DEPOSIT"->"ATM"
   p.rule=="CHASE_ACCOUNT_ALERT"&&p.kind=="DEPOSIT"->"DIRECT_DEPOSIT"
   p.kind=="TRANSFER"->"BANK_TRANSFER"
   else->null
  }
  val party=when {
   p.kind=="CARD_PAYMENT"&&bank!=null->bank
   zin!=null->zin
   zout!=null->zout
   else->p.merchant
  }
  val role=when {
   p.kind=="CARD_PAYMENT"->"CARD_ISSUER"
   p.kind=="PURCHASE"->"MERCHANT"
   p.kind=="TRANSFER"&&p.direction=="IN"->"SENDER"
   p.kind=="TRANSFER"&&p.direction=="OUT"->"RECIPIENT"
   else->null
  }
  val note=when {
   namedHolder(body)!=null->"Named cardholder; relationship unverified."
   p.kind=="CARD_PAYMENT"->"Card repayment, not additional spending."
   method=="ZELLE"->"Payment purpose unavailable in SMS."
   p.lifecycle=="PENDING"->"Authorization is not proof of settlement."
   else->null
  }
  return TransactionDetails(bank,method,party,role,namedHolder(body),
   last4(body),grab("""Chase\s+acct\s+(\d{4})""",body)?:p.accountSuffix,note)
 }
}
