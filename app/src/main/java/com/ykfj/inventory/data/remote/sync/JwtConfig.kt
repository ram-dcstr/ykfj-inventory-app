package com.ykfj.inventory.data.remote.sync

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.util.Date

class JwtConfig(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val realm = "ykfj"
    val issuer = "ykfj-tablet"

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .build()

    fun generateToken(userId: String, role: String): String = JWT.create()
        .withIssuer(issuer)
        .withSubject(userId)
        .withClaim("role", role)
        .withExpiresAt(Date(System.currentTimeMillis() + TOKEN_TTL_MS))
        .sign(algorithm)

    companion object {
        const val TOKEN_TTL_MS = 24 * 60 * 60 * 1_000L
    }
}
