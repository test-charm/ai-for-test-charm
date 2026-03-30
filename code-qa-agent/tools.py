import re
import subprocess
from pathlib import Path

from langchain_core.tools import tool

from config import settings
from repo_map import detect_language, extract_symbols

IGNORE_DIRS = {
    ".git", "node_modules", "__pycache__", ".venv", "venv",
    "target", "build", "dist", ".next", ".nuxt", "vendor",
    ".idea", ".vscode", ".gradle", "bin", "obj", ".cache",
}


def _safe_path(rel_path: str) -> Path:
    workspace = Path(settings.workspace_path).resolve()
    full = (workspace / rel_path).resolve()
    if not str(full).startswith(str(workspace)):
        raise ValueError(f"Path traversal blocked: {rel_path}")
    return full


def _should_ignore(path: Path) -> bool:
    return any(part in IGNORE_DIRS for part in path.parts)


@tool
def list_directory(path: str = ".", max_depth: int = 3) -> str:
    """List directory tree structure. Use this first to understand project layout.

    Args:
        path: Directory relative to workspace root. Default "." for root.
        max_depth: Traversal depth (1-5). Default 3.
    """
    target = _safe_path(path)
    if not target.is_dir():
        return f"Not a directory: {path}"

    workspace = Path(settings.workspace_path).resolve()
    lines: list[str] = []

    def _walk(dir_path: Path, prefix: str, depth: int):
        if depth > max_depth:
            return
        try:
            entries = sorted(
                dir_path.iterdir(),
                key=lambda e: (not e.is_dir(), e.name.lower()),
            )
        except PermissionError:
            return

        dirs = [e for e in entries if e.is_dir() and e.name not in IGNORE_DIRS and not e.name.startswith(".")]
        files = [e for e in entries if e.is_file() and not e.name.startswith(".")]
        all_items = dirs + files

        for i, item in enumerate(all_items):
            is_last = i == len(all_items) - 1
            connector = "└── " if is_last else "├── "
            suffix = "/" if item.is_dir() else ""
            lines.append(f"{prefix}{connector}{item.name}{suffix}")
            if item.is_dir():
                extension = "    " if is_last else "│   "
                _walk(item, prefix + extension, depth + 1)

    rel = target.relative_to(workspace) if target != workspace else Path(".")
    lines.append(f"{rel}/")
    _walk(target, "", 1)

    result = "\n".join(lines[:500])
    if len(lines) > 500:
        result += f"\n... ({len(lines) - 500} more entries truncated)"
    return result


@tool
def find_files(pattern: str, path: str = ".") -> str:
    """Find files matching a glob pattern.

    Args:
        pattern: Glob pattern, e.g. "**/*.py", "**/test_*", "src/**/*.java"
        path: Base directory relative to workspace root. Default ".".
    """
    target = _safe_path(path)
    matches = []
    workspace = Path(settings.workspace_path).resolve()

    for p in target.glob(pattern):
        if _should_ignore(p) or not p.is_file():
            continue
        matches.append(str(p.relative_to(workspace)))
        if len(matches) >= 100:
            break

    if not matches:
        return f"No files found matching: {pattern}"

    result = "\n".join(matches)
    if len(matches) == 100:
        result += "\n... (limited to 100 results)"
    return result


@tool
def grep_code(pattern: str, file_glob: str | None = None, path: str = ".") -> str:
    """Search code content with regex (powered by ripgrep).

    Args:
        pattern: Regex pattern to search for.
        file_glob: Optional file filter, e.g. "*.py", "*.java".
        path: Directory to search, relative to workspace root.
    """
    target = _safe_path(path)
    cmd = [
        "rg", "--no-heading", "-n", "-S",
        "--max-count", str(settings.max_search_results),
        "--max-filesize", "1M",
    ]
    for d in IGNORE_DIRS:
        cmd.extend(["--glob", f"!{d}"])
    if file_glob:
        cmd.extend(["--glob", file_glob])
    cmd.extend([pattern, str(target)])

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode == 0:
            output = result.stdout
            if len(output) > 8000:
                output = output[:8000] + "\n... (output truncated)"
            return output
        elif result.returncode == 1:
            return "No matches found."
        else:
            return f"Search error: {result.stderr[:500]}"
    except FileNotFoundError:
        return _grep_fallback(pattern, target, file_glob)
    except subprocess.TimeoutExpired:
        return "Search timed out (30s limit)."


