/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.contexts

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.INDETERMINATE
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_LESS_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.IS_MORE_SPECIFIC
import org.jetbrains.amper.frontend.contexts.ContextsInheritance.Result.SAME
import java.nio.file.Path

class PathCtx(val path: VirtualFile, override val trace: Trace? = null) : Context {
    override fun withoutTrace() = PathCtx(path)
    override fun toString() = path.path
}

/**
 * Compares path contexts based on whether they belong to the module file or to templates.
 *
 * The module file is always more specific than any template. Templates are unordered relative to each other
 * (INDETERMINATE), so conflicting values between templates are detected and reported as errors.
 *
 * TODO: When nested templates are introduced, the comparison between modules and templates will use
 *  topological ordering of the application graph (a template has precedence over its dependencies).
 */
class PathInheritance(
    private val templatePaths: Set<Path>,
    modulePath: VirtualFile,
) : ContextsInheritance<PathCtx> {
    private val modulePathString = modulePath.toNioPath()

    override fun Collection<PathCtx>.compareContexts(other: Collection<PathCtx>): ContextsInheritance.Result {
        val thisPaths = mapTo(mutableSetOf()) { it.path.toNioPath() }
        val otherPaths = other.mapTo(mutableSetOf()) { it.path.toNioPath() }

        return when {
            thisPaths == otherPaths -> SAME
            // We treat absence of path ctx as the most generic ctx.
            thisPaths.isEmpty() -> IS_LESS_SPECIFIC
            otherPaths.isEmpty() -> IS_MORE_SPECIFIC
            // Module is more specific than any template.
            modulePathString in thisPaths && otherPaths.all { it in templatePaths } -> IS_MORE_SPECIFIC
            modulePathString in otherPaths && thisPaths.all { it in templatePaths } -> IS_LESS_SPECIFIC
            // Templates are unordered relative to each other.
            // TODO: Order nested templates by application graph in the topological order
            else -> INDETERMINATE
        }
    }
}