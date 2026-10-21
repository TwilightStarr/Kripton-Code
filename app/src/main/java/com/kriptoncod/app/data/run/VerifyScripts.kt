package com.kriptoncod.app.data.run

/**
 * Proje türüne göre CI doğrulama betiği (`.kriptoncod/verify.sh`) ve test kuralları.
 *
 * Projenin kendi `.kriptoncod/verify.sh` dosyası varsa ona dokunulmaz. Yoksa tür algılanır ve derleme + test
 * koşan bir betik eklenir. Test dosyası hiç yoksa betik KASITLI olarak başarısız olur ("TEST YOK"): aksi halde
 * "CI geçti" hiçbir şeyi doğrulamamış olur. Eksik testleri [TestSynthesizer] AI'a yazdırır.
 */
object VerifyScripts {

    const val PATH = ".kriptoncod/verify.sh"

    enum class ProjectType { NODE, PYTHON, GRADLE, MAVEN, GO, CARGO, UNKNOWN }

    /** Kök dizindeki işaret dosyalarına göre proje türü. */
    fun detect(paths: Collection<String>): ProjectType {
        val root = paths.filter { '/' !in it }.toSet()
        return when {
            "package.json" in root -> ProjectType.NODE

            root.any { it in setOf("build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts") } -> ProjectType.GRADLE

            "pom.xml" in root -> ProjectType.MAVEN

            "go.mod" in root -> ProjectType.GO

            "Cargo.toml" in root -> ProjectType.CARGO

            root.any { it in setOf("pyproject.toml", "requirements.txt", "setup.py", "setup.cfg") } ||
                root.any { it.endsWith(".py") } -> ProjectType.PYTHON

            else -> ProjectType.UNKNOWN
        }
    }

    private fun isTestPathStrict(type: ProjectType, path: String): Boolean {
        val name = path.substringAfterLast('/')
        val dirs = path.split('/').dropLast(1)
        return when (type) {
            ProjectType.NODE -> dirs.any { it in setOf("__tests__", "tests", "test", "spec") } ||
                Regex("""\.(test|spec)\.[cm]?[jt]sx?$""").containsMatchIn(name)

            ProjectType.PYTHON -> (dirs.any { it == "tests" || it == "test" } && name.endsWith(".py")) ||
                Regex("""^test_.*\.py$|.*_test\.py$""").matches(name)

            ProjectType.GRADLE, ProjectType.MAVEN -> path.startsWith("src/test/") || "/src/test/" in path

            ProjectType.GO -> name.endsWith("_test.go")

            ProjectType.CARGO -> path.startsWith("tests/") && name.endsWith(".rs")

            ProjectType.UNKNOWN -> false
        }
    }

    /**
     * Yapay zekanın YAZABİLECEĞİ yollar: yalnız test dosyaları. Kaynak kodun, workflow'un ve doğrulama
     * betiğinin üzerine yazılması engellenir. `package.json` gibi yapılandırma değişikliği bu yoldan yapılmaz.
     */
    fun isAllowedTestPath(type: ProjectType, path: String): Boolean = !path.startsWith(".github/") && path != PATH && !path.startsWith("_kripton/") && isTestPathStrict(type, path)

