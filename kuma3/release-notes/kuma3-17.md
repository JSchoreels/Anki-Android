**Changes since release 16**

For everyone:

- **No more "Processing…" on the deck list with RWKV.** Coming back from Settings or the card browser, or opening the app, no longer waits on RWKV. With a big review history this was seconds to minutes: on a 3.8M-review collection a deck list refresh took 20 s and is now 0.1 s, and each answer in the reviewer took 2.5 s and is now instant (measured on a PC; phones were slower still). At start the deck list now shows first, and RWKV's order and counts arrive a few seconds later, as before.
- The RWKV state precision setting no longer offers int4: on long review histories its state broke down mid-rebuild, RWKV switched itself off and the whole history was replayed again at every start. If you had picked int4, kuma3 now uses int8 (the default) and rebuilds once.
- Changing the RWKV state precision while a rebuild is running now rebuilds with the new precision.
- An RWKV rebuild keeps running with the screen off. On Samsung phones and tablets, set kuma3's battery use to "Unrestricted" (Settings > Apps > kuma3 > Battery), or Samsung pauses the rebuild while the screen is off.
- kuma3 shows up in the app list of Google TV devices.
- kumapie can open a card you just mined in kuma3's card previewer.

Anki XP theme:

- Undo after an answer undoes the answer again: the XP progress saved after each answer no longer takes Undo's place.
- The session report and other XP windows no longer jump up and down while they load.

<!-- es -->
**Cambios desde la versión 16**

Para todos:

- **Se acabó el "Processing…" en la lista de mazos con RWKV.** Volver desde Settings o desde el explorador de tarjetas, o abrir la app, ya no espera a RWKV. Con un historial de repasos grande eran segundos o minutos: en una colección de 3,8 millones de repasos, refrescar la lista de mazos tardaba 20 s y ahora 0,1 s, y cada respuesta en los repasos tardaba 2,5 s y ahora es instantánea (medido en un PC; en los teléfonos era aún más lento). Al abrir, la lista de mazos aparece primero, y el orden y los conteos de RWKV llegan unos segundos después, como antes.
- El ajuste de precisión del estado RWKV ya no ofrece int4: con historiales largos su estado se rompía a mitad de la reconstrucción, RWKV se apagaba solo y todo el historial se volvía a repetir en cada inicio. Si habías elegido int4, kuma3 ahora usa int8 (el valor por defecto) y reconstruye una vez.
- Cambiar la precisión del estado RWKV mientras se reconstruye ahora reconstruye con la nueva precisión.
- Una reconstrucción de RWKV sigue con la pantalla apagada. En teléfonos y tabletas Samsung, pon el uso de batería de kuma3 en "Sin restricciones" (Ajustes > Aplicaciones > kuma3 > Batería), o Samsung pausa la reconstrucción mientras la pantalla está apagada.
- kuma3 aparece en la lista de apps de los dispositivos Google TV.
- kumapie puede abrir en el visor de tarjetas de kuma3 una tarjeta que acabas de minar.

Tema Anki XP:

- Deshacer después de una respuesta vuelve a deshacer la respuesta: el progreso XP que se guarda tras cada respuesta ya no ocupa el lugar de Deshacer.
- El informe de la sesión y las otras ventanas XP ya no saltan arriba y abajo mientras cargan.
