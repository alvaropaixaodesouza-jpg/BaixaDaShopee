package br.com.rastrai.scan

/** Política de segurança independente da interface: jamais retomar em outro aplicativo. */
object ReplayResumePolicy {
    // Correspondem a android.text.InputType.TYPE_MASK_CLASS e TYPE_MASK_VARIATION.
    private const val CLASS_MASK=0x0000000f
    private const val VARIATION_MASK=0x00000ff0

    fun sameField(
        expectedPackage:String, expectedField:Int, expectedInputType:Int, expectedHint:String,
        actualPackage:String, actualField:Int, actualInputType:Int, actualHint:String
    ):Boolean {
        if(expectedPackage.isBlank() || expectedPackage!=actualPackage) return false
        if((expectedInputType and CLASS_MASK)!=(actualInputType and CLASS_MASK)) return false
        if((expectedInputType and VARIATION_MASK)!=(actualInputType and VARIATION_MASK)) return false
        val sameHint=expectedHint.isNotBlank() && actualHint.isNotBlank() &&
            expectedHint.trim()==actualHint.trim()
        if(expectedHint.isNotBlank() && actualHint.isNotBlank() && !sameHint) return false
        if(expectedField>0 && actualField>0 && expectedField!=actualField && !sameHint) return false
        return true
    }
    fun remainingDelay(lastEnterAt:Long, delayMillis:Int, now:Long):Long {
        if(lastEnterAt<=0L) return 300L
        return (lastEnterAt + delayMillis - now).coerceAtLeast(300L)
    }
}
