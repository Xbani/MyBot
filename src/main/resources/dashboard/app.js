const state = {
    sources: [],
    chunks: new Map(),
    headImages: new Map(),
    zoom: 1,
    center: { x: 0, z: 0 },
    selectedBotId: "",
    needsDraw: true
};

const $ = (id) => document.getElementById(id);
const mapCanvas = $("map-canvas");
const mapCtx = mapCanvas.getContext("2d");
const limitsCanvas = $("limits-canvas");
const limitsCtx = limitsCanvas.getContext("2d");

function resizeCanvas(canvas) {
    const rect = canvas.getBoundingClientRect();
    const ratio = window.devicePixelRatio || 1;
    const width = Math.max(1, Math.floor(rect.width * ratio));
    const height = Math.max(1, Math.floor(rect.height * ratio));
    if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
    }
}

function flattenSources(payload) {
    const sources = payload.sources || [];
    return sources.map((source) => ({
        id: source.id,
        name: source.name,
        status: source.status,
        error: source.error || "",
        data: source.state || null
    }));
}

function allBots() {
    return state.sources.flatMap((source) => (source.data?.bots || []).map((bot) => ({ ...bot, proxy: source.name })));
}

function allPlayers() {
    const merged = new Map();
    for (const source of state.sources) {
        for (const player of source.data?.players || []) {
            const key = observedKey(player);
            const previous = merged.get(key);
            const seenBy = normalizeSeenBy(player.seenBy).map((observer) => `${source.name}:${observer}`);
            if (!previous) {
                merged.set(key, { ...player, proxy: source.name, proxies: [source.name], seenBy });
                continue;
            }
            previous.proxies = [...new Set([...previous.proxies, source.name])];
            previous.seenBy = [...new Set([...previous.seenBy, ...seenBy])];
            if (new Date(player.updatedAt || 0) > new Date(previous.updatedAt || 0)) {
                merged.set(key, {
                    ...previous,
                    ...player,
                    proxy: previous.proxies.join(", "),
                    proxies: previous.proxies,
                    seenBy: previous.seenBy
                });
            }
        }
    }
    return [...merged.values()].map((player) => ({
        ...player,
        proxy: player.proxies?.join(", ") || player.proxy,
        seenCount: player.seenBy?.length || 0
    }));
}

function allEvents() {
    return state.sources.flatMap((source) => (source.data?.events || []).map((event) => ({ ...event, proxy: source.name })))
        .sort((a, b) => String(b.at).localeCompare(String(a.at)));
}

function updateChunkCache() {
    for (const source of state.sources) {
        const chunks = source.data?.map?.chunks || [];
        for (const chunk of chunks) {
            const key = `${source.id}:${chunk.chunkX}:${chunk.chunkZ}`;
            const previous = state.chunks.get(key);
            const seenBy = normalizeSeenBy(chunk.seenBy).map((observer) => `${source.name}:${observer}`);
            if (previous && Number(previous.updatedAt || 0) >= Number(chunk.updatedAt || 0)) {
                previous.seenBy = [...new Set([...(previous.seenBy || []), ...seenBy])];
                continue;
            }
            state.chunks.set(key, {
                ...chunk,
                proxy: source.name,
                seenBy,
                decodedColors: decodeChunkColors(chunk.colors),
                heights: Array.isArray(chunk.heights) ? chunk.heights : []
            });
        }
    }
}

function formatUptime(seconds) {
    seconds = Math.max(0, Number(seconds || 0));
    const h = String(Math.floor(seconds / 3600)).padStart(2, "0");
    const m = String(Math.floor((seconds % 3600) / 60)).padStart(2, "0");
    const s = String(Math.floor(seconds % 60)).padStart(2, "0");
    return `${h}:${m}:${s}`;
}

function fmtPos(position) {
    if (!position) return "--";
    return `${Math.round(position.x)}, ${Math.round(position.z)}`;
}

function observedKey(player) {
    if (player.uuid) return `uuid:${player.uuid}`;
    if (player.username) return `name:${String(player.username).toLowerCase()}`;
    return `entity:${player.entityId}`;
}

function normalizeSeenBy(value) {
    if (Array.isArray(value)) return value;
    if (value) return [value];
    return [];
}

