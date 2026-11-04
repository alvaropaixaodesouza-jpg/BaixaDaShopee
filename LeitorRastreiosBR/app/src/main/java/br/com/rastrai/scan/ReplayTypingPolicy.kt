package br.com.rastrai.scan

/** Regras para decidir se o próximo BR pode ser anexado ao campo sem concatenar códigos. */
object ReplayTypingPolicy {
    fun canType(before:String, after:String, selected:String, newlineMode:Boolean):Boolean {
        if(selected.isNotEmpty()) return true
        if(after.isNotEmpty()) return false // cursor fora do fim
        if(before.isBlank()) return true
        return newlineMode && before.endsWith("\n")
    }
    fun newlineWasInserted(textBeforeCursor:String?, code:String):Boolean =
        textBeforeCursor?.endsWith(code+"\n") == true
}
