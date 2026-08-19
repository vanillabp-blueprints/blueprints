A location without migrations, used by MissingTableIT to play the deployment which applied
everything except VanillaBP's part. Flyway reads only files named V<version>__... or R__..., so
this file is invisible to it - and a directory has to contain something to exist in a JAR.
