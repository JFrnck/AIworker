# Plan: bot de Minecraft controlado por LLM (mod Fabric)

## Objetivo
NPC dentro de un server de Minecraft Java Edition (Fabric, 26.2) cuyo comportamiento
lo decide un LLM vía API. Mod nativo en Java, sin depender de un cliente externo
tipo Mineflayer.

## Arquitectura
Dos loops separados, a distinto ritmo:

- **Cuerpo** (tick loop, 20/s): ejecuta la acción actual en curso (mover un paso,
  seguir minando el bloque, etc). No sabe nada de IA.
- **Cerebro** (decision loop, cada 1-3s): recolecta el estado del mundo, llama al
  LLM de forma asíncrona, y cuando llega la respuesta la reintegra al hilo
  principal del server.

Regla dura: toda mutación de mundo/entidades pasa por `server.execute()`. Nunca se
bloquea el tick loop esperando una respuesta HTTP.

## Toolchain (Minecraft 26.2)
- Fabric Loom 1.17
- Gradle 9.5.1
- Fabric Loader 0.19.3 (stable)
- Java 25+ para el JVM de Gradle
- IntelliJ IDEA 2025.3+ (recomendado, necesario para que los mixins funcionen)

## Fases

### 1. Setup del proyecto
- [x] Generar el mod desde el template oficial (fabricmc.net/develop) — armado a mano
      (settings.gradle/build.gradle/gradle.properties) verificado línea por línea
      contra la rama `26.2` real del repo `FabricMC/fabric-example-mod`, sin
      descargar el zip del generador.
- [x] Configurar `gradle.properties` con versiones de Minecraft/Loader/API para
      26.2 — `minecraft_version=26.2`, `loader_version=0.19.3`,
      `loom_version=1.17.19`, `fabric_api_version=0.157.0+26.2`. Sin mappings:
      Minecraft dejó de estar ofuscado desde 26.1 (Mojang, oct. 2025), así que
      no hace falta Yarn ni mappings oficiales. Ver STATUS.md § Tree decision.
- [x] Verificar build limpio (`./gradlew clean build`) — `BUILD SUCCESSFUL`,
      **corrido contra 26.2 real**. Genera `build/libs/aiworker-0.1.0.jar`.
- [x] `runServer` levanta — probado con `timeout`/`kill` manual acotado (no
      hay `timeout` en macOS por defecto). Log muestra `[aiworker]
      Inicializando` y luego `Done (1.704s)! For help, type "help"`. Server
      bajado a mano con SIGTERM al terminar la prueba, sin procesos huérfanos.
- [ ] `runClient` — no probado: abre una ventana gráfica de Minecraft, no
      viable en este entorno headless de terminal.
- [x] Definir el mod id (namespace) y estructura de paquetes — mod id `aiworker`,
      paquete `com.jeanfranck.aiworker`, con subpaquetes `body/` y `brain/`
      ya creados (vacíos) para separar ejecución de acciones vs. decision loop.

### 2. Entidad base del bot
- [x] Clase `AIWorkerEntity extends PathfinderMob` — el nombre real de la
      clase en 26.2 es `PathfinderMob`, no `PathAwareEntity` (eso era el
      nombre de Yarn; ya no aplica, ver STATUS.md Fase 1). Renombrado de
      `LlmBotEntity` a `AIWorkerEntity` para no mezclar con la marca del mod.
- [x] Registrar en el `EntityType` registry — con `FabricEntityType.Builder`
      (la API vieja `FabricEntityTypeBuilder` ya no existe en esta versión
      de fabric-api).
- [x] Quitar el `GoalSelector` vanilla por defecto — no hizo falta código:
      al extender `PathfinderMob` directamente y no sobreescribir
      `registerGoals()`, el goal selector queda vacío por diseño.
- [x] Comando de spawn de prueba — `/aiworker spawn` (renombrado de
      `/llmbot spawn`). Probado desde la consola del server sin excepciones.

### 3. Ejecutor de acciones ("el cuerpo")
- [x] `moveTo(BlockPos)` — vía `Mob.getNavigation()` (clase real: `PathNavigation`,
      no existe "NavigationController"). Probado headless: el bot llegó al
      destino exacto.
- [x] `mineBlock(BlockPos)` con progreso de rotura por tick — usa
      `BlockState.getDestroySpeed()` para estimar ticks (aproximado, sin
      sistema de herramientas todavía) y `Level.destroyBlockProgress()` para
      la animación. Probado: bloque de piedra destruido en ~1.5s.
- [x] `attack(Entity target)` — se acerca por navegación si esta lejos,
      pega con `Mob.doHurtTarget()` con cooldown de 20 ticks. Probado: mató
      un chancho de 10 HP en menos de 8 segundos.
- [x] `say(String msg)` al chat del server — `PlayerList.broadcastSystemMessage()`.
      Probado, aparece como `<AIWorker> mensaje`.
- [x] Cola de una acción "en curso" por bot, consumida en cada tick — vía
      `Mob.customServerAiStep()`, el hook oficial para IA custom sin goals.

