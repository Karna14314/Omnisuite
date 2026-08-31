package com.karnadigital.omnisuite.core.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

data class SyntaxRule(
    val pattern: Regex,
    val color: Color,
    val fontWeight: FontWeight? = null
)

object SyntaxHighlighter {

    val keywordColor = Color(0xFF569CD6)
    val stringColor = Color(0xFFCE9178)
    val commentColor = Color(0xFF6A9955)
    val numberColor = Color(0xFFB5CEA8)
    val typeColor = Color(0xFF4EC9B0)
    val functionColor = Color(0xFFDCDCAA)
    val tagColor = Color(0xFF569CD6)
    val attributeColor = Color(0xFF9CDCFE)
    val propertyColor = Color(0xFF9CDCFE)
    val keywordBold = FontWeight.Bold

    private val commonKeywords = listOf(
        "abstract", "as", "assert", "break", "case", "catch", "class", "const",
        "continue", "default", "do", "else", "enum", "extends", "false", "final",
        "finally", "for", "fun", "if", "implements", "import", "in", "interface",
        "is", "new", "null", "object", "override", "package", "private", "protected",
        "public", "return", "static", "super", "switch", "this", "throw", "throws",
        "true", "try", "type", "val", "var", "when", "while", "with", "yield",
        "def", "elif", "except", "from", "global", "lambda", "nonlocal", "pass",
        "raise", "async", "await", "function", "let", "typeof", "undefined",
        "namespace", "module", "declare", "readonly", "abstract", "require",
        "echo", "print", "namespace", "use", "trait", "insteadof", "as",
        "int", "float", "double", "boolean", "byte", "char", "short", "long",
        "void", "string", "bool", "integer", "array", "map", "list", "set",
        "struct", "union", "typedef", "sizeof", "goto", "volatile", "register",
        "extern", "signed", "unsigned", "auto"
    )

    private val typeKeywords = listOf(
        "String", "Int", "Long", "Float", "Double", "Boolean", "Byte", "Char",
        "Short", "Void", "Unit", "Any", "Nothing", "Object", "List", "Map",
        "Set", "Array", "MutableList", "MutableMap", "MutableSet",
        "Integer", "Number", "BigDecimal", "BigInteger", "Optional",
        "Promise", "Array", "Record", "Map", "Set", "WeakMap", "WeakSet",
        "Date", "RegExp", "Error", "Symbol", "Function", "Math", "JSON",
        "HTMLElement", "Document", "Window", "Console", "Node", "Element",
        "Activity", "Fragment", "ViewModel", "LiveData", "Flow", "StateFlow",
        "Context", "Intent", "Bundle", "Application", "Service", "BroadcastReceiver",
        "ContentProvider", "RecyclerView", "Adapter", "ViewHolder"
    )

    private val commonKeywordsSet = commonKeywords.toSet()
    private val typeKeywordsSet = typeKeywords.toSet()

    private fun buildKeywordPattern(keywords: Set<String>): Regex {
        return Regex("\\b(${keywords.joinToString("|") { Regex.escape(it) }})\\b")
    }

    fun getRulesForExtension(extension: String): List<SyntaxRule> {
        return when (extension.lowercase()) {
            "kt", "kts" -> kotlinRules()
            "java" -> javaRules()
            "py" -> pythonRules()
            "js", "jsx", "ts", "tsx" -> javascriptRules()
            "c", "h" -> cRules()
            "cpp", "hpp", "cc", "cxx" -> cppRules()
            "cs" -> csharpRules()
            "php" -> phpRules()
            "sql" -> sqlRules()
            "html", "htm", "xhtml" -> htmlRules()
            "css" -> cssRules()
            "xml", "svg", "xaml" -> xmlRules()
            "json" -> jsonRules()
            "yaml", "yml" -> yamlRules()
            "md", "markdown" -> markdownRules()
            "gradle", "groovy" -> gradleRules()
            "properties", "ini", "cfg", "conf", "config" -> propertiesRules()
            "log" -> logRules()
            "csv", "tsv" -> csvRules()
            "sh", "bash", "zsh" -> shellRules()
            "rb" -> rubyRules()
            "go" -> goRules()
            "rs" -> rustRules()
            "swift" -> swiftRules()
            "dart" -> dartRules()
            "scala" -> scalaRules()
            "r" -> rRules()
            "lua" -> luaRules()
            "pl" -> perlRules()
            else -> emptyList()
        }
    }