def _grep_fallback(pattern: str, target: Path, file_glob: str | None) -> str:
    """Pure-Python fallback when ripgrep is not installed."""
    regex = re.compile(pattern, re.IGNORECASE)
    results: list[str] = []
    glob_pattern = file_glob.replace("**/", "") if file_glob else "*"

    for f in target.rglob(glob_pattern):
        if not f.is_file() or _should_ignore(f):
            continue
        try:
            for i, line in enumerate(f.read_text(errors="ignore").splitlines(), 1):
                if regex.search(line):
                    results.append(f"{f.relative_to(target)}:{i}:{line.rstrip()}")
                    if len(results) >= settings.max_search_results:
                        return "\n".join(results) + "\n... (limited)"
        except (PermissionError, OSError):
            continue

    return "\n".join(results) if results else "No matches found."


@tool
def read_file(file_path: str, start_line: int = 1, end_line: int | None = None) -> str:
    """Read file contents with optional line range.

    Args:
        file_path: Path relative to workspace root.
        start_line: First line (1-indexed). Default 1.
        end_line: Last line. Default reads up to 300 lines from start_line.
    """
    full_path = _safe_path(file_path)
    if not full_path.exists():
        return f"File not found: {file_path}"
    if not full_path.is_file():
        return f"Not a file: {file_path}"

    try:
        content = full_path.read_text(errors="replace")
    except PermissionError:
        return f"Permission denied: {file_path}"

    lines = content.splitlines()
    total = len(lines)
    start_line = max(1, start_line)

    if end_line is None:
        end_line = min(start_line + settings.max_file_lines - 1, total)
    end_line = min(end_line, total)

    selected = lines[start_line - 1 : end_line]
    numbered = [f"{i + start_line:4d} │ {line}" for i, line in enumerate(selected)]

    header = f"── {file_path} ({start_line}-{end_line} of {total} lines) ──"
    result = header + "\n" + "\n".join(numbered)

    if end_line < total:
        result += f"\n── Use read_file('{file_path}', start_line={end_line + 1}) to continue ──"

    return result


@tool
def get_symbols(file_path: str) -> str:
    """Extract code symbols (functions, classes, methods) from a file via tree-sitter AST parsing.

    Args:
        file_path: Path relative to workspace root.
    """
    full_path = _safe_path(file_path)
    if not full_path.is_file():
        return f"File not found: {file_path}"

    symbols = extract_symbols(str(full_path))
    if not symbols:
        lang = detect_language(file_path)
        if not lang:
            return f"Unsupported language for: {file_path}"
        return f"No symbols extracted from {file_path}"

    lines: list[str] = []
    for s in symbols:
        indent = "    " if s["parent"] else ""
        lines.append(
            f"{indent}{s['type']} {s['name']} (L{s['line']}-{s['end_line']}): {s['signature']}"
        )
    return "\n".join(lines)


@tool
def get_repo_map(path: str = ".", file_glob: str | None = None) -> str:
    """Generate a symbol map of the repository — a bird's-eye view of all
    functions, classes, and methods across the codebase. Extremely useful
    for understanding project structure before deep-diving into files.

    Args:
        path: Base directory relative to workspace root.
        file_glob: Optional glob filter, e.g. "**/*.py", "**/*.java".
    """
    target = _safe_path(path)
    workspace = Path(settings.workspace_path).resolve()

    glob_pattern = file_glob or "**/*"
    result_lines: list[str] = []
    file_count = 0
    symbol_count = 0

    for f in sorted(target.glob(glob_pattern)):
        if not f.is_file() or _should_ignore(f):
            continue
        if not detect_language(str(f)):
            continue

        symbols = extract_symbols(str(f))
        if not symbols:
            continue

        rel = str(f.relative_to(workspace))
        result_lines.append(f"\n── {rel} ──")
        for s in symbols:
            indent = "    " if s["parent"] else ""
            result_lines.append(
                f"  {indent}{s['type']} {s['name']} (L{s['line']}): {s['signature']}"
            )
            symbol_count += 1

        file_count += 1
        if file_count >= 200:
            result_lines.append("\n... (limited to 200 files)")
            break

    if not result_lines:
        return "No parseable source files found. (Is tree-sitter-languages installed?)"

    header = f"Repository symbol map — {file_count} files, {symbol_count} symbols:"
    return header + "\n".join(result_lines)