function updateMetrics(payload) {
    const bots = allBots();
    const players = allPlayers();
    const online = state.sources.filter((source) => source.status === "online").length;
    const local = state.sources.find((source) => source.data)?.data;
    const transitions = state.sources.reduce((sum, source) => sum + Number(source.data?.proxy?.graphTransitions || 0), 0);
    $("source-count").textContent = `${state.sources.length} source${state.sources.length > 1 ? "s" : ""}`;
    $("bot-count").textContent = bots.length;
    $("player-count").textContent = players.length;
    $("chunk-count").textContent = state.chunks.size;
    $("uptime").textContent = formatUptime(local?.proxy?.uptimeSeconds);
    $("sync-status").textContent = online === state.sources.length ? "SYNC" : "DEGRADE";
    $("sync-status").className = online === state.sources.length ? "ok" : "warn";
    $("info-sources").textContent = state.sources.length;
    $("info-online").textContent = online;
    $("info-transitions").textContent = transitions;
    $("info-sync").textContent = new Date(payload.generatedAt || Date.now()).toLocaleTimeString();
}

function updateTables() {
    const search = $("bot-search").value.toLowerCase();
    $("bots-table").innerHTML = allBots()
        .filter((bot) => !search || bot.username.toLowerCase().includes(search) || bot.id.toLowerCase().includes(search))
        .slice(0, 12)
        .map((bot) => `<tr class="clickable" data-bot-id="${escapeHtml(bot.id)}" data-proxy="${escapeHtml(bot.proxy)}">
            <td>${escapeHtml(bot.username)}</td>
            <td>${escapeHtml(bot.proxy)}</td>
            <td class="${bot.health > 8 ? "good" : "warn"}">${Number(bot.health || 0).toFixed(1)}</td>
            <td>${fmtPos(bot.position)}</td>
            <td>${escapeHtml(bot.intent || "")}</td>
            <td class="${bot.connected ? "good" : "warn"}">${bot.connected ? "Actif" : "Off"}</td>
        </tr>`).join("");
    for (const row of $("bots-table").querySelectorAll("tr[data-bot-id]")) {
        row.addEventListener("click", () => openBotModal(row.dataset.botId, row.dataset.proxy));
    }
    $("players-table").innerHTML = allPlayers().slice(0, 12).map((player) => `<tr>
        <td>${escapeHtml(player.username || "unknown")}</td>
        <td>${escapeHtml(player.proxy)}</td>
        <td>${fmtPos(player.position)}</td>
        <td>${player.bot ? "Bot" : "Humain"} · ${player.seenCount || 1} vue(s)</td>
    </tr>`).join("");
    $("events-list").innerHTML = allEvents().slice(0, 10).map((event) => `<div class="event">
        <time>${new Date(event.at).toLocaleTimeString()}</time>
        <span>${escapeHtml(event.message || event.type)} <small>${escapeHtml(event.proxy)}</small></span>
    </div>`).join("");
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    }[char]));
}

function worldToScreen(x, z) {
    const scale = 2.2 * state.zoom;
    return {
        x: mapCanvas.width / 2 + (x - state.center.x) * scale,
        y: mapCanvas.height / 2 + (z - state.center.z) * scale
    };
}

function screenToWorld(x, y) {
    const scale = 2.2 * state.zoom;
    return {
        x: (x - mapCanvas.width / 2) / scale + state.center.x,
        z: (y - mapCanvas.height / 2) / scale + state.center.z
    };
}

function drawMap() {
    resizeCanvas(mapCanvas);
    mapCtx.clearRect(0, 0, mapCanvas.width, mapCanvas.height);
    mapCtx.fillStyle = "#09111a";
    mapCtx.fillRect(0, 0, mapCanvas.width, mapCanvas.height);
    drawGrid();
    drawLimits();
    for (const chunk of state.chunks.values()) {
        drawSurfaceChunk(chunk);
    }
    for (const player of allPlayers()) {
        drawEntity(player, player.bot ? "#8ef05b" : "#5bb8ff", 22);
    }
    for (const bot of allBots()) {
        drawEntity(bot, "#8ef05b", 24);
    }
    drawMiniLimits();
}

function updateTimeline() {
    const game = state.sources.find((source) => source.data?.game)?.data.game;
    const feastSeconds = Number(game?.feastSeconds ?? -1);
    const phase = String(game?.phase || "").toLowerCase();
    let marker = 10;
    if (phase.includes("inv")) marker = 35;
    else if (phase.includes("game")) marker = 62;
    else if (phase.includes("feast")) marker = 84;
    else if (feastSeconds >= 0) {
        const feastAt = 15 * 60;
        marker = 42 + Math.max(0, Math.min(1, (feastAt - feastSeconds) / feastAt)) * 42;
    }
    $("timeline-marker").style.left = `${marker}%`;
    $("timeline-status").textContent = feastSeconds >= 0
        ? `Phase: ${game?.phase || "Game"} - Feast dans ${feastSeconds}s`
        : `Phase: ${game?.phase || "inconnue"}`;
}

