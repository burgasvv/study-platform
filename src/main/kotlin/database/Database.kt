

package org.burgas.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object DatabaseConnection {

    private val hikariConfig = HikariConfig().apply {
        driverClassName = "org.postgresql.Driver"
        jdbcUrl = "jdbc:postgresql://localhost:5000/study-platform-db"
        username = "postgres"
        password = "postgres"
        maximumPoolSize = 20
        minimumIdle = 5
        validate()
    }

    private val dataSource = HikariDataSource(hikariConfig)

    val postgres = Database.connect(datasource = dataSource)

    val redisPool = JedisPool(JedisPoolConfig(), "localhost", 6000)
}

object ImageTable : UUIDTable(name = "image") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val preview = bool("preview").default(true)
    val data = blob("data")
    init {
        index(isUnique = false, name, contentType, preview)
    }
}

object FileTable : UUIDTable(name = "file") {
    val name = varchar("name", 250)
    val contentType = varchar("content_type", 250)
    val data = blob("data")
    init {
        index(isUnique = false, name, contentType)
    }
}

@Suppress("unused")
enum class Authority {
    ADMIN, USER
}

object IdentityTable : UUIDTable(name = "identity") {
    val authority = enumerationByName<Authority>("authority", 250)
    val email = varchar("email", 250).uniqueIndex()
    val password = varchar("password", 250)
    val status = bool("status").default(true)
    val firstname = varchar("firstname", 250)
    val lastname = varchar("lastname", 250)
    val patronymic = varchar("patronymic", 250)
    val about = text("about")
    val imageId = optReference(
        name = "image_id", refColumn = ImageTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    ).uniqueIndex()
    init {
        index(isUnique = false, firstname, lastname, patronymic)
    }
}

object IdentityFileTable : Table(name = "identity_file") {
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val fileId = reference(
        name = "file_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, fileId))
}

object CourseTable : UUIDTable(name = "course") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description")
    val createdAt = date("created_at").defaultExpression(CurrentDate)
}

object IdentityCourseTable : Table(name = "identity_course") {
    val identityId = reference(
        name = "identity_id", refColumn = IdentityTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    val courseId = reference(
        name = "course_id", refColumn = CourseTable.id,
        onDelete = ReferenceOption.CASCADE, onUpdate = ReferenceOption.CASCADE
    )
    override val primaryKey: PrimaryKey
        get() = PrimaryKey(arrayOf(identityId, courseId))
}

object ProjectTable : UUIDTable(name = "project") {
    val name = varchar("name", 250).uniqueIndex()
    val description = text("description")
    val courseId = reference(
        name = "course_id", refColumn = CourseTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    )
    val taskId = optReference(
        name = "task_id", refColumn = FileTable.id,
        onDelete = ReferenceOption.SET_NULL, onUpdate = ReferenceOption.CASCADE
    ).uniqueIndex()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

fun Application.configureDatabase() {
    transaction(db = DatabaseConnection.postgres) {
        SchemaUtils.create(
            ImageTable, FileTable, IdentityTable, IdentityFileTable,
            CourseTable, IdentityCourseTable, ProjectTable
        )
    }
}