# STATUS — bot de Minecraft controlado por LLM (mod Fabric)

Documento vivo de seguimiento. Se actualiza a medida que se completan pasos de
`plan.md`. No reemplaza los checkboxes de `plan.md`, los complementa con el
detalle de *qué* se hizo y *por qué*.

---

## 1. Walkthrough

Bitácora cronológica de pasos, referenciados a la fase/sub-paso de `plan.md`
(ej: `Fase 1.1`, `Fase 1.2`, `Fase 2.1`...).

| Fecha | Fase | Paso | Detalle |
|-------|------|------|---------|
| 2026-08-16 | — | — | Revisión inicial de `plan.md` y `CLAUDE.md`. Se crea este archivo `STATUS.md` antes de arrancar Fase 1. |
| 2026-08-16 | Fase 1.1 | Toolchain | No había Gradle instalado en el sistema. Se instaló vía `brew install gradle` (trae Gradle 9.7.0) para poder generar el wrapper pineado a la versión del proyecto. |
| 2026-08-16 | Fase 1.1 | Naming | Se definió mod id `aiworker` (display name "AI Worker") y paquete base `com.jeanfranck.aiworker`, con subpaquetes `body/` (ejecutor de acciones) y `brain/` (decision loop / cliente LLM) separados desde el inicio, según guardrail de estilo de CLAUDE.md. |
| 2026-08-16 | Fase 1.1/1.2 | Scaffolding | Se armaron a mano `settings.gradle`, `build.gradle`, `gradle.properties`, `fabric.mod.json`, clase principal `AIWorkerMod` (main) y `AIWorkerModClient` (client), y `.gitignore`, siguiendo la estructura estándar de Fabric Loom (split source sets main/client). |
| 2026-08-16 | Fase 1.2 | Wrapper | `gradle wrapper --gradle-version 9.5.1` generado con el Gradle de Homebrew, pineando el proyecto a Gradle 9.5.1 (versión exacta de CLAUDE.md, verificada como release real en `services.gradle.org`). |
| 2026-08-16 | Fase 1.2 | ⚠️ Diagnóstico erróneo (corregido más abajo) | Primer intento de build contra `26.2` falló con `Failed to find official mojang mappings for 26.2`. Se interpretó como "Mojang todavía no publicó mappings para 26.x, hay que esperar" y se bajó `minecraft_version` a `1.21.11` (última versión con Yarn) solo para validar el toolchain. Confirmado con el usuario antes de aplicarlo. **Esta lectura era incorrecta** — ver fila siguiente. |
| 2026-08-16 | Fase 1.2 | Causa real encontrada | El usuario preguntó por qué ni siquiera 26.1 (más vieja) tenía mappings — buena señal de que "esperar" no explicaba el patrón. Investigación (`WebSearch` + `WebFetch` a `docs.fabricmc.net`, GitHub `FabricMC/discussions#5273`, y comparación directa con el `build.gradle`/`gradle.properties`/`fabric.mod.json` reales de la rama `26.2` de `github.com/FabricMC/fabric-example-mod`) confirmó: **Mojang dejó de ofuscar Minecraft Java Edition a partir de la 26.1** (anunciado oct. 2025). El jar ya viene con nombres legibles — no faltan mappings, **ya no hacen falta**. El template oficial para 26.2 no tiene bloque `mappings` en `dependencies{}`. |
| 2026-08-16 | Fase 1.2/1.3 | Corrección | Se revirtió todo a los valores reales de CLAUDE.md: `minecraft_version=26.2`, `fabric_api_version=0.157.0+26.2`, sin línea de mappings, `implementation` en vez de `modImplementation` (ya no hay remapeo), plugin id `net.fabricmc.fabric-loom`, `rootProject.name = 'aiworker'` en settings.gradle. `fabric.mod.json` vuelto a `"minecraft": "~26.2"`. |
| 2026-08-16 | Fase 1.3 | Build final | `./gradlew clean build` → `BUILD SUCCESSFUL`, **contra Minecraft 26.2 real**, sin tareas de remap (no hacen falta). Genera `build/libs/aiworker-0.1.0.jar` y `aiworker-0.1.0-sources.jar`. |
| 2026-08-16 | Fase 1.1 | `runServer` | macOS no trae `timeout` por defecto (es de GNU coreutils) — se corrió `./gradlew runServer` en background con `nohup ... &`, guardando el PID, y se lo mató a mano después de verificar el log, en vez de usar `timeout`. 1ra pasada (sin EULA): log muestra `[aiworker] Inicializando` y el proceso termina solo al pedir aceptar el EULA — sin colgarse. 2da pasada (con `run/eula.txt` en `eula=true`): server llega a `Done (1.704s)! For help, type "help"` con el mundo generado. Se bajó con `kill -TERM` al PID del proceso Java del server tras confirmar el boot; se verificó con `ps` que no quedó ningún proceso `Knot`/del proyecto corriendo. |
| 2026-08-16 | — | Naming | El usuario notó que se estaba usando `llmbot` (entidad/comando, heredado del texto original de `plan.md`) mezclado con `aiworker` (mod id, definido después). Se unificó todo bajo `aiworker`: `LlmBotEntity` → `AIWorkerEntity`, `/llmbot spawn` → `/aiworker spawn`. |
| 2026-08-16 | Fase 2.1/2.2 | Verificación de API vía bytecode | Antes de escribir código se decompiló/inspeccionó con `javap` el jar real de Minecraft 26.2 (`.gradle/loom-cache/minecraftMaven/...`) y de `fabric-api 0.157.0+26.2` en vez de asumir nombres de tutoriales viejos de Fabric (que usan mappings Yarn, ya no válidos en 26.x). Cambios confirmados: `PathAwareEntity`→`PathfinderMob`, `ResourceLocation`→`Identifier`, `FabricEntityTypeBuilder`→`FabricEntityType.Builder.createMob(...)` (API rediseñada, ya no acepta `.sized()` dentro del lambda — hay que encadenarlo después, sobre el `EntityType.Builder` que devuelve `createMob`). |
| 2026-08-16 | Fase 2.3 | Comando probado headless | Se validó `/aiworker spawn` sin GUI: server levantado con stdin conectado a un named pipe (`mkfifo`), se inyectó el comando por ahí. Log: `Bot spawneado.` sin excepciones — confirma que entidad + comando + atributos registran y ejecutan bien server-side. Limitación encontrada: el pipe/file descriptor no sobrevive entre invocaciones de Bash (cada una es un shell nuevo), así que el `stop` se mandó en una llamada aparte por `kill -TERM` directo al PID en vez de por el pipe. |
| 2026-08-16 | Fase 2.4 | Renderer placeholder | `AIWorkerEntityRenderer` reutiliza `ZombieModel`+textura vanilla de zombie (no hereda de `Zombie`, solo del modelo/render vía `MobRenderer<AIWorkerEntity, ZombieRenderState, ZombieModel<ZombieRenderState>>`) — es descartable, para tener algo visible antes de tener arte propio. `EntityRendererRegistry.register(...)` compila pero tira warning de API deprecada en esta versión de fabric-api; no se investigó el reemplazo todavía (queda como pendiente menor, no bloquea nada). |
| 2026-08-16 | Fase 2 | Confirmación visual del usuario | `runClient` en singleplayer, `/aiworker spawn` → aparece el bot con modelo de zombie, quieto (sin AI vanilla), sin crash de render. **Fase 2 cerrada** — cuerpo base + registro + comando de spawn funcionando de punta a punta (server headless y cliente gráfico). |
| 2026-08-17 | Fase 3 | Verificación de API vía bytecode | Antes de escribir código se verificó con `javap` sobre el jar real: `Mob.getNavigation()` → `PathNavigation` (no "NavigationController", nombre que traía `plan.md` de un tutorial viejo), `Mob.customServerAiStep(ServerLevel)` como hook oficial para IA custom, `Mob.doHurtTarget(ServerLevel, Entity)` (ahora pide `ServerLevel`, cambio de firma respecto a versiones viejas), `BlockState.getDestroySpeed()`, `Level.destroyBlockProgress()`/`destroyBlock()`, `PlayerList.broadcastSystemMessage()`. |
| 2026-08-17 | Fase 3 | **Bug real encontrado y arreglado**: crash del server al atacar | Primera prueba headless: el bot ataca, el server tira `IllegalArgumentException: Can't find attribute minecraft:attack_damage` dentro de `Mob.doHurtTarget` y **el server entero se cae** (`ReportedException`, "Stopping server"). Causa: `Mob.createMobAttributes()` no incluye `ATTACK_DAMAGE` por defecto (los mobs vanilla lo agregan ellos mismos). Fix: `createAttributes()` ahora agrega `Attributes.ATTACK_DAMAGE` y `Attributes.ATTACK_KNOCKBACK` explícitamente. Esto es exactamente el tipo de bug que la prueba headless post-fase esta pensada para atrapar antes de decirle al usuario "andá y probalo". |
| 2026-08-17 | Fase 3 | Bug menor encontrado y arreglado | `AIWorkerCommands.spawn()` ignoraba el valor de retorno (`boolean`) de `Level.addFreshEntity(...)` — si fallaba (ej. chunk no cargado), el comando igual reportaba "Bot spawneado." Corregido para chequear el resultado y reportar error. |
| 2026-08-17 | Fase 3 | Falso negativo en las pruebas (lección de metodología) | Probando `move`, el bot parecía no moverse nunca (posición idéntica en cada chequeo). Tras varias corridas de prueba sin limpiar el mundo persistente (`run/world`) entre cada una, se habían acumulado 8 bots y 6 chanchos de pruebas anteriores — las queries "@e[...,limit=1]" y "el bot más cercano" agarraban entidades viejas al azar, no la recién creada. Con logging temporal (`getNavigation().moveTo(...)` devuelve `boolean`, mas `isDone()`) se confirmó que la navegación sí arrancaba (`started=true`). Después de `/kill @e[type=aiworker:worker]` + `/kill @e[type=minecraft:pig]` al principio de cada corrida, `move` funcionó perfecto (llegó al destino exacto). **Lección**: limpiar el mundo persistente entre corridas de prueba, o usar un mundo nuevo por corrida, para no confundir resultados. |
| 2026-08-17 | — | Git | Repo inicializado y pusheado a `github.com/JFrnck/AIworker` (rama `main`). El remoto ya traía un `LICENSE` (MIT, generado por GitHub al crear el repo) — se mergeó con `--allow-unrelated-histories` antes del push, sin conflictos. Pendiente para cuando lleguemos a empaquetar el modelo de embeddings (~98MB, Fase 5 de IA): configurar Git LFS para ese archivo antes de commitearlo, para no pasarse del límite de 100MB por archivo de GitHub. |
| 2026-08-17 | Fase 3 | Verificación final limpia | Con el mundo limpio (`/kill` de entidades de prueba al inicio): `move` llegó al destino, `mine` destruyó el bloque en ~1.5s, `attack` mató a un chancho de 10 HP en <8s sin crashear, `say` broadcasteó `<AIWorker> hola, soy el bot`. **Fase 3 cerrada.** Se limpiaron las entidades de prueba del mundo persistente al final (`/kill` + `save-all`) para no dejar el mundo de pruebas lleno de bots/chanchos viejos. |
| 2026-08-17 | — | Discusión de arquitectura (LLM como agente) | El usuario pidió pensar la Fase 4+ como un agent loop (percepción → memoria → decisión → acción), similar al loop de un agente de código, en vez de un polling ciego. Se definieron 4 piezas: percepción (world snapshot enriquecido), memoria de corto plazo (historial acotado + plan en texto libre, RAM), memoria de largo plazo (vectorial, SQLite local), loop event-driven. Se reestructuró `plan.md` de 7 a 10 fases para reflejarlo. |
| 2026-08-17 | — | Decisión: modelo de embeddings | Para la memoria de largo plazo (Fase 5, todavía no implementada) se eligió `granite-embedding-97m-multilingual-r2` (IBM, Apache 2.0, 97M params, 384 dim, español en el tier de soporte reforzado, ~98MB en ONNX cuantizado) — verificado vía WebFetch a la blog post oficial de HuggingFace. Se descartó `all-MiniLM-L6-v2` por ser mono-idioma inglés (todo el texto del mod es español). Se eligió correrlo local (ONNX Runtime Java + DJL HuggingFace Tokenizers) en vez de llamar a una API externa de embeddings, para no sumar otra dependencia de red/costo al riesgo de latencia que ya menciona `plan.md`. Se eligió empaquetar el modelo dentro del jar del mod en vez de descargarlo en el primer arranque — evita el riesgo real de que un link de Hugging Face caído/movido rompa el mod en producción (sin conexión a internet en runtime, determinístico). Implica configurar Git LFS antes de commitear ese archivo (~98MB, cerca del límite de 100MB de GitHub). |
| 2026-08-17 | Fase 4 | Verificación de API vía bytecode | Como en fases anteriores, se verificó con `javap`: `BlockPos.betweenClosed` (no `BlockPos.iterate`), `PlayerChatMessage.signedContent()` para el texto plano del chat, `state.is(BlockTags.LOGS)`/`state.is(BlockTags.CROPS)` para categorizar árboles/cultivos, `CropBlock.isMaxAge()` para "cultivo maduro", `Enemy` como interfaz marcadora de hostilidad, `ServerMessageEvents.CHAT_MESSAGE` de Fabric API para capturar chat. |
| 2026-08-17 | Fase 4 | Ajuste de scope: hambre e inventario | El `plan.md` original pedía snapshot de "hambre e inventario", asumiendo que el bot es como un jugador. Al implementar se confirmó que `AIWorkerEntity` (un `Mob`, no un `Player`) no tiene hambre (`FoodData` es exclusivo de `Player`) ni inventario propio (los `Mob` solo tienen slots de equipo). Se ajustó el snapshot a lo que existe hoy (salud + item en mano); el inventario real queda como prerequisito de la Fase 7 (`PlaceBlock`/`Equip`/`Craft` lo necesitan de verdad, no solo para reportarlo). |
| 2026-08-17 | Fase 4 | Verificación headless limpia | Con el mundo limpio: `/aiworker snapshot` devolvió JSON correcto con bloques reales del mundo (incluyendo un cofre y un horno colocados a mano para la prueba), *points of interest* categorizados bien, y — lo más importante — el historial de memoria (`BotMemory`) registró correctamente tanto una acción exitosa (`moveTo(...) -> finalizada`) como una rechazada con su motivo (`mineBlock(...) -> rechazada: no hay ningun bloque en ...`). Sin bugs encontrados en este pase. Mundo de pruebas limpiado al final. **Fase 4 cerrada.** |
| 2026-08-17 | Fase 4 | Chat confirmado en cliente | El usuario probó `runClient`: mensaje real de chat capturado y filtrado por cercanía, apareció correcto en `/aiworker snapshot`. |
| 2026-08-17 | — | PR #1 mergeado | `feature/fase-4-percepcion-memoria` → `main` sin conflictos (GraphQL de GitHub tuvo un 503 transitorio durante el merge, se resolvió pegándole a la REST API directo con `gh api -X PUT`). |
| 2026-08-17 | — | Discusión: peso real del modelo local | Al cotizar Fase 5 (memoria vectorial) se descubrió que el "~98MB" que se había hablado solo contaba el modelo — el peso real bundleado (modelo + tokenizer.json 25MB + onnxruntime 54MB + sqlite-jdbc 12MB + DJL tokenizers 19MB) es **~208MB**. Se evaluó "modelo más chico" como alternativa y se descartó tras verificar (`multilingual-e5-small` pesa más, 118MB, pese al nombre; `EmbeddingGemma` es más grande, 300M params) — Granite-97M ya es de los más chicos viables en calidad multilingüe real. |
| 2026-08-17 | — | Reconsideración: el argumento de "confiabilidad" de local no aplicaba | Se había justificado el modelo local (vs. API externa) por evitar depender de un link externo en producción. Al pensarlo mejor: el bot YA depende de red para lo esencial (el LLM de decisiones es una API externa, Fase 5/6). Blindar solo la memoria contra fallas de red mientras el cerebro entero depende de la red no aporta confiabilidad real, solo suma 200MB y complejidad de build (bundlear ONNX Runtime con binarios nativos en un mod de Fabric es territorio poco transitado). El usuario decidió igual mantener el modelo local (ya elegido, la decisión de descarga es del usuario que instala el mod) pero **se reordenó el plan**: construir primero el decision loop (Fase 5, antes Fase 6) y postergar la memoria de largo plazo (nueva Fase 6) hasta después del MVP. |
| 2026-08-17 | Fase 5 | Decisión: proveedor LLM | Se eligió **OpenAI**, modelo `gpt-5.6-luna` — verificado como modelo real vía WebFetch a la documentación oficial de OpenAI (no confiar en agregadores de pricing de terceros para el nombre exacto). $0.20/$1.20 por 1M tokens in/out, soporta la Responses API con salida estructurada estricta (`text.format` con `json_schema` + `strict:true`) — mejor que pedir JSON en el prompt a mano, la API rechaza cualquier respuesta que no matchee el schema exacto. Costo estimado: ~$0.0004 por ciclo de decisión, ~$0.6-0.7/hora de bot activo. |
| 2026-08-17 | Fase 5 | Verificación de API vía documentación oficial | En modo `strict`, OpenAI exige que TODOS los campos del schema estén en `required` (los que no aplican a una acción puntual van tipados nullable, `["integer","null"]`) y `additionalProperties: false`. El JSON de salida del modelo viene en `response.output_text` (o `output[0].content[0].text` como fallback). |
| 2026-08-17 | Fase 5 | Implementación | `brain/llm/` (OpenAiConfig, ActionSchema, LlmDecision, LlmException, OpenAiClient, SystemPrompt, DecisionValidator) + `brain/DecisionScheduler` enganchado a `ServerTickEvents.END_SERVER_TICK`. Loop event-driven: revisa cada tick qué bots en modo automático están idle o trabados (timeout 10s) y solo a esos les dispara una decisión — nunca más de una decisión en vuelo por bot (`IN_FLIGHT` set). Modo automático opt-in por bot (`/aiworker auto on\|off`), no arranca solo al spawnear. `/aiworker think` para disparar un ciclo manual. |
| 2026-08-17 | Fase 5 | **Bug real encontrado y arreglado con la key real del usuario** | El usuario probó `/aiworker think` con su `OPENAI_API_KEY` real: la request fue exitosa (`"status":"completed"`, sin error), pero el mod tiraba "Respuesta de OpenAI sin output_text". Causa: el campo `output_text` que documenta la guía oficial es una comodidad que agregan los SDKs oficiales (Python/JS) armando el objeto client-side - **no existe en el JSON crudo de la API**. El array `output` real trae varios items (acá: uno `"type":"reasoning"` con contenido vacío/encriptado, *antes* del `"type":"message"` con la respuesta) - el código asumía que el texto estaba en `output[0]`, que resultó ser el reasoning vacío. Fix: buscar el item con `"type":"message"` dentro de `output`, y dentro de su `content` el item con `"type":"output_text"`. Confirmado con la respuesta real del usuario: el modelo eligió `idle` correctamente (nada relevante cerca) con un `plan` coherente - el diseño del schema/prompt/validación era correcto desde el principio, el bug era puramente de parseo del lado nuestro. |
| 2026-08-17 | Fase 5 | Verificación headless (sin key real) | Sin `OPENAI_API_KEY`: warning claro al bootear el mod, y `/aiworker think`/`/aiworker auto on` fallan con mensaje explícito en vez de intentar la llamada. Con una key falsa: el request llegó real a OpenAI, la API devolvió 401, se capturó el error async, se reintegró al hilo principal, se logueó — **sin crashear el server**. Confirma que el formato del request es válido a nivel HTTP. **Falta probar el camino feliz (200 real) con la key real del usuario** — no se le pidió compartirla, queda pendiente de que él la setee y pruebe. |
| 2026-08-17 | Fase 5 | **Prueba real end-to-end exitosa** | El usuario probó con su propia key: `sigueme`, `mina con ese pico la piedra de aqui`, `detente`, `protegeme` — el bot interpretó cada instrucción correctamente vía chat, ejecutó `move_to`/`mine_block`/`say`/`attack` según correspondía, y hasta intentó atacar a un esqueleto que le disparó al jugador cuando le pidieron protección. Reportó no poder plantar semillas (correcto - `Plant` no existe en el schema todavía). El diseño completo (schema, prompt, loop, validación) funciona de punta a punta. |
| 2026-08-17 | Fase 5 | Ajustes post-prueba real | Dos problemas de UX detectados por el usuario en la prueba real: (1) delay de 2-3s por decisión — se agregó `reasoning:{"effort":"none"}` al request (confirmado con la doc oficial de OpenAI que es el valor recomendado para tareas latency-critical que no necesitan razonar). (2) Movimiento entrecortado seguiendo al jugador ("sigueme") — cada ciclo pedía un `move_to` nuevo a la posición vieja del jugador; se agregó la acción `Follow(entityId)` que persigue en tiempo real tick a tick (reusa el patrón de navegación de `attack`), probada headless moviendo el objetivo a mitad de camino y confirmando que el bot recalcula solo. |
| 2026-08-17 | — | Nueva fase agregada: pathfinding avanzado | El usuario pidió que el bot pueda puentear con tierra o apilar bloques para subir cuando el camino lo requiera — el pathfinding vanilla de Minecraft no soporta esto (solo camina/salta 1 bloque). Se agregó como Fase 8 de `plan.md` (después del inventario de Fase 7, del que depende), con Baritone como referencia de diseño. No implementado todavía - deliberadamente pospuesto, es un sistema grande. |

