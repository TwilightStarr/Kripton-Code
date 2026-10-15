package com.kriptoncod.app.domain

enum class AuthType { API_KEY, GOOGLE, EMAIL }

enum class AccountStatus { READY, WAITING, ERROR }

enum class AssignmentMode { AUTO, MANUAL }

enum class JobState { PENDING, RUNNING, WAITING, DONE, FAILED }

enum class RunStatus {
    PREPARING, RUNNING, PUSHING, CI_WAITING, FIXING, DONE, FAILED, CANCELLED,
}
