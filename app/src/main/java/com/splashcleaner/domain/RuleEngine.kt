package com.splashcleaner.domain

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** JSON5-compatible parser for the schema-3 rule package. Text selectors stay in memory only. */
class RuleEngine {
    private val mapper = ObjectMapper(JsonFactory.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .build())

    fun parseRulePackage(source: String): List<AdRule> {
        val root = mapper.readTree(source)
        require(root.path("schema").asInt(-1) == 3) { "Unsupported schema; expected 3" }
        val rulesNode = root.path("rules")
        require(rulesNode.isArray) { "rules must be an array" }
        return rulesNode.mapNotNull { node -> runCatching { parseRule(node) }.getOrNull() }
    }

    private fun parseRule(n: JsonNode): AdRule {
        val id = n.requiredText("id")
        val pkg = n.requiredText("pkg")
        val name = n.requiredText("name")
        val scene = Scene.valueOf(n.requiredText("scene"))
        require(pkg != "*" || scene == Scene.jumpGuard) { "Wildcard package only allowed for jumpGuard" }
        val whenNode = n.path("when")
        val activities = whenNode.path("activity").map { it.asText() }
        require(activities.isNotEmpty() || scene == Scene.jumpGuard) { "activity is required" }
        val match = n.path("match")
        val any = match.path("any").map { parseSelector(it) }
        require(any.isNotEmpty()) { "match.any must not be empty" }
        val all = match.path("all").map { parseSelector(it) }
        val act = n.path("act")
        val guard = n.path("guard")
        return AdRule(
            id = id, packageName = pkg, name = name, revision = n.path("rev").asInt(1),
            versionConstraint = n.requiredText("ver"), scene = scene, activities = activities,
            elapsedMaxMs = whenNode.path("elapsedMax").asLong(6_000),
            stayMs = whenNode.path("stayMs").asLong(120),
            firstLaunchOnly = whenNode.path("firstLaunchOnly").asBoolean(true),
            any = any, all = all, action = act.path("type").asText("none"),
            target = act.path("target").asText("self"), waitMs = act.path("waitMs").asLong(0),
            verify = act.path("verify").asText("windowChanged"),
            cooldownMs = guard.path("cooldownMs").asLong(1_500),
            maxPerSession = guard.path("maxPerSession").asInt(1),
            maxPerDay = guard.path("maxPerDay").asInt(24)
        )
    }

    private fun parseSelector(n: JsonNode): RuleSelector {
        val regex = n.path("t").takeIf { it.isTextual }?.asText()?.let {
            require(it.startsWith("^") && it.endsWith("$")) { "Text regex must be fully anchored" }
            Regex(it)
        }
        val depth = n.path("depth")
        return RuleSelector(regex, n.path("rid").textOrNull(), n.path("cls").textOrNull(),
            n.path("desc").textOrNull(), if (depth.isArray && depth.size() > 0) depth[0].asInt() else null,
            if (depth.isArray && depth.size() > 1) depth[1].asInt() else null,
            n.path("maxArea").takeIf { it.isNumber }?.asInt(), n.path("within").textOrNull())
    }

    fun candidates(rule: AdRule, snapshot: WindowSnapshot, density: Float): List<NodeSnapshot> {
        if (rule.state != RuleState.active) return emptyList()
        if (rule.packageName != "*" && rule.packageName != snapshot.packageName) return emptyList()
        if (snapshot.elapsedSinceServiceStartMs > rule.elapsedMaxMs && rule.elapsedMaxMs > 0) return emptyList()
        if (rule.activities.isNotEmpty() && rule.activities.none { wildcard(it, snapshot.activityName.orEmpty()) }) return emptyList()
        return snapshot.nodes.filter { node ->
            node.visible && node.enabled &&
                rule.any.any { matches(it, node, density) } &&
                rule.all.all { matches(it, node, density) }
        }.sortedWith(compareBy<NodeSnapshot>({ selectorRank(rule, it, density) }, { it.areaPx }, { distanceToEdge(rule, it) }))
    }

    private fun selectorRank(rule: AdRule, node: NodeSnapshot, density: Float): Int {
        val s = rule.any.firstOrNull { matches(it, node, density) }
        return when {
            s?.resourceId != null -> 0
            s?.textRegex != null -> 1
            s?.description != null -> 2
            else -> 3
        }
    }
    private fun distanceToEdge(rule: AdRule, node: NodeSnapshot): Int = when {
        rule.any.any { it.within?.startsWith("top:") == true } -> node.top
        rule.any.any { it.within?.startsWith("right:") == true } -> -node.right
        else -> 0
    }
    private fun matches(s: RuleSelector, n: NodeSnapshot, density: Float): Boolean {
        if (s.textRegex != null && !(n.text?.let { s.textRegex.matches(it) } ?: false)) return false
        if (s.resourceId != null && !(n.resourceId?.let { wildcard(s.resourceId, it) } ?: false)) return false
        if (s.className != null && !(n.className?.let { s.className.split('|').any { p -> wildcard(p, it) } } ?: false)) return false
        if (s.description != null && !(n.contentDescription?.let { wildcard(s.description, it) } ?: false)) return false
        if (s.minDepth != null && n.depth < s.minDepth) return false
        if (s.maxDepth != null && n.depth > s.maxDepth) return false
        if (s.maxAreaDp2 != null && n.areaPx / (density * density).coerceAtLeast(1f) > s.maxAreaDp2) return false
        if (s.within != null) {
            val p = s.within.split(':'); val limit = p.getOrNull(1)?.toIntOrNull() ?: return false
            if (p[0] == "top" && n.top > limit * density) return false
            if (p[0] == "right" && n.right < limit * density) return false
        }
        return true
    }
    private fun wildcard(pattern: String, value: String): Boolean =
        Regex("^" + pattern.split("*").joinToString(".*") { Regex.escape(it) } + "$").matches(value)
    private fun JsonNode.requiredText(key: String): String = path(key).takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
        ?: error("Missing required field: $key")
    private fun JsonNode.textOrNull(): String? = takeIf { it.isTextual }?.asText()
}
