package com.splashcleaner.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/** Verifies Ed25519 signatures over the UTF-8 JSON package with the top-level `sig` field removed. */
class RulePackageVerifier(private val publicKey: PublicKey) {
    private val mapper = ObjectMapper()
    fun verify(packageBytes: ByteArray): Boolean {
        val root = mapper.readTree(packageBytes) as? ObjectNode ?: return false
        val signatureText = root.remove("sig")?.asText() ?: return false
        val signatureBytes = runCatching { Base64.getDecoder().decode(signatureText) }.getOrNull() ?: return false
        val canonicalPayload = mapper.writeValueAsBytes(root)
        return runCatching {
            Signature.getInstance("Ed25519").run { initVerify(publicKey); update(canonicalPayload); verify(signatureBytes) }
        }.getOrDefault(false)
    }
}
