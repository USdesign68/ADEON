package com.splashcleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class RuleEngineTest {
    @Test fun parsesJson5Schema3AndDropsInvalidRules() {
        val input = """{
          schema: 3,
          rules: [
            { id: 'demo/splash-1', pkg: 'com.example.demo', name: '关闭开屏', rev: 1,
              ver: '>=1.0.0 <2.0.0', scene: 'splash', when: { activity: ['*.SplashActivity'] },
              match: { any: [{ rid: '*:id/close' }] }, act: { type: 'none' } },
            { id: 'bad', pkg: 'com.example.bad', name: '坏规则', rev: 1, ver: '*', scene: 'splash',
              when: { activity: ['*'] }, match: { any: [{ t: 'close' }] }, act: { type: 'click' } }
          ]
        }"""
        val rules = RuleEngine().parseRulePackage(input)
        assertEquals(1, rules.size)
        assertEquals("demo/splash-1", rules.first().id)
    }

    @Test fun elapsedLimitIsMeasuredFromTargetAppLaunch() {
        val rule = AdRule(
            id = "test", packageName = "com.example.demo", name = "test", revision = 1,
            versionConstraint = "*", scene = Scene.splash, activities = listOf("*"),
            elapsedMaxMs = 6_000, any = listOf(RuleSelector(resourceId = "close"))
        )
        val node = NodeSnapshot(0, null, "Button", "close", null, null, true, true, true,
            0, 0, 20, 20, 0)
        fun snapshot(elapsed: Long) = WindowSnapshot("com.example.demo", "SplashActivity",
            1L, listOf(node), false, elapsed)

        val engine = RuleEngine()
        assertEquals(1, engine.candidates(rule, snapshot(6_000), 1f).size)
        assertTrue(engine.candidates(rule, snapshot(6_001), 1f).isEmpty())
    }

    @Test fun safetyGateRejectsNonClickableAndSensitiveTargets() {
        val rule = AdRule(
            id = "test", packageName = "com.example.demo", name = "test", revision = 1,
            versionConstraint = "*", scene = Scene.splash, activities = listOf("*"),
            any = listOf(RuleSelector(resourceId = "target")), action = "click"
        )
        val nonClickable = NodeSnapshot(0, null, "TextView", "target", null, null, false, true, true,
            0, 0, 20, 20, 0)
        val sensitive = nonClickable.copy(clickable = true, text = "确认支付")
        assertFalse(SafetyGate.mayAct(rule, "com.example.demo", "com.example.demo", nonClickable))
        assertFalse(SafetyGate.mayAct(rule, "com.example.demo", "com.example.demo", sensitive))
    }
}
