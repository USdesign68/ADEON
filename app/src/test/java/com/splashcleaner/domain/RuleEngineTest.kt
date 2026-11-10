package com.splashcleaner.domain

import org.junit.Assert.assertEquals
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
}