---

## 2. Objectives completed

Checklist plano de objetivos ya cerrados (compilan / pasan prueba local según
guardrail de "Progreso" en `CLAUDE.md`). Se mueve un ítem acá solo cuando está
verificado, no cuando está "casi listo".

- [x] Gradle disponible localmente (vía Homebrew) para generar el wrapper.
- [x] Estructura del proyecto Fabric (settings.gradle, build.gradle,
      gradle.properties, fabric.mod.json) — verificada línea por línea contra
      el template oficial real de la rama `26.2`.
- [x] Mod id y paquete base definidos (`aiworker`, `com.jeanfranck.aiworker`),
      con separación `body/` vs `brain/` ya reflejada en los paquetes.
- [x] Clase principal (`AIWorkerMod`) y entrypoint de cliente
      (`AIWorkerModClient`) registrados en `fabric.mod.json`.
- [x] `gradle-wrapper` generado y pineado a Gradle 9.5.1.
- [x] `./gradlew clean build` limpio **contra Minecraft 26.2**, la versión
      real del proyecto — `build/libs/aiworker-0.1.0.jar` generado.
- [x] `runServer` levanta correctamente y el mod se inicializa
      (`[aiworker] Inicializando` en el log, luego `Done (...)! For help,
      type "help"`). Probado de forma acotada (background + kill manual,
      no `timeout` en macOS) y sin dejar procesos huérfanos.
