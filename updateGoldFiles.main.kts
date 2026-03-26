/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

private val amperRootDir: Path = __FILE__.toPath().absolute().parent
private val drModuleDir = amperRootDir / "sources/frontend/dr"
private val schemaModuleDir = amperRootDir / "sources/frontend/schema"
private val cliTestsModuleDir = amperRootDir / "sources/test-integration/amper-cli-test"
private val testResourcesDir = schemaModuleDir / "testResources"
private val testResourcePathRegex = Regex("(${Regex.escape(testResourcesDir.absolutePathString())})[^),\"'\n\r]*")
private val MAX_ATTEMPTS = 10

fun updateGoldFiles() {
    updateDrGoldFiles()
    updateSchemaGoldFiles()
    updateCliGoldFiles()
}

fun updateDrGoldFiles() {
    updateGoldFilesUntilSuccess(
        sectionName = "dr module",
        goldFilesRoot = drModuleDir,
    ) {
        runAmperCli("test", "-m", "dr")
    }
}

fun updateSchemaGoldFiles() {
    updateGoldFilesUntilSuccess(
        sectionName = "schema module",
        goldFilesRoot = schemaModuleDir,
    ) {
        runAmperCli("test", "-m", "schema")
    }
}

fun updateCliGoldFiles() {
    updateGoldFilesUntilSuccess(
        sectionName = "amper-cli-test module",
        goldFilesRoot = cliTestsModuleDir,
    ) {
        runAmperCli("test", "-m", "amper-cli-test", "--include-classes=*.ShowSettingsCommandTest", "--include-classes=*.ShowDependenciesCommandTest")
    }
}

fun updateGoldFilesUntilSuccess(sectionName: String, goldFilesRoot: Path, runTests: () -> Int) {
    println("=== Updating $sectionName gold files ===")
    repeat(MAX_ATTEMPTS) { attemptIndex ->
        val attemptNumber = attemptIndex + 1
        println("Attempt $attemptNumber/$MAX_ATTEMPTS: running tests...")
        val exitCode = runTests()
        if (exitCode == 0) {
            println("Tests passed for $sectionName.")
            println()
            return
        }

        val updatedFilesCount = updateTmpFilesUnder(goldFilesRoot)
        if (updatedFilesCount == 0) {
            println("Tests failed for $sectionName, but no .tmp files were found.")
        } else {
            println("Updated $updatedFilesCount gold file(s) for $sectionName.")
        }
        println()
    }

    error("Failed to update $sectionName gold files after $MAX_ATTEMPTS attempts.")
}

fun updateTmpFilesUnder(root: Path): Int {
    var updatedFilesCount = 0
    root.walk()
        .filter { it.name.endsWith(".tmp") }
        .forEach { tmpResultFile ->
            updateGoldFileFor(tmpResultFile)
            updatedFilesCount++
        }
    return updatedFilesCount
}

@Suppress("PROCESS_BUILDER_START_LEAK")
fun runAmperCli(vararg args: String): Int {
    val isWindows = System.getProperty("os.name").startsWith("Win", ignoreCase = true)
    val amperScript = amperRootDir.resolve(if (isWindows) "amper.bat" else "amper")
    return ProcessBuilder(amperScript.pathString, *args).inheritIO().start().waitFor()
}

fun updateGoldFileFor(tmpResultFile: Path) {
    val realGoldFile = goldFileFor(tmpResultFile)
    println("Replacing ${realGoldFile.name} with the contents of ${tmpResultFile.name}")
    val newGoldContent = tmpResultFile.contentsWithVariables()
    realGoldFile.writeText(newGoldContent)
    tmpResultFile.deleteExisting()
}

fun goldFileFor(tmpResultFile: Path): Path = tmpResultFile.resolveSibling(tmpResultFile.name.removeSuffix(".tmp"))

/**
 * Gets the contents of this temp file with the paths replaced with variables, as they are usually in gold files to
 * make them machine-/os-independent.
 */
fun Path.contentsWithVariables(): String = readText().replace(testResourcePathRegex) { match ->
    // See variable substitution in schema/helper/util.kt
    match.value
        // {{ testResources }} is used for the "base" path, which is the dir containing the gold file
        .replace(parent.absolutePathString(), "{{ testResources }}")
        // {{ testProcessDir }} is the dir in which tests are run, which is the schema module
        .replace(schemaModuleDir.absolutePathString(), "{{ testProcessDir }}")
        .replace(File.separator, "{{ fileSeparator }}")
}

updateGoldFiles()
