The migrations of an embedded engine end up here while the application is built: the camunda7
profile of application/pom.xml takes Camunda's scripts out of the engine JAR and names them for
Flyway. With a remote engine the directory stays empty, and it exists in the classpath either way,
because Flyway resolves its locations at build time. Flyway reads only files named V<version>__...
or R__..., so this file is invisible to it.
