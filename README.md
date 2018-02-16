# DaKo-Backend
Server-Teil des DaKo-Frameworks. Stellt zwei WebSocket-Serverendpunkte bereit, die die beiden Implementierungsvarianten des DaKo-Frameworks (simple/advanced) repräsentieren.
## Bedienung :computer:
Die Anwendung ist als Maven Projekt konzipiert. Durch den Aufruf von `mvn clean package` wird eine WAR-Datei erstellt, die auf einem Apache Tomcat Catalina 9 bereitgestellt werden kann. Soll das Frontend ebenfalls von diesem ausgeliefert werden, so muss das Projekt [dako-frontend](https://github.com/hm-aweink/dako-frontend) mit dem Befehl `ng build –prod –base-href /dako-backend/` kompiliert und anschließend in den Ordner `webapp` des Backends kopiert werden.

