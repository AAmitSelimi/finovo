package com.finovo.app.bank

/**
 * Well known German banks for the selection list.
 *
 * The ids are the institution ids of the open-banking provider used by the
 * Finovo backend (GoCardless / Nordigen). The backend resolves the id - the app
 * only shows the names, so the list can be extended server side at any time.
 */
object BankInstitutions {

    data class Bank(val id: String, val name: String, val bic: String)

    val popular: List<Bank> = listOf(
        Bank("SPARKASSE", "Sparkasse", "SPKADE"),
        Bank("DEUTSCHE_BANK", "Deutsche Bank", "DEUTDEFF"),
        Bank("DKB", "DKB – Deutsche Kreditbank", "BYLADEM1001"),
        Bank("ING", "ING", "INGDDEFF"),
        Bank("COMMERZBANK", "Commerzbank", "COBADEFF"),
        Bank("POSTBANK", "Postbank", "PBNKDEFF"),
        Bank("SPARDA", "Sparda-Bank", "GENODEF1S"),
        Bank("VOLKSBANK", "Volksbank / Raiffeisenbank", "GENODEFF"),
        Bank("HYPOVEREINSBANK", "HypoVereinsbank", "HYVEDEMM"),
        Bank("SANTANDER", "Santander Consumer Bank", "SCFBDE33"),
        Bank("TARGOBANK", "Targobank", "CMCIDEDD"),
        Bank("KREDITBANK", "PSK / easybank", "GIBAATWW"),
        Bank("REVOLUT", "Revolut", "REVOLT21"),
        Bank("N26", "N26", "NTSBDEB1"),
        Bank("TRADE_REPUBLIC", "Trade Republic", "TSLADE51"),
        Bank("PAYPAL", "PayPal", "PPALDE")
    )

    fun nameOf(id: String): String =
        popular.firstOrNull { it.id == id }?.name ?: id
}
