package com.example.ledgerlens.domain.parser

object RawAlertStatus {
    const val NEW = "NEW"
    const val IMPORTED_SMS = "IMPORTED_SMS"
    const val SOURCE_PENDING = "SOURCE_PENDING"
    const val SOURCE_IGNORED = "SOURCE_IGNORED"
    const val PARSE_PENDING = "PARSE_PENDING"
    const val PARSED_TRANSACTION = "PARSED_TRANSACTION"
    const val IGNORED_NON_TRANSACTION = "IGNORED_NON_TRANSACTION"
    const val FAILED_TRANSACTION_PARSE = "FAILED_TRANSACTION_PARSE"
}
