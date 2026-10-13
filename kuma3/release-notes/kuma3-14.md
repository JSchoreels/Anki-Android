**Changes since release 13**

- Updated to the desktop fork's build 102 (JSchoreels FSRS7 fork, was build 100). Among its changes: RWKV ignores a card's reviews from before it was reset (Forget), FSRS review queues respect "interday learning" priority, and deck options have a tidier Scheduler panel with inline help and an "Apply schedule to all presets" button.
- RWKV memory is kept as int8 by default, like the desktop fork: about a third of the memory of full precision, with the same accuracy (36 users of the open anki-revlogs-10k dataset, 3.7M reviews: same log loss; single predictions differ by 0.02 percentage points on average). On a Pixel 8a with a 22k-card collection: 505 MB instead of 1.69 GB, and a 337 MB state file instead of 1.1 GB.
- New: Settings > kuma3 > RWKV state precision: int8 (default), f32 (full precision) or int4 (experimental, less memory, less accurate). A change rebuilds the RWKV state right away.
- An RWKV rebuild keeps going when you switch to another app; before, Android paused it until you came back. A "Preparing RWKV" notification shows its progress, and "RWKV ready" tells you when it's done, with how many reviews and how long it took.
- This update rebuilds the RWKV state once from your review history (the new state format).

<!-- es -->
**Cambios desde la versión 13**

- Actualizado a la versión 102 del fork de escritorio (el fork FSRS7 de JSchoreels; antes la 100). Entre sus cambios: RWKV ignora los repasos de una tarjeta anteriores a reiniciarla (Olvidar), las colas de repaso de FSRS respetan la prioridad del "aprendizaje entre días", y las opciones del mazo tienen un panel del programador más ordenado, con ayuda en línea y un botón "Apply schedule to all presets".
- La memoria de RWKV se guarda en int8 por defecto, como en el fork de escritorio: más o menos un tercio de la memoria de la precisión completa, con la misma exactitud (36 usuarios del conjunto abierto anki-revlogs-10k, 3,7 millones de repasos: misma log loss; las predicciones cambian 0,02 puntos porcentuales de media). En un Pixel 8a con una colección de 22k tarjetas: 505 MB en vez de 1,69 GB, y un archivo de estado de 337 MB en vez de 1,1 GB.
- Nuevo: Settings > kuma3 > RWKV state precision: int8 (por defecto), f32 (precisión completa) o int4 (experimental, menos memoria, menos exacto). Al cambiarlo, el estado RWKV se reconstruye en el momento.
- La reconstrucción de RWKV sigue aunque cambies a otra app; antes Android la pausaba hasta que volvías. Una notificación "Preparing RWKV" muestra el progreso, y "RWKV ready" avisa cuando termina, con cuántos repasos y cuánto tardó.
- Esta actualización reconstruye el estado RWKV una vez desde tu historial de repasos (el formato nuevo del estado).