- [ ] `runClient` — no probado por mí (entorno headless, sin GUI disponible).
      El usuario sí lo corrió y confirmó `(Modded)` en el título.
- [x] `AIWorkerEntity` (cuerpo) registrada como `EntityType`, sin goals
      vanilla, con atributos base (`Mob.createMobAttributes()`).
- [x] Comando `/aiworker spawn` — probado headless desde consola de
      `runServer` (sin jugador conectado), spawnea sin excepciones.
- [x] Renderer placeholder (modelo/textura de zombie) registrado en el
      cliente — **verificado visualmente por el usuario** con `runClient` en
      singleplayer: el bot aparece con el modelo de zombie, quieto (sin
      goals vanilla, tal como se buscaba), sin errores de render.
- [ ] Deprecation warning de `EntityRendererRegistry.register(...)` — build
      compila igual, reemplazo no investigado (pendiente menor).
- [x] `BotAction` (sealed interface: `MoveTo`/`MineBlock`/`Attack`/`Say`) +
      ejecución en `AIWorkerEntity.customServerAiStep()` — una acción en
      curso por bot, consumida tick a tick.
- [x] Las cuatro acciones probadas headless de punta a punta contra Minecraft
      26.2 real, con mundo limpio: mover, minar, atacar (mató un mob sin
      crashear el server) y hablar. Bug de crash real (atributo
      `attack_damage` faltante) encontrado y arreglado antes de darlo por
      bueno.
