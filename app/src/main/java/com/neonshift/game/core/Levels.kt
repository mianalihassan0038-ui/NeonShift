package com.neonshift.game.core

/** Tiny JSON reader (objects, arrays, strings, numbers, booleans, null). Enough for the level files. */
object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "Trailing characters at ${p.i}" }
        return v
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "Unexpected end of JSON" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else error("Unexpected '$c' at $i")
            }
        }
        fun lit(word: String, v: Any?): Any? {
            require(s.startsWith(word, i)) { "Bad literal at $i" }
            i += word.length
            return v
        }
        fun num(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
        fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "Unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = s[i++]
                        when (e) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun arr(): List<Any?> {
            i++
            val out = mutableListOf<Any?>()
            ws()
            if (s[i] == ']') { i++; return out }
            while (true) {
                out += value()
                ws()
                when (s[i++]) { ',' -> continue; ']' -> return out; else -> error("Expected , or ] at ${i - 1}") }
            }
        }
        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            ws()
            if (s[i] == '}') { i++; return out }
            while (true) {
                ws()
                val k = str()
                ws()
                require(s[i++] == ':') { "Expected : at ${i - 1}" }
                out[k] = value()
                ws()
                when (s[i++]) { ',' -> continue; '}' -> return out; else -> error("Expected , or } at ${i - 1}") }
            }
        }
    }
}

object LevelParser {
    @Suppress("UNCHECKED_CAST")
    fun parse(json: String): LevelData {
        val m = MiniJson.parse(json) as Map<String, Any?>
        fun int(map: Map<String, Any?>?, k: String, def: Int) = (map?.get(k) as? Number)?.toInt() ?: def
        fun pos(v: Any?): GridPos {
            val p = v as? Map<String, Any?> ?: return GridPos(0, 0)
            return GridPos(int(p, "x", 0), int(p, "y", 0))
        }
        fun list(b: Map<String, Any?>, k: String) = (b[k] as? List<Any?>)?.map { pos(it) } ?: emptyList()
        fun board(v: Any?): BoardData {
            val b = v as? Map<String, Any?> ?: return BoardData()
            val portals = (b["portals"] as? List<Any?>)?.mapNotNull {
                val m = it as? Map<String, Any?> ?: return@mapNotNull null
                pos(m["a"]) to pos(m["b"])
            } ?: emptyList()
            return BoardData(pos(b["start"]), pos(b["exit"]), list(b, "walls"), list(b, "cores"), portals,
                list(b, "switches"), list(b, "gates"), list(b, "crumble"))
        }
        val st = m["starThresholds"] as? Map<String, Any?>
        return LevelData(
            levelId = int(m, "levelId", 1),
            worldId = int(m, "worldId", 1),
            gridWidth = int(m, "gridWidth", 5),
            gridHeight = int(m, "gridHeight", 5),
            maxMoves = int(m, "maxMoves", 0),
            hint = m["hint"] as? String ?: "",
            light = board(m["light"]),
            shadow = board(m["shadow"]),
            starThresholds = StarThresholds(int(st, "threeStarsMaxMoves", 0), int(st, "twoStarsMaxMoves", 0), int(st, "oneStarMaxMoves", 0))
        )
    }

    fun toJson(l: LevelData, solution: List<Direction>?): String {
        fun p(g: GridPos) = "{\"x\": ${g.x}, \"y\": ${g.y}}"
        fun arr(xs: List<GridPos>) = xs.joinToString(", ", "[", "]") { p(it) }
        fun board(b: BoardData) = buildString {
            append("{\n    \"start\": ${p(b.start)},\n    \"exit\": ${p(b.exit)},\n    \"walls\": ${arr(b.walls)}")
            if (b.cores.isNotEmpty()) append(",\n    \"cores\": ${arr(b.cores)}")
            if (b.portals.isNotEmpty()) append(",\n    \"portals\": " + b.portals.joinToString(", ", "[", "]") { "{\"a\": ${p(it.first)}, \"b\": ${p(it.second)}}" })
            if (b.switches.isNotEmpty()) append(",\n    \"switches\": ${arr(b.switches)}")
            if (b.gates.isNotEmpty()) append(",\n    \"gates\": ${arr(b.gates)}")
            if (b.crumble.isNotEmpty()) append(",\n    \"crumble\": ${arr(b.crumble)}")
            append("\n  }")
        }
        val t = l.starThresholds
        val hint = l.hint.replace("\\", "\\\\").replace("\"", "\\\"")
        val sol = solution?.joinToString("") { it.name.take(1) } ?: ""
        return "{\n  \"levelId\": ${l.levelId},\n  \"worldId\": ${l.worldId},\n  \"gridWidth\": ${l.gridWidth},\n  \"gridHeight\": ${l.gridHeight},\n" +
            "  \"maxMoves\": ${l.maxMoves},\n  \"hint\": \"$hint\",\n  \"light\": ${board(l.light)},\n  \"shadow\": ${board(l.shadow)},\n" +
            "  \"starThresholds\": {\"threeStarsMaxMoves\": ${t.threeStarsMaxMoves}, \"twoStarsMaxMoves\": ${t.twoStarsMaxMoves}, \"oneStarMaxMoves\": ${t.oneStarMaxMoves}},\n" +
            "  \"_solution\": \"$sol\"\n}\n"
    }

