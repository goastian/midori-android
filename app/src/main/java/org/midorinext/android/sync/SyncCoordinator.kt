package org.midorinext.android.sync

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class SyncUiState(
    val connected: Boolean = false, val busy: Boolean = false, val name: String = "", val phase: String = "disconnected",
    val server: String = "", val error: String? = null, val lastSync: Long = 0, val conflicts: Int = 0,
    val accountId: String = "", val pending: Int = 0, val selected: Set<String> = SyncModels.schemas.keys, val unsupported: Set<String> = emptySet(), val email: String = "",
)

internal data class SyncConflict(val collection: String, val id: String, val local: String?, val remote: String?, val canResolve: Boolean)

@Singleton
internal class SyncCoordinator @Inject constructor(private val vault: SyncVault, private val stores: SyncNativeStores) {
    private val mutex = Mutex()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private class Flight(val epoch: Long, val task: Deferred<Unit>, var waiters: Int = 0)
    private val flightLock = Any()
    private var flight: Flight? = null
    private val accountEpoch = AtomicLong()
    @Volatile private var disconnecting = false
    @Volatile private var network: SyncNetworkContext? = null
    private val requestedCount = AtomicLong()
    private val executedCount = AtomicLong()
    private val coalescedCount = AtomicLong()
    private val changedCount = AtomicLong()
    private val unchangedCount = AtomicLong()
    private val cancelledCount = AtomicLong()
    private val failedCount = AtomicLong()
    private val totalSent = AtomicLong()
    private val totalReceived = AtomicLong()
    private val totalCpu = AtomicLong()
    internal fun statisticsSnapshot(): JSONObject = obj(
        "requested" to requestedCount.get(), "executed" to executedCount.get(), "coalesced" to coalescedCount.get(),
        "with_changes" to changedCount.get(), "without_changes" to unchangedCount.get(), "cancelled" to cancelledCount.get(), "failed" to failedCount.get(),
        "sent_body_bytes" to totalSent.get(), "received_body_bytes" to totalReceived.get(), "process_cpu_ms" to totalCpu.get())
    private val latestRun = MutableStateFlow<SyncRunResult?>(null)
    val statistics = latestRun.asStateFlow()
    fun localChanges(): Flow<Pair<Long, Long>> = stores.localChanges()
    private fun transport(server: String) = SyncTransport(server, network)
    private var state = obj("version" to 1)
    private var loaded = false
    private val mutable = MutableStateFlow(SyncUiState())
    val ui = mutable.asStateFlow()
    private var activeKeys: SyncKeys? = null
    private fun save() = vault.write(state)
    private fun load() { if (!loaded) { state = vault.read(); loaded = true } }
    private fun selected(): Set<String> = state.optJSONArray("selected")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() } ?: SyncModels.schemas.keys
    private fun report(error: String? = null, busy: Boolean = false) {
        val session = state.objectOrNull("session")
        val collections = state.objectOrNull("collections")?.entries().orEmpty().map { it.second as JSONObject }
        mutable.value = SyncUiState(session != null && !disconnecting, busy, session?.getJSONObject("user")?.let { it.optString("name").ifBlank { it.optString("email").substringBefore('@') } }.orEmpty(),
            state.optString("phase", if (session == null) "disconnected" else "connecting"), state.optString("server"), error,
            state.optLong("lastSync"), collections.sumOf { it.optJSONObject("conflicts")?.length() ?: 0 },
            state.textOrNull("scope")?.let { value -> java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) } }.orEmpty(),
            collections.sumOf { (it.optJSONObject("outbox")?.length() ?: 0) + (it.optJSONObject("inbox")?.length() ?: 0) }, selected(),
            state.optJSONArray("unsupported")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }.orEmpty(), session?.optJSONObject("user")?.optString("email").orEmpty())
    }
    private suspend fun <T> operation(metrics: SyncRunMetrics? = null, reportErrors: Boolean = true, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            load(); if (reportErrors) report(busy = true)
            coroutineScope {
                val context = SyncNetworkContext(currentCoroutineContext().job, metrics)
                network = context
                val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                    try { awaitCancellation() } finally { context.cancel() }
                }
                try { block().also { if (reportErrors) report() } } catch (error: Exception) {
                    if (error is CancellationException) { if (reportErrors) report(); throw error }
                    if (reportErrors) report((error as? SyncException)?.code ?: "sync_unavailable")
                    throw error
                } finally {
                    cancellation.cancel()
                    network = null
                }
            }
        }
    }
    suspend fun inspect() = operation { Unit }
    suspend fun needsLocalSync(): Boolean = mutex.withLock {
        load()
        if (!state.has("session") || state.optString("phase") != "ready") false
        else stores.hasPendingLocal(state.optJSONObject("collections") ?: JSONObject(), selected())
    }
    suspend fun pair(server: String, token: String) = operation {
        pairSession(server, token)
    }
    suspend fun pairQr(contents: String) = operation {
        val pairing = SyncPairing.parse(contents)
        try { pairSession(pairing.server, pairing.token) }
        catch (error: SyncException) {
            if (error.status == 404) throw SyncException("pairing_expired")
            throw error
        }
    }
    private fun pairSession(server: String, token: String) {
        requireSync(!state.has("session"), "account_connected")
        val transport = transport(server)
        val capabilities = discover(transport)
        val session = transport.request("api/v1/pair/redeem", method = "POST", body = obj("pairing_token" to token,
            "device_name" to deviceName(), "device_type" to "mobile", "native_refresh" to true))
        installSession(transport, capabilities, session)
    }
    suspend fun beginLogin(server: String): String = operation {
        requireSync(!state.has("session"), "account_connected")
        val transport = transport(server)
        val capabilities = discover(transport)
        val login = capabilities.getJSONObject("authentication").objectOrNull("browser_login")
        requireSync(login?.optJSONArray("device_types")?.toString()?.contains("mobile") == true, "browser_login_unavailable")
        val verifier = base64url(randomBytes(32)); val nonce = base64url(randomBytes(32))
        state.put("pendingLogin", obj("server" to transport.server, "verifier" to verifier, "state" to nonce, "expires" to System.currentTimeMillis() + 600_000))
        save()
        transport.server + "auth/desktop?" + mapOf("state" to nonce, "code_challenge" to base64url(digest(verifier.toByteArray())),
            "redirect_uri" to CALLBACK, "device_type" to "mobile").entries.joinToString("&") { SyncTransport.encode(it.key) + "=" + SyncTransport.encode(it.value) }
    }
    suspend fun completeLogin(uri: Uri) = operation {
        requireSync(!state.has("session"), "account_connected")
        val pending = state.objectOrNull("pendingLogin") ?: throw SyncException("login_expired")
        requireSync(uri.scheme == "org.midorinext.android" && uri.authority == null && uri.path == "/midori-sync/callback" && uri.fragment == null &&
            uri.getQueryParameters("state").size == 1 && uri.getQueryParameter("state") == pending.getString("state") &&
            uri.getQueryParameters("code").size == 1 && uri.getQueryParameter("code")?.matches(Regex("[0-9a-f]{64}")) == true &&
            System.currentTimeMillis() <= pending.getLong("expires"), "invalid_login_callback")
        val transport = transport(pending.getString("server"))
        val capabilities = discover(transport)
        val session = transport.request("api/v1/auth/browser-token", method = "POST", body = obj("code" to uri.getQueryParameter("code"),
            "code_verifier" to pending.getString("verifier"), "device_name" to deviceName()))
        installSession(transport, capabilities, session)
    }
    private fun installSession(transport: SyncTransport, capabilities: JSONObject, session: JSONObject) {
        val issuer = capabilities.getJSONObject("authentication").getString("issuer")
        val identity = session.getJSONObject("identity")
        requireSync(identity.getString("issuer") == issuer && identity.getString("subject").length in 1..255 &&
            session.getJSONObject("device").getString("type") == "mobile" && session.getJSONObject("device").getString("id").matches(UUID_PATTERN) &&
            session.getString("token").matches(Regex("[A-Za-z0-9._~-]{1,4096}")) && session.getString("refresh_token").matches(Regex("mrf_[0-9a-f]{64}")), "invalid_account_identity")
        val account = transport.request("api/v1/account", token = session.getString("token"))
        requireSync(same(account.getJSONObject("identity"), identity) && account.getJSONObject("device").getString("id") == session.getJSONObject("device").getString("id"), "invalid_account_identity")
        state = obj("version" to 1, "server" to transport.server, "scope" to JSONArray().put(transport.server).put(issuer).put(identity.getString("subject")).toString(),
            "session" to session, "collections" to JSONObject(), "phase" to "connecting")
        save()
    }
    private fun request(transport: SyncTransport, path: String, method: String = "GET", body: JSONObject? = null, query: Map<String, String> = emptyMap()): JSONObject {
        val session = state.getJSONObject("session")
        val remaining = state.optLong("retry_at") - System.currentTimeMillis()
        if (remaining > 0) throw SyncException("rate_limited", 429, (remaining + 999) / 1000)
        state.objectOrNull("refresh")?.let { refresh(transport, it) }
        return try { transport.request(path, state.getJSONObject("session").getString("token"), method, body, query) }
        catch (error: SyncException) {
            if (error.status == 429) {
                state.put("retry_at", System.currentTimeMillis() + maxOf(error.retryAfter, 1) * 1000); save()
                throw error
            }
            if (error.status != 401) throw error
            val pending = obj("operation_id" to UUID.randomUUID().toString(), "refresh_token" to session.getString("refresh_token"))
            state.put("refresh", pending); save(); refresh(transport, pending)
            transport.request(path, state.getJSONObject("session").getString("token"), method, body, query)
        }
    }
    private fun refresh(transport: SyncTransport, pending: JSONObject) {
        val response = transport.request("api/v1/auth/refresh", method = "POST", body = pending)
        requireSync(same(response.getJSONObject("identity"), state.getJSONObject("session").getJSONObject("identity")) &&
            response.getJSONObject("device").getString("id") == state.getJSONObject("session").getJSONObject("device").getString("id"), "refresh_identity_changed")
        state.put("session", response); state.remove("refresh"); save()
    }
    suspend fun prepareKeys(): String = operation {
        val transport = transport(state.getString("server"))
        SyncKeys(state.getString("scope")).use { keys ->
            val (code, local) = keys.prepare(request(transport, "api/v1/crypto/state"))
            state.put("keys", local).put("phase", "backup_required"); save(); code
        }
    }
    suspend fun activateKeys() = operation {
        requireSync(state.getString("phase") == "backup_required", "recovery_backup_required")
        state.getJSONObject("keys").put("confirmed", true); save()
        val transport = transport(state.getString("server"))
        val pending = state.getJSONObject("keys").getJSONObject("activation")
        request(transport, "api/v1/crypto/activate", "POST", pending)
        state.put("phase", "ready"); save()
    }
    suspend fun recover(code: String) = operation {
        val transport = transport(state.getString("server"))
        val keys = SyncKeys(state.getString("scope"))
        try {
            val local = keys.restore(request(transport, "api/v1/crypto/state"), null, code)
            state.put("keys", local).put("phase", "ready"); save()
            activeKeys?.close(); activeKeys = keys
        } catch (error: Throwable) { keys.close(); throw error }
    }
    suspend fun select(name: String, enabled: Boolean) = operation {
        requireSync(name in SyncModels.schemas)
        state.put("selected", JSONArray((if (enabled) selected() + name else selected() - name).toList()))
        save()
    }
    suspend fun sync(reason: SyncReason = SyncReason.MANUAL) {
        requestedCount.incrementAndGet()
        org.midorinext.android.diagnostics.ScenarioMetrics.count(org.midorinext.android.diagnostics.ScenarioMetrics.Event.SYNC_REQUESTED)
        if (disconnecting) return
        if (!loaded) inspect()
        if (!ui.value.connected) return
        val selectedFlight = synchronized(flightLock) {
            val existing = flight?.takeIf { !it.task.isCompleted && it.epoch == accountEpoch.get() }
            if (existing != null) coalescedCount.incrementAndGet()
            val current = existing ?: run {
                val epoch = accountEpoch.get()
                val task = syncScope.async(start = CoroutineStart.LAZY) {
                    val metrics = SyncRunMetrics()
                    var cancelled = false
                    var failed = false
                    try { executeSync(epoch, metrics) }
                    catch (error: CancellationException) { cancelled = true; throw error }
                    catch (error: Exception) { failed = true; throw error }
                    finally {
                        val result = metrics.finish(cancelled, failed)
                        latestRun.value = result
                        if (cancelled) cancelledCount.incrementAndGet()
                        else if (result.requests > 0) {
                            if (failed) failedCount.incrementAndGet()
                            else if (result.changed) changedCount.incrementAndGet() else unchangedCount.incrementAndGet()
                        }
                        totalSent.addAndGet(result.sentBytes); totalReceived.addAndGet(result.receivedBytes); totalCpu.addAndGet(result.cpuMs)
                        if (org.midorinext.android.BuildConfig.DEBUG) android.util.Log.i("MidoriSyncMetrics", statisticsSnapshot().put("reason", reason.name).toString())
                    }
                }
                Flight(epoch, task).also { flight = it }
            }
            current.waiters++
            current.task.start()
            current
        }
        try { selectedFlight.task.await() }
        finally {
            synchronized(flightLock) {
                selectedFlight.waiters--
                if (selectedFlight.waiters == 0 && selectedFlight.task.isActive) selectedFlight.task.cancel()
            }
        }
    }

    suspend fun notificationTicket(): Pair<String, String> = operation(reportErrors = false) {
        requireSync(state.has("session") && state.optString("phase") == "ready", "auth_required")
        val transport = transport(state.getString("server"))
        val response = request(transport, "api/v1/sync/notifications/ticket", "POST")
        requireSync(response.optInt("version") == 1 && response.optString("ticket").matches(Regex("[0-9a-f]{64}")) &&
            runCatching { Instant.parse(response.getString("expires_at")).toEpochMilli() > System.currentTimeMillis() }.getOrDefault(false), "invalid_notification_ticket")
        transport.server to response.getString("ticket")
    }

    private suspend fun executeSync(epoch: Long, metrics: SyncRunMetrics): Unit = operation(metrics) {
            if (epoch != accountEpoch.get() || disconnecting) throw CancellationException("Account changed")
            if (!state.has("session")) return@operation
            val remaining = state.optLong("retry_at") - System.currentTimeMillis()
            if (remaining > 0) throw SyncException("rate_limited", 429, (remaining + 999) / 1000)
            org.midorinext.android.diagnostics.ScenarioMetrics.count(org.midorinext.android.diagnostics.ScenarioMetrics.Event.SYNC_EXECUTED)
            executedCount.incrementAndGet()
            val transport = transport(state.getString("server"))
            val capabilities = discover(transport)
            requireSync(capabilities.getJSONObject("authentication").getString("issuer") == state.getJSONObject("session").getJSONObject("identity").getString("issuer"), "identity_issuer_mismatch")
            val selected = selected()
            state.put("unsupported", JSONArray(selected.filter { !SyncModels.supported(capabilities, it) }))
            run {
                val keys = connectKeys(transport, capabilities) ?: return@operation
                state.put("phase", "ready"); save()
                val engine = SyncEngine(keys, stores, { path, method, body, query -> request(transport, path, method, body, query) }, metrics, ::save)
                val failures = mutableListOf<String>()
                for (name in selected.filter { SyncModels.supported(capabilities, it) }) {
                    val collections = state.getJSONObject("collections")
                    val collection = collections.objectOrNull(name) ?: JSONObject().also { collections.put(name, it) }
                    try { engine.sync(name, collection); collection.remove("error") }
                    catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        if (error is SyncException && error.status == 429) throw error
                        val code = (error as? SyncException)?.let { it.code + if (it.status == 0) "" else "_" + it.status } ?: "sync_unavailable"
                        failures.add("$name:$code"); collection.put("error", code)
                    }
                    save()
                }
                requireSync(failures.isEmpty(), failures.joinToString(","))
                state.put("lastSync", System.currentTimeMillis()); save()
            }
        }

    private fun connectKeys(transport: SyncTransport, capabilities: JSONObject): SyncKeys? {
        var remote = request(transport, "api/v1/crypto/state")
        val managed = capabilities.optJSONObject("crypto")?.optInt("account_recovery_version") == 1 && remote.has("account_recovery")
        var local = state.objectOrNull("keys")
        if (managed && remote.objects("keys").isEmpty() && !remote.getBoolean("migration_required")) {
            if (local == null) {
                local = SyncKeys(state.getString("scope")).use { it.prepareAccount(remote) }
                state.put("keys", local).put("phase", "connecting"); save()
            }
            if (local.has("activation")) {
                val activation = local.getJSONObject("activation")
                val next = copy(remote).put("keys", JSONArray().put(obj("key_id" to activation.getString("key_id"),
                    "encrypted_bundle" to activation.getString("encrypted_bundle")))).put("identity_key", activation.getJSONObject("identity_key"))
                if (!activation.has("account_recovery")) {
                    SyncKeys(state.getString("scope")).use { activation.put("account_recovery", it.accountCapsule(local, next)) }
                }
                local.put("confirmed", true); save()
            }
            if (local.optBoolean("confirmed") && local.has("activation")) {
                try { remote = request(transport, "api/v1/crypto/activate", "POST", local.getJSONObject("activation")) }
                catch (error: SyncException) {
                    if (error.code !in listOf("crypto_state_conflict", "native_keys_already_active")) throw error
                    remote = request(transport, "api/v1/crypto/state")
                }
            }
        }
        if (!managed) {
            val stored = local ?: throw SyncException("account_recovery_unsupported")
            if (state.optString("phase") == "backup_required" && !stored.optBoolean("confirmed")) {
                throw SyncException("account_recovery_unsupported")
            }
            if (remote.objects("keys").isEmpty() && stored.optBoolean("confirmed")) {
                remote = request(transport, "api/v1/crypto/activate", "POST", stored.getJSONObject("activation"))
            }
            return readyKeys(remote, stored)
        }
        val available = local?.objects("keys")?.map { it.getString("key_id") }?.toSet().orEmpty()
        val canOpen = local != null && remote.objects("keys").isNotEmpty() &&
            remote.objects("keys").all { it.getString("key_id") in available } &&
            local.getJSONObject("identity_key").getString("key_id") == remote.getJSONObject("identity_key").getString("key_id")
        if (!canOpen) {
            if (!remote.optBoolean("account_recovery")) {
                state.put("phase", "account_recovery_pending"); save()
                return null
            }
            val response = request(transport, "api/v1/crypto/account-recovery")
            requireSync(response.getString("epoch") == remote.getString("epoch"), "invalid_account_capsule")
            val restored = SyncKeys(state.getString("scope"))
            try {
                local = restored.restoreAccount(remote, response.getJSONObject("capsule"), local)
                state.put("keys", local); save()
                activeKeys?.close(); activeKeys = restored
            } catch (error: Throwable) { restored.close(); throw error }
        }
        val keys = readyKeys(remote, local!!)
        if (!remote.optBoolean("account_recovery")) {
            request(transport, "api/v1/crypto/account-recovery", "PUT", obj("crypto" to keys.context, "capsule" to keys.accountCapsule(local, remote)))
        }
        return keys
    }
    suspend fun disconnect() = withContext(NonCancellable + Dispatchers.IO) {
        disconnecting = true
        accountEpoch.incrementAndGet()
        mutable.update { it.copy(connected = false, busy = true) }
        try {
            network?.cancel()
            synchronized(flightLock) { flight?.task }?.cancelAndJoin()
            val revoke = mutex.withLock {
                load()
                val session = state.objectOrNull("session")
                val previous = session?.let { state.getString("server") to it.getString("refresh_token") }
                activeKeys?.close(); activeKeys = null
                state = obj("version" to 1); save(); report()
                previous
            }
            // Local disconnection is durable before the optional network revocation.
            revoke?.let { (server, refresh) ->
                try {
                    SyncTransport(server).request("api/v1/auth/refresh", method = "DELETE",
                        body = obj("refresh_token" to refresh), timeoutMs = 5_000)
                } catch (_: Exception) { }
            }
        } finally { disconnecting = false }
    }
    suspend fun resolve(name: String, id: String, keepLocal: Boolean) = operation {
        val collection = state.getJSONObject("collections").getJSONObject(name)
        val conflict = collection.getJSONObject("conflicts").getJSONObject(id)
        val record = conflict.getJSONObject("record")
        val transport = transport(state.getString("server"))
        run {
            val keys = readyKeys(request(transport, "api/v1/crypto/state"), state.getJSONObject("keys"))
            val remote = keys.open(name, collection.getString("generation"), record)
            remote?.let { SyncModels.validate(keys, name, id, it) }
            if (!keepLocal) {
                val local = stores.read(name, id, remote ?: conflict.objectOrNull("base"), collection, keys)
                stores.apply(name, id, remote, local, remote ?: conflict.objectOrNull("base"), collection, keys, ::save)
            }
            collection.getJSONObject("mirror").put(id, obj("revision" to record.getString("revision"), "value" to remote, "record" to record))
            collection.getJSONObject("conflicts").remove(id)
            if (keepLocal) stores.markPending(name, id, conflict.objectOrNull("local") ?: conflict.objectOrNull("base"), collection)
            save()
        }
    }
    suspend fun conflictSummaries(): List<SyncConflict> = operation {
        fun describe(name: String, value: JSONObject?): String? {
            if (value == null) return null
            return when (name) {
                "bookmarks", "history" -> listOf(value.optString("title"), value.optString("url")).filter { it.isNotEmpty() }.joinToString(" — ")
                "passwords" -> value.optString("origin") + " · " + value.optString("username")
                "credit-cards" -> value.optString("name") + " · " + value.optString("number").takeLast(4)
                "addresses" -> value.getJSONObject("fields").let { it.optString("name") + " · " + it.optString("address-level2") }
                "form-history" -> value.optString("fieldname") + " · " + value.optString("value")
                else -> ""
            }.take(200)
        }
        state.objectOrNull("collections")?.entries().orEmpty().flatMap { (name, value) ->
            (value as JSONObject).objectOrNull("conflicts")?.entries().orEmpty().map { (id, untyped) ->
                val conflict = untyped as JSONObject
                SyncConflict(name, id, describe(name, conflict.objectOrNull("local") ?: conflict.objectOrNull("base")),
                    describe(name, conflict.objectOrNull("remote")), conflict.has("remote") && conflict.has("record"))
            }
        }
    }
    private fun readyKeys(remote: JSONObject, local: JSONObject): SyncKeys {
        activeKeys?.let { keys ->
            keys.refresh(remote)
            local.put("revision", remote.getString("revision")); state.put("keys", local)
            return keys
        }
        val keys = SyncKeys(state.getString("scope"))
        try {
            state.put("keys", keys.restore(remote, local))
            activeKeys = keys
            return keys
        } catch (error: Throwable) { keys.close(); throw error }
    }
    private fun discover(transport: SyncTransport): JSONObject = transport.request("api/v1/capabilities").also {
        requireSync(it.optString("protocol") == "MSP" && it.optInt("changes_version") == 1 && it.optInt("operations_version") == 1 &&
            it.optInt("crypto_custody_version") == 1 && it.optJSONObject("client_recovery")?.optInt("registry_auth_version") == 2, "incompatible_server")
    }
    private fun deviceName() = "${Build.MANUFACTURER} ${Build.MODEL}".take(255)
    companion object { const val CALLBACK = "org.midorinext.android:/midori-sync/callback" }
}