    /** Projede (algılanan türe göre) en az bir gerçek test var mı. */
    fun hasTests(type: ProjectType, files: Map<String, ByteArray>): Boolean {
        if (type == ProjectType.UNKNOWN) return false
        if (files.keys.any { isTestPathStrict(type, it) }) return true
        if (type == ProjectType.CARGO) { // Rust'ta birim testler kaynak dosyanın içindedir
            return files.any { (p, b) -> p.endsWith(".rs") && String(b, Charsets.UTF_8).contains("#[test]") }
        }
        if (type == ProjectType.NODE) { // package.json'da gerçek bir test betiği tanımlıysa yeterli
            val pkg = files["package.json"]?.let { String(it, Charsets.UTF_8) }.orEmpty()
            val script = Regex(""""test"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(pkg)?.groupValues?.get(1)
            if (script != null && !script.contains("no test specified")) return true
        }
        return false
    }

    /** Türe özel betik; [ProjectType.UNKNOWN] için null (workflow zayıf denetime düşer ve UYARI basar). */
    fun scriptFor(type: ProjectType): String? = when (type) {
        ProjectType.NODE -> NODE
        ProjectType.PYTHON -> PYTHON
        ProjectType.GRADLE -> GRADLE
        ProjectType.MAVEN -> MAVEN
        ProjectType.GO -> GO
        ProjectType.CARGO -> CARGO
        ProjectType.UNKNOWN -> null
    }?.replace('§', '$')?.trimIndent()?.plus("\n")

    /** AI'a test yazdırırken türe özel kurallar (hangi çatı, nereye, nasıl adlandırılacak). */
    fun testGuidance(type: ProjectType): String = when (type) {
        ProjectType.NODE ->
            "Node.js projesi. Testleri `tests/` altına `*.test.js` (ya da projede TypeScript/Jest/Vitest zaten " +
                "kuruluysa o çatıyla `*.test.ts`) olarak yaz. Başka çatı yoksa yalnızca Node'un yerleşik `node:test` ve " +
                "`node:assert` modüllerini kullan (ek bağımlılık gerekmez)."

        ProjectType.PYTHON ->
            "Python projesi. Testleri `tests/test_<modül>.py` olarak pytest ile yaz; yalnızca standart kütüphane ve pytest kullan."

        ProjectType.GRADLE ->
            "Gradle projesi. Testleri `src/test/...` altına, kaynakla aynı paket yoluyla yaz (Kotlin ise kotlin.test/JUnit, " +
                "Java ise JUnit 4/5). Android bağımlı olmayan saf mantığı test et; bağımlılık eklemen gerekirse test yazma."

        ProjectType.MAVEN ->
            "Maven projesi. Testleri `src/test/java/...` altına JUnit ile yaz; yeni bağımlılık varsayma."

        ProjectType.GO ->
            "Go projesi. Testleri ilgili paketin yanına `<dosya>_test.go` olarak standart `testing` paketiyle yaz."

        ProjectType.CARGO ->
            "Rust projesi. Entegrasyon testlerini `tests/<ad>.rs` altına yaz; yalnızca standart kütüphane ve projedeki bağımlılıkları kullan."

        ProjectType.UNKNOWN -> ""
    }

    // `§` karakteri `$` yerine yazılır (Kotlin ham dizgisinde `$` kaçışı okunaksız olurdu); scriptFor çevirir.
    private const val HEAD = """
        #!/usr/bin/env bash
        # Kripton Cod tarafından üretildi. Projeye kendi doğrulamanı koymak için bu dosyayı değiştir.
        set -euo pipefail
        no_tests() {
          echo "::error::TEST YOK: projede çalıştırılabilir test bulunamadı. Gerçek testler yazılmalı (derleme tek başına doğrulama sayılmaz)."
          exit 1
        }
    """

    private const val NODE = HEAD + """
        echo "== Node.js doğrulama =="
        has_script() {
          node -e "const s=(require('./package.json').scripts||{})['§1']; process.exit(s && !/no test specified/.test(s) ? 0 : 1)"
        }
        # Test yoksa bağımlılık kurmadan hemen başarısız ol.
        if has_script test; then
          MODE=npm
        elif [ -n "§(find . -path ./node_modules -prune -o -type f \( -name '*.test.js' -o -name '*.test.mjs' -o -name '*.spec.js' \) -print -quit)" ]; then
          MODE=node
        else
          no_tests
        fi
        if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; else npm install --no-audit --no-fund; fi
        if has_script build; then npm run build; else echo "build betiği yok, atlandı"; fi
        if [ "§MODE" = npm ]; then npm test; else node --test; fi
    """

    private const val PYTHON = HEAD + """
        echo "== Python doğrulama =="
        # Test yoksa bağımlılık kurmadan hemen başarısız ol.
        if [ -z "§(find . \( -path ./.venv -o -path ./node_modules \) -prune -o -type f \( -name 'test_*.py' -o -name '*_test.py' \) -print -quit)" ]; then
          no_tests
        fi
        python -m pip install --quiet --upgrade pip pytest
        if [ -f requirements.txt ]; then python -m pip install --quiet -r requirements.txt; fi
        if [ -f pyproject.toml ] || [ -f setup.py ]; then python -m pip install --quiet -e .; fi
        python -m compileall -q .
        python -m pytest -q
    """

    private const val GRADLE = HEAD + """
        echo "== Gradle doğrulama =="
        if [ -f gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then chmod +x gradlew; G=./gradlew; else G=gradle; fi
        if [ -z "§(find . -path '*/src/test/*' -type f -print -quit)" ]; then no_tests; fi
        if grep -rqs --include='build.gradle' --include='build.gradle.kts' 'com.android' .; then
          "§G" --no-daemon testDebugUnitTest assembleDebug
        else
          "§G" --no-daemon build
        fi
    """

    private const val MAVEN = HEAD + """
        echo "== Maven doğrulama =="
        if [ -z "§(find . -path '*/src/test/*' -type f -print -quit)" ]; then no_tests; fi
        mvn -B -q verify
    """

    private const val GO = HEAD + """
        echo "== Go doğrulama =="
        if [ -z "§(find . -name '*_test.go' -type f -print -quit)" ]; then no_tests; fi
        go vet ./...
        go test ./...
    """

    private const val CARGO = HEAD + """
        echo "== Rust doğrulama =="
        if [ -z "§(find tests -name '*.rs' -type f -print -quit 2>/dev/null)" ] && ! grep -rqs --include='*.rs' '#\[test\]' .; then no_tests; fi
        cargo build --quiet
        cargo test --quiet
    """
}
