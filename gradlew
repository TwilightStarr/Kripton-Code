#!/bin/sh
# Basit wrapper başlatıcı. `gradle wrapper --gradle-version 8.9` resmi sürümüyle değiştirir.
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
exec "${JAVA_HOME:+$JAVA_HOME/bin/}java" -Dorg.gradle.appname=gradlew \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
