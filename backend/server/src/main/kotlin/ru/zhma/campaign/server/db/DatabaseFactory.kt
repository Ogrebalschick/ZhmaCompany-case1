package ru.zhma.campaign.server.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import ru.zhma.campaign.server.db.table.AuditTable
import ru.zhma.campaign.server.db.table.BannerTypesTable
import ru.zhma.campaign.server.db.table.BannersTable
import ru.zhma.campaign.server.db.table.CampaignPlacesTable
import ru.zhma.campaign.server.db.table.CampaignSegmentsTable
import ru.zhma.campaign.server.db.table.CampaignTagsTable
import ru.zhma.campaign.server.db.table.CampaignsTable
import ru.zhma.campaign.server.db.table.ChannelsTable
import ru.zhma.campaign.server.db.table.ContentsTable
import ru.zhma.campaign.server.db.table.CreativesTable
import ru.zhma.campaign.server.db.table.JobsTable
import ru.zhma.campaign.server.db.table.MessagesTable
import ru.zhma.campaign.server.db.table.PlacesTable
import ru.zhma.campaign.server.db.table.SegmentsTable
import ru.zhma.campaign.server.db.table.SessionsTable
import ru.zhma.campaign.server.db.table.TagsTable
import ru.zhma.campaign.server.db.table.TopicsTable
import java.io.File

object DatabaseFactory {
    private val mutex = Mutex()
    private var database: Database? = null

    fun init(path: String) {
        close()
        File(path).parentFile?.mkdirs()
        val sqlitePath = path.replace('\\', '/')
        val db =
            Database.connect(
                url = "jdbc:sqlite:$sqlitePath",
                driver = "org.sqlite.JDBC",
                setupConnection = { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("PRAGMA foreign_keys = ON")
                        statement.execute("PRAGMA busy_timeout = 5000")
                    }
                },
            )
        database = db
        transaction(db) {
            SchemaUtils.create(
                TopicsTable,
                TagsTable,
                SegmentsTable,
                PlacesTable,
                BannerTypesTable,
                ChannelsTable,
                CampaignsTable,
                CampaignTagsTable,
                CampaignPlacesTable,
                CampaignSegmentsTable,
                BannersTable,
                ContentsTable,
                CreativesTable,
                AuditTable,
                SessionsTable,
                MessagesTable,
                JobsTable,
            )
            Seed.seedIfEmpty()
        }
    }

    fun close() {
        val current = database ?: return
        runBlocking {
            mutex.withLock {
                transaction(current) {
                    exec("PRAGMA wal_checkpoint(TRUNCATE)")
                }
                TransactionManager.closeAndUnregister(current)
                database = null
            }
        }
    }

    suspend fun <T> db(block: () -> T): T =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                transaction(database ?: error("База не открыта")) { block() }
            }
        }
}
