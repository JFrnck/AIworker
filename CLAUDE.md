# CLAUDE.md — bot de Minecraft controlado por LLM (mod Fabric)

## Contexto del proyecto
Mod de Fabric para Minecraft Java Edition 26.2, escrito en Java. Implementa una
entidad NPC cuyo comportamiento lo decide un LLM externo vía API. Ver `plan.md`
para el roadmap completo de fases.

Stack: Java, Fabric Loader 0.19.3, Fabric API, Loom 1.17, Gradle 9.5.1.

## Guardrails obligatorios

### Threading
- Nunca mutar el mundo, entidades, inventarios o bloques fuera del hilo
  principal del server. Cualquier callback de una llamada HTTP async debe
  reintegrarse con `server.execute(() -> { ... })` antes de tocar el mundo.
- Nunca usar llamadas HTTP síncronas (`.send()` bloqueante) en código que corre
  en el tick loop o en un handler de evento del server. Siempre `sendAsync()`.

### Salida del LLM
- Tratar toda respuesta del LLM como no confiable: validar tipo de acción y
  rango de parámetros (coordenadas dentro de distancia razonable, targets
  existentes, etc.) antes de ejecutar nada.
- Si el JSON no parsea o no matchea el schema esperado: loguear y hacer que el
  bot no haga nada ese ciclo. Nunca asumir un valor por defecto silencioso que
  pueda causar una acción no intencional.
- Whitelist de acciones, no blacklist — no ejecutar nada que el schema no
  contemple explícitamente.

### Secretos
- La API key del LLM nunca se hardcodea ni se commitea. Se lee de variable de
  entorno o de un archivo de config fuera del control de versiones (agregarlo
  a `.gitignore`).
- No loguear la API key ni headers de autenticación crudos, ni siquiera en
  modo debug.

### Alcance de cambios
- No modificar `gradle.properties` (versiones de Minecraft/Loader/API/Loom)
  sin confirmar antes — afecta el toolchain entero del proyecto.
- No correr `runServer` / `runClient` de forma prolongada sin avisar: estos
  comandos pueden quedar colgados esperando input y bloquear la sesión.
- No tocar la config ni el estado del server de Oracle Cloud (producción)
  desde este repo sin confirmación explícita — este repo es solo el código
  del mod.

### Estilo y convenciones
- Mod id único en minúsculas; paquete base `com.<usuario>.llmbot` (ajustar al
  definitivo). Registro de contenido vía `Registry` / `FabricEntityTypeBuilder`,
  nunca por reflection manual.
- Mixins solo cuando no exista un hook equivalente en Fabric API — preferir
  siempre la API pública sobre mixins.
- Separar en paquetes distintos el "cuerpo" (ejecución de acciones sobre la
  entidad) del "cerebro" (decision loop / cliente LLM). No mezclar lógica de
  red con lógica de entidad en la misma clase.

### Progreso
- No saltar a la fase siguiente de `plan.md` sin que la fase actual compile y,
  cuando aplique, se haya probado en un server local.
- Marcar los checkboxes de `plan.md` a medida que se completan las tareas.
