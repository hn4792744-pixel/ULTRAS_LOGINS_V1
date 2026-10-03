# Ultras_login_v1

Calm, premium authentication for **Velocity** networks, with a small **Paper/Purpur bridge** for the backend servers.
Messages, menus and commands are fully available in **English** and **العربية**; every player has their own language.

> **Honest status.** The `common` module (hashing, sessions, flow decisions, signed bridge protocol, database access,
> text/small-caps) has 41 unit tests that passed in the development sandbox (against stand-ins for Hikari/SnakeYAML/JUnit; the
> SQLite repository test needs the real `sqlite-jdbc` and was not run there). The `proxy` (Velocity) and `bridge` (Paper) modules were **written
> against the public APIs but never compiled or started** in that sandbox, because the Velocity/Paper repositories were not reachable.
> Expect to fix a few compile errors on the first `./gradlew build`, and test every scenario in "Testing checklist" before going live.

---

## 1. What Ultras Login is

| Account kind | What happens |
|---|---|
| **Premium (Java)** | Verified by Mojang through Velocity's online-mode handshake. No `/register`, no `/login`, no auth world. |
| **Cracked (Java)** | Goes to the auth server/world, uses `/register` once, then `/login`. Sessions, IP binding and brute-force lockout apply. |
| **Bedrock** | Detected through the official **Floodgate API** (never by name). Authenticated automatically by default; optionally forced through the cracked flow. |

Design rules: the username is never proof of a Premium account, an IP alone is never proof of identity, Mojang being down never grants access (fail-safe), passwords/hashes/tokens are never logged, displayed or sent to backend servers.

Visual identity: `UC |` red-gradient prefix (regular text), `◦` secondary lines, `✓ ◇ → •` symbols, small caps for English, Arabic untouched.

## 2. Requirements

- **Velocity 3.4+** (built against 3.5.0-SNAPSHOT), Java 21+ on the proxy.
- Backend servers: **Paper/Purpur 26.2** (Java 25) for the bridge. Other versions need the `paperApi` dependency adjusted in `bridge/build.gradle.kts`.
- **Modern Velocity forwarding** (`player-info-forwarding-mode = "modern"`) on every backend.
- Optional: Geyser + Floodgate for Bedrock; MariaDB/MySQL for a shared database (SQLite is the default).

Build: `./gradlew build` (Gradle 9.2.1 wrapper settings are included; run `gradle wrapper` once if the jar is missing).
Outputs: `proxy/build/libs/Ultras_login_v1-proxy.jar` and `bridge/build/libs/Ultras_login_v1-bridge.jar`.

## 3. Velocity installation

1. Install Velocity and start it once.
2. Put `Ultras_login_v1-proxy.jar` into the proxy's `plugins/` folder and start the proxy.
3. The plugin creates `plugins/ultras_login/` with `config.yml`, `gui.yml`, `messages_en.yml`, `messages_ar.yml`, `ip-hash.key` (IP hashing secret) and `bridge-secret.key`.
4. Configure your servers in `velocity.toml` (an `auth` server for cracked players, your game servers) and set `locations.authentication` / `locations.spawn` in `config.yml`.

If the plugin fails to start, it fails **closed**: only Mojang-verified players are admitted.

## 4. Backend bridge installation

1. Put `Ultras_login_v1-bridge.jar` into `plugins/` of **every** backend (auth and game servers).
2. Start once, stop, then open `plugins/UltrasLoginBridge/config.yml`.
3. Copy the content of the proxy's `plugins/ultras_login/bridge-secret.key` into `secret:` (or set the same value in the proxy's `bridge.secret`).
4. Start the backend. Without a valid secret every player stays restricted (safe default).

The bridge only enforces restrictions, draws menus and teleports players inside its server. It never sees a password.
Every bridge message is HMAC-signed, time-limited and replay-protected; messages for another player are ignored.

## 5. Geyser setup

Install Geyser on the Velocity proxy, set `auth-type: floodgate` in Geyser's config and make sure the Floodgate key is shared (section 6). Bedrock players then join through Geyser like Java players.

## 6. Floodgate setup

Install Floodgate on the proxy (and on backends only if you use its features there). Share `key.pem` only between Floodgate instances; **never** publish it or copy it into this plugin. Ultras Login reads Floodgate only through its API, and runs normally without Floodgate installed (Bedrock detection is then off).

## 7. Database setup

`database.type` in `config.yml`:

- `SQLITE` (default): file `database.sqlite-file`, WAL mode. Good for a single proxy.
- `MARIADB` / `MYSQL`: set `host`, `port`, `database`, `username`, `password`, `pool-size`, `ssl-mode`. Use a dedicated DB user with only the rights it needs.

Tables `ul_accounts` and `ul_schema_version` are created and migrated automatically. All database access is asynchronous. Only Argon2id hashes, a hash of the session token and a keyed hash of the IP are stored.

## 8. Configuration

All comments in `config.yml` are in Arabic. Main sections: `database`, `password`, `sessions`, `security`, `premium`, `cracked`, `bedrock`, `locations`, `language`, `restrictions`, `commands`, `login-reminder`, `ui`, `sounds`, `rate-limit`, `bridge`, `prefix`, `colors`, `logging`.
`/ultraslogin reload` re-reads everything safely: an invalid value falls back to its default and is reported as a warning. When `file-version` changes, the old file is saved as `*.old-<time>`.
Menu layouts live in `gui.yml`; texts in `messages_en.yml` / `messages_ar.yml` (MiniMessage; `<raw>…</raw>` keeps text out of small caps).