### 4. Recolector de estado del mundo + memoria de corto plazo
- [x] Snapshot de posición, salud, inventario — **hambre e inventario
      ajustados a la realidad**: `AIWorkerEntity` es un `Mob`, no un
      `Player`, no tiene hambre (`FoodData` es exclusivo de jugadores) ni
      inventario propio todavía (los `Mob` solo tienen slots de equipo). Se
      reporta salud + item en mano; el inventario real se construye en la
      Fase 7 (donde además hace falta para `PlaceBlock`/`Craft`).
- [x] Bloques cercanos en un radio configurable — `BlockPos.betweenClosed`
      (no existe `BlockPos.iterate` en esta versión). Dump crudo en radio
      chico (2) + resumen categorizado (*points of interest*: cultivos
      maduros, árboles, cofres, hornos) en radio grande (12), acotado a 5
      por categoría para no explotar el tamaño del JSON.
- [x] Entidades cercanas — `Level.getEntitiesOfClass`, radio 16, tope 10,
      con flag `hostile` (interfaz `Enemy`).
- [x] Últimos mensajes de chat — `ServerMessageEvents.CHAT_MESSAGE`
      (Fabric API), buffer global filtrado por cercanía al bot en cada
      snapshot ("dirigido al bot" = proximidad, el LLM decide si le compete).
- [x] Serialización a JSON compacto — vía Gson (ya viene con Minecraft, no
      hizo falta agregar dependencia).
- [x] **Memoria de corto plazo** (agregado durante el diseño, no estaba en
      el plan original): historial acotado de (acción→resultado) por bot +
      campo de "plan" en texto libre que el LLM va a reescribir en la
      Fase 6. Vive en RAM en `AIWorkerEntity`, no sobrevive un reinicio del
      server todavía (pendiente de NBT si hace falta).
- [x] Comando de prueba `/aiworker snapshot` para verificar el JSON sin
      esperar a que exista el cliente LLM (Fase 6).

### 5. Memoria de largo plazo (vectorial)
- [ ] SQLite local (`sqlite-jdbc`) — tabla `memories` (texto + embedding BLOB + tags)
- [ ] Modelo de embeddings local `granite-embedding-97m-multilingual-r2`
      (ONNX, ~98MB, Apache 2.0, español soportado), empaquetado dentro del
      jar del mod (no se descarga en runtime — ver STATUS.md, decisión
      tomada por el riesgo de depender de un link externo en producción)
- [ ] ONNX Runtime Java + DJL HuggingFace Tokenizers para correr el modelo
- [ ] Búsqueda por similitud coseno a mano en Java (fuerza bruta, alcanza
      para la escala esperada — sin índice ANN ni extensión nativa de SQLite)
- [ ] Acción nueva `Remember(text)` — el LLM decide qué persistir
- [ ] Búsqueda automática top-K inyectada en cada snapshot (sin acción
      explícita de "recall")
- [ ] Configurar Git LFS para el archivo del modelo antes de commitearlo
      (GitHub bloquea archivos >100MB en git normal)

### 6. Cliente LLM + decision loop (event-driven)
- [ ] `HttpClient` (`java.net.http`) con `sendAsync()`
- [ ] Prompt de sistema que fuerza salida JSON estructurada (schema de acción,
      incluye el campo de "plan" que el LLM reescribe)
- [ ] Parseo y validación de la respuesta antes de aplicar nada
- [ ] Reintegración al hilo principal con `server.execute()`
- [ ] Manejo de timeouts / fallos de red sin crashear el bot
- [ ] Loop dispara al completar la acción anterior / timeout de acción
      trabada / evento interruptor (chat, ataque) — no polling ciego cada
      N segundos como se pensó originalmente
- [ ] Diseño pensado para escalar a N bots en paralelo

### 7. Acciones ampliadas — lote 1 (inventario básico)
- [ ] Inventario real en `AIWorkerEntity` (hoy no existe, solo slots de equipo)
- [ ] `PlaceBlock(BlockPos, item)`
- [ ] `Equip(item)`

### 8. Crafting y cocina
- [ ] `Craft(item, count)` — grid 2x2 o mesa de crafteo
- [ ] `Smelt(furnacePos, item, fuel)` — horno, acción async (tarda varios ticks)

### 9. Agricultura
- [ ] `Till(pos)` — azada en tierra
- [ ] `Plant(pos, seed)`
- [ ] `Harvest(pos)`

### 10. Pruebas e iteración
- [ ] Deploy en el server de Oracle Cloud
- [ ] Logging estructurado de cada decisión (estado visto → acción tomada)
- [ ] Ajuste iterativo del prompt según comportamiento observado
- [ ] Definir métricas mínimas de "funciona bien" (no se traba, no repite loops
      sin sentido, reacciona al chat)

## Riesgos / cosas a vigilar
- Costo y latencia de la API del LLM si se llama muy seguido — ajustar la
  frecuencia del decision loop según necesidad real.
- Concurrencia: cualquier mutación de mundo fuera del hilo principal corrompe el
  estado del server.
- La salida del LLM no es confiable por definición — nunca ejecutar una acción
  sin validar tipo y parámetros primero.
- Un error de red no debe detener el tick loop del bot ni del server.

## Definición de "listo" (MVP)
Un bot camina, mina un bloque, y responde a un mensaje de chat dirigido a él —
todo decidido por el LLM — corriendo estable por al menos 30 minutos sin
intervención manual.
