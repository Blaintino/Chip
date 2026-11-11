# Cloud-Session-Hilfen

`repos.init.gradle`: In Cloud-Sessions drosselt `repo.maven.apache.org` oft (HTTP 429). Das Init-Skript ersetzt die
Repositories durch `google()`, `repo1.maven.org` und den Gradle Plugin Portal:

```bash
./gradlew --init-script docs/cloud/repos.init.gradle testDebugUnitTest
```

Hat nicht verlässlich geholfen; das CI (`.github/workflows/build-apk.yml`) bleibt die maßgebliche Prüfung.
Nur lokal verwenden, nicht in `settings.gradle.kts` übernehmen.