- [x] Comandos de prueba `/aiworker move|mine|attack|say` agregados,
      apuntan al bot `AIWorkerEntity` más cercano al ejecutor.
- [ ] Warning de Gradle "deprecated features, incompatible con Gradle 10" —
      pendiente de investigar si viene del plugin fabric-loom 1.17.19 o de
      algo propio. No bloquea el build, es solo un aviso.
- [x] Repo en GitHub (`JFrnck/AIworker`), commit inicial pusheado sin
      conflictos (merge con el `LICENSE` que trae GitHub por defecto).
- [x] `WorldSnapshot`/`WorldSnapshotCollector` — self, bloques crudos
      (radio chico), *points of interest* categorizados (radio grande,
      acotado), entidades cercanas, chat reciente filtrado por cercanía,
      historial + plan de `BotMemory`. Serializado a JSON con Gson.
- [x] `BotMemory` — historial acotado de (acción→resultado) + plan en texto
      libre, integrada a `AIWorkerEntity.setAction()`/`customServerAiStep()`.
      Verificado headless: queda registrado tanto lo que sale bien como lo
      que se rechaza (con motivo).
- [x] `ChatLog` global vía `ServerMessageEvents.CHAT_MESSAGE` — **confirmado
      por el usuario con `runClient`**: mensaje real de chat capturado y
      filtrado por cercanía, apareció correcto en `/aiworker snapshot`
      (`recentChat":[{"sender":"Player134","message":"hola bot, como andas"}]`).
