package com.jetbrains.edu.learning.stepic

/**
 * Base on a class from intellij plugin from Stepik
 *
 * @see <a href="https://github.com/StepicOrg/intellij-plugins/blob/develop/stepik-union/src/main/java/org/stepik/core/SupportedLanguages.kt"> SupportedLanguages.kt</a>
 *
 */
enum class StepikLanguages (val id: String?, val langName: String?) {
    JAVA("JAVA", "java8"),
    KOTLIN("kotlin", "kotlin"),
    PYTHON("Python", "python3"),
    INVALID(null, null);


    override fun toString() = id ?: ""


    companion object {
        private val nameMap: Map<String?, StepikLanguages> by lazy {
            values().associateBy { it.langName }
        }

        private val titleMap: Map<String?, StepikLanguages> by lazy {
            values().associateBy { it.id }
        }

        @JvmStatic
        fun langOfName(lang: String) = nameMap.getOrElse(lang, { INVALID })

        @JvmStatic
        fun langOfId(lang: String) = titleMap.getOrElse(lang, { INVALID })
    }
}