## 9. Premium authentication

Velocity looks the name up at Mojang (cached, rate-limited) to decide the mode. A name that exists at Mojang is **forced through online mode**, so only the real owner can join with it. If Mojang cannot be reached, `premium.verification-failure.mode` decides: `KICK` (default, safe) or `CRACKED` (documented risk: a premium name could be taken over). A name already registered as cracked keeps working without an external request unless `registered-cracked-name-policy: FORCE_PREMIUM`.

## 10. Cracked authentication

`/register <password> <password>` once, then `/login <password>`. Password policy, Argon2id parameters, session length, IP binding and lockout are configurable. Duplicate logins with the same name are denied (`DENY_NEW`); Velocity does not allow two sessions with one UUID.

## 11. Bedrock authentication

`bedrock.enabled` allows Bedrock players. `bedrock.require-authentication: false` (default) authenticates Floodgate-verified players automatically; `true` puts them through the cracked flow. Prefixed Bedrock names are accounts of their own type, so they never collide with Java names.

## 12. Commands

| Command | Use |
|---|---|
| `/register <pw> <pw>` | create a cracked account |
| `/register setting` | personal settings menu (notifications, sounds, reminders, language) |
| `/login <pw>` | log in |
| `/un_register password <pw>` | remove your own account (only when logged in, always asks for the password) |
| `/un_register <player>` | admin removal |
| `/register_info <player>` | safe account info: type, UUID, language, dates, session, IP shown only as a short hash |
| `/language [en\|ar]` | change language |
| `/ultraslogin reload \| info \| unregister \| force-login \| force-logout \| session <player>` | administration |

No command ever shows a password or hash (`/list_register` is intentionally not implemented).

## 13. Permissions

| Node | Allows |
|---|---|
| `ultraslogin.admin` | everything below |
| `ultraslogin.admin.reload` | `/ultraslogin reload` |
| `ultraslogin.admin.info` | `/ultraslogin info`, `/register_info` |
| `ultraslogin.admin.unregister` | `/ultraslogin unregister`, `/un_register <player>` |
| `ultraslogin.admin.session` | `session`, `force-login`, `force-logout` |

Players need no permission for `/login`, `/register`, `/language`, `/un_register password`.

## 14. Security recommendations

- Backends **must not be reachable from the internet**: firewall them so only the proxy's IP can connect, and use modern forwarding with a strong `forwarding.secret`. Otherwise anyone can bypass the proxy.
- Keep `bridge-secret.key`, `secret.key`, `key.pem` and database credentials private; never commit them.
- Keep `premium.verification-failure.mode: KICK` (the default).
- Use `bind-to-ip` for sessions; keep the session duration short.
- Use a distinct, restricted DB user; enable SSL for remote databases.
- Logs (`logs/security-<date>.log`) never contain passwords, hashes or tokens.
- Where a requested feature conflicts with security, the secure alternative is implemented (for example, `/register_info` instead of showing passwords; duplicate sessions denied).

Known limits: jump blocking is approximate (the server cannot see the jump key); the plugin requires the bridge on every backend a not-yet-authenticated player can reach.

## 15. Troubleshooting

| Symptom | Check |
|---|---|
| "not ready for secure login" kick | bridge missing on that backend or `secret` differs from `bridge-secret.key` |
| Players not restricted | bridge not installed; proxy log says "has no Ultras Login bridge" |
| Menu does not open | bridge secret/protocol mismatch; look for "Rejected a bridge message" in the backend log |
| Bedrock players rejected | Floodgate missing, or `bedrock.enabled: false` |
| Premium players kicked | Mojang lookup failing; see `premium.error-cache-seconds` and the security log |
| Teleport does nothing | `locations.*.world` does not exist on that backend |

## Installation flow

1 Install Velocity · 2 Install the proxy jar · 3 Install the bridge on backends · 4 Modern forwarding · 5 Backend firewall · 6 Geyser (if Bedrock) · 7 Floodgate (if Bedrock auto-auth) · 8 Edit `config.yml` · 9 Start the network · 10 Test Premium · 11 Test Cracked · 12 Test Bedrock.

## Testing checklist (not yet done on real servers)

Premium join · cracked first join (language menu, `/register`, restrictions, teleport) · rejoin within session · IP change · wrong passwords until lockout · Mojang outage · Bedrock auto/forced · duplicate login · `/ultraslogin reload` · English and Arabic output of every message.

## بالعربي باختصار

1. ركّب `Ultras_login_v1-proxy.jar` على Velocity و`Ultras_login_v1-bridge.jar` على كل سيرفر خلفي.
2. انسخ محتوى `bridge-secret.key` من البروكسي إلى `secret:` في إعدادات الـ Bridge.
3. فعّل Modern Forwarding وأغلق السيرفرات الخلفية بالـ firewall بحيث يدخل البروكسي فقط.
4. اللاعب الأصلي يدخل مباشرة، والكراكد يسجّل بـ `/register` ثم `/login`، وبيدروك عبر Floodgate.
5. اللغة (العربية/الإنجليزية) تُختار من قائمة إجبارية عند أول دخول ويمكن تغييرها بـ `/language`.
6. تنبيه صريح: وحدتا proxy و bridge لم تُترجما (compile) في بيئة التطوير لعدم توفر مستودعات Velocity/Paper، فجرّب البناء والاختبار قبل الاستخدام.
