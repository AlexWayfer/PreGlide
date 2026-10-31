package name.alexwayfer.preglide.premium

import android.content.Context
import android.telephony.TelephonyManager

/**
 * PreGlide Premium on Tribute, in rubles and in dollars, as the Mecena bot offers it too. The free edition's Premium
 * page leads there, as premium's access check does.
 */
const val PREMIUM_PURCHASE_URL_RUB = "https://t.me/tribute/app?startapp=s1aEI"
const val PREMIUM_PURCHASE_URL_USD = "https://t.me/tribute/app?startapp=s1aEK"

/** SIM countries that see the price in rubles; everyone else, and a device without a SIM, sees dollars. */
private val rubleCountries = setOf("ru", "by")

/**
 * The purchase in the currency for the SIM's country, as CustomTV's; a dual-SIM value such as `ru,` counts by its
 * first SIM.
 */
fun premiumPurchaseUrl(simCountryIso: String?): String {
    val country = simCountryIso?.substringBefore(',')?.trim()?.lowercase()
    return if (country in rubleCountries) PREMIUM_PURCHASE_URL_RUB else PREMIUM_PURCHASE_URL_USD
}

/** The SIM's country, by which the bot offers the purchase in rubles or dollars; null without a SIM. */
fun simCountry(context: Context): String? =
    context.getSystemService(TelephonyManager::class.java)?.simCountryIso?.takeIf { it.isNotBlank() }
