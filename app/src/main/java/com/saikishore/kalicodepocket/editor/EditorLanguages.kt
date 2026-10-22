package com.saikishore.kalicodepocket.editor

import android.content.Context
import io.github.dingyi222666.monarch.languages.CppLanguage
import io.github.dingyi222666.monarch.languages.CssLanguage
import io.github.dingyi222666.monarch.languages.GoLanguage
import io.github.dingyi222666.monarch.languages.HtmlLanguage
import io.github.dingyi222666.monarch.languages.JavaLanguage
import io.github.dingyi222666.monarch.languages.JavascriptLanguage
import io.github.dingyi222666.monarch.languages.KotlinLanguage
import io.github.dingyi222666.monarch.languages.LuaLanguage
import io.github.dingyi222666.monarch.languages.MarkdownLanguage
import io.github.dingyi222666.monarch.languages.PhpLanguage
import io.github.dingyi222666.monarch.languages.PythonLanguage
import io.github.dingyi222666.monarch.languages.RubyLanguage
import io.github.dingyi222666.monarch.languages.RustLanguage
import io.github.dingyi222666.monarch.languages.ShellLanguage
import io.github.dingyi222666.monarch.languages.SqlLanguage
import io.github.dingyi222666.monarch.languages.TypescriptLanguage
import io.github.dingyi222666.monarch.languages.XmlLanguage
import io.github.dingyi222666.monarch.languages.YamlLanguage
import io.github.dingyi222666.monarch.types.IMonarchLanguage
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.langs.monarch.MonarchColorScheme
import io.github.rosemoe.sora.langs.monarch.MonarchLanguage
import io.github.rosemoe.sora.langs.monarch.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.monarch.registry.MonarchGrammarRegistry
import io.github.rosemoe.sora.langs.monarch.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.monarch.registry.dsl.monarchLanguages
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.monarch.registry.model.ThemeSource
import io.github.rosemoe.sora.langs.monarch.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor

data class LanguageSpec(
    val id: String,
    val label: String,
    val scope: String,
    val extensions: Set<String>,
    val grammar: IMonarchLanguage,
) {
    val configPath: String get() = "languages/$id.json"
}

/**
 * Python files should never gain indentation automatically when Enter is pressed.
 * Sora's smart-enter can otherwise carry an indent onto the next statement. We keep
 * Monarch highlighting, completion and symbol pairs, but make Enter a plain newline.
 */
private class PythonPlainNewlineLanguage(private val delegate: Language) : Language by delegate {
    override fun getIndentAdvance(text: ContentReference, line: Int, column: Int): Int = 0

    override fun getNewlineHandlers(): Array<NewlineHandler> = emptyArray()
}

object EditorLanguages {
    private var installed = false

    val all: List<LanguageSpec> = listOf(
        LanguageSpec("python", "Python", "source.python", setOf("py", "pyw"), PythonLanguage),
        LanguageSpec("javascript", "JavaScript", "source.js", setOf("js", "mjs", "cjs", "jsx"), JavascriptLanguage),
        LanguageSpec("typescript", "TypeScript", "source.ts", setOf("ts", "tsx"), TypescriptLanguage),
        LanguageSpec("html", "HTML", "text.html.basic", setOf("html", "htm", "xhtml"), HtmlLanguage),
        LanguageSpec("css", "CSS", "source.css", setOf("css"), CssLanguage),
        // Monarch's JS tokenizer is an excellent lightweight JSON highlighter too.
        LanguageSpec("json", "JSON", "source.json", setOf("json", "jsonc"), JavascriptLanguage),
        LanguageSpec("markdown", "Markdown", "text.html.markdown", setOf("md", "markdown", "mdx"), MarkdownLanguage),
        LanguageSpec("java", "Java", "source.java", setOf("java"), JavaLanguage),
        LanguageSpec("kotlin", "Kotlin", "source.kotlin", setOf("kt", "kts"), KotlinLanguage),
        LanguageSpec("xml", "XML", "text.xml", setOf("xml", "svg"), XmlLanguage),
        LanguageSpec("yaml", "YAML", "source.yaml", setOf("yml", "yaml"), YamlLanguage),
        LanguageSpec("shell", "Shell", "source.shell", setOf("sh", "bash", "zsh", "env"), ShellLanguage),
        LanguageSpec("sql", "SQL", "source.sql", setOf("sql"), SqlLanguage),
        LanguageSpec("cpp", "C / C++", "source.cpp", setOf("c", "h", "cc", "cpp", "cxx", "hpp", "hh"), CppLanguage),
        LanguageSpec("go", "Go", "source.go", setOf("go"), GoLanguage),
        LanguageSpec("rust", "Rust", "source.rust", setOf("rs"), RustLanguage),
        LanguageSpec("php", "PHP", "source.php", setOf("php"), PhpLanguage),
        LanguageSpec("ruby", "Ruby", "source.ruby", setOf("rb"), RubyLanguage),
        LanguageSpec("lua", "Lua", "source.lua", setOf("lua"), LuaLanguage),
    )

