pluginManagement {
    repositories {
        maven {
            url = uri("file:///workspace/2c374d04-cb0c-4045-8c80-c1b0f1ec52d4/sessions/agent_0dd2f075-37e1-4263-861f-c7c4094ef7eb/local-plugins")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("file:///workspace/2c374d04-cb0c-4045-8c80-c1b0f1ec52d4/sessions/agent_0dd2f075-37e1-4263-861f-c7c4094ef7eb/local-plugins")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "OmniSuite"
include(":app")
