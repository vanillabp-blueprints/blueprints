The application's own migrations go here. This blueprint owns no table of its own, so the
directory holds nothing but this file, and with an embedded engine the engine's scripts join the
same Flyway instance from db/migration-camunda7. Flyway reads only files named V<version>__... or
R__..., so this file is invisible to it - and a directory has to contain something to exist in a
JAR.