- [x] Comando `/aiworker snapshot` — probado headless, JSON correcto.
- [x] `brain/llm/` (OpenAiConfig, ActionSchema, LlmDecision, OpenAiClient,
      SystemPrompt, DecisionValidator) — cliente async contra la Responses
      API de OpenAI, salida estructurada estricta, validación completa
      antes de tocar el mundo.
- [x] `DecisionScheduler` — loop event-driven vía `END_SERVER_TICK`, modo
      automático opt-in por bot, timeout de acción trabada (10s), nunca
      más de una decisión en vuelo por bot.
- [x] Comandos `/aiworker think` y `/aiworker auto on|off`.
- [x] Manejo de error de red/API probado con key inválida real (401) — sin
      crash, logueado, reintegrado al hilo principal correctamente.
- [x] **Camino feliz confirmado por el usuario con su key real** — bot
      siguió instrucciones de chat correctamente end-to-end (mover, minar,
      atacar, hablar). **Fase 5 cerrada.**
- [x] `BotAction.Follow` + `reasoning:none` — agregados tras feedback real
      de UX (delay y movimiento entrecortado). Probado headless (follow
      recalcula solo si el objetivo se mueve).
- [ ] Decisión de arquitectura de memoria de largo plazo (modelo de
      embeddings, SQLite, `Remember(text)`) — **diseñada, pospuesta hasta
      después del MVP** (ver Tree decision).
