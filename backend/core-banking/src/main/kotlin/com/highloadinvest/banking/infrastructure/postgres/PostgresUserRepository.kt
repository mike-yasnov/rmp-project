package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.domain.entities.User
import com.highloadinvest.banking.domain.repositories.UserRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class PostgresUserRepository : UserRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun findById(id: UUID): User? {
        logger.debug("findById id={}", id)
        var user: User? = null
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("SELECT id, username, email, created_at FROM users WHERE id = ?")
                .use { stmt ->
                    stmt.setObject(1, id)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        user = User(
                            id = rs.getObject("id", UUID::class.java),
                            username = rs.getString("username"),
                            email = rs.getString("email"),
                            createdAt = rs.getTimestamp("created_at").toInstant()
                        )
                    }
                }
            conn.commit()
        }
        return user
    }

    override suspend fun findByUsername(username: String): User? {
        logger.debug("findByUsername username={}", username)
        var user: User? = null
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("SELECT id, username, email, created_at FROM users WHERE username = ?")
                .use { stmt ->
                    stmt.setString(1, username)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        user = User(
                            id = rs.getObject("id", UUID::class.java),
                            username = rs.getString("username"),
                            email = rs.getString("email"),
                            createdAt = rs.getTimestamp("created_at").toInstant()
                        )
                    }
                }
            conn.commit()
        }
        return user
    }

    override suspend fun create(username: String, email: String): User {
        val id = UUID.randomUUID()
        logger.info("create user id={} username={}", id, username)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("INSERT INTO users (id, username, email) VALUES (?, ?, ?)")
                .use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setString(2, username)
                    stmt.setString(3, email)
                    stmt.executeUpdate()
                }
            conn.commit()
        }
        return User(id = id, username = username, email = email, createdAt = java.time.Instant.now())
    }
}
