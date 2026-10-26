# ARGENTAS · Control de hotspot

Proyecto Android con dos APK:
- **ARGENTAS-Invitados.apk**: control remoto desde el celular.
- **ARGENTAS-Control-Tablet.apk**: servidor y panel en la tablet.

## Compilar y descargar
GitHub Actions compila ambos módulos. El artefacto **ARGENTAS-APKs** contiene las dos APK en la raíz del ZIP, con nombres claros y sin carpetas anidadas.

## Limitación importante de Android
Android/HyperOS restringe el encendido y apagado programático del punto de acceso a aplicaciones privilegiadas o a flujos autorizados por el sistema. La app intenta el método heredado; si el sistema lo bloquea, el servidor abre los ajustes del hotspot en la tablet para que el usuario confirme la acción. La app no debe mostrar el hotspot como conectado sin confirmación real.

El estado del celular conectado es independiente del estado real del hotspot. El servidor deja de considerar conectado al celular tras 15 segundos sin un comando autenticado.

## Red local
Ambos dispositivos deben poder comunicarse por la misma red local. El protocolo TCP de este prototipo no está cifrado; utilizar solo en una red de confianza.