- [ ] Pathfinding avanzado (puentes/escaleras, Fase 8 de `plan.md`) —
      **pedido, no diseñado en detalle todavía, no implementado**.

---

## 3. Tree decision

Árbol de decisiones técnicas tomadas y alternativas descartadas, con la razón.
Formato: decisión → alternativas consideradas → por qué se eligió esta.

```
Decisión: mod id = "aiworker" (display "AI Worker")
├─ Alternativa: "blockworker" (coincide con el nombre de carpeta del repo)
│   └─ Descartada: el usuario pidió el nombre "más comercial" para tienda de
│      mods; "aiworker" comunica más directo la función (NPC decidido por IA)
│      y es más corto/buscable. Se puede renombrar después, es solo el
│      namespace inicial.
└─ Elegida: "aiworker" → paquete base com.jeanfranck.aiworker

Decisión: generar el proyecto a mano (sin clonar/descargar el template zip
oficial de fabricmc.net/develop)
├─ Alternativa: git clone del repo oficial fabric-example-mod
│   └─ Descartada inicialmente para no requerir permiso de descarga sin
│      necesidad — pero terminó siendo necesario usarlo igual como
│      referencia (fetch de archivos puntuales vía WebFetch/curl) para
│      verificar la estructura real de 26.2 cuando el build falló.
└─ Elegida: escribir los archivos a mano, pero **verificados contra el
   contenido real** de la rama `26.2` de `FabricMC/fabric-example-mod`
   (build.gradle, gradle.properties, fabric.mod.json, settings.gradle)
   en vez de inventar la sintaxis de memoria.

Decisión: Minecraft 26.2 sin bloque de mappings (SUPERA la decisión anterior
de bajar a 1.21.11)
├─ Diagnóstico inicial (erróneo): el error "Failed to find official mojang
│   mappings for 26.2" se interpretó como que Mojang todavía no publicó el
│   archivo de mappings y habría que esperar (como pasa habitualmente con
│   versiones muy nuevas). Se verificó que ni 26.2 ni 26.1.x tienen
│   `client_mappings`/`server_mappings` en el manifest de Mojang, ni Yarn
│   ni Quilt-mappings los tienen tampoco (se quedaron en 1.21.11).
│   → Con esa lectura se bajó `minecraft_version` a `1.21.11` para poder
│   validar el toolchain, confirmado con el usuario.
├─ Pregunta clave del usuario: "¿por qué tampoco hay mappings para 26.1, que
│   ya lleva varios meses?" — señal de que "hay que esperar" no cuadraba con
│   el patrón (ninguna versión de toda la rama 26.x tiene mappings, ni la
│   más vieja).
├─ Causa real (confirmada vía WebSearch + comparación con el repo oficial):
│   Mojang anunció en octubre 2025 que Minecraft Java Edition deja de
│   ofuscarse a partir de la 26.1. El jar ya trae nombres legibles de
│   fábrica — los mappings dejaron de ser necesarios, no es que falten.
│   El propio `build.gradle` de `fabric-example-mod` rama `26.2` no tiene
│   bloque `mappings` y usa `implementation` en vez de `modImplementation`
│   (ya no hay remapeo entre intermediary y named).
└─ Elegida: revertir a `minecraft_version=26.2`,
   `fabric_api_version=0.157.0+26.2`, sin mappings, `implementation` en vez
   de `modImplementation`, plugin id `net.fabricmc.fabric-loom`. Build
   verificado: `BUILD SUCCESSFUL` contra 26.2 real.
   Lección: cuando una fuente de datos (meta.fabricmc.net, Mojang manifest)
   da un resultado que "no cierra" con el patrón esperado (ausencia total
   en toda una rama de versiones, no solo la más nueva), vale la pena
   buscar una explicación estructural antes de asumir "hay que esperar".

Decisión: reordenar plan.md — decision loop (Fase 5) antes que memoria de
largo plazo (Fase 6, pospuesta)
├─ Contexto: al cotizar la memoria vectorial se descubrió que el peso real
│   bundleado es ~208MB (no ~98MB), lo que llevó a reconsiderar si el
│   modelo local seguia siendo la mejor opcion.
├─ Insight durante la discusión: el argumento original para justificar
│   "local" (evitar depender de un link externo que se puede caer en
│   producción) no se sostiene del todo — el bot YA depende de red para
│   lo esencial (el LLM de decisiones es una API externa). Blindar solo
│   la memoria contra fallas de red no hace al sistema más confiable en
│   la práctica si el cerebro entero depende de que la red funcione.
├─ El usuario decidió mantener igual el modelo local (su costo/beneficio,
│   es quien decide si publicar un mod de 200MB), pero coincidió en que
│   no tenía sentido construir la memoria vectorial antes que el propio
│   loop de decisión — sin loop, no hay nada que alimente la memoria.
└─ Elegida: reordenar `plan.md` — Fase 5 = cliente LLM + decision loop
   (la definición de MVP), Fase 6 = memoria de largo plazo (pospuesta,
   diseño ya cerrado, se implementa después de tener el MVP andando).

Decisión: proveedor LLM = OpenAI, modelo `gpt-5.6-luna`
├─ Alternativa: Claude (Anthropic) Haiku 4.5 — se ofreció como
│   recomendación inicial (rápido/barato, tool use maduro), pero el
│   usuario eligió OpenAI explícitamente.
├─ Verificado el modelo real vía WebFetch a la documentación oficial de
│   OpenAI (no confiar en agregadores de pricing de terceros para el
│   nombre exacto — el ecosistema de nombres de modelos cambia rápido).
└─ Elegida: `gpt-5.6-luna` vía la Responses API (`/v1/responses`) con
   salida estructurada estricta (`text.format.type=json_schema`,
   `strict:true`) — fuerza el schema exacto de acciones a nivel API, no
   a nivel de prompt ("por favor respondé en JSON").
```

