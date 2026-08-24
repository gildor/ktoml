# How to release a new version of ktoml

* You should have permissions to push to the main repo
* Run `./gradlew apiCheck` before tagging. The committed JVM and KLib dumps include both supported
  APIs and binary-public declarations marked `@InternalKtomlApi`. If an intentional additive API
  change is missing, run `./gradlew apiDump`, review the diff, and commit it. Do not approve removed
  or changed signatures without an explicit compatibility and migration decision.
* Simply create a new git tag with format `v*` and push it. The GitHub workflow will perform the release automatically.
  
  For example:
  ```bash
  $ git tag v1.0.0
  $ git push origin v1.0.0
  ```
  
After the release workflow starts, the version number is determined from the tag. Signed binaries are uploaded and
promoted to Maven Central, then a GitHub release is created with generated release notes.

The release workflow uses `closeAndReleaseSonatypeStagingRepository`, so Maven Central promotion is automatic after
Sonatype validation passes; there is no manual staging step. Monitor both workflow jobs and verify the published
artifacts on Maven Central before announcing the release.
