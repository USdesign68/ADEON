package com.splashcleaner.domain

/** Hard-coded protection boundary. Rules can never override these checks. */
object SafetyGate {
    private val protectedPackagePrefixes = setOf(
        "com.android.settings", "com.android.systemui", "com.google.android.apps.walletnfcrel",
        "com.google.android.apps.nbu.paisa.user", "com.android.vending"
    )
    private val protectedPackageFragments = listOf("bank", "wallet", "payment", "gov", "government", "pay")
    private val sensitiveWords = listOf("同意", "授权", "允许", "支付", "确认", "绑定", "登录")

    fun isProtectedPackage(packageName: String): Boolean =
        packageName in protectedPackagePrefixes || protectedPackageFragments.any { packageName.contains(it, ignoreCase = true) }

    fun containsSensitiveText(text: String?): Boolean = text != null && sensitiveWords.any { text.contains(it) }

    fun mayAct(rule: AdRule, eventPackage: String, rootPackage: String, node: NodeSnapshot): Boolean {
        if (rule.packageName != "*" && rule.packageName != eventPackage) return false
        if (eventPackage != rootPackage) return false
        if (isProtectedPackage(eventPackage)) return false
        if (!node.visible || !node.enabled) return false
        if (containsSensitiveText(node.text) || containsSensitiveText(node.contentDescription)) return false
        // A click must target an explicitly clickable node. Back is a separate action path.
        if (rule.action == "click" && !node.clickable) return false
        return rule.action == "click" || rule.action == "back"
    }
}
