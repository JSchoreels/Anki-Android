**Changes since release 20**

For everyone:

- **A better small RWKV model.** Settings > kuma3 > RWKV model > Small is now JSchoreels' SM55: the same size as before, trained on 8,000 users instead of 5,000, and closer to the big model (about 0.5% worse in log loss instead of 0.7%). If you use Small, kuma3 rebuilds its RWKV state once after the update.
- **Showing the answer stops the question's audio.** Note types that play their audio from a script (the bear and MvJ templates, for example) kept playing the question's audio under the answer's.

From AnkiDroid (2.26 alphas):

- Browser: no more crash when you scroll while a search is refreshing.
- Background syncs use the network timeout again, so a bad connection can't leave one hanging.
- Study screen: no white flash when it opens in a dark theme.
- Tablets: tapping decks refreshes the study options panel instead of rebuilding it each time.
- Note types: the selection actions are visible on e-ink screens.
- Deck list: a memory leak fixed.

Also a lot of internal cleanup: less code, same behaviour.

<!-- es -->
**Cambios desde la versión 20**

Para todos:

- **Un modelo RWKV pequeño mejor.** Ajustes > kuma3 > RWKV model > Small ahora es SM55 de JSchoreels: del mismo tamaño que antes, entrenado con 8.000 usuarios en vez de 5.000, y más cerca del modelo grande (alrededor de 0,5 % peor en log loss en vez de 0,7 %). Si usas Small, kuma3 reconstruye su estado RWKV una vez después de actualizar.
- **Mostrar la respuesta detiene el audio de la pregunta.** Los tipos de nota que reproducen su audio desde un script (por ejemplo las plantillas bear y MvJ) seguían reproduciendo el audio de la pregunta debajo del de la respuesta.

De AnkiDroid (alphas de la 2.26):

- Explorador: ya no se cierra si desplazas mientras se actualiza una búsqueda.
- Las sincronizaciones en segundo plano vuelven a usar el tiempo de espera de red, así que una mala conexión no puede dejar una colgada.
- Pantalla de estudio: sin destello blanco al abrirse con un tema oscuro.
- Tabletas: tocar mazos actualiza el panel de opciones de estudio en vez de reconstruirlo cada vez.
- Tipos de nota: las acciones de selección se ven en pantallas de tinta electrónica.
- Lista de mazos: corregida una fuga de memoria.

Además mucha limpieza interna: menos código, el mismo comportamiento.
