plugins {
  id("java")
  id("org.jetbrains.intellij") version "1.17.0"
}

group = "com.picoedge"
version = "1.0.0"

repositories {
  mavenCentral()
  maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
  maven { url = uri("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
}

dependencies {
  implementation("org.apache.commons:commons-lang3:3.14.0")
}

// Configure Gradle IntelliJ Plugin
intellij {
  version.set("2023.2.5")
  type.set("IC") // Target IDE Platform (IntelliJ Community)

  plugins.set(listOf("java"))
}

// Configure runIde task to use a local IntelliJ installation if available
tasks {
  runIde {
    // Specify path to local IntelliJ IDEA installation (adjust path as needed)
    ideDir.set(file("/path/to/local/intellij/ideaIC-2023.2.5"))
  }

  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }

  buildPlugin {
    dependsOn("patchPluginXml")
  }

  patchPluginXml {
    version.set(project.version.toString())
    sinceBuild.set("232")
    untilBuild.set("299.*")
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}