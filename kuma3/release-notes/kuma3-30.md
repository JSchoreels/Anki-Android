**Changes since release 28**

- **RWKV-Instant updates the review queue while you study again** (with JSchoreels' fix, merged from [kuma3-anki-core#1](https://github.com/Pedrubik2000/kuma3-anki-core/pull/1)). Since release 22 the queue only updated the card you had just answered, so the count went down by one per card and then jumped when you went back to the deck list, whatever "Update the RWKV queue every" was set to. Now the whole queue is rescored every that many answers (1 by default), as on the desktop, so the order and the count follow RWKV as you answer. With the default it adds about a tenth of a second per answer on a Tab S7+ with 10,000 due cards; a higher number, or "Use faster, approximate queue updates", makes it lighter.
- **With "Use faster, approximate queue updates" on**, the deck list no longer reuses the partly updated scores of the review queue: it rescores every card.
- **When the reviews run out** before "Update the RWKV queue every" answers, the queue is refreshed at once, so cards that became due meanwhile still show up.
- **If an answer could not be added to RWKV straight away** (something else changed the collection first), RWKV catches up before checking whether the queue is due a refresh.

<!-- es -->
**Cambios desde la versión 28**

- **RWKV-Instant vuelve a actualizar la cola de repaso mientras estudias** (con el arreglo de JSchoreels, integrado desde [kuma3-anki-core#1](https://github.com/Pedrubik2000/kuma3-anki-core/pull/1)). Desde la versión 22 la cola solo actualizaba la tarjeta que acababas de responder, así que el número bajaba de uno en uno y luego saltaba al volver a la lista de mazos, sin importar el valor de "Update the RWKV queue every". Ahora toda la cola se vuelve a calcular cada esa cantidad de respuestas (1 por defecto), como en el escritorio, y el orden y el número siguen a RWKV mientras respondes. Con el valor por defecto añade cerca de una décima de segundo por respuesta en una Tab S7+ con 10.000 tarjetas pendientes; un número mayor, o "Use faster, approximate queue updates", lo hace más ligero.
- **Con "Use faster, approximate queue updates" activado**, la lista de mazos ya no reutiliza las puntuaciones a medio actualizar de la cola de repaso: vuelve a calcular todas las tarjetas.
- **Si los repasos se acaban** antes de llegar a "Update the RWKV queue every" respuestas, la cola se actualiza enseguida, así que siguen apareciendo las tarjetas que vencieron mientras tanto.
- **Si una respuesta no se pudo añadir a RWKV en el momento** (otra cosa cambió la colección antes), RWKV se pone al día antes de comprobar si toca actualizar la cola.
