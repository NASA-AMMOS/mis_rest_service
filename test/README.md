# Testing

These test scripts and procedures require a `bash` (or `bash` compatible) shell with the following utilities

* `curl`
* `java` and `javac` version 21 or greater
* `mvn` 3.9 or greater
* `diff`
* `tr`

## Data Preparation

Run `test/download-data.sh` to download some Mars 2020 test images.  These are not checked into git due to their size.  They will be downloaded into the `test/` directory without any additional subdirectories.

## Automated CLI Test

Run `test/build-jar.sh`, then `test/auto-test-cli.sh`.  It will print SUCCESS or FAILURE.

## Automated Tomcat Localhost Test

Run `test/download-tomcat.sh`, `test/build-war.sh`, then `test/auto-test-servlet.sh`. It will print SUCCESS or FAILURE.

