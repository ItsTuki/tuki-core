# TukiCore

Núcleo administrativo para servidores **Paper 26.3** (Java 25): **rangos con prefijos**, **chat con formato**, **moderación** y utilidades de staff. Funciona solo o sobre **LuckPerms**, y es compatible con **[TukiUHC](https://github.com/ItsTuki/tuki-uhc)**.

> Creado por **ItsTuki**.

## Instalación

1. Descarga `TukiCore-<versión>.jar` desde [Releases](../../releases) y cópialo a `plugins/`.
2. Opcional: LuckPerms, PlaceholderAPI y TukiUHC.
3. Reinicia. Se crean `config.yml`, `ranks.yml` y `lang/`.

## Rangos

Vienen 7 rangos de ejemplo en `ranks.yml`: owner, admin, mod, helper, mvp, vip y default. Cada rango tiene:
- nombre visible y peso;
- prefijo y sufijo, con `&a` o `&#FF8800`;
- color del chat y formato de chat propio (opcional);
- permisos (un `-` delante niega el permiso);
- rangos de los que hereda.

| Comando | |
|---|---|
| `/rank list` · `/rank info <rango>` · `/rank user <jugador>` | Ver rangos |
| `/rank set <jugador> <rango> [tiempo]` | Asignar (`30d`, `2h`, `perm`…). Los temporales expiran solos |
| `/rank create <rango> [peso]` · `/rank delete <rango>` | Crear y borrar |
| `/rank prefix <rango> <texto\|none>` · `suffix` · `display` · `weight` · `color` | Editar |
| `/rank perm <rango> add\|remove <permiso>` | Permisos |
| `/rank inherit <rango> add\|remove <rango>` | Herencia |

**Backend** (`ranks.backend`):
- `AUTO` (por defecto): usa LuckPerms si está instalado, y si no, el sistema propio (`players.yml`).
- Con LuckPerms, cada rango de `ranks.yml` es un grupo: se crean los que faltan, y `/rank` cambia el prefijo, el peso y los permisos del grupo. Lo que agregues tú en LuckPerms no se toca.

## Chat

- Formato por rango: `chat.format` o el `chat-format` del rango. Etiquetas: `<prefix>`, `<name>`, `<suffix>`, `<rank>`, `<message>` y `<papi:...>`. Al pasar el mouse sobre el nombre se ve el rango; con clic le escribes por privado.
- Colores `&` en el chat con `tukicore.chat.color`.
- **Menciones**: escribir el nombre de alguien lo resalta y le suena un aviso.
- **Anti-spam**: espera entre mensajes y bloqueo de mensajes repetidos. **Filtro** de palabras.
- **Staff chat**: `/sc`, o un mensaje que empiece con `$`.
- `/msg` y `/r` para privados; `/socialspy` para leerlos como staff.
- Mensajes de entrada y salida con el rango.
- Prefijo del rango en el tab.

## Moderación y utilidades

| Comando | Permiso |
|---|---|
| `/mute <jugador> [tiempo] [razón]` · `/unmute <jugador>` | `tukicore.mute` |
| `/mutechat` · `/clearchat` · `/broadcast <mensaje>` | `tukicore.mutechat` · `tukicore.clearchat` · `tukicore.broadcast` |
| `/staffchat` (`/sc`) · `/socialspy` | `tukicore.staffchat` · `tukicore.socialspy` |
| `/gm <modo> [jugador]` · `/fly [jugador]` | `tukicore.gamemode(.others)` · `tukicore.fly(.others)` |
| `/vanish` (`/v`) · `/v <jugador>` · `/v list` | `tukicore.vanish` · `tukicore.vanish.others` (ver: `tukicore.vanish.see`) |
| `/mods [jugador\|all]` (`/clientinfo`) | `tukicore.clientcheck` (avisos: `tukicore.clientcheck.alerts`) |
| `/msg` · `/r` | `tukicore.msg` (todos) |
| `/tukicore [reload\|help]` | `tukicore.reload` |

Permisos agrupados:
- `tukicore.admin` (OP): todo.
- `tukicore.staff`: moderación, vanish y detección de clientes.
- Bypass: `tukicore.chat.bypass`, `tukicore.mute.bypass`, `tukicore.mutechat.bypass` y `tukicore.clearchat.bypass`.

## Vanish y detección de clientes

- **`/vanish` (`/v`)**:
  - Quien no tenga `tukicore.vanish.see` deja de verte, también en el tab y en la lista de servidores.
  - Mientras estás en vanish: sin daño, sin recoger ítems, los mobs no te atacan y no activas placas de presión.
  - Al entrar o salir se muestra un mensaje falso de salida/entrada. Si sales del servidor en vanish, vuelves en vanish.
  - El chat normal queda bloqueado para que no te delates (empieza con `!` para hablar igual); el staff chat sí funciona.
  - Otros comandos: `/v <jugador>` y `/v list`.
- **Detección de clientes**:
  - Cuando alguien entra, el staff con `tukicore.clientcheck.alerts` recibe un aviso con el cliente: Vanilla, Fabric, Forge, NeoForge, Quilt, Lunar, Badlion, Feather o LabyMod. Al pasar el mouse por el aviso se ven los canales de mods.
  - `/mods <jugador>` (`/clientinfo`) o `/mods all` para revisarlo cuando quieras.
  - Marca como **sospechoso** a quien:
    - dice ser vanilla pero registra canales de Fabric o Forge (está ocultando la marca);
    - no envía marca;
    - coincide con la lista `client-check.suspicious` (wurst, meteor, liquidbounce, baritone, xray…).
  - Paper **no recibe la lista de mods** del cliente: los canales de red son la mejor pista disponible. Un cliente de hacks bien hecho puede disfrazarse de vanilla, así que es una ayuda para vigilar, no una prueba.

## Compatibilidad con TukiUHC

- TukiUHC usa los prefijos de TukiCore en el chat de la partida.
- En el lobby, el chat y el tab son de TukiCore. En partida manda TukiUHC: canales de equipo y nombres "???" con rotación de skins. TukiCore no pisa esos nombres.
- Los silencios, el mutechat y el anti-spam siguen funcionando durante la partida.
- Quien está en vanish no entra en la partida.
- Los rangos de ejemplo ya incluyen `tukiuhc.admin` (owner/admin) y `tukiuhc.staff` (mod).

## PlaceholderAPI

| Placeholder | Valor |
|---|---|
| `%tukicore_rank%`, `%tukicore_rank_display%` | Rango del jugador |
| `%tukicore_prefix%`, `%tukicore_suffix%` | Prefijo y sufijo |
| `%tukicore_weight%` | Peso del rango |
| `%tukicore_muted%`, `%tukicore_mute_left%` | Silencio del jugador |
| `%tukicore_vanished%`, `%tukicore_client%`, `%tukicore_client_brand%` | Vanish y cliente del jugador |
| `%tukicore_online%` (sin los que están en vanish), `%tukicore_vanished_count%`, `%tukicore_chat_muted%`, `%tukicore_ranks%`, `%tukicore_backend%` | Globales |

## API

```java
cl.itstuki.core.api.TukiCoreAPI.prefix(player);   // "&a[VIP] "
cl.itstuki.core.api.TukiCoreAPI.rank(player);     // "vip"
cl.itstuki.core.api.TukiCoreAPI.isMuted(uuid);
```

## Compilar

```bash
./gradlew build        # jar en build/libs/
```
