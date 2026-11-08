# Changelog

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
