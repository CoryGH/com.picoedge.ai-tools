### picoEdge AI-Tools IntelliJ Plugin
Enhances IntelliJ-based IDEs (including Android Studio) with advanced file handling actions in the Project view context menu. This plugin streamlines workflows for developers, especially those integrating AI tools, by providing formatted clipboard operations for file contents and diffs.

#### Features
* **Copy Contents:** Copies the contents of selected files and directories (recursively) to the clipboard, formatted with file headers, language-specific code blocks, and an ASCII file tree. Customizable via a project-level .env file.
* **Paste Contents:** Parses clipboard content in the "Copy Contents" format and creates or updates files in the selected directory, maintaining a .latest version for diff tracking.

#### Prerequisites
* **Java JDK 17:** Required for compiling and running the plugin. Download from Oracle or Adoptium.
* **IntelliJ IDEA Community/Ultimate 2023.2+:** The IDE where the plugin will be developed and tested. Download from JetBrains.
* **Gradle 8.10:** Used for building the plugin. Can be installed globally or locally via the Gradle Wrapper (preferred, see below). Download from Gradle Releases.
* **Git (optional):** For cloning the repository. Download from Git.
* 