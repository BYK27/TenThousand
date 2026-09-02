package com.example.tenthousand.util.focus

import kotlin.random.Random

/**
 * Celokupna matematika brainrot coin-ova, izdvojena iz ViewModel-a.
 *
 * Razlog za izdvajanje: coin-ovi se sada dodeljuju u FocusService-u, a ne u
 * ViewModel-u, jer moraju da rade i kad je app u pozadini. Da je logika ostala
 * u UI sloju, servis bi je duplirao.
 *
 * Ključna ideja: coin-ovi NISU vezani za otkucaje petlje, nego za PROTEKLO
 * VREME sesije. Vreme se deli na "slotove" od 100 ms; svaki slot je jedno
 * bacanje kockice. Servis pamti dokle je stigao ([FocusSession.coinCreditedMillis])
 * i pri svakom buđenju nadoknadi sve slotove koji su u međuvremenu prošli.
 *
 * Zato je svejedno da li je app u prvom planu, u pozadini, ili je telefon bio
 * u doze režimu pola sata - kad se petlja probudi, isplati tačno onoliko
 * slotova koliko je vremena stvarno prošlo. Nema izgubljenih coin-ova.
 */
object FocusCoinEngine {

    /** Jedno bacanje na svakih 100 ms fokusa - isti raster kao u starom kodu. */
    const val SLOT_MILLIS = 100L

    /**
     * Množilac raste 1% po minutu neprekidnog fokusa u TEKUĆOJ sesiji.
     *
     * Praktično: 25 min pomodoro završava na 1.25x, sat vremena na 1.6x, a
     * plafon od 3x se dostiže tek posle ~3h i 20min. Namerno je ovako blago -
     * tražio si "vrlo vrlo sporo".
     *
     * Množilac se resetuje sa svakom novom sesijom, jer nagrađuje neprekinut
     * fokus. Ako želiš da umesto toga raste sa UKUPNIM vremenom navike, prosledi
     * `habit.totalSeconds * 1000 + elapsedMillis` umesto `elapsedMillis` na
     * mestu poziva u FocusService.creditCoins().
     */
    private const val MULTIPLIER_PER_MINUTE = 0.01f
    private const val MULTIPLIER_MAX = 3.0f

    /**
     * Iznad ovog broja slotova se ne baca kockica po slotu, nego se računa
     * očekivana vrednost. Bez ovoga bi štoperica zaboravljena preko vikenda
     * napravila petlju od nekoliko miliona iteracija na main thread-u.
     * 200k slotova je ~5.5h fokusa - realan gap se nikad ne približi tome.
     */
    private const val MAX_ROLLED_SLOTS = 200_000

    /**
     * Očekivan broj coin-ova po slotu pri množiocu 1.0, izveden iz raspodele
     * u [rollSlot]:
     *   0.02 * 549.5 + 0.13 * 29.5 + 0.45 * 5.5 + 0.40 * 1 = 17.7
     */
    private const val EXPECTED_COINS_PER_SLOT = 17.7

    data class Award(
        val coins: Int,
        val slots: Int,
        /** Nova vrednost coinCreditedMillis koju pozivalac treba da zapamti. */
        val creditedMillis: Long
    )

    fun multiplierAt(elapsedMillis: Long): Float {
        val minutes = elapsedMillis / 60_000f
        return (1f + minutes * MULTIPLIER_PER_MINUTE).coerceAtMost(MULTIPLIER_MAX)
    }

    /**
     * Isplaćuje sve slotove između [creditedMillis] i [elapsedMillis].
     *
     * Vraća [Award] sa ukupnim iznosom, brojem slotova (da pozivalac zna da li
     * je ovo jedan običan drop ili nadoknada za period u pozadini) i novim
     * `creditedMillis` koji uvek ostaje poravnat na granicu slota - ostatak
     * ispod 100 ms se prenosi u sledeći poziv, pa se ništa ne gubi.
     */
    fun award(creditedMillis: Long, elapsedMillis: Long): Award {
        val gap = elapsedMillis - creditedMillis
        if (gap < SLOT_MILLIS) return Award(0, 0, creditedMillis)

        val slots = (gap / SLOT_MILLIS).toInt()
        val newCredited = creditedMillis + slots * SLOT_MILLIS

        val coins = if (slots <= MAX_ROLLED_SLOTS) {
            var total = 0L
            var at = creditedMillis
            repeat(slots) {
                at += SLOT_MILLIS
                total += rollSlot(at)
            }
            total
        } else {
            // Statistička nadoknada. Množilac raste linearno (do plafona), pa se
            // uzima prosek na krajevima intervala - preciznost ovde nije bitna,
            // scenario je patološki.
            val averageMultiplier =
                (multiplierAt(creditedMillis) + multiplierAt(newCredited)) / 2f
            (slots * EXPECTED_COINS_PER_SLOT * averageMultiplier).toLong()
        }

        return Award(
            coins = coins.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            slots = slots,
            creditedMillis = newCredited
        )
    }

    /**
     * Raspodela je nepromenjena u odnosu na stari processBrainrotDrops();
     * jedina razlika je množilac na kraju.
     */
    private fun rollSlot(elapsedMillisAtSlot: Long): Int {
        val chance = Random.nextFloat()
        val base = when {
            chance > 0.98f -> Random.nextInt(100, 1000)
            chance > 0.85f -> Random.nextInt(10, 50)
            chance > 0.40f -> Random.nextInt(2, 10)
            else -> 1
        }
        return (base * multiplierAt(elapsedMillisAtSlot)).toInt().coerceAtLeast(1)
    }
}