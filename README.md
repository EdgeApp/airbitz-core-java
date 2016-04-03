# airbitz-core-java

This repository contains the java bindings to the [airbitz-core][core] library.

## Setup

Just add `airbitz-core-android` to your dependencies in your `build.gradle`.

    dependencies {
        compile 'com.airbitz:airbitz-core-android:0.0.1@aar'
        ...
    }

## Documentation

Docs will be available soon.

## Building

First have [airbitz-core][core] cloned locally at the same level as this repository.

    ./gradlew buildAirbitzMainnet assemble

If all goes well, you can publish to your local maven.

    ./gradlew publishToMavenLocal

[core]: https://github.com/airbitz/airbitz-core
