package com.finovo.app.bank

/**
 * Central switch for the bank connection.
 *
 * LIVE = false  -> the app shows the modern "Coming soon" page
 *                  (no bank connection, no backend configuration, no sandbox).
 * LIVE = true   -> the app shows the real screen (BankScreen) again.
 *
 * Switching back later is a one-line change: set LIVE = true as soon as the
 * backend is hosted and the whole flow has been tested end to end.
 * Nothing in the database is touched either way.
 */
object BankFeature {

    const val LIVE = false
}
