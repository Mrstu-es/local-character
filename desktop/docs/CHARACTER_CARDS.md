# Character Cards

El comando `import_character_card` acepta tarjetas v2 en JSON y PNG. En PNG se leen únicamente los chunks de texto `chara` (`tEXt`, `zTXt` o `iTXt`), se valida Base64 y se limita el archivo a 20 MB y la metadata a 2 MB.

Se conservan nombre, descripción, personalidad, escenario, primer saludo, mensajes de ejemplo, prompt de sistema, notas del creador, tags, saludos alternativos y entradas del lorebook. El registro se guarda localmente en la tabla `characters` como JSON, sin subir la imagen ni la tarjeta a ningún servicio.

La pantalla Personajes permite importar JSON/PNG, crear una tarjeta y elegir un avatar local. La exportación de tarjetas se mantiene como siguiente extensión de la interfaz; los datos importados no se descartan aunque todavía no se muestren todos los campos en el editor compacto.
