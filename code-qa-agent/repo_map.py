"""Tree-sitter based symbol extraction for repository mapping."""

from pathlib import Path

try:
    from tree_sitter_languages import get_parser

    TREE_SITTER_AVAILABLE = True
except ImportError:
    TREE_SITTER_AVAILABLE = False

EXTENSION_TO_LANG: dict[str, str] = {
    ".py": "python",
    ".js": "javascript",
    ".jsx": "javascript",
    ".ts": "typescript",
    ".tsx": "tsx",
    ".java": "java",
    ".go": "go",
    ".rs": "rust",
    ".rb": "ruby",
    ".cpp": "cpp",
    ".cc": "cpp",
    ".cxx": "cpp",
    ".c": "c",
    ".h": "c",
    ".hpp": "cpp",
    ".cs": "c_sharp",
    ".kt": "kotlin",
    ".kts": "kotlin",
    ".scala": "scala",
    ".swift": "swift",
    ".php": "php",
    ".sh": "bash",
    ".lua": "lua",
    ".r": "r",
    ".R": "r",
}

# AST node types that represent "interesting" symbols
_SYMBOL_NODE_TYPES = frozenset(
    {
        # Functions
        "function_definition",
        "function_declaration",
        "method_definition",
        "method_declaration",
        "constructor_declaration",
        "arrow_function",
        "function_item",  # Rust
        # Classes / types
        "class_definition",
        "class_declaration",
        "class_body",
        "interface_declaration",
        "enum_declaration",
        "enum_definition",
        "struct_item",  # Rust
        "struct_declaration",
        "type_declaration",
        "trait_item",  # Rust
        "impl_item",  # Rust
        "module_declaration",
        "object_declaration",  # Kotlin
    }
)

# Node types that carry the "name" of a symbol
_NAME_NODE_TYPES = frozenset(
    {"identifier", "property_identifier", "type_identifier", "name"}
)


def detect_language(file_path: str | Path) -> str | None:
    ext = Path(file_path).suffix.lower()
    return EXTENSION_TO_LANG.get(ext)


def extract_symbols(file_path: str | Path) -> list[dict]:
    """Extract symbols (functions, classes, methods…) from a source file.

    Returns a list of dicts with keys:
        type, name, line, end_line, signature, parent
    """
    if not TREE_SITTER_AVAILABLE:
        return []

    lang = detect_language(file_path)
    if not lang:
        return []

    try:
        parser = get_parser(lang)
        source = Path(file_path).read_bytes()
        tree = parser.parse(source)
    except Exception:
        return []

    symbols: list[dict] = []
    _walk(tree.root_node, source, symbols, parent=None)
    return symbols


def _walk(node, source: bytes, symbols: list[dict], parent: str | None):
    if node.type in _SYMBOL_NODE_TYPES:
        name = _find_name(node, source)
        if name:
            line_start = node.start_point[0]
            source_lines = source.splitlines()
            sig = (
                source_lines[line_start].decode(errors="replace").strip()
                if line_start < len(source_lines)
                else ""
            )
            symbols.append(
                {
                    "type": _simplify_type(node.type),
                    "name": name,
                    "line": line_start + 1,
                    "end_line": node.end_point[0] + 1,
                    "signature": sig,
                    "parent": parent,
                }
            )
            # Recurse into children with current symbol as parent
            for child in node.children:
                _walk(child, source, symbols, name)
            return

    for child in node.children:
        _walk(child, source, symbols, parent)


def _find_name(node, source: bytes) -> str | None:
    """Find the name identifier among a node's direct children."""
    for child in node.children:
        if child.type in _NAME_NODE_TYPES:
            return source[child.start_byte : child.end_byte].decode(errors="replace")
    # Some grammars nest the name one level deeper (e.g. Java constructor)
    for child in node.children:
        for grandchild in child.children:
            if grandchild.type in _NAME_NODE_TYPES:
                return source[grandchild.start_byte : grandchild.end_byte].decode(
                    errors="replace"
                )
    return None


def _simplify_type(node_type: str) -> str:
    for label in ("class", "interface", "enum", "struct", "trait", "impl", "module"):
        if label in node_type:
            return label
    if "constructor" in node_type:
        return "constructor"
    if "method" in node_type:
        return "method"
    if "function" in node_type:
        return "function"
    return node_type
