package com.ykfj.inventory.data.remote.sync

import at.favre.lib.crypto.bcrypt.BCrypt
import com.ykfj.inventory.data.local.db.dao.UserDao
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(userDao: UserDao, jwtConfig: JwtConfig) {
    route("/api/auth") {

        post("/login") {
            val req = call.receive<LoginRequest>()
            val user = userDao.getByUsername(req.username)
            if (user == null || !user.is_active) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                return@post
            }
            val match = BCrypt.verifyer().verify(req.password.toCharArray(), user.password_hash)
            if (!match.verified) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                return@post
            }
            val token = jwtConfig.generateToken(user.user_id, user.role.name)
            call.respond(LoginResponse(token = token, user = user.toSyncDto()))
        }

        authenticate("jwt-auth") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val user = userDao.getById(userId)
                    ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(user.toSyncDto())
            }
        }
    }
}