    private val byId = all.associateBy { it.id }
    private val byExtension = buildMap {
        all.forEach { spec -> spec.extensions.forEach { put(it, spec) } }
    }

    fun byId(id: String?): LanguageSpec? = id?.let(byId::get)

    fun detect(fileName: String): LanguageSpec? {
        val clean = fileName.substringAfterLast('/').lowercase()
        if (clean == ".env" || clean.startsWith(".env.")) return byId("shell")
        if (clean == "readme" || clean == "license" || clean == "changelog") return byId("markdown")
        if (clean == "dockerfile" || clean == "makefile") return byId("shell")
        val extension = clean.substringAfterLast('.', "")
        return byExtension[extension]
    }

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            // Monarch grammars are written for Oniguruma regex syntax. The pure-Java
            // Joni implementation is bundled, so no native ABI libraries are needed.
            runCatching { io.github.dingyi222666.regex.oniguruma.applyOnigRegexLibToGlobal() }
                .onFailure { android.util.Log.w("EditorLanguages", "Oniguruma setup failed", it) }
            FileProviderRegistry.addProvider(AssetsFileResolver(context.applicationContext.assets))
            ThemeRegistry.loadTheme(
                ThemeModel(ThemeSource("themes/kali.json", "kali")).apply { isDark = true },
                false,
            )
            ThemeRegistry.loadTheme(
                ThemeModel(ThemeSource("themes/paper.json", "paper")).apply { isDark = false },
                false,
            )
            ThemeRegistry.setTheme("kali")

            // Register one by one. A single unusual grammar must never prevent the
            // editor from launching; that language simply falls back to plain text.
            all.forEach { spec ->
                runCatching {
                    MonarchGrammarRegistry.INSTANCE.loadGrammars(
                        monarchLanguages {
                            language(spec.id) {
                                monarchLanguage = spec.grammar
                                scopeName = spec.scope
                                languageConfiguration = spec.configPath
                            }
                        }
                    )
                }.onFailure {
                    android.util.Log.w("EditorLanguages", "Could not load ${spec.id}", it)
                }
            }
            installed = true
        }
    }

    fun applyTheme(name: String) {
        ThemeRegistry.setTheme(if (name == "paper") "paper" else "kali")
    }

    fun colorScheme(): MonarchColorScheme = MonarchColorScheme.create(ThemeRegistry.currentTheme)

    fun applyLanguage(editor: CodeEditor, spec: LanguageSpec?) {
        // Keep Enter predictable in Python: no copied or synthesized indent.
        editor.props.autoIndent = spec != null && spec.id != "python"
        editor.setEditorLanguage(
            if (spec == null) {
                EmptyLanguage()
            } else {
                val language = runCatching { MonarchLanguage.create(spec.scope, true) }
                    .getOrElse { EmptyLanguage() as Language }
                if (spec.id == "python") PythonPlainNewlineLanguage(language) else language
            }
        )
    }
}
