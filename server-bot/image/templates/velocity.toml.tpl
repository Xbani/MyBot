# Config version. Do not change this
config-version = "2.7"

bind = "0.0.0.0:${SERVER_PORT}"
motd = "${MOTD}"
show-max-players = 1000
online-mode = ${ONLINE_MODE}
force-key-authentication = ${FORCE_KEY_AUTH}
prevent-client-proxy-connections = ${PREVENT_CLIENT_PROXY_CONNECTIONS}
player-info-forwarding-mode = "modern"
forwarding-secret-file = "hg-pvp-ipforwarding.secret"
announce-forge = ${ANNOUNCE_FORGE}
kick-existing-players = ${KICK_EXISTING_PLAYERS}
ping-passthrough = "${PING_PASSTHROUGH}"
sample-players-in-ping = ${SAMPLE_PLAYERS}
enable-player-address-logging = ${ENABLE_PLAYER_ADDRESS_LOGGING}

[servers]
lobby0 = "${SERVER_IP_LOBBY0}"
lobby1 = "${SERVER_IP_LOBBY1}"
lobby2 = "${SERVER_IP_LOBBY2}"
lobby3 = "${SERVER_IP_LOBBY3}"
lobby4 = "${SERVER_IP_LOBBY4}"
hg0 = "${SERVER_IP_HG0}"
hg1 = "${SERVER_IP_HG1}"
hg2 = "${SERVER_IP_HG2}"
hg3 = "${SERVER_IP_HG3}"
hg4 = "${SERVER_IP_HG4}"

try = ["hg2"]

[forced-hosts]

[advanced]
compression-threshold = ${COMPRESSION_THRESHOLD}
compression-level = ${COMPRESSION_LEVEL}
login-ratelimit = ${LOGIN_RATELIMIT}
connection-timeout = ${CONNECTION_TIMEOUT}
read-timeout = ${READ_TIMEOUT}
haproxy-protocol = ${HAPROXY_PROTOCOL}
tcp-fast-open = ${TCP_FAST_OPEN}
bungee-plugin-message-channel = ${BUNGEE_PLUGIN_MESSAGE_CHANNEL}
show-ping-requests = false
failover-on-unexpected-server-disconnect = ${FAILOVER_ON_UNEXPECTED}
announce-proxy-commands = true
log-command-executions = ${LOG_COMMAND_EXECUTIONS}
log-player-connections = ${LOG_PLAYER_CONNECTIONS}
accepts-transfers = ${ACCEPTS_TRANSFERS}
enable-reuse-port = ${ENABLE_REUSE_PORT}
command-rate-limit = ${COMMAND_RATE_LIMIT}
forward-commands-if-rate-limited = ${FORWARD_COMMANDS_IF_RATE_LIMITED}
kick-after-rate-limited-commands = ${KICK_AFTER_RATE_LIMITED_COMMANDS}
tab-complete-rate-limit = ${TAB_COMPLETE_RATE_LIMIT}
kick-after-rate-limited-tab-completes = ${KICK_AFTER_RATE_LIMITED_TAB_COMPLETES}

[query]
enabled = ${QUERY_ENABLED}
port = ${QUERY_PORT}
map = "Velocity"
show-plugins = ${QUERY_SHOW_PLUGINS}