function drawSurfaceChunk(chunk) {
    const colors = chunk.decodedColors || [];
    const blockSize = Math.max(1, 2.2 * state.zoom);
    if (colors.length !== 256 || blockSize < 1.5) {
        const p = worldToScreen(chunk.chunkX * 16, chunk.chunkZ * 16);
        const size = 16 * 2.2 * state.zoom;
        mapCtx.fillStyle = "rgba(76, 150, 84, 0.38)";
        mapCtx.fillRect(p.x, p.y, Math.max(2, size - 1), Math.max(2, size - 1));
        return;
    }

    for (let localZ = 0; localZ < 16; localZ++) {
        for (let localX = 0; localX < 16; localX++) {
            const index = localZ * 16 + localX;
            const color = colors[index];
            if (!color) continue;
            const p = worldToScreen(chunk.chunkX * 16 + localX, chunk.chunkZ * 16 + localZ);
            mapCtx.fillStyle = color;
            mapCtx.fillRect(p.x, p.y, Math.ceil(blockSize), Math.ceil(blockSize));
        }
    }

    if (blockSize >= 4) {
        const p = worldToScreen(chunk.chunkX * 16, chunk.chunkZ * 16);
        const size = 16 * 2.2 * state.zoom;
        mapCtx.strokeStyle = "rgba(7, 14, 22, 0.28)";
        mapCtx.lineWidth = 1;
        mapCtx.strokeRect(p.x, p.y, size, size);
    }
}

function decodeChunkColors(encoded) {
    if (!encoded || encoded.length < 1536) return [];
    const colors = new Array(256);
    for (let i = 0; i < 256; i++) {
        const hex = encoded.slice(i * 6, i * 6 + 6);
        colors[i] = hex === "000000" ? "" : `#${hex}`;
    }
    return colors;
}

function drawGrid() {
    const step = 16 * 2.2 * state.zoom;
    if (step < 5) return;
    mapCtx.strokeStyle = "rgba(141, 156, 176, 0.12)";
    mapCtx.lineWidth = 1;
    const offsetX = ((mapCanvas.width / 2 - state.center.x * 2.2 * state.zoom) % step + step) % step;
    const offsetY = ((mapCanvas.height / 2 - state.center.z * 2.2 * state.zoom) % step + step) % step;
    for (let x = offsetX; x < mapCanvas.width; x += step) {
        mapCtx.beginPath(); mapCtx.moveTo(x, 0); mapCtx.lineTo(x, mapCanvas.height); mapCtx.stroke();
    }
    for (let y = offsetY; y < mapCanvas.height; y += step) {
        mapCtx.beginPath(); mapCtx.moveTo(0, y); mapCtx.lineTo(mapCanvas.width, y); mapCtx.stroke();
    }
}

function drawLimits() {
    const limits = state.sources.find((source) => source.data?.map?.limits)?.data.map.limits;
    if (!limits) return;
    drawLimitRect(limits.initialRadius, "#ff5b5b");
    drawLimitRect(limits.currentRadius, "#f5c84b");
    drawLimitRect(limits.finalRadius, "#d8e2f0");
}

function drawLimitRect(radius, color) {
    const a = worldToScreen(-radius, -radius);
    const b = worldToScreen(radius, radius);
    mapCtx.strokeStyle = color;
    mapCtx.lineWidth = 2;
    mapCtx.strokeRect(a.x, a.y, b.x - a.x, b.y - a.y);
}

function drawDot(position, color, radius) {
    if (!position) return;
    const p = worldToScreen(position.x, position.z);
    mapCtx.fillStyle = color;
    mapCtx.strokeStyle = "#081018";
    mapCtx.lineWidth = 2;
    mapCtx.beginPath();
    mapCtx.arc(p.x, p.y, radius * (window.devicePixelRatio || 1), 0, Math.PI * 2);
    mapCtx.fill();
    mapCtx.stroke();
}