---

## 4. Briefing para un modelo de IA nuevo (contexto de sesión perdido)

Esta sección existe porque la conversación donde se hizo todo este trabajo
va a perder su contexto. Está escrita para que **vos, un modelo nuevo sin
memoria de nada de lo anterior**, puedas seguir trabajando en este proyecto
sin tener que releer todo el historial. Leé esto primero, después `plan.md`
para el estado exacto de checkboxes, y recién si necesitás el detalle
histórico completo andá a las secciones 1-3 de arriba.

### Qué es este proyecto
Mod de Fabric para Minecraft Java Edition **26.2** que agrega un NPC
(`AIWorkerEntity`) cuyo comportamiento decide un LLM externo vía API. Mod id
`aiworker`, paquete `com.jeanfranck.aiworker`, repo
`github.com/JFrnck/AIworker`, licencia MIT. El usuario (jeanfranck) es el
dueño del proyecto y quien toma las decisiones de producto/arquitectura;
vos sos quien ejecuta, investiga, y propone, pero **no decidís solo** cosas
grandes (ver "Cómo se trabaja" abajo).

`CLAUDE.md` (en la raíz del repo) tiene los guardrails obligatorios del
proyecto — threading, validación de salida del LLM, secretos, alcance de
cambios. Es de lectura obligatoria, no es opcional. `plan.md` tiene las 11
fases del roadmap con checkboxes. Este archivo (`STATUS.md`) es el diario
de a bordo con el *por qué* de cada decisión.

### Estado exacto al momento de escribir esto
Fases 1 a 5 de `plan.md` **completas y probadas** (build limpio contra
26.2 real, headless y con el usuario probando en `runClient`/singleplayer
con su propia `OPENAI_API_KEY`). El bot:
- Existe en el mundo, sin IA vanilla (`registerGoals()` vacío a propósito).
- Ejecuta `moveTo`, `mineBlock`, `attack`, `follow`, `say` vía
  `AIWorkerEntity.customServerAiStep()`.
- Arma un snapshot JSON del mundo (`WorldSnapshotCollector`) con su estado,
  bloques cercanos, *points of interest*, entidades, chat reciente, y su
  propia memoria de corto plazo (`BotMemory`: historial + campo "plan"
  libre que el LLM reescribe).
- Tiene un decision loop event-driven (`DecisionScheduler`, enganchado a
  `ServerTickEvents.END_SERVER_TICK`) que llama a OpenAI (`gpt-5.6-luna`,
  Responses API, salida estructurada estricta) cuando el bot está idle o
  trabado (10s), valida la respuesta (`DecisionValidator`) antes de tocar
  nada, y la aplica.
- **Confirmado funcionando en vivo por el usuario**: siguió instrucciones
  de chat reales (moverse, seguir, minar, atacar, hablar) correctamente.

Comandos de prueba disponibles: `/aiworker spawn|move|mine|attack|follow|
say|snapshot|think|auto on|auto off`.

**Lo próximo pendiente** (en orden de `plan.md`):
- Fase 6: memoria de largo plazo (vectorial) — **diseñada por completo,
  cero código escrito**. Ver Tree decision arriba para la discusión larga
  de por qué se pospuso y el modelo/stack elegido
  (`granite-embedding-97m-multilingual-r2` local, SQLite, sin vector DB
  con embeddings si el usuario prefiere la alternativa más simple que se
  charló al final - **confirmar con el usuario cuál de las dos versiones
  quiere antes de implementar**, quedó como pregunta abierta sin cerrar
  del todo).
- Fase 7: inventario real + `PlaceBlock`/`Equip`.
- Fase 8: pathfinding avanzado (puentes con tierra, apilar bloques para
  subir) — pedido explícito del usuario, **no diseñado en detalle**, solo
  el esqueleto de la fase en `plan.md`. Depende de Fase 7 (necesita
  materiales en inventario). Referencia de diseño: Baritone.
- Fases 9-11: crafting/cocina, agricultura, deploy en Oracle Cloud.

### Cómo se trabaja en este proyecto (patrones establecidos, no improvises otra cosa)
1. **Plan antes de ejecutar.** Para cualquier fase o tarea no trivial, se
   presenta el plan (qué archivos, qué comandos, qué decisiones quedan
   abiertas) y se espera confirmación antes de tocar código. Esto lo pidió
   el usuario explícitamente después de que en la Fase 1 se arrancó a
   ejecutar sin mostrar el plan primero — no lo repitas.
