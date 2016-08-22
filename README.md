# airbitz-core-java

This repository contains the java bindings to the [airbitz-core][core] library.

## Setup Using Gradle/Maven. (Does not require cloning this repo)

Just add `airbitz-core-android` to your dependencies in your `build.gradle`. For now, use the develop branch builds to be compatible with the next Airbitz release v2.2.0

    dependencies {
        compile 'co.airbitz:airbitz-core-java-develop:0.9.+'
        compile 'co.airbitz:airbitz-core-android-develop:0.9.+@aar'
        ...
    }

Then add the Airbitz maven repo to your list of repositories

    allprojects {
        repositories {
            ...
    
            maven {
                url "https://developer.airbitz.co/maven"
            }
        }
    }

## Use with React Native

Install the iOS/ObjC side of the bridge first. Follow instructions from [airbitz-core-objc][core-objc]

Add Gson to your dependencies

    dependencies {
        ...
        compile 'co.airbitz:airbitz-core-java:0.9.+'
        compile 'co.airbitz:airbitz-core-android:0.9.+@aar'
        compile 'com.google.code.gson:gson:2.6.2'
    }

Add imports for React Native Bridge to your MainActivity.java

    import com.facebook.react.ReactPackage;
    import com.facebook.react.shell.MainReactPackage;
    
    import co.airbitz.AirbitzCoreRCT.AirbitzCoreRCT;
    import co.airbitz.AirbitzCoreRCT.AirbitzCoreRCTPackage;

    import java.util.Arrays;
    import java.util.List;
    
Add getPackages to the MainActivity class

    public class MainActivity extends ReactActivity {
        ...
        protected List<ReactPackage> getPackages() {
            return Arrays.<ReactPackage>asList(
                    new MainReactPackage(),
                    new AirbitzCoreRCTPackage() // include it in getPackages
            );
        }
    }

Copy the Java bridge files from this repo to your project

    airbitz-core-java/ReactBridge/java/co/airbitz/AirbitzCoreRCT/*

to

    /yourapp-repo-name/YourApp/android/app/src/main/java/co/airbitz/AirbitzCoreRCT/*

Sample javascript code for using AirbitzCore from ReactNative can be seen in the following repo

https://github.com/Airbitz/airbitz-react-test

See the file ```abc-react-test.js```

## Documentation

https://developer.airbitz.co/android

## Building (Build the entire Java and C++ core library)

First have [airbitz-core][core] cloned locally at the same level as this repository.

    ./gradlew buildAirbitzMainnet assemble

If all goes well, you can publish to your local maven.

    ./gradlew publishCoreDevelopPublicationToMavenLocal
    ./gradlew publishDevelopAndroidPublicationToMavenLocal
    ./gradlew publishToMavenLocal

[core]: https://github.com/airbitz/airbitz-core
[core-objc]: https://github.com/Airbitz/airbitz-core-objc/tree/develop
