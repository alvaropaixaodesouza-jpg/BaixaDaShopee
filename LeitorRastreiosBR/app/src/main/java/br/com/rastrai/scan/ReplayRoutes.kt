package br.com.rastrai.scan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ReplayRoute(
    val id: String,
    val title: String,
    val codes: List<String>,
    val nextIndex: Int = 0
) {
    val remaining: Int get() = (codes.size - nextIndex).coerceAtLeast(0)
}

data class ReplayContinuation(
    val routeId: String,
    val targetPackage: String,
    val targetFieldId: Int,
    val targetInputType: Int,
    val targetHint: String,
    val lastEnterAt: Long,
    val expiresAt: Long
)

object ReplayRoutes {
    private const val KEY = "routes_json"
    private const val SELECTED = "route_selected"
    private const val DELAY_MS = "route_delay_ms"
    private const val CONT = "continuation"
    // Não reativar uma automação esquecida muito tempo depois de a tela desaparecer.
    private const val RESUME_WINDOW_MS = 3 * 60 * 1000L
    private val lock = Any()
    private fun pref(c: Context) = c.getSharedPreferences("replay_routes", Context.MODE_PRIVATE)
    fun routes(c: Context): List<ReplayRoute> = synchronized(lock) {
        val arr = JSONArray(pref(c).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { idx ->
            val o=arr.getJSONObject(idx)
            val raw=o.optJSONArray("codes") ?: JSONArray()
            ReplayRoute(o.getString("id"),o.getString("title"),
                (0 until raw.length()).map { raw.getString(it) },
                o.optInt("next",0).coerceIn(0,raw.length()))
        }
    }
    private fun save(c: Context, routes: List<ReplayRoute>) {
        val array=JSONArray()
        for (r in routes) {
            val codes=JSONArray()
            r.codes.forEach { codes.put(it) }
            array.put(JSONObject().put("id",r.id).put("title",r.title)
                .put("codes",codes).put("next",r.nextIndex))
        }
        check(pref(c).edit().putString(KEY,array.toString()).commit()) { "Falha ao gravar a rota" }
    }
    fun addFile(c: Context, title: String, codes: List<String>): ReplayRoute = synchronized(lock) {
        val cleaned=codes.mapNotNull { TrackingParser.parse(it,true) }.distinct()
        require(cleaned.isNotEmpty()) { "Nenhum código BR encontrado no arquivo" }
        require(cleaned.size<=10000) { "Planilha acima do limite de 10.000 códigos" }
        val id=System.currentTimeMillis().toString()+"_"+(Math.random()*10000).toInt()
        val r=ReplayRoute(id,title.take(70).ifBlank { "Rota importada" },cleaned)
        save(c,routes(c)+r)
        pref(c).edit().putString(SELECTED,id).commit()
        r
    }
    fun active(c: Context): ReplayRoute? {
        val selected=pref(c).getString(SELECTED,null)
        return routes(c).firstOrNull { it.id==selected } ?: routes(c).lastOrNull()
    }
    fun select(c: Context,id: String): Boolean = synchronized(lock) {
        if(routes(c).none { it.id==id }) return false
        pref(c).edit().putString(SELECTED,id).commit()
    }
    fun advance(c: Context,routeId: String, expectedIndex: Int): Boolean = synchronized(lock) {
        val all=routes(c)
        val current=all.firstOrNull { it.id==routeId } ?: return false
        if(current.nextIndex!=expectedIndex || expectedIndex>=current.codes.size) return false
        save(c,all.map { if(it.id==routeId) it.copy(nextIndex=expectedIndex+1) else it })
        true
    }
    fun resetProgress(c: Context,routeId: String): Boolean = synchronized(lock) {
        val all=routes(c)
        if(all.none { it.id==routeId }) return false
        save(c,all.map { if(it.id==routeId) it.copy(nextIndex=0) else it })
        stopContinuation(c)
        true
    }
    /** Limpar lista da aba ROTA remove só o arquivo selecionado; outras rotas são preservadas. */
    fun removeRoute(c: Context,routeId: String): Int = synchronized(lock) {
        val all=routes(c)
        val target=all.firstOrNull { it.id==routeId } ?: return 0
        val other=all.filterNot { it.id==routeId }
        save(c,other)
        val editor=pref(c).edit()
        if(other.isEmpty()) editor.remove(SELECTED) else editor.putString(SELECTED,other.last().id)
        editor.commit()
        stopContinuation(c)
        target.codes.size
    }
    fun intervalMillis(c: Context) = pref(c).getInt(DELAY_MS,3000).coerceIn(500,60000)
    fun adjustInterval(c: Context, delta: Int): Int {
        val next=(intervalMillis(c)+delta*500).coerceIn(500,60000)
        pref(c).edit().putInt(DELAY_MS,next).apply()
        return next
    }
    fun setInterval(c: Context,millis: Int) {
        pref(c).edit().putInt(DELAY_MS,millis.coerceIn(500,60000)).apply()
    }

    /** Uma continuação é autorizada somente por INICIAR, nunca só por importar Excel. */
    fun armContinuation(c: Context, routeId: String, pkg: String, fieldId: Int,
                        inputType: Int, hint: String, lastEnterAt: Long = 0L) {
        val now=System.currentTimeMillis()
        val data=JSONObject().put("route",routeId).put("pkg",pkg)
            .put("field",fieldId).put("type",inputType).put("hint",hint)
            .put("last",lastEnterAt).put("expires",now+RESUME_WINDOW_MS)
        pref(c).edit().putString(CONT,data.toString()).commit()
    }
    fun continuation(c: Context): ReplayContinuation? {
        val raw=pref(c).getString(CONT,null) ?: return null
        return runCatching {
            val o=JSONObject(raw)
            ReplayContinuation(o.getString("route"),o.getString("pkg"),
                o.getInt("field"),o.getInt("type"),o.optString("hint",""),
                o.optLong("last",0),o.optLong("expires",0))
        }.getOrNull()?.takeIf { System.currentTimeMillis() < it.expiresAt }
    }
    fun updateLastEnter(c: Context, routeId: String, at: Long) {
        val state=continuation(c) ?: return
        if(state.routeId!=routeId) return
        armContinuation(c,state.routeId,state.targetPackage,state.targetFieldId,
            state.targetInputType,state.targetHint,at)
    }
    fun stopContinuation(c: Context) {
        pref(c).edit().remove(CONT).commit()
    }
    fun saveRunState(c: Context, status: String) {
        pref(c).edit().putString("run_state",status).apply()
    }
    fun runState(c: Context): String = pref(c).getString("run_state","Parado") ?: "Parado"
}
