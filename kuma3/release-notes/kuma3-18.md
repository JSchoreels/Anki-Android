**Changes since release 17**

For everyone:

- **Faster answers.** On a Tab S7+ with 11,000 cards in one RWKV deck, the next card now shows about 0.3 s after you answer (it was 0.4 s). In a deck outside your main RWKV deck (a grammar deck, say) it took over 2 s per card, because every answer recounted the whole deck list: now it is under 0.1 s.
- Cards whose note type has scripts with brackets (the Migaku Japanese pitch-accent template, for example) no longer load the math renderer on every card. Cards with real `\( \)` or `\[ \]` math still do.
- **Faster deck list with RWKV.** Opening a subdeck reuses the scores its parent deck already has (2.3 s to 0.1 s on the tablet), the "RWKV ready" refresh no longer scores everything twice, and the review heatmap no longer recounts your whole history after each review (1.3 s to instant on a 3.8M-review collection).
- With "approximate" RWKV queue updates, scores could stay partly stale for up to 30 minutes after reviewing; they are re-scored now.
- The RWKV state precision setting offers int4 again ("int4 cards, int8 notes"): about a third less memory than int8, close in accuracy. The int4 crash on long histories in release 16 is fixed.
- Deck options' RWKV line shows how many cards are scored (it said 0).

Anki XP theme:

- The "cards left" counter in the reviewer reads the reviewer's own count instead of recounting the deck list after every answer.

<!-- es -->
**Cambios desde la versión 17**

Para todos:

- **Respuestas más rápidas.** En una Tab S7+ con 11.000 tarjetas en un mazo RWKV, la siguiente tarjeta aparece unos 0,3 s después de responder (antes 0,4 s). En un mazo fuera de tu mazo RWKV principal (por ejemplo, uno de gramática) tardaba más de 2 s por tarjeta, porque cada respuesta volvía a contar toda la lista de mazos: ahora es menos de 0,1 s.
- Las tarjetas cuyo tipo de nota tiene scripts con corchetes (por ejemplo, la plantilla japonesa de acento tonal de Migaku) ya no cargan el renderizador de fórmulas en cada tarjeta. Las tarjetas con fórmulas de verdad, `\( \)` o `\[ \]`, lo siguen cargando.
- **Lista de mazos más rápida con RWKV.** Abrir un submazo reutiliza las puntuaciones que ya tiene su mazo padre (de 2,3 s a 0,1 s en la tableta), el refresco de "RWKV listo" ya no puntúa todo dos veces, y el mapa de actividad ya no vuelve a contar todo tu historial después de cada repaso (de 1,3 s a instantáneo en una colección de 3,8 millones de repasos).
- Con las actualizaciones "aproximadas" de la cola RWKV, las puntuaciones podían quedar en parte desactualizadas hasta 30 minutos después de repasar; ahora se vuelven a puntuar.
- El ajuste de precisión del estado RWKV vuelve a ofrecer int4 ("int4 tarjetas, int8 notas"): un tercio menos de memoria que int8, con una precisión parecida. Está arreglado el fallo de int4 con historiales largos de la versión 16.
- La línea de RWKV en las opciones del mazo muestra cuántas tarjetas están puntuadas (decía 0).

Tema Anki XP:

- El contador de "tarjetas restantes" en los repasos usa el conteo de los propios repasos en vez de volver a contar la lista de mazos después de cada respuesta.
