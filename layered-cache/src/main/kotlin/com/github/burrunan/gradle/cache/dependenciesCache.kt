/*
 * Copyright 2020 Vladimir Sitnikov <sitnikov.vladimir@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.burrunan.gradle.cache

import actions.core.debug
import com.github.burrunan.gradle.github.env.ActionsEnvironment
import com.github.burrunan.gradle.github.event.ActionsTrigger
import com.github.burrunan.gradle.github.event.cacheKey
import com.github.burrunan.gradle.github.suspendingStateVariable
import com.github.burrunan.gradle.hashFiles

/**
 * Populate cache only when building a default branch, otherwise treat the cache as read-only.
 */
suspend fun dependenciesCache(
    name: String,
    trigger: ActionsTrigger,
    cacheLocation: List<String>,
    pathDependencies: List<String>,
): Cache {
    val defaultBranch = trigger.event.repository.default_branch
    val pkPrefix = trigger.cacheKey
    val cacheName = "dependencies-$name"

    // Avoid re-computing the hash for saving the cache
    val dependencyDeclarationHash = suspendingStateVariable(cacheName) {
        hashFiles(*pathDependencies.toTypedArray()).hash
    }
    debug { "$cacheName: dependencyDeclarationHash=${dependencyDeclarationHash.get()}" }
    val prefix = "dependencies-$name-${ActionsEnvironment.RUNNER_OS}"
    return LayeredCache(
        name = cacheName,
        baseline = prefix,
        primaryKey = "$prefix-$pkPrefix-${dependencyDeclarationHash.get()}",
        restoreKeys = listOf(
            "$prefix-$pkPrefix",
            "$prefix-$defaultBranch",
        ),
        paths = cacheLocation,
    )
}

suspend fun gradleDependenciesCache(trigger: ActionsTrigger, path: String, gradleDependenciesCacheKey: String): Cache =
    dependenciesCache(
        "gradle",
        trigger,
        cacheLocation = listOf(
            "~/.gradle/caches/modules-2",
            "!~/.gradle/caches/modules-2/gc.properties",
            "!~/.gradle/caches/modules-2/modules-2.lock",
        ),
        pathDependencies = listOf(
            "!$path/**/.gradle/",
            "$path/**/*.gradle.kts",
            "$path/**/gradle/dependency-locking/**",
            // We do not want .gradle folder, so we want to have at least one character before .gradle
            "$path/**/?*.gradle",
            "$path/**/*.properties",
        ) + gradleDependenciesCacheKey.split(Regex("[\r\n]+"))
            .map { it.trim() }
            .filterNot { it.isEmpty() }
            .map {
                (if (it.startsWith("!")) "!" else "") +
                    "$path/**/" + it.trim().trimStart('!')
            },
    )

suspend fun mavenDependenciesCache(trigger: ActionsTrigger, path: String): Cache =
    dependenciesCache(
        "maven",
        trigger,
        cacheLocation = listOf("~/.m2/repository"),
        pathDependencies = listOf(
            "$path/**/pom.xml",
        ),
    )

