**Changes since release 18**

For everyone:

- **New: a small RWKV model.** Settings > kuma3 > RWKV model: "Big" (the desktop fork's model, still the default) or "Small", JSchoreels' 331k-parameter model (8x smaller, shipped with his permission). Small is about 0.7% less accurate (log loss on 1,000 held-out users), uses about a quarter of the memory and rebuilds about 3x faster: on a Tab S7+ a 203k-review rebuild took 14 s instead of 46 s, and on a Pixel 8a a 4M-review, 100k-card collection fits in under 1 GB, where the big model needs 2.6 GB. Pick it on phones with little memory or for very large collections. Changing the model rebuilds the RWKV state once.
- **Faster cards in the reviewer.** When the next card has the same note type, only the card's content is swapped instead of reloading the whole page (on the tablet: question about 85 to 60 ms, answer about 85 to 36 ms). Cards whose scripts need a fresh page still load it.
- Settings > kuma3 > Reviewer > "Fade answer buttons" (on by default): off, the answer buttons appear at once instead of fading in over 200 ms.

Anki XP theme:

- The next card comes sooner: XP's progress is saved when you leave the reviewer instead of after every answer (on the tablet about 0.3 s to 0.2 s per answer).
- The session report shows up after coming back from Settings (it was lost).

<!-- es -->
**Cambios desde la versión 18**

Para todos:

- **Nuevo: un modelo RWKV pequeño.** Settings > kuma3 > RWKV model: "Big" (el modelo del fork de escritorio, sigue siendo el predeterminado) o "Small", el modelo de 331k parámetros de JSchoreels (8 veces más pequeño, incluido con su permiso). Small es alrededor de un 0,7% menos preciso (log loss en 1.000 usuarios de prueba), usa más o menos una cuarta parte de la memoria y reconstruye unas 3 veces más rápido: en una Tab S7+, reconstruir 203.000 repasos tardó 14 s en vez de 46 s, y en un Pixel 8a una colección de 4 millones de repasos y 100.000 tarjetas cabe en menos de 1 GB, donde el modelo grande necesita 2,6 GB. Elígelo en teléfonos con poca memoria o para colecciones muy grandes. Cambiar de modelo reconstruye el estado RWKV una vez.
- **Tarjetas más rápidas en los repasos.** Cuando la siguiente tarjeta tiene el mismo tipo de nota, solo se cambia el contenido de la tarjeta en vez de recargar toda la página (en la tableta: pregunta de unos 85 a 60 ms, respuesta de unos 85 a 36 ms). Las tarjetas cuyos scripts necesitan una página nueva la siguen cargando.
- Settings > kuma3 > Reviewer > "Fade answer buttons" (activado por defecto): desactivado, los botones de respuesta aparecen enseguida en vez de aparecer poco a poco durante 200 ms.

Tema Anki XP:

- La siguiente tarjeta llega antes: el progreso de XP se guarda al salir de los repasos en vez de después de cada respuesta (en la tableta, de unos 0,3 s a 0,2 s por respuesta).
- El informe de la sesión aparece al volver de Settings (se perdía).
