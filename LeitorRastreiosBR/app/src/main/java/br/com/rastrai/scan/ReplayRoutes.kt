package br.com.rastrai.scan

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ReplayRoute(
    val id: String,
    val title: String,
    val codes: List<String>,
    val nextIndex: Int = 0
) {
    val remaining: Int get() = (codes.size - nextIndex).coerceAtLeast(0)
}

object ReplayRoutes {
    private const val KEY = "routes_json"
    private const val SELECTED = "route_selected"
    private const val DELAY_MS = "route_delay_ms"
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
            r.codes.forEach(codes::put)
            array.put(JSONObject().put("id",r.id).put("title",r.title)
                .put("codes",codes).put("next",r.nextIndex))
        }
        check(pref(c).edit().putString(KEY,array.toString()).commit()) { "Falha ao gravar a rota" }
    }
    fun addFile(c: Context, title: String, codes: List<String>): ReplayRoute = synchronized(lock) {
        val cleaned=codes.mapNotNull { TrackingParser.parse(it,true) }.distinct()
        require(cleaned.isNotEmpty()) { "Nenhum código BR/SPXBR encontrado no arquivo" }
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
    fun intervalMillis(c: Context) = pref(c).getInt(DELAY_MS,3000).coerceIn(500,60000)
    fun adjustInterval(c: Context, delta: Int): Int {
        val next=(intervalMillis(c)+delta*500).coerceIn(500,60000)
        pref(c).edit().putInt(DELAY_MS,next).apply()
        return next
    }
    fun setInterval(c: Context,millis: Int) {
        pref(c).edit().putInt(DELAY_MS,millis.coerceIn(500,60000)).apply()
    }
    fun saveRunState(c: Context, status: String) {
        pref(c).edit().putString("run_state",status).apply()
    }
    fun runState(c: Context): String = pref(c).getString("run_state","Parado") ?: "Parado"
}
