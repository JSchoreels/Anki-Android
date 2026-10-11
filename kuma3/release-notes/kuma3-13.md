**Changes since release 12**

- Big collections open without freezing: RWKV gets ready after the collection opens instead of during the open. On a 101k-card collection with 3.7M reviews the screen was stuck for 22 s; now 0.2 s.
- A full RWKV rebuild needs about half the memory: it reads the review history in parts and frees memory as it goes. Histories of 100k reviews or more show a notification, "Preparing RWKV: N% · about M min left", and the app keeps waiting while it advances. A collection with millions of reviews can still be too big for a phone's memory.
- App updates keep the saved RWKV state, so a new version no longer rebuilds it from the whole history. This update still rebuilds once.
- Saving deck options or editing notes no longer re-reads the whole review history.
- RWKV scores cards about 2x faster on phones (4,099 cards: 676 → 318 ms on a Pixel 8a). Same results.
- For kumapie: reading today's due cards no longer selects each deck, so it leaves no "Select Deck" steps in Undo, and Undo keeps kumapie's ratings.
- Smaller speedups: opening the collection (it no longer checks every card when nothing changed), the Browser sorted by retrievability or stability (about 1.6x), building the study queue, and the RWKV start-up check. Same queues, orders and scores.

<!-- es -->
**Cambios desde la versión 12**

- Las colecciones grandes abren sin congelarse: RWKV se prepara después de abrir la colección y no durante la apertura. En una colección de 101k tarjetas y 3,7 millones de repasos la pantalla quedaba bloqueada 22 s; ahora 0,2 s.
- Reconstruir RWKV desde cero necesita más o menos la mitad de memoria: lee el historial de repasos por partes y libera memoria sobre la marcha. Con 100k repasos o más aparece una notificación, "Preparing RWKV: N% · about M min left", y la app sigue esperando mientras avanza. Una colección con millones de repasos todavía puede no caber en la memoria de un móvil.
- Las actualizaciones de la app conservan el estado RWKV guardado, así que una versión nueva ya no lo reconstruye desde todo el historial. Esta actualización todavía lo reconstruye una vez.
- Guardar las opciones de un mazo o editar notas ya no vuelve a leer todo el historial de repasos.
- RWKV puntúa las tarjetas unas 2 veces más rápido en móviles (4.099 tarjetas: 676 → 318 ms en un Pixel 8a). Mismos resultados.
- Para kumapie: leer las tarjetas pendientes de hoy ya no selecciona cada mazo, así que no deja pasos "Select Deck" en Deshacer, y Deshacer conserva las respuestas de kumapie.
- Mejoras menores de velocidad: abrir la colección (ya no revisa todas las tarjetas si nada cambió), el explorador ordenado por recuperabilidad o estabilidad (unas 1,6 veces), armar la cola de estudio y la comprobación de RWKV al arrancar. Mismas colas, órdenes y puntuaciones.
