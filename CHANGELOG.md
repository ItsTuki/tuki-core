# Changelog

## 1.3.0 — 2026-11-15

Para las dos ediciones (completa y Legacy).

### Nuevo
- **Integración con Lunar Client (Apollo)**, sin instalar el plugin de Apollo:
  - **Mods de staff nativos de Lunar** con `/lunar staff [on|off]` (`tukicore.lunar.staff`): **XRay**, **Nametags** a través de paredes y **Bunny hop**. Se eligen en `lunar.staff-mods.mods`; por defecto vienen XRay y Nametags.
  - Se activan solos al entrar en vanish y se apagan al salir (`lunar.staff-mods.with-vanish`), o siempre al entrar al servidor (`auto-enable`).
  - `/lunar` (`/apollo`): quién está usando Lunar Client.
  - `/lunar staff <jugador> [on|off]` (`tukicore.lunar.others`): activarlos a otro miembro del staff.
  - Notificación nativa de Lunar al staff cuando entra un cliente sospechoso (`lunar.notifications`).

## 1.2.0 — 2026-11-08

Para las dos ediciones (completa y Legacy).

### Nuevo
- **`/invsee <jugador> [ender]`** (`/inv`): abre el inventario o el cofre de ender de un jugador. Con `tukicore.invsee.edit` los cambios son en vivo; sin él, solo se mira. `tukicore.invsee.exempt` protege tu inventario de quien no lo tenga.

### Arreglos
- `/broadcast` (`/anuncio`, `/bc`) acepta códigos `&` y `&#RRGGBB` además de MiniMessage: `&l&6Hola` sale en negrita y dorado.
- El staff chat y los mensajes privados (con `tukicore.chat.color`) también aceptan códigos `&`.

## 1.1.0 Legacy — 2026-11-08

### Nuevo
- **Edición Legacy** (`TukiCore-Legacy-1.1.0.jar`): funciona en Spigot, PaperSpigot y Paper desde **1.8.8 hasta 26.3**, con los mismos rangos, comandos, moderación, vanish, detección de clientes y modo dev que la edición completa.
- Compatible con **TukiUHC Legacy** (rangos en chat, tab y sobre la cabeza).
- El chat usa el formato clásico de Bukkit (sin hover ni clic en el nombre); la marca del cliente se lee del canal de la marca en cada versión.

## 1.1.0 — 2026-11-08

### Nuevo
- **Modo dev** (`/dev`), solo para las cuentas premium que pongas en `dev-mode.players` (por UUID de Mojang). Viene desactivado, no lo trae el OP y nadie más puede activarlo. Al activarlo:
  - habilita `/uhcdev` de TukiUHC;
  - muestra TPS, MSPT y RAM en la barra;
  - trae los avisos y errores de la consola al chat;
  - agrega `/dev info` y `/dev perm <jugador> <permiso>`.
- **Prefijo del rango sobre la cabeza** de los jugadores, con el tab ordenado por peso del rango (`tab.nametag-prefix`). Con TukiUHC lo pone TukiUHC en sus propios scoreboards.

### Correcciones
- Si al archivo `lang/` del servidor le falta un texto nuevo, se usa el del jar en vez de mostrar la clave.

## 1.0.0 — 2026-11-07

Primera versión.

- Rangos con prefijo, sufijo, peso, color de chat, formato propio, permisos y herencia (`ranks.yml` + `/rank`).
- Rangos temporales que expiran solos.
- Backend propio o LuckPerms (crea los grupos que faltan y sincroniza los cambios de `/rank`).
- Chat con formato por rango, hover y clic en el nombre, colores por permiso, menciones con sonido, anti-spam, mensajes repetidos y filtro.
- Staff chat, `/msg`, `/r`, `/socialspy`, `/broadcast`, `/clearchat`, `/mutechat`, `/mute` con tiempo y razón, `/unmute`.
- `/vanish` (`/v`): invisible para los jugadores, el tab y la lista de servidores, sin daño, sin recoger ítems, con mensajes falsos de entrada/salida, y se mantiene al reconectar.
- Detección de clientes al entrar: Vanilla, Fabric, Forge, Lunar…, los canales de mods y alertas de sospechosos (marca oculta o nombres de clientes de hacks). `/mods <jugador>`.
- `/gm`, `/fly`, mensajes de entrada y salida con rango y prefijo en el tab.
- PlaceholderAPI (`%tukicore_...%`, `<papi:...>` en los textos) y API pública `TukiCoreAPI`.
- Compatible con TukiUHC.
- Español e inglés.
