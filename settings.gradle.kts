pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "KriptonCod"
include(":app")

// Sora Editor (https://github.com/Rosemoe/sora-editor, LGPL-2.1): kaynak olarak eklenmiş yerel modüller.
include(":sora-editor", ":sora-language-textmate", ":sora-oniguruma")
project(":sora-editor").projectDir = file("sora/editor")
project(":sora-language-textmate").projectDir = file("sora/language-textmate")
project(":sora-oniguruma").projectDir = file("sora/oniguruma")
