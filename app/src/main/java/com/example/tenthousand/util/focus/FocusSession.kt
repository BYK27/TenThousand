package com.example.tenthousand.util.focus

import java.util.Locale

enum class FocusMode { TIMER, STOPWATCH }

/**
 * Jedna aktivna focus sesija. U celom app-u može da postoji najviše jedna.
 *
 * Zašto ovaj model, a ne stari `targetTimeMillis`:
 *
 * Stari kod je za tajmer čuvao ciljno vreme, a za štopericu vreme starta - dve
 * različite reprezentacije, pa su pauza i nastavak morali da se pišu dvaput i
 * za tajmer nisu ni postojali (pauza je brisala stanje). Ovde je stanje jedno:
 * koliko je milisekundi VEĆ akumulirano dok je bilo pauzirano
 * ([accumulatedMillis]) i, ako trenutno teče, od kog zidnog vremena teče
 * ([runningSinceMillis]). Iz ta dva polja se izvodi i proteklo i preostalo
 * vreme, za oba moda, istom formulom.
 *
 * Sve je vezano za zidni sat (System.currentTimeMillis), pa vreme teče i kad
 * je proces ubijen ili telefon u doze-u - restore posle restarta daje tačan
 * broj sekundi bez ikakvog "sustizanja".
 */
data class FocusSession(
    val habitId: Long,
    val habitName: String,
    val habitColor: Int,
    val backgroundName: String?,
    val mode: FocusMode,
    /** Puno trajanje za TIMER, u sekundama. Za STOPWATCH je 0. */
    val totalSeconds: Long,
    val accumulatedMillis: Long,
    val runningSinceMillis: Long?,
    /**
     * Do kog trenutka protekle sesije su coin-ovi već isplaćeni. Uvek je
     * poravnato na granicu slota od 100 ms (vidi [FocusCoinEngine]).
     *
     * Ovo polje je razlog što coin-ovi ne mogu ni da se izgube ni da se
     * dupliraju: servis pri svakom buđenju isplati tačno razliku između
     * proteklog vremena i ove vrednosti, bez obzira koliko je dugo spavao.
     */
    val coinCreditedMillis: Long = 0L
) {
    val isRunning: Boolean get() = runningSinceMillis != null

    fun elapsedMillisAt(nowMillis: Long): Long {
        val live = runningSinceMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        return accumulatedMillis + live
    }

    fun elapsedSecondsAt(nowMillis: Long): Long = elapsedMillisAt(nowMillis) / 1000L

    fun remainingMillisAt(nowMillis: Long): Long =
        if (mode == FocusMode.TIMER) (totalSeconds * 1000L - elapsedMillisAt(nowMillis)).coerceAtLeast(0L)
        else 0L

    fun remainingSecondsAt(nowMillis: Long): Long = remainingMillisAt(nowMillis) / 1000L

    /**
     * Zidno vreme kad tajmer treba da istekne, ili null ako je štoperica /
     * pauzirano. Koristi ga servis za AlarmManager.
     */
    fun finishAtMillis(): Long? {
        if (mode != FocusMode.TIMER) return null
        val since = runningSinceMillis ?: return null
        return since + (totalSeconds * 1000L - accumulatedMillis)
    }

    fun pausedAt(nowMillis: Long): FocusSession =
        copy(accumulatedMillis = elapsedMillisAt(nowMillis), runningSinceMillis = null)

    fun resumedAt(nowMillis: Long): FocusSession =
        copy(runningSinceMillis = nowMillis)
}

/**
 * Jedan coin drop koji je servis upravo isplatio. UI ga koristi samo za
 * animaciju - novac je već upisan u CoinManager pre nego što je ovo emitovano.
 */
data class CoinDrop(
    val habitId: Long,
    val amount: Int,
    /**
     * True kad je ovo nadoknada za period koji je app proveo u pozadini, pa se
     * više slotova sabralo u jedan iznos. UI to prikazuje kao jedan veliki
     * popup umesto stotinu malih.
     */
    val isCatchUp: Boolean
)

/**
 * Isti format kao formatSleekTimer u UI sloju, ali sa satima kad pređe 60 min -
 * notifikacija mora da se poklopi sa onim što Chronometer sam ispisuje kad
 * sesija duže traje, pa je ovde format H:MM:SS umesto MM:SS preko 60 minuta.
 */
fun formatFocusClock(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}