function drawEntity(entity, color, size) {
    if (!entity.position) return;
    const p = worldToScreen(entity.position.x, entity.position.z);
    const ratio = window.devicePixelRatio || 1;
    const pxSize = size * ratio;
    const x = p.x - pxSize / 2;
    const y = p.y - pxSize / 2;
    const username = entity.username || entity.id || "unknown";
    const image = playerHead(username);

    mapCtx.save();
    mapCtx.shadowColor = "rgba(0, 0, 0, 0.55)";
    mapCtx.shadowBlur = 5 * ratio;
    mapCtx.fillStyle = "#081018";
    mapCtx.fillRect(x - 2 * ratio, y - 2 * ratio, pxSize + 4 * ratio, pxSize + 4 * ratio);
    mapCtx.shadowBlur = 0;

    if (image.complete && image.naturalWidth > 0) {
        mapCtx.drawImage(image, x, y, pxSize, pxSize);
    } else {
        mapCtx.fillStyle = color;
        mapCtx.fillRect(x, y, pxSize, pxSize);
        mapCtx.fillStyle = "#081018";
        mapCtx.fillRect(x + pxSize * 0.25, y + pxSize * 0.28, pxSize * 0.18, pxSize * 0.18);
        mapCtx.fillRect(x + pxSize * 0.58, y + pxSize * 0.28, pxSize * 0.18, pxSize * 0.18);
    }

    mapCtx.strokeStyle = color;
    mapCtx.lineWidth = 2 * ratio;
    mapCtx.strokeRect(x - 1 * ratio, y - 1 * ratio, pxSize + 2 * ratio, pxSize + 2 * ratio);

    drawNameplate(username, p.x, y - 6 * ratio, color, ratio);
    mapCtx.restore();
}

function drawNameplate(username, centerX, topY, color, ratio) {
    mapCtx.font = `${12 * ratio}px Consolas, monospace`;
    mapCtx.textAlign = "center";
    mapCtx.textBaseline = "bottom";
    const width = mapCtx.measureText(username).width + 12 * ratio;
    const height = 18 * ratio;
    mapCtx.fillStyle = "rgba(8, 14, 24, 0.82)";
    mapCtx.fillRect(centerX - width / 2, topY - height, width, height);
    mapCtx.strokeStyle = color;
    mapCtx.strokeRect(centerX - width / 2, topY - height, width, height);
    mapCtx.fillStyle = "#d8e2f0";
    mapCtx.fillText(username, centerX, topY - 4 * ratio);
}

function playerHead(username) {
    const clean = String(username || "Steve").replace(/[^a-zA-Z0-9_]/g, "") || "Steve";
    let image = state.headImages.get(clean);
    if (image) return image;
    image = new Image();
    image.crossOrigin = "anonymous";
    image.onload = () => { state.needsDraw = true; };
    image.onerror = () => { state.needsDraw = true; };
    image.src = `https://minotar.net/helm/${encodeURIComponent(clean)}/32.png`;
    state.headImages.set(clean, image);
    return image;
}

function openBotModal(botId, proxy) {
    const bot = allBots().find((candidate) => candidate.id === botId && candidate.proxy === proxy)
        || allBots().find((candidate) => candidate.id === botId);
    if (!bot) return;
    state.selectedBotId = botId;
    $("bot-modal-title").textContent = bot.username || bot.id;
    $("bot-modal-body").innerHTML = botDetailsHtml(bot);
    $("bot-modal").classList.remove("hidden");
}

function botDetailsHtml(bot) {
    const inventory = Array.isArray(bot.inventory) ? bot.inventory : [];
    const slots = Array.from({ length: 36 }, (_, index) => {
        const slotId = index + 9;
        const item = inventory.find((entry) => Number(entry.slot) === slotId);
        return `<div class="slot" title="${escapeHtml(item?.name || "")}">${item ? `${escapeHtml(item.name || item.id)}${item.amount > 1 ? " x" + item.amount : ""}` : ""}</div>`;
    }).join("");
    return `<div class="detail-grid">
        <section class="detail-block">
            <h3>Etat</h3>
            <dl>
                <dt>Vie</dt><dd>${Number(bot.health || 0).toFixed(1)} / 20</dd>
                <dt>Nourriture</dt><dd>${Number(bot.food || 0)} / 20</dd>
                <dt>Saturation</dt><dd>${Number(bot.saturation || 0).toFixed(1)}</dd>
                <dt>XP</dt><dd>Niveau ${Number(bot.xpLevel || 0)} (${Math.round(Number(bot.xpProgress || 0) * 100)}%)</dd>
            </dl>
        </section>
        <section class="detail-block">
            <h3>IA</h3>
            <dl>
                <dt>Lifecycle</dt><dd>${escapeHtml(bot.ai?.lifecycle || bot.lifecycle || "")}</dd>
                <dt>Intent</dt><dd>${escapeHtml(bot.ai?.intent || bot.intent || "")}</dd>
                <dt>Path</dt><dd>${Number(bot.ai?.pathLength ?? bot.pathLength ?? 0)} nodes</dd>
                <dt>Bloque</dt><dd>${bot.ai?.pathStuck || bot.pathStuck ? "Oui" : "Non"}</dd>
            </dl>
        </section>
        <section class="detail-block">
            <h3>Position</h3>
            <dl>
                <dt>Serveur</dt><dd>${escapeHtml(bot.server || "")}</dd>
                <dt>Position</dt><dd>${fmtPos(bot.position)}</dd>
                <dt>Vitesse</dt><dd>${fmtVec(bot.velocity)}</dd>
                <dt>Yaw/Pitch</dt><dd>${Math.round(Number(bot.yaw || 0))} / ${Math.round(Number(bot.pitch || 0))}</dd>
            </dl>
        </section>
        <section class="detail-block">
            <h3>Inventaire</h3>
            <div class="inventory-grid">${slots}</div>
        </section>
    </div>`;
}

