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
| 2026-08-17 | Fase 3 | Verificación final limpia | Con el mundo limpio (`/kill` de entidades de prueba al inicio): `move` llegó al destino, `mine` destruyó el bloque en ~1.5s, `attack` mató a un chancho de 10 HP en <8s sin crashear, `say` broadcasteó `<AIWorker> hola, soy el bot`. **Fase 3 cerrada.** Se limpiaron las entidades de prueba del mundo persistente al final (`/kill` + `save-all`) para no dejar el mundo de pruebas lleno de bots/chanchos viejos. |

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
```