2. **Verificar contra el bytecode real antes de escribir código**, nunca
   asumir nombres de clases/métodos de memoria o de tutoriales. Minecraft
   26.2 rompe muchísimas convenciones viejas de modding porque **Mojang
   dejó de ofuscar el juego desde la 26.1** (oct. 2025) — no hay mappings
   Yarn para esta rama, y muchas clases tienen nombres distintos a los que
   cualquier LLM entrenado antes de esa fecha va a "recordar" (ejemplos
   reales que salieron mal: `PathAwareEntity`→`PathfinderMob`,
   `ResourceLocation`→`Identifier`, `FabricEntityTypeBuilder` ya no existe,
   `EntityRendererRegistry` deprecado, etc). El patrón que funcionó: buscar
   el jar real en `.gradle/loom-cache/minecraftMaven/.../minecraft-common-*
   .jar` (y `-clientOnly-*.jar` para cosas de render), extraer la clase con
   `unzip`, e inspeccionar con `javap -p`. Mismo cuidado con librerías
   externas (OpenAI, HuggingFace) — verificar contra documentación oficial
   actual, no contra lo que "se sabe" de memoria, los nombres de modelos y
   shapes de API cambian rápido.
3. **Probar headless antes de pedirle al usuario que pruebe.** El patrón
   que se usó en toda la sesión: levantar `./gradlew runServer` en
   background con un named pipe conectado a stdin (`mkfifo`, `exec 3>
   "$PIPE"`, mandar comandos con `echo "comando" >&3`), esperar, revisar
   el log, y al final `kill -TERM` al proceso + `pkill -f KnotServer` para
   no dejar nada corriendo. **Importante**: el file descriptor del pipe NO
   sobrevive entre llamadas de Bash separadas (cada una es un shell nuevo)
   - todo el ciclo de vida de un server de prueba (arrancar, mandar
   comandos, parar) tiene que ir en un solo bloque de comandos. También:
   **limpiar las entidades de prueba al principio de cada corrida**
   (`/kill @e[type=aiworker:worker]`) - el mundo de pruebas es persistente
   entre corridas (`run/world`), y entidades viejas acumuladas causaron un
   falso negativo real en la Fase 3 (las queries "más cercano" agarraban
   bots viejos, no el nuevo).
4. **No hay `timeout` en macOS por defecto** (es de GNU coreutils) - usar
   `nohup ... &` + `kill` manual en vez de `timeout ...`.
5. **Nunca manejar la API key del usuario directamente.** Se le dan
   instrucciones para que la setee como variable de entorno (`export
   OPENAI_API_KEY=...`) y pruebe él mismo — nunca pedirle que la pegue en
   el chat.
6. **Git**: el repo vive en GitHub (`JFrnck/AIworker`), rama `main`. El
   flujo usado: rama de feature por bloque de trabajo grande, PR, merge
   (con `gh pr create`/`gh pr merge` — si la API GraphQL de GitHub da 503,
   `gh api -X PUT repos/.../pulls/N/merge` por REST funciona como
   alternativa). Solo commitear/pushear cuando el usuario lo pide
   explícitamente, nunca de forma proactiva.
7. **Actualizar `plan.md` y `STATUS.md` al cerrar cada fase o decisión
   importante** - marcar checkboxes, agregar fila a la tabla de
   Walkthrough, y si es una decisión con alternativas descartadas, agregar
   al árbol de Tree decision. Es lo que le da continuidad al proyecto
   entre sesiones (como esta misma).

### Gotchas / lecciones caras (no las repitas)
- El primer intento de `attack` **crasheó el server entero** porque
  `Mob.createMobAttributes()` no incluye `ATTACK_DAMAGE` por defecto - hay
  que agregarlo a mano. Cualquier atributo nuevo que se use (ej. si se
  necesita `MOVEMENT_SPEED` custom, `FOLLOW_RANGE`, etc.) hay que
  verificar que esté en `AIWorkerEntity.createAttributes()`.
- El campo `output_text` que muestra la documentación de OpenAI **no
  existe en el JSON crudo de la Responses API** - es una comodidad que
  arman los SDKs oficiales client-side. El array `output` real trae varios
  items (uno de tipo `"reasoning"` con contenido vacío/encriptado *antes*
  del `"message"` con la respuesta real) - hay que buscar el item con
  `"type":"message"`, no asumir que es `output[0]`.
- `Level.addFreshEntity()` devuelve `boolean` - si no se chequea, un spawn
  fallido (ej. chunk no cargado) reporta éxito falso.
- Todas las acciones del cuerpo (`AIWorkerEntity.setAction()`) devuelven un
  `String` con el motivo de rechazo (o `null` si está OK) - nunca fallan
  en silencio. Mismo patrón para `DecisionValidator` del lado del LLM. Si
  agregás una acción nueva, seguí este patrón.

### Cosas que no hay que hacer sin preguntar (de CLAUDE.md, resumido)
No tocar `gradle.properties` (versiones del toolchain) sin confirmar. No
correr `runServer`/`runClient` de forma prolongada sin avisar (usar el
patrón de pipe + timeout manual de arriba). No tocar el server de Oracle
Cloud de producción desde este repo. No commitear la API key ni loguearla,
ni siquiera en debug. Whitelist de acciones, no blacklist - nunca ejecutar
algo que el schema no contemple explícitamente.