function fmtVec(position) {
    if (!position) return "--";
    return `${Number(position.x || 0).toFixed(2)}, ${Number(position.y || 0).toFixed(2)}, ${Number(position.z || 0).toFixed(2)}`;
}

function drawMiniLimits() {
    resizeCanvas(limitsCanvas);
    limitsCtx.clearRect(0, 0, limitsCanvas.width, limitsCanvas.height);
    limitsCtx.fillStyle = "#0b1420";
    limitsCtx.fillRect(0, 0, limitsCanvas.width, limitsCanvas.height);
    const cx = limitsCanvas.width / 2;
    const cy = limitsCanvas.height / 2;
    const max = Math.min(limitsCanvas.width, limitsCanvas.height) * 0.38;
    [["#ff5b5b", 1], ["#f5c84b", 0.55], ["#d8e2f0", 0.22]].forEach(([color, ratio]) => {
        limitsCtx.strokeStyle = color;
        limitsCtx.lineWidth = 2;
        const r = max * ratio;
        limitsCtx.strokeRect(cx - r, cy - r, r * 2, r * 2);
    });
}

async function fetchState() {
    try {
        const response = await fetch("/api/state", { cache: "no-store" });
        const payload = await response.json();
        state.sources = flattenSources(payload);
        updateChunkCache();
        updateMetrics(payload);
        updateTables();
        updateTimeline();
        state.needsDraw = true;
    } catch (error) {
        $("sync-status").textContent = "OFFLINE";
        $("sync-status").className = "warn";
    }
}

function loop() {
    if (state.needsDraw) {
        state.needsDraw = false;
        drawMap();
    }
    requestAnimationFrame(loop);
}

$("zoom-in").addEventListener("click", () => { state.zoom = Math.min(8, state.zoom * 1.25); state.needsDraw = true; });
$("zoom-out").addEventListener("click", () => { state.zoom = Math.max(0.2, state.zoom / 1.25); state.needsDraw = true; });
$("center-map").addEventListener("click", () => {
    const bot = allBots()[0];
    if (bot?.position) state.center = { x: bot.position.x, z: bot.position.z };
    state.needsDraw = true;
});
mapCanvas.addEventListener("wheel", (event) => {
    event.preventDefault();
    const before = screenToWorld(event.offsetX * (window.devicePixelRatio || 1), event.offsetY * (window.devicePixelRatio || 1));
    state.zoom = Math.max(0.2, Math.min(12, state.zoom * (event.deltaY < 0 ? 1.12 : 1 / 1.12)));
    const after = screenToWorld(event.offsetX * (window.devicePixelRatio || 1), event.offsetY * (window.devicePixelRatio || 1));
    state.center.x += before.x - after.x;
    state.center.z += before.z - after.z;
    state.needsDraw = true;
}, { passive: false });
$("bot-search").addEventListener("input", updateTables);
$("bot-modal-close").addEventListener("click", () => $("bot-modal").classList.add("hidden"));
$("bot-modal").addEventListener("click", (event) => {
    if (event.target === $("bot-modal")) $("bot-modal").classList.add("hidden");
});
window.addEventListener("resize", () => { state.needsDraw = true; });

fetchState();
setInterval(fetchState, 500);
requestAnimationFrame(loop);