    /** Sorted by world, then level number. */
    fun sort(levels: List<LevelData>) = levels.sortedWith(compareBy({ it.worldId }, { it.levelId }))
}

/** Key/value storage. Android uses SharedPreferences; tests use a map. */
interface KeyValueStore {
    fun getInt(key: String, def: Int): Int
    fun putInt(key: String, value: Int)
    fun getFloat(key: String, def: Float): Float
    fun putFloat(key: String, value: Float)
}

class MemoryStore : KeyValueStore {
    private val map = HashMap<String, Any>()
    override fun getInt(key: String, def: Int) = map[key] as? Int ?: def
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getFloat(key: String, def: Float) = map[key] as? Float ?: def
    override fun putFloat(key: String, value: Float) { map[key] = value }
}

/** Local progress and wallet. Offline only, no accounts. */
class SaveData(private val kv: KeyValueStore) {
    var currentLevelIndex: Int
        get() = kv.getInt("current", 0)
        set(v) = kv.putInt("current", v)

    val highestUnlockedIndex get() = kv.getInt("highest", 0)
    fun unlock(index: Int) { if (index > highestUnlockedIndex) kv.putInt("highest", index) }

    private fun key(kind: String, l: LevelData) = "$kind.${l.worldId}-${l.levelId}"
    fun stars(l: LevelData) = kv.getInt(key("stars", l), 0)
    fun recordStars(l: LevelData, stars: Int): Int {
        val best = stars(l)
        if (stars > best) { kv.putInt(key("stars", l), stars); return stars }
        return best
    }
    fun completedWithoutHint(l: LevelData) = kv.getInt(key("nohint", l), 0) == 1
    fun markNoHint(l: LevelData) = kv.putInt(key("nohint", l), 1)

    val coins get() = kv.getInt("coins", 0)
    fun addCoins(amount: Int) = kv.putInt("coins", maxOf(0, coins + amount))
    val hints get() = kv.getInt("hints", Rewards.STARTING_HINTS)

    /** Uses a free hint, or buys one with coins. */
    fun trySpendHint(): Boolean {
        if (hints > 0) { kv.putInt("hints", hints - 1); return true }
        if (coins >= Rewards.HINT_COST) { addCoins(-Rewards.HINT_COST); return true }
        return false
    }

    val dailyLastDay get() = kv.getInt("daily.last", 0)
    val dailyStreak get() = kv.getInt("daily.streak", 0)
    val dailyBestStreak get() = kv.getInt("daily.best", 0)
    val dailiesCompleted get() = kv.getInt("daily.count", 0)

    /** Returns coins awarded (0 if today's daily was already done). */
    fun completeDaily(today: Int): Int {
        if (dailyLastDay == today) return 0
        val streak = Rewards.nextStreak(dailyLastDay, today, dailyStreak, ::nextDay)
        kv.putInt("daily.last", today)
        kv.putInt("daily.streak", streak)
        kv.putInt("daily.best", maxOf(streak, dailyBestStreak))
        kv.putInt("daily.count", dailiesCompleted + 1)
        addCoins(Rewards.DAILY_COINS)
        return Rewards.DAILY_COINS
    }

    fun activeDailyStreak(today: Int): Int {
        val last = dailyLastDay
        return if (last == today || (last != 0 && nextDay(last) == today)) dailyStreak else 0
    }

    fun snapshot(levels: List<LevelData>): ProgressSnapshot {
        val p = ProgressSnapshot(totalLevels = levels.size, bestDailyStreak = dailyBestStreak, dailiesCompleted = dailiesCompleted, timeAttackBest = timeAttackBest)
        for (l in levels) {
            val s = stars(l)
            val w = l.worldId.coerceIn(0, 7)
            if (l.worldId == 1) p.world1Total++
            p.worldTotal[w]++
            if (s <= 0) continue
            p.worldCompleted[w]++
            p.levelsCompleted++
            if (l.worldId == 1) p.world1Completed++
            if (s >= 3) p.perfectLevels++
            if (completedWithoutHint(l)) p.noHintLevels++
        }
        return p
    }

