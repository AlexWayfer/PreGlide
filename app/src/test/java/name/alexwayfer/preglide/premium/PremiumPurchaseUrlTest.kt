package name.alexwayfer.preglide.premium

import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumPurchaseUrlTest {
    @Test
    fun aRussianOrBelarusianSimOpensTheRublePrice() {
        assertEquals(PREMIUM_PURCHASE_URL_RUB, premiumPurchaseUrl("ru"))
        assertEquals(PREMIUM_PURCHASE_URL_RUB, premiumPurchaseUrl("by"))
    }

    @Test
    fun anUpperCaseCountryOpensTheRublePrice() {
        assertEquals(PREMIUM_PURCHASE_URL_RUB, premiumPurchaseUrl("RU"))
    }

    @Test
    fun aDualSimValueCountsByTheFirstSim() {
        assertEquals(PREMIUM_PURCHASE_URL_RUB, premiumPurchaseUrl("ru,"))
        assertEquals(PREMIUM_PURCHASE_URL_USD, premiumPurchaseUrl("kz,ru"))
    }

    @Test
    fun anotherCountryOrNoSimOpensTheDollarPrice() {
        assertEquals(PREMIUM_PURCHASE_URL_USD, premiumPurchaseUrl("kz"))
        assertEquals(PREMIUM_PURCHASE_URL_USD, premiumPurchaseUrl(""))
        assertEquals(PREMIUM_PURCHASE_URL_USD, premiumPurchaseUrl(null))
    }

    @Test
    fun thePricesAreMecenasTributeLinksForPreGlide() {
        assertEquals("https://t.me/tribute/app?startapp=s1aEI", PREMIUM_PURCHASE_URL_RUB)
        assertEquals("https://t.me/tribute/app?startapp=s1aEK", PREMIUM_PURCHASE_URL_USD)
    }
}