    fun highlight(text: String, extension: String): AnnotatedString {
        val rules = getRulesForExtension(extension)
        if (rules.isEmpty()) {
            return AnnotatedString(text)
        }
        return buildAnnotatedString {
            append(text)
            for (rule in rules) {
                val matches = rule.pattern.findAll(text)
                for (match in matches) {
                    val style = SpanStyle(
                        color = rule.color,
                        fontWeight = rule.fontWeight
                    )
                    addStyle(style, match.range.first, match.range.last + 1)
                }
            }
        }
    }

    private fun commonExpressionRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b"), numberColor)
        )
    }

    private fun lineCommentRules(commentPrefix: String): SyntaxRule {
        return SyntaxRule(Regex("$commentPrefix.*"), commentColor)
    }

    private fun blockCommentRules(): SyntaxRule {
        return SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor)
    }

    private fun kotlinRules(): List<SyntaxRule> {
        val keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for",
            "fun", "if", "in", "interface", "is", "null", "object", "package",
            "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while", "by", "catch", "constructor",
            "delegate", "dynamic", "field", "file", "finally", "get", "import",
            "init", "param", "property", "receiver", "set", "setparam", "where",
            "actual", "abstract", "annotation", "companion", "const", "crossinline",
            "data", "enum", "expect", "external", "final", "infix", "inline",
            "inner", "internal", "lateinit", "noinline", "open", "operator",
            "out", "override", "private", "protected", "public", "reified",
            "sealed", "suspend", "tailrec", "vararg", "field", "it"
        )
        val types = setOf(
            "String", "Int", "Long", "Float", "Double", "Boolean", "Byte", "Char",
            "Short", "Void", "Unit", "Any", "Nothing", "Object", "List", "Map",
            "Set", "Array", "MutableList", "MutableMap", "MutableSet"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"\"\"[\\s\\S]*?\"\"\""), stringColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(buildKeywordPattern(types), typeColor),
            SyntaxRule(Regex("@\\w+"), Color(0xFFD7BA7D)),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun javaRules(): List<SyntaxRule> {
        val keywords = setOf(
            "abstract", "assert", "break", "case", "catch", "class", "const",
            "continue", "default", "do", "else", "enum", "extends", "final",
            "finally", "for", "goto", "if", "implements", "import", "instanceof",
            "interface", "native", "new", "package", "private", "protected",
            "public", "return", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try",
            "volatile", "while", "true", "false", "null", "var", "record",
            "sealed", "permits", "yield", "non-sealed"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(buildKeywordPattern(typeKeywordsSet), typeColor),
            SyntaxRule(Regex("@\\w+"), Color(0xFFD7BA7D)),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun pythonRules(): List<SyntaxRule> {
        val keywords = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield"
        )
        return listOf(
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("\"\"\"[\\s\\S]*?\"\"\""), stringColor),
            SyntaxRule(Regex("'''[\\s\\S]*?'''"), stringColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[jJ]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor),
            SyntaxRule(Regex("@\\w+(\\.\\w+)*"), Color(0xFFD7BA7D))
        )
    }

    private fun javascriptRules(): List<SyntaxRule> {
        val keywords = setOf(
            "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof",
            "let", "new", "null", "return", "super", "switch", "this", "throw",
            "true", "try", "typeof", "var", "void", "while", "with", "yield",
            "async", "await", "of", "static", "get", "set", "from", "as",
            "enum", "interface", "type", "namespace", "declare", "module",
            "abstract", "readonly", "keyof", "infer", "is", "asserts", "unknown",
            "never", "any", "string", "number", "boolean", "symbol", "bigint",
            "undefined", "object", "unique", "global"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("`[^`]*`"), stringColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(buildKeywordPattern(typeKeywordsSet), typeColor),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun cRules(): List<SyntaxRule> {
        val keywords = setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if",
            "inline", "int", "long", "register", "restrict", "return", "short",
            "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
            "unsigned", "void", "volatile", "while", "bool", "true", "false",
            "NULL", "include", "define", "ifdef", "ifndef", "endif", "pragma"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("#\\w+"), Color(0xFFC586C0)),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFlLuU]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun cppRules(): List<SyntaxRule> {
        val keywords = setOf(
            "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand",
            "bitor", "bool", "break", "case", "catch", "char", "char8_t",
            "char16_t", "char32_t", "class", "compl", "concept", "const",
            "consteval", "constexpr", "constinit", "const_cast", "continue",
            "co_await", "co_return", "co_yield", "decltype", "default", "delete",
            "do", "double", "dynamic_cast", "else", "enum", "explicit", "export",
            "extern", "false", "float", "for", "friend", "goto", "if", "inline",
            "int", "long", "mutable", "namespace", "new", "noexcept", "not",
            "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected",
            "public", "register", "reinterpret_cast", "requires", "return",
            "short", "signed", "sizeof", "static", "static_assert", "static_cast",
            "struct", "switch", "template", "this", "thread_local", "throw",
            "true", "try", "typedef", "typeid", "typename", "union", "unsigned",
            "using", "virtual", "void", "volatile", "wchar_t", "while", "xor",
            "xor_eq", "include", "define", "ifdef", "ifndef", "endif", "pragma",
            "std", "string", "vector", "map", "set", "list", "array", "queue",
            "stack", "pair", "tuple", "cout", "cin", "endl", "nullptr"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("#\\w+"), Color(0xFFC586C0)),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFlLuU]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun csharpRules(): List<SyntaxRule> {
        val keywords = setOf(
            "abstract", "as", "base", "break", "case", "catch", "checked",
            "class", "const", "continue", "default", "delegate", "do", "else",
            "enum", "event", "explicit", "extern", "false", "finally", "fixed",
            "for", "foreach", "goto", "if", "implicit", "in", "interface",
            "internal", "is", "lock", "namespace", "new", "null", "object",
            "operator", "out", "override", "params", "private", "protected",
            "public", "readonly", "ref", "return", "sealed", "sizeof", "stackalloc",
            "static", "struct", "switch", "this", "throw", "true", "try",
            "typeof", "unchecked", "unsafe", "using", "virtual", "void",
            "volatile", "while", "add", "alias", "ascending", "async", "await",
            "by", "descending", "dynamic", "equals", "from", "get", "global",
            "group", "into", "join", "let", "nameof", "on", "orderby", "partial",
            "remove", "select", "set", "value", "var", "when", "where", "yield",
            "string", "int", "bool", "float", "double", "long", "byte", "char",
            "decimal", "short", "uint", "ulong", "ushort", "object"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("@\"[^\"]*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlLmMuU]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("@\\w+"), Color(0xFFD7BA7D)),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun phpRules(): List<SyntaxRule> {
        val keywords = setOf(
            "abstract", "and", "array", "as", "break", "callable", "case",
            "catch", "class", "clone", "const", "continue", "declare", "default",
            "die", "do", "echo", "else", "elseif", "empty", "enddeclare",
            "endfor", "endforeach", "endif", "endswitch", "endwhile", "eval",
            "exit", "extends", "final", "finally", "fn", "for", "foreach",
            "function", "global", "goto", "if", "implements", "include",
            "include_once", "instanceof", "insteadof", "interface", "isset",
            "list", "match", "namespace", "new", "or", "print", "private",
            "protected", "public", "require", "require_once", "return", "static",
            "switch", "throw", "trait", "try", "unset", "use", "var", "while",
            "xor", "yield", "true", "false", "null", "self", "parent"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\$\\w+"), Color(0xFF9CDCFE)),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun sqlRules(): List<SyntaxRule> {
        val keywords = setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE",
            "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX",
            "VIEW", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "ON",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION",
            "ALL", "DISTINCT", "AS", "AND", "OR", "NOT", "IN", "BETWEEN",
            "LIKE", "IS", "NULL", "EXISTS", "CASE", "WHEN", "THEN", "ELSE",
            "END", "IF", "WHILE", "FOR", "WITH", "COUNT", "SUM", "AVG",
            "MIN", "MAX", "ASC", "DESC", "PRIMARY", "KEY", "FOREIGN",
            "REFERENCES", "CONSTRAINT", "UNIQUE", "CHECK", "DEFAULT",
            "AUTO_INCREMENT", "CASCADE", "RESTRICT", "NO", "ACTION",
            "BEGIN", "COMMIT", "ROLLBACK", "TRANSACTION", "TRIGGER",
            "PROCEDURE", "FUNCTION", "DATABASE", "USE", "SHOW", "DESCRIBE",
            "EXPLAIN", "GRANT", "REVOKE", "TO", "FROM", "INTO", "OUTFILE",
            "LOAD", "DATA", "INFILE", "REPLACE", "IGNORE", "DUAL", "TOP"
        )
        return listOf(
            SyntaxRule(Regex("--.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("'[^']*'"), stringColor),
            SyntaxRule(Regex("\"[^\"]*\""), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?\\b"), numberColor),
            SyntaxRule(Regex("\\b(${keywords.joinToString("|")})\\b", RegexOption.IGNORE_CASE), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun htmlRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("<!--[\\s\\S]*?-->"), commentColor),
            SyntaxRule(Regex("</?[a-zA-Z][a-zA-Z0-9]*"), tagColor),
            SyntaxRule(Regex("\\b[a-zA-Z-]+(?==)"), attributeColor),
            SyntaxRule(Regex("\"[^\"]*\""), stringColor),
            SyntaxRule(Regex("'[^']*'"), stringColor)
        )
    }

    private fun cssRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("[.#]?[a-zA-Z][a-zA-Z0-9_-]*(?=\\s*\\{)"), tagColor),
            SyntaxRule(Regex("[a-zA-Z-]+(?=\\s*:)"), propertyColor),
            SyntaxRule(Regex(":\\s*[^;]+"), stringColor),
            SyntaxRule(Regex("#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})\\b"), numberColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?(px|em|rem|%|pt|cm|mm|in|vh|vw|fr|deg|rad|s|ms|Hz|kHz|dpi|dpcm)?\\b"), numberColor)
        )
    }

    private fun xmlRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("<!--[\\s\\S]*?-->"), commentColor),
            SyntaxRule(Regex("<\\?[\\s\\S]*?\\?>"), Color(0xFFC586C0)),
            SyntaxRule(Regex("</?[a-zA-Z][a-zA-Z0-9:.-]*"), tagColor),
            SyntaxRule(Regex("\\b[a-zA-Z:.-]+(?==)"), attributeColor),
            SyntaxRule(Regex("\"[^\"]*\""), stringColor),
            SyntaxRule(Regex("'[^']*'"), stringColor)
        )
    }

    private fun jsonRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"(?=\\s*:)"), propertyColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(Regex("\\b(true|false|null)\\b"), keywordColor, keywordBold)
        )
    }

    private fun yamlRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("^[a-zA-Z_][a-zA-Z0-9_.]*(?=\\s*:)", RegexOption.MULTILINE), propertyColor),
            SyntaxRule(Regex("-\\s+"), keywordColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?\\b"), numberColor),
            SyntaxRule(Regex("\\b(true|false|yes|no|null|~)\\b", RegexOption.IGNORE_CASE), keywordColor, keywordBold)
        )
    }

    private fun markdownRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("^#{1,6}\\s+.*$", RegexOption.MULTILINE), keywordColor, keywordBold),
            SyntaxRule(Regex("\\[.*?\\]\\(.*?\\)"), functionColor),
            SyntaxRule(Regex("`[^`]+`"), stringColor),
            SyntaxRule(Regex("```[\\s\\S]*?```"), stringColor),
            SyntaxRule(Regex("\\*\\*.*?\\*\\*"), Color(0xFF569CD6)),
            SyntaxRule(Regex("\\*.*?\\*"), Color(0xFFDCDCAA)),
            SyntaxRule(Regex("^>\\s+.*$", RegexOption.MULTILINE), commentColor),
            SyntaxRule(Regex("^[-*+]\\s+", RegexOption.MULTILINE), keywordColor),
            SyntaxRule(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), keywordColor)
        )
    }

    private fun gradleRules(): List<SyntaxRule> {
        val keywords = setOf(
            "plugins", "dependencies", "repositories", "android", "defaultConfig",
            "buildTypes", "compileSdk", "minSdk", "targetSdk", "versionCode",
            "versionName", "applicationId", "testInstrumentationRunner",
            "consumerProguardFiles", "proguardFiles", "buildFeatures",
            "compose", "viewBinding", "dataBinding", "multiDexEnabled",
            "ndk", "externalNativeBuild", "sourceSets", "packagingOptions",
            "lintOptions", "testOptions", "compileOptions", "kotlinOptions",
            "buildToolsVersion", "ndkVersion", "signingConfigs", "productFlavors",
            "flavorDimensions", "allprojects", "subprojects", "task", "doLast",
            "doFirst", "apply", "from", "include", "exclude", "group", "description",
            "version", "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "androidTestImplementation", "kapt", "annotationProcessor",
            "classpath", "mavenCentral", "google", "mavenLocal", "maven", "flatDir",
            "url", "credentials", "username", "password", "allowInsecureProtocol"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\{)"), functionColor),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun propertiesRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("[#;!].*"), commentColor),
            SyntaxRule(Regex("^[a-zA-Z0-9._-]+(?=\\s*[=:])", RegexOption.MULTILINE), propertyColor),
            SyntaxRule(Regex("(?<=[=:])\\s*.*"), stringColor)
        )
    }

    private fun logRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("\\b(ERROR|FATAL|CRITICAL|SEVERE)\\b", RegexOption.IGNORE_CASE), Color(0xFFFF6B6B), keywordBold),
            SyntaxRule(Regex("\\b(WARN|WARNING)\\b", RegexOption.IGNORE_CASE), Color(0xFFFFD93D)),
            SyntaxRule(Regex("\\b(INFO|NOTICE)\\b", RegexOption.IGNORE_CASE), Color(0xFF6BCB77)),
            SyntaxRule(Regex("\\b(DEBUG|TRACE|VERBOSE)\\b", RegexOption.IGNORE_CASE), Color(0xFF4D96FF)),
            SyntaxRule(Regex("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}"), Color(0xFF9CDCFE)),
            SyntaxRule(Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b"), Color(0xFFB5CEA8)),
            SyntaxRule(Regex("\"[^\"]*\""), stringColor),
            SyntaxRule(Regex("'[^']*'"), stringColor)
        )
    }

    private fun csvRules(): List<SyntaxRule> {
        return listOf(
            SyntaxRule(Regex("^[^,\\n]*", RegexOption.MULTILINE), propertyColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?\\b"), numberColor)
        )
    }

    private fun shellRules(): List<SyntaxRule> {
        val keywords = setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
            "until", "do", "done", "in", "function", "select", "time", "coproc",
            "export", "local", "readonly", "declare", "typeset", "unset", "unsetenv",
            "alias", "unalias", "bg", "fg", "jobs", "kill", "wait", "disown",
            "trap", "return", "exit", "source", "cd", "pwd", "pushd", "popd",
            "dirs", "echo", "printf", "read", "test", "true", "false"
        )
        return listOf(
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\$\\{[^}]*}"), Color(0xFF9CDCFE)),
            SyntaxRule(Regex("\\$\\w+"), Color(0xFF9CDCFE)),
            SyntaxRule(Regex("\\b\\d+\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun rubyRules(): List<SyntaxRule> {
        val keywords = setOf(
            "alias", "and", "begin", "break", "case", "class", "def", "defined?",
            "do", "else", "elsif", "end", "ensure", "false", "for", "if", "in",
            "module", "next", "nil", "not", "or", "redo", "rescue", "retry",
            "return", "self", "super", "then", "true", "undef", "unless", "until",
            "when", "while", "yield", "__FILE__", "__LINE__", "require", "include",
            "extend", "attr_reader", "attr_writer", "attr_accessor", "puts", "print"
        )
        return listOf(
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("=begin[\\s\\S]*?=end"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex(":\\w+"), Color(0xFF9CDCFE)),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun goRules(): List<SyntaxRule> {
        val keywords = setOf(
            "break", "case", "chan", "const", "continue", "default", "defer",
            "else", "fallthrough", "for", "func", "go", "goto", "if", "import",
            "interface", "map", "package", "range", "return", "select", "struct",
            "switch", "type", "var", "true", "false", "nil", "iota", "make",
            "new", "len", "cap", "append", "copy", "delete", "close", "panic",
            "recover", "print", "println", "Printf", "Sprintf", "Errorf"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("`[^`]*`"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun rustRules(): List<SyntaxRule> {
        val keywords = setOf(
            "as", "async", "await", "break", "const", "continue", "crate",
            "dyn", "else", "enum", "extern", "false", "fn", "for", "if", "impl",
            "in", "let", "loop", "match", "mod", "move", "mut", "pub", "ref",
            "return", "self", "Self", "static", "struct", "super", "trait", "true",
            "type", "unsafe", "use", "where", "while", "yield", "abstract", "become",
            "box", "do", "final", "macro", "override", "priv", "typeof", "unsized",
            "virtual", "try", "union", "Result", "Option", "Some", "None", "Ok", "Err",
            "Vec", "String", "println", "format", "vec", "assert", "assert_eq"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[uif]?\\d*\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor),
            SyntaxRule(Regex("#!?\\[[^\\]]*]"), Color(0xFFD7BA7D))
        )
    }

    private fun swiftRules(): List<SyntaxRule> {
        val keywords = setOf(
            "as", "associativity", "break", "case", "catch", "class", "continue",
            "convenience", "default", "defer", "deinit", "didSet", "do", "dynamic",
            "dynamicType", "else", "enum", "extension", "fallthrough", "false",
            "fileprivate", "final", "for", "func", "get", "guard", "if", "import",
            "in", "infix", "init", "inout", "internal", "is", "lazy", "left",
            "let", "mutating", "nil", "none", "nonmutating", "operator", "optional",
            "override", "postfix", "precedence", "prefix", "private", "protocol",
            "public", "repeat", "required", "rethrows", "return", "right", "self",
            "set", "static", "struct", "subscript", "super", "switch", "throw",
            "throws", "true", "try", "typealias", "unowned", "var", "weak", "where",
            "while", "willSet", "print", "String", "Int", "Double", "Float", "Bool",
            "Array", "Dictionary", "Optional", "UInt", "Character", "Void"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("@\\w+"), Color(0xFFD7BA7D)),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun dartRules(): List<SyntaxRule> {
        val keywords = setOf(
            "abstract", "as", "assert", "async", "await", "break", "case", "catch",
            "class", "const", "continue", "covariant", "default", "deferred", "do",
            "dynamic", "else", "enum", "export", "extends", "extension", "external",
            "factory", "false", "final", "finally", "for", "Function", "get", "hide",
            "if", "implements", "import", "in", "interface", "is", "late", "library",
            "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
            "return", "sealed", "set", "show", "static", "super", "switch", "sync",
            "this", "throw", "true", "try", "type", "typedef", "var", "void", "when",
            "while", "with", "yield", "print", "String", "int", "double", "bool",
            "List", "Map", "Set", "Future", "Stream", "Widget", "BuildContext"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("@\\w+"), Color(0xFFD7BA7D)),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun scalaRules(): List<SyntaxRule> {
        val keywords = setOf(
            "abstract", "case", "catch", "class", "def", "do", "else", "extends",
            "false", "final", "finally", "for", "forSome", "if", "implicit",
            "import", "lazy", "match", "new", "null", "object", "override",
            "package", "private", "protected", "return", "sealed", "super", "this",
            "throw", "trait", "true", "try", "type", "val", "var", "while", "with",
            "yield", "println", "String", "Int", "Double", "Boolean", "Long", "Float",
            "List", "Map", "Set", "Array", "Option", "Some", "None", "Any", "Unit"
        )
        return listOf(
            SyntaxRule(Regex("//.*"), commentColor),
            SyntaxRule(Regex("/\\*[\\s\\S]*?\\*/"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("\"\"\"[\\s\\S]*?\"\"\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[lLfFdD]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun rRules(): List<SyntaxRule> {
        val keywords = setOf(
            "if", "else", "repeat", "while", "function", "for", "in", "next",
            "break", "TRUE", "FALSE", "NULL", "Inf", "NaN", "NA", "NA_integer_",
            "NA_real_", "NA_complex_", "NA_character_", "print", "cat", "paste",
            "library", "require", "source", "return", "try", "tryCatch", "stop",
            "warning", "message"
        )
        return listOf(
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[iLl]?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun luaRules(): List<SyntaxRule> {
        val keywords = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while", "print", "tostring",
            "tonumber", "type", "string", "table", "math", "io", "os", "pairs",
            "ipairs", "require", "module"
        )
        return listOf(
            SyntaxRule(Regex("--.*"), commentColor),
            SyntaxRule(Regex("--\\[[\\s\\S]*?]"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\[\\[[\\s\\S]*?]]"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }

    private fun perlRules(): List<SyntaxRule> {
        val keywords = setOf(
            "bless", "caller", "continue", "die", "do", "dump", "else", "elsif",
            "eval", "exit", "for", "foreach", "goto", "if", "import", "last",
            "local", "my", "next", "no", "our", "package", "print", "printf",
            "redo", "ref", "require", "return", "scalar", "sub", "tie", "tied",
            "unless", "until", "use", "wantarray", "while", "print", "say",
            "open", "close", "chomp", "split", "join", "push", "pop", "shift",
            "unshift", "sort", "map", "grep", "keys", "values", "defined"
        )
        return listOf(
            SyntaxRule(Regex("#.*"), commentColor),
            SyntaxRule(Regex("=pod[\\s\\S]*?=cut"), commentColor),
            SyntaxRule(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), stringColor),
            SyntaxRule(Regex("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'"), stringColor),
            SyntaxRule(Regex("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), numberColor),
            SyntaxRule(buildKeywordPattern(keywords), keywordColor, keywordBold),
            SyntaxRule(Regex("\\b\\w+(?=\\s*\\()"), functionColor)
        )
    }
}