    // Settings
    private fun flag(k: String, def: Boolean) = kv.getInt(k, if (def) 1 else 0) == 1
    var sound: Boolean get() = flag("set.sound", true); set(v) = kv.putInt("set.sound", if (v) 1 else 0)
    var music: Boolean get() = flag("set.music", true); set(v) = kv.putInt("set.music", if (v) 1 else 0)
    var vibration: Boolean get() = flag("set.vibration", true); set(v) = kv.putInt("set.vibration", if (v) 1 else 0)
    var tutorialDone: Boolean get() = flag("set.tutorial", false); set(v) = kv.putInt("set.tutorial", if (v) 1 else 0)
    var musicVolume: Float get() = kv.getFloat("set.musicVol", 0.7f); set(v) = kv.putFloat("set.musicVol", v)
    var soundVolume: Float get() = kv.getFloat("set.soundVol", 0.8f); set(v) = kv.putFloat("set.soundVol", v)

    // Shop: owned skins are a bit mask, skin 0 (Classic) is always owned.
    var skin: Int get() = kv.getInt("skin", 0); set(v) = kv.putInt("skin", v)
    fun ownsSkin(i: Int) = i == 0 || (kv.getInt("skins", 1) shr i) and 1 == 1
    fun buySkin(i: Int, price: Int): Boolean {
        if (ownsSkin(i)) return true
        if (coins < price) return false
        addCoins(-price)
        kv.putInt("skins", kv.getInt("skins", 1) or (1 shl i))
        return true
    }
    fun addHints(n: Int) = kv.putInt("hints", hints + n)

    // Daily login reward: a 7-day cycle. Missing a day restarts it.
    val loginLastDay get() = kv.getInt("login.last", 0)
    val loginStreak get() = kv.getInt("login.streak", 0)
    fun loginAvailable(today: Int) = loginLastDay != today
    /** Day of the cycle (1..7) that today's reward would be. */
    fun loginDay(today: Int): Int {
        val last = loginLastDay
        val streak = if (last != 0 && nextDay(last) == today) loginStreak else 0
        return streak % 7 + 1
    }
    fun claimLogin(today: Int): Int {
        if (!loginAvailable(today)) return 0
        val day = loginDay(today)
        val reward = Rewards.loginReward(day)
        kv.putInt("login.streak", if (day == 1) 1 else loginStreak + 1)
        kv.putInt("login.last", today)
        addCoins(reward)
        if (day == 7) addHints(2)
        return reward
    }

    var timeAttackBest: Int get() = kv.getInt("ta.best", 0); set(v) = kv.putInt("ta.best", v)
    fun introSeen(world: Int) = kv.getInt("intro.$world", 0) == 1
    fun markIntro(world: Int) = kv.putInt("intro.$world", 1)

    companion object {
        fun nextDay(day: Int): Int {
            val d = java.time.LocalDate.of(day / 10000, day / 100 % 100, day % 100).plusDays(1)
            return DailyChallenge.dayNumber(d.year, d.monthValue, d.dayOfMonth)
        }
    }
}

/** The level list, the current level and which levels are unlocked. */
class LevelManager(val all: List<LevelData>, private val save: SaveData) {
    var currentIndex = if (all.isEmpty()) 0 else minOf(save.currentLevelIndex, all.size - 1); private set
    val count get() = all.size
    val current get() = all.getOrNull(currentIndex)
    val isLastLevel get() = currentIndex >= all.size - 1
    val totalStars get() = all.sumOf { save.stars(it) }

    fun isUnlocked(i: Int) = i in all.indices && i <= save.highestUnlockedIndex
    fun indicesInWorld(w: Int) = all.indices.filter { all[it].worldId == w }
    fun countInWorld(w: Int) = indicesInWorld(w).size
    fun starsInWorld(w: Int) = indicesInWorld(w).sumOf { save.stars(all[it]) }
    fun isWorldUnlocked(w: Int) = indicesInWorld(w).firstOrNull()?.let { isUnlocked(it) } ?: false

    fun select(i: Int) {
        if (!isUnlocked(i)) return
        currentIndex = i
        save.currentLevelIndex = i
    }

    fun markCurrentCompleted() { if (currentIndex + 1 < all.size) save.unlock(currentIndex + 1) }

    fun advance() {
        if (all.isEmpty()) return
        currentIndex = (currentIndex + 1) % all.size
        save.unlock(currentIndex)
        save.currentLevelIndex = currentIndex
    }
}
