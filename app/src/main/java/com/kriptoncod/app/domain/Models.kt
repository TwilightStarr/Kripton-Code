package com.kriptoncod.app.domain

/**
 * Hesap türü. [PROXY]: CLIProxyAPI uyumlu (OpenAI biçimli) kullanıcı proxy'si — taban adres + anahtar.
 * Anahtar her türde KeyVault'ta saklanır.
 * [WEB_SESSION]: sağlayıcının web sohbetine WebView ile girilen oturum (API anahtarı yok); çerezler KeyVault'ta.
 */
enum class AuthType { API_KEY, GOOGLE, EMAIL, KIMI, PROXY, WEB_SESSION }

enum class AccountStatus { READY, WAITING, ERROR }

enum class AssignmentMode { AUTO, MANUAL }

enum class JobState { PENDING, RUNNING, WAITING, DONE, FAILED }

enum class RunStatus {
    PREPARING, RUNNING, PUSHING, CI_WAITING, FIXING, DONE, FAILED, CANCELLED, PAUSED,
}

/**
 * Düğüm grafiğindeki düğüm türü (bir iş = bir düğüm).
 *  PLAN -> WORK (katalog başına çok sayıda) -> CATALOG_MERGE (katalog başına bir) -> FINAL_MERGE (tek).
 */
enum class JobKind { PLAN, WORK, CATALOG_MERGE, FINAL_MERGE